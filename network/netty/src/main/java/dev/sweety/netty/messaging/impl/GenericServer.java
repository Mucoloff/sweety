package dev.sweety.netty.messaging.impl;

import dev.sweety.netty.packet.registry.IPacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.function.BiConsumer;

public class GenericServer extends SimpleServer {
    private BiConsumer<ChannelHandlerContext, ChannelPromise> joinHandler;
    private BiConsumer<ChannelHandlerContext, ChannelPromise> quitHandler;
    private BiConsumer<ChannelHandlerContext, Throwable> exceptionHandler;
    private BiConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet> packetReceiveHandler;
    private dev.sweety.math.function.TriConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet, Boolean> packetSendHandler;

    public GenericServer(String host, int port, IPacketRegistry packetRegistry) {
        super(host, port, packetRegistry);
    }

    public void setPacketReceiveHandler(BiConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet> packetReceiveHandler) {
        this.packetReceiveHandler = packetReceiveHandler;
    }

    public void setPacketSendHandler(dev.sweety.math.function.TriConsumer<ChannelHandlerContext, dev.sweety.netty.packet.model.Packet, Boolean> packetSendHandler) {
        this.packetSendHandler = packetSendHandler;
    }

    public void setJoinHandler(BiConsumer<ChannelHandlerContext, ChannelPromise> joinHandler) {
        this.joinHandler = joinHandler;
    }

    public void setQuitHandler(BiConsumer<ChannelHandlerContext, ChannelPromise> quitHandler) {
        this.quitHandler = quitHandler;
    }

    public void setExceptionHandler(BiConsumer<ChannelHandlerContext, Throwable> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public void join(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (joinHandler != null) joinHandler.accept(ctx, promise);
        else super.join(ctx, promise);
    }

    @Override
    public void quit(ChannelHandlerContext ctx, ChannelPromise promise) {
        if (quitHandler != null) quitHandler.accept(ctx, promise);
        else super.quit(ctx, promise);
    }

    @Override
    public void onPacketReceive(ChannelHandlerContext ctx, dev.sweety.netty.packet.model.Packet packet) {
        if (packetReceiveHandler != null) packetReceiveHandler.accept(ctx, packet);
        else super.onPacketReceive(ctx, packet);
    }

    @Override
    public void onPacketSend(ChannelHandlerContext ctx, dev.sweety.netty.packet.model.Packet packet, boolean pre) {
        if (packetSendHandler != null) packetSendHandler.accept(ctx, packet, pre);
        else super.onPacketSend(ctx, packet, pre);
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        if (exceptionHandler != null) exceptionHandler.accept(ctx, throwable);
        else super.exception(ctx, throwable);
    }
}
