package dev.sweety.saas.hub.backend;

import dev.sweety.netty.metrics.EMA;
import dev.sweety.netty.packet.MetricsUpdatePacket;
import dev.sweety.netty.packet.internal.InternalPacket;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.server.backend.BackendNode;
import dev.sweety.saas.hub.ServiceHub;
import dev.sweety.saas.hub.backend.handler.HandlerRegistry;
import dev.sweety.saas.hub.backend.handler.ServiceNodeHandler;
import dev.sweety.saas.service.IService;
import dev.sweety.saas.service.ServiceType;
import dev.sweety.saas.service.config.ServiceNodeConfig;
import dev.sweety.saas.service.packet.global.handshake.SystemConnection;
import dev.sweety.saas.service.packet.global.handshake.SystemConnectionTransaction;
import dev.sweety.saas.service.packet.global.monitoring.request.MonitoringMetricReportRequest;
import dev.sweety.saas.service.packet.global.monitoring.response.MonitoringMetricReportResponse;
import dev.sweety.saas.service.packet.global.monitoring.transaction.MonitoringMetricReportTransaction;
import dev.sweety.saas.service.packet.global.ping.SystemPing;
import dev.sweety.saas.service.packet.global.ping.SystemPong;
import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static dev.sweety.netty.messaging.model.Messenger.timeMode;

public class ServiceNode extends BackendNode implements IService {

    private final ServiceNodeConfig backend;

    private final EMA pingEMA = new EMA(0.75f);
    private final ServiceNodeHandler handler;
    private final Map<String, Long> metrics = new ConcurrentHashMap<>();

    public ServiceNode(ServiceHub hub, ServiceNodeConfig backend) {
        super(hub, backend.getPort(), backend.getType() != null ? backend.getType().id() : -1);
        this.backend = backend;
        this.handler = HandlerRegistry.INSTANCE.create(this);
    }

    public ServiceHub hub() {
        return ((ServiceHub) loadBalancer);
    }

    @Override
    public ServiceType type() {
        return backend.getType();
    }

    @Override
    public int typeId() {
        return IService.super.typeId();
    }

    @Override
    public String host() {
        return backend.getHost();
    }

    @Override
    public String typeName() {
        final ServiceType t = backend.getType();
        return t != null ? t.name() : "UNKNOWN";
    }

    @Override
    public boolean handled(Packet packet) {
        return packet instanceof MetricsUpdatePacket ||
                packet instanceof InternalPacket ||
                packet instanceof SystemPing ||
                packet instanceof SystemPong ||
                packet instanceof SystemConnectionTransaction ||
                packet instanceof MonitoringMetricReportTransaction ||
                handler.handled(packet) ||
                super.handled(packet);
    }

    @Override
    public void disconnect() {
        super.disconnect();
        handler.disconnect();
    }

    @Override
    public void onPacketReceive(final ChannelHandlerContext ctx, final Packet packet) {
        if (packet instanceof MetricsUpdatePacket met) {
            super.onPacketReceive(ctx, packet);
            handler.metricsUpdate(met.load());
            return;
        } else if (packet instanceof InternalPacket) {
            super.onPacketReceive(ctx, packet);
            return;
        }
        super.ctx = ctx;

        final long now = timeMode.now();
        switch (packet) {
            //case SystemPing ping -> sendToSelf(new SystemPong(now));
            case SystemPong pong -> {
                long time = pong.timestamp();

                float timing = pingEMA.update(now - time);
                handler.keepAlive(timing);
                // Schedule next ping after 5s — NOT immediately (was causing infinite tight loop)
                if (super.ctx != null && super.ctx.channel().isActive()) {
                    super.ctx.channel().eventLoop().schedule(
                            () -> sendToSelf(new SystemPing(timeMode.now())),
                            5, TimeUnit.SECONDS
                    );
                }
            }

            case SystemConnectionTransaction systemConnection -> {
                final Optional<SystemConnection> opt = systemConnection.get();

                if (opt.isPresent()) {
                    final SystemConnection connection = opt.get();
                    if (connection.request()) {
                        final ServiceType connectedType = connection.serviceType();

                        if (type() != null && connectedType != type()) {
                            logger().warn("Service node " + host() + " identified as " + connectedType + " but is not of type " + type());
                            sendToSelf(new SystemConnectionTransaction(systemConnection.getRequestId(),
                                    new SystemConnection(connectedType, SystemConnection.State.TIMEOUT)));
                            return;
                        }

                        // Promote placeholder node: update type and register in pool
                        if (type() == null && connectedType != null) {
                            backend.setType(connectedType);
                            hub().services().promoteNode(connectedType, this);
                            logger().info("Service node " + host() + " self-identified as " + connectedType + " (promoted from placeholder)");
                        } else {
                            logger().info("Service node " + host() + " identified as " + connectedType);
                        }

                        sendToSelf(new SystemConnectionTransaction(systemConnection.getRequestId(),
                                new SystemConnection(connectedType, SystemConnection.State.RESPONSE)));
                    }
                }

            }
            case MonitoringMetricReportTransaction monitoring when monitoring.hasRequest() -> {
                final MonitoringMetricReportRequest request = monitoring.getRequest();

                final boolean success;
                if (request.metrics() != null) {
                    metrics.putAll(request.metrics());
                    this.handler.metricsUpdate(metrics);
                    success = true;
                } else success = false;

                //todo graphana/prometheus

                //todo monitoring

                sendToSelf(new MonitoringMetricReportTransaction(monitoring.getRequestId(), new MonitoringMetricReportResponse(success)));
            }
            case null, default -> handler.handle(ctx, packet);
        }

    }

    @Override
    public String toString() {
        return super.toString();
    }

    public ServiceNodeConfig backend() {
        return backend;
    }

    public EMA pingEMA() {
        return pingEMA;
    }

    public ServiceNodeHandler handler() {
        return handler;
    }

    public Map<String, Long> metrics() {
        return metrics;
    }
}