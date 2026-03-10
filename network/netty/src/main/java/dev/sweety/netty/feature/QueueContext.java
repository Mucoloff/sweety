package dev.sweety.netty.feature;

import dev.sweety.netty.packet.model.Packet;

import java.util.concurrent.CompletableFuture;

public record QueueContext<T>(CompletableFuture<T> future, Packet... packets) {
}
