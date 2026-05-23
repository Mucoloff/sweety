package dev.sweety.netty.messaging.impl;

import dev.sweety.math.function.TriConsumer;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.PacketRegistry;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.function.BiConsumer;

public class GenericClient extends SimpleClient {
    private BiConsumer<ChannelHandlerContext, ChannelPromise> joinHandler;
    private BiConsumer<ChannelHandlerContext, ChannelPromise> quitHandler;
    private BiConsumer<ChannelHandlerContext, Throwable> exceptionHandler;
    private BiConsumer<ChannelHandlerContext, Packet> packetReceiveHandler;
    private TriConsumer<ChannelHandlerContext, Packet, Boolean> packetSendHandler;

    public GenericClient(String host, int port, PacketRegistry packetRegistry) {
        this(host, port, packetRegistry, -1);
    }

    public GenericClient(String host, int port, PacketRegistry packetRegistry, int localPort) {
        super(host, port, packetRegistry, localPort);
    }

    public void setPacketReceiveHandler(BiConsumer<ChannelHandlerContext, Packet> packetReceiveHandler) {
        this.packetReceiveHandler = packetReceiveHandler;
    }

    public void setPacketSendHandler(TriConsumer<ChannelHandlerContext, Packet, Boolean> packetSendHandler) {
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
    public void onPacketReceive(ChannelHandlerContext ctx, Packet packet) {
        if (packetReceiveHandler != null) packetReceiveHandler.accept(ctx, packet);
    }

    @Override
    public void onPacketSend(ChannelHandlerContext ctx, Packet packet, boolean pre) {
        if (packetSendHandler != null) packetSendHandler.accept(ctx, packet, pre);
        else super.onPacketSend(ctx, packet, pre);
    }

    @Override
    public void exception(ChannelHandlerContext ctx, Throwable throwable) {
        if (exceptionHandler != null) exceptionHandler.accept(ctx, throwable);
        else super.exception(ctx, throwable);
    }
}
