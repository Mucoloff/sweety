package dev.sweety.netty.backend;

import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.messaging.impl.SimpleServer;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.netty.packet.registry.PacketRegistry;
import dev.sweety.util.logger.SimpleLogger;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.net.SocketAddress;

public abstract class LobbyBackend extends SimpleServer {

    protected final SimpleLogger lobbyLogger;

    public LobbyBackend(String host, int port, PacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
        this.lobbyLogger = SimpleLogger.of("LobbyBackend-" + port);
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        if (!AutoReconnect.exception(throwable)) this.lobbyLogger.profile("exception").error(throwable);
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        this.lobbyLogger.profile("connect").info(Messenger.address(ctx.channel()));
        super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        this.lobbyLogger.profile("disconnect").info(Messenger.address(ctx.channel()));
        promise.setSuccess();
    }

    public SimpleLogger lobbyLogger() {
        return lobbyLogger;
    }
}
