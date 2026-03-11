package dev.sweety.netty.loadbalancer.backend;

import dev.sweety.core.color.AnsiColor;
import dev.sweety.logger.SimpleLogger;
import dev.sweety.thread.ProfileThread;
import dev.sweety.netty.feature.AutoReconnect;
import dev.sweety.netty.loadbalancer.common.backend.IBackend;
import dev.sweety.netty.messaging.Server;
import dev.sweety.netty.messaging.model.Messenger;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public abstract class LobbyBackend<T extends Backend> extends Server implements IBackend {

    private final ProfileThread backendThread;
    private final SimpleLogger lobbyLogger;
    protected final T backend;

    public LobbyBackend(String lobbyHost, int lobbyPort, T backend) {
        super(lobbyHost, lobbyPort, backend.packetRegistry());
        this.backend = backend;
        this.lobbyLogger = new SimpleLogger("Lobby");
        this.backendThread = new ProfileThread("backend-" + backend.typeName());
    }

    @Override
    public Channel start() {
        this.backendThread.execute(this.backend::start);
        return super.start();
    }

    @Override
    public void stop() {
        this.backendThread.execute(this.backend::stop);
        this.backendThread.shutdown();
        super.stop();
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        if (!AutoReconnect.exception(throwable)) this.lobbyLogger.push("exception").error(throwable).pop();
        ctx.close();
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        this.lobbyLogger.push("connect", AnsiColor.GREEN_BRIGHT).info(Messenger.address(ctx.channel())).pop();
        super.addClient(ctx, ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        this.lobbyLogger.push("disconnect", AnsiColor.RED_BRIGHT).info(Messenger.address(ctx.channel())).pop();
        super.removeClient(ctx.channel().remoteAddress());
        promise.setSuccess();
    }

    public SimpleLogger lobbyLogger() {
        return lobbyLogger;
    }

    public T backend() {
        return backend;
    }

    public abstract int typeId();

}
