package dev.sweety.netty.tls;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;

import javax.net.ssl.KeyManagerFactory;
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
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Centralized TLS configuration for sweety Netty connections.
 *
 * Server side: loads cert + key from files (mounted via Docker or configured via env/prop).
 * Client side: loads pinned CA cert from classpath resource.
 *
 * Env vars:
 *   SWEETY_TLS_ENABLED         — "true" to enable TLS (default: false)
 *   SWEETY_TLS_CERT            — path to server certificate PEM (server only)
 *   SWEETY_TLS_KEY             — path to server private key PEM (server only)
 *   SWEETY_TLS_ALLOW_PLAINTEXT — "true" only on closed dev networks
 */
public final class Tls {

    private static final String CA_RESOURCE = "tls/sweety-ca.crt";

    private static final AtomicBoolean PLAINTEXT_WARN_EMITTED = new AtomicBoolean(false);

    private Tls() {}

    public static boolean isEnabled() {
        final String prop = System.getProperty("sweety.tls.enabled");
        if (prop != null) return Boolean.parseBoolean(prop);
        final String env = System.getenv("SWEETY_TLS_ENABLED");
        if (env != null) return Boolean.parseBoolean(env);
        return Tls.class.getClassLoader().getResource(CA_RESOURCE) != null;
    }

    public static boolean isPlaintextExplicitlyAllowed() {
        final String prop = System.getProperty("sweety.tls.allow.plaintext");
        if (prop != null) return Boolean.parseBoolean(prop);
        return Boolean.parseBoolean(System.getenv().getOrDefault("SWEETY_TLS_ALLOW_PLAINTEXT", "false"));
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
        System.err.println(bar);
        System.err.println("  SWEETY SECURITY WARNING — TLS DISABLED (" + componentLabel + ")");
        System.err.println("  SWEETY_TLS_ALLOW_PLAINTEXT=true and Tls.isEnabled()=false.");
        System.err.println("  All peers must use the same mode. Do not use outside a trusted dev network.");
        System.err.println(bar);
    }

    private static void fatalBanner(final String componentLabel) {
        final String bar = "=".repeat(80);
        System.err.println(bar);
        System.err.println("  SWEETY WILL NOT START — TLS IS REQUIRED");
        System.err.println("  Component: " + componentLabel);
        System.err.println("  Fix: set SWEETY_TLS_ENABLED=true and mount cert/key, OR set");
        System.err.println("       SWEETY_TLS_ALLOW_PLAINTEXT=true on every mesh process for dev only.");
        System.err.println(bar);
    }

    public static SslContext serverContext() throws SSLException {
        final String certPath = resolve("sweety.tls.cert", "SWEETY_TLS_CERT", "/app/certs/server.crt");
        final String keyPath  = resolve("sweety.tls.key",  "SWEETY_TLS_KEY",  "/app/certs/server.key");

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

        final String keyPem = Files.readString(keyFile.toPath());
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

    public static SslContext clientContext() throws SSLException {
        try (InputStream caStream = Tls.class.getClassLoader().getResourceAsStream(CA_RESOURCE)) {
            if (caStream == null)
                throw new SSLException("Pinned CA certificate not found in classpath: " + CA_RESOURCE);

            final CertificateFactory cf = CertificateFactory.getInstance("X.509");
            final Certificate caCert = cf.generateCertificate(caStream);

            final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("sweety-ca", caCert);

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

    private static String resolve(String propKey, String envKey, String fallback) {
        final String prop = System.getProperty(propKey);
        if (prop != null && !prop.isBlank()) return prop.trim();
        final String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) return env.trim();
        return fallback;
    }
}
