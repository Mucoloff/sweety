package dev.sweety.netty.tls;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import dev.sweety.util.logger.SimpleLogger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLException;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centralized TLS configuration for luce Netty connections.
 *
 * Server side: loads cert + key from files (mounted via Docker or configured via env/prop).
 * Client side: loads pinned CA cert from classpath resource.
 *
 * Env vars:
 *   LUCE_TLS_ENABLED         — "true" to enable TLS (default: false)
 *   LUCE_TLS_CERT            — path to server certificate PEM (server only)
 *   LUCE_TLS_KEY             — path to server private key PEM (server only)
 *   LUCE_TLS_ALLOW_PLAINTEXT — "true" only on closed dev networks
 */
public final class Tls {

    private static final SimpleLogger logger = SimpleLogger.of(Tls.class);

    private static final String CA_RESOURCE = "tls/luce-ca.crt";

    private static final AtomicBoolean PLAINTEXT_WARN_EMITTED = new AtomicBoolean(false);

    private Tls() {}

    /** TLS is mandatory — always on, never config/env toggleable (matches {@code SessionSettings.TLS_ENABLED}). */
    public static boolean isEnabled() {
        return true;
    }

    public static boolean isPlaintextExplicitlyAllowed() {
        final String prop = System.getProperty("luce.tls.allow.plaintext");
        if (prop != null) return Boolean.parseBoolean(prop);
        return Boolean.parseBoolean(System.getenv().getOrDefault("LUCE_TLS_ALLOW_PLAINTEXT", "false"));
    }

    public static void enforceTlsPolicy(final String componentLabel) {
        if (isPlaintextExplicitlyAllowed()) {
            if (!isEnabled()) plaintextWarnOnce(componentLabel);
            return;
        }
        if (!isEnabled()) {
            fatalBanner(componentLabel);
            System.exit(1);
        }
    }

    private static void plaintextWarnOnce(final String componentLabel) {
        if (!PLAINTEXT_WARN_EMITTED.compareAndSet(false, true)) return;
        final String bar = "=".repeat(80);
        logger.profile("policy").warn(String.join("\n", bar,
                "  LUCE SECURITY WARNING — TLS DISABLED (" + componentLabel + ")",
                "  LUCE_TLS_ALLOW_PLAINTEXT=true and Tls.isEnabled()=false.",
                "  All peers must use the same mode. Do not use outside a trusted dev network.",
                bar));
    }

    private static void fatalBanner(final String componentLabel) {
        final String bar = "=".repeat(80);
        logger.profile("policy").error(String.join("\n", bar,
                "  LUCE WILL NOT START — TLS IS REQUIRED",
                "  Component: " + componentLabel,
                "  Fix: set LUCE_TLS_ENABLED=true and mount cert/key, OR set",
                "       LUCE_TLS_ALLOW_PLAINTEXT=true on every mesh process for dev only.",
                bar));
    }

    public static SslContext serverContext() throws SSLException {
        return serverContext(
                resolve("luce.tls.cert", "LUCE_TLS_CERT", "/app/certs/server.crt"),
                resolve("luce.tls.key",  "LUCE_TLS_KEY",  "/app/certs/server.key"));
    }

    public static SslContext serverContext(String certPath, String keyPath) throws SSLException {

        final File certFile = new File(certPath);
        final File keyFile  = new File(keyPath);

        if (!certFile.exists()) throw new SSLException("TLS cert not found: " + certPath);
        if (!keyFile.exists())  throw new SSLException("TLS key not found: " + keyPath);

        try {
            final KeyManagerFactory kmf = buildKeyManagerFactory(certFile, keyFile);
            return SslContext.newServerContext(SslProvider.JDK, null, null,
                    null, null, null, kmf,
                    null, (ciphers, defaultCiphers, supportedCiphers) ->
                            supportedCiphers.toArray(new String[0]),
                    null, 0, 0);
        } catch (SSLException e) {
            throw e;
        } catch (Exception e) {
            throw new SSLException("Failed to build server TLS context", e);
        }
    }

    private static KeyManagerFactory buildKeyManagerFactory(File certFile, File keyFile) throws Exception {
        final CertificateFactory cf = CertificateFactory.getInstance("X.509");
        final X509Certificate cert;
        try (InputStream certStream = Files.newInputStream(certFile.toPath())) {
            cert = (X509Certificate) cf.generateCertificate(certStream);
        }

        final String keyPem;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(Files.newInputStream(keyFile.toPath()), java.nio.charset.StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            keyPem = sb.toString();
        }
        final String keyBase64 = keyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        final byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        final PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

        PrivateKey privateKey;
        try {
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (Exception e) {
            privateKey = KeyFactory.getInstance("EC").generatePrivate(keySpec);
        }

        final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setKeyEntry("server", privateKey, new char[0], new Certificate[]{cert});

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);
        return kmf;
    }

    /**
     * Builds a JDK {@link SSLContext} for the built-in {@code com.sun.net.httpserver.HttpsServer},
     * reusing the same cert/key PEMs as the Netty server context. Lets the HTTP download/webhook
     * endpoints run over TLS with one cert source.
     */
    public static SSLContext httpsServerContext() throws SSLException {
        return httpsServerContext(
                resolve("luce.tls.cert", "LUCE_TLS_CERT", "/app/certs/server.crt"),
                resolve("luce.tls.key",  "LUCE_TLS_KEY",  "/app/certs/server.key"));
    }

    public static SSLContext httpsServerContext(String certPath, String keyPath) throws SSLException {
        final File certFile = new File(certPath);
        final File keyFile  = new File(keyPath);
        if (!certFile.exists()) throw new SSLException("TLS cert not found: " + certPath);
        if (!keyFile.exists())  throw new SSLException("TLS key not found: " + keyPath);
        try {
            final KeyManagerFactory kmf = buildKeyManagerFactory(certFile, keyFile);
            final SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            return ctx;
        } catch (SSLException e) {
            throw e;
        } catch (Exception e) {
            throw new SSLException("Failed to build HTTPS server context", e);
        }
    }

    /** Dev-only HTTPS context backed by a self-signed cert. NEVER use in production. */
    public static SSLContext devHttpsServerContext() throws SSLException {
        try {
            final SelfSignedCertificate ssc = new SelfSignedCertificate();
            final KeyManagerFactory kmf = buildKeyManagerFactory(ssc.certificate(), ssc.privateKey());
            final SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), null, null);
            return ctx;
        } catch (SSLException e) {
            throw e;
        } catch (Exception e) {
            throw new SSLException("Failed to build dev HTTPS context", e);
        }
    }

    public static SslContext clientContext() throws SSLException {
        try (InputStream caStream = Tls.class.getClassLoader().getResourceAsStream(CA_RESOURCE)) {
            if (caStream == null)
                throw new SSLException("Pinned CA certificate not found in classpath: " + CA_RESOURCE);

            final CertificateFactory cf = CertificateFactory.getInstance("X.509");
            final Certificate caCert = cf.generateCertificate(caStream);

            final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("luce-ca", caCert);

            final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            return SslContext.newClientContext(SslProvider.JDK, null, tmf,
                    null, (ciphers, defaultCiphers, supportedCiphers) ->
                            supportedCiphers.toArray(new String[0]),
                    null, 0, 0);
        } catch (SSLException e) {
            throw e;
        } catch (Exception e) {
            throw new SSLException("Failed to build client TLS context from embedded CA", e);
        }
    }

    /**
     * Dev TLS mode: use a self-signed cert (server) + trust-all (client) instead of real cert
     * files / pinned CA. Best-effort for local testing only — NEVER enable in production.
     */
    public static boolean devMode() {
        final String prop = System.getProperty("luce.tls.dev");
        if (prop != null) return Boolean.parseBoolean(prop);
        return Boolean.parseBoolean(System.getenv().getOrDefault("LUCE_TLS_DEV", "false"));
    }

    public static SslContext devServerContext() throws SSLException {
        try {
            final SelfSignedCertificate ssc = new SelfSignedCertificate();
            return SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } catch (CertificateException e) {
            throw new SSLException("Failed to generate self-signed certificate", e);
        }
    }

    public static SslContext devClientContext() throws SSLException {
        return SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
    }

    private static String resolve(String propKey, String envKey, String fallback) {
        final String prop = System.getProperty(propKey);
        if (prop != null && !prop.isBlank()) return prop.trim();
        final String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) return env.trim();
        return fallback;
    }
}
