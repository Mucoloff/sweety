package dev.sweety.saas.service.packet.global.monitoring.transaction;

import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.packet.global.monitoring.request.MonitoringServiceControlRequest;
import dev.sweety.saas.service.packet.global.monitoring.response.MonitoringServiceControlResponse;

public class MonitoringServiceControlTransaction extends PacketTransaction<MonitoringServiceControlRequest, MonitoringServiceControlResponse> {

    public MonitoringServiceControlTransaction() {
        super();
    }

    public MonitoringServiceControlTransaction(MonitoringServiceControlRequest request) {
        super(request);
    }

    public MonitoringServiceControlTransaction(long id, MonitoringServiceControlResponse response) {
        super(id, response);
    }

    @Override
    protected MonitoringServiceControlRequest request() {
        return new MonitoringServiceControlRequest();
    }

    @Override
    protected MonitoringServiceControlResponse response() {
        return new MonitoringServiceControlResponse();
    }
}
