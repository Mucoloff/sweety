package dev.sweety.launcher.update;

import dev.sweety.launcher.adapter.out.netty.NettyUpdaterClient;
import dev.sweety.launcher.infra.LauncherConfig;
import dev.sweety.netty.packet.registry.IPacketRegistry;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @deprecated Use {@link NettyUpdaterClient} directly.
 *             This class is kept for backward compatibility only.
 */
@Deprecated
public class UpdaterClient extends NettyUpdaterClient {

    public UpdaterClient(AtomicReference<LauncherConfig> config,
                         IPacketRegistry packetRegistry,
                         UpdateManager updateManager,
                         Runnable stop) {
        super(config, packetRegistry, updateManager, stop);
    }

    public UpdaterClient(AtomicReference<LauncherConfig> config,
                         IPacketRegistry packetRegistry,
                         int localPort,
                         UpdateManager updateManager,
                         Runnable stop) {
        super(config, packetRegistry, localPort, updateManager, stop);
    }
}
