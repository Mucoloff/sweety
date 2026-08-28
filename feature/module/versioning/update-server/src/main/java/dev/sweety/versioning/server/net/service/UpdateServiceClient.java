package dev.sweety.versioning.server.net.service;

import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.netty.service.ServiceClient;
import dev.sweety.util.logger.SimpleLogger;

/**
 * Connects the {@code update-server} to the central internal Netty Service Mesh (port 9902).
 * Registered under {@code SERVICE_ID_UPDATE = 8} (or configured service ID).
 */
public final class UpdateServiceClient extends ServiceClient {

    private static final SimpleLogger LOGGER = SimpleLogger.of(UpdateServiceClient.class);
    public static final int SERVICE_ID_UPDATE = 8;

    public UpdateServiceClient(String hubHost, int hubPort, PacketRegistry registry, String secret) {
        super(hubHost, hubPort, SERVICE_ID_UPDATE, registry, secret);
    }

    public static UpdateServiceClient of(String hubHost, int hubPort, PacketRegistry registry, String secret) {
        return new UpdateServiceClient(hubHost, hubPort, registry, secret);
    }

    @Override
    public Packet handle(int senderServiceId, Packet packet) {
        LOGGER.info("Received service mesh packet from service " + senderServiceId + ": " + packet.getClass().getSimpleName());
        // Handle incoming mesh broadcast/RPC commands (e.g. publish new release, purge cache)
        return null;
    }
}
