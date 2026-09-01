package dev.sweety.saas.service.packet.global.monitoring.transaction;

import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.packet.global.monitoring.request.MonitoringGetMetricsRequest;
import dev.sweety.saas.service.packet.global.monitoring.response.MonitoringGetMetricsResponse;

public class MonitoringGetMetricsTransaction extends PacketTransaction<MonitoringGetMetricsRequest, MonitoringGetMetricsResponse> {

    public MonitoringGetMetricsTransaction() {
        super();
    }

    public MonitoringGetMetricsTransaction(MonitoringGetMetricsRequest request) {
        super(request);
    }

    public MonitoringGetMetricsTransaction(long id, MonitoringGetMetricsResponse response) {
        super(id, response);
    }

    @Override
    protected MonitoringGetMetricsRequest request() {
        return new MonitoringGetMetricsRequest();
    }

    @Override
    protected MonitoringGetMetricsResponse response() {
        return new MonitoringGetMetricsResponse();
    }
}
