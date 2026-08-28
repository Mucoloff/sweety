package dev.sweety.launcher.service;

import dev.sweety.versioning.protocol.integrity.IntegrityResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Out-of-band integrity probe: asks the update server (over the authenticated Netty channel) for the
 * rolling SHA-256 of the bytes it has served for a download token so far, plus the final HMAC/Ed25519
 * tags once complete. Used for live progress and an authenticated cross-check independent of the HTTP
 * transport. Implemented by the Netty adapter.
 */
@FunctionalInterface
public interface IntegrityProbePort {

    CompletableFuture<IntegrityResponse> probe(String token);
}
