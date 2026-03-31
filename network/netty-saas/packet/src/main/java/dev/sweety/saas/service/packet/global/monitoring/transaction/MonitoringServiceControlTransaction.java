package dev.sweety.saas.service.packet.global.monitoring.transaction;


import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.packet.global.monitoring.request.MonitoringServiceControlRequest;
import dev.sweety.saas.service.packet.global.monitoring.response.MonitoringServiceControlResponse;

public class MonitoringServiceControlTransaction extends PacketTransaction<MonitoringServiceControlRequest, MonitoringServiceControlResponse> {


    public MonitoringServiceControlTransaction(MonitoringServiceControlRequest request) {
        super(request);
    }

    public MonitoringServiceControlTransaction(long id, MonitoringServiceControlResponse response) {
        super(id, response);
    }

    public MonitoringServiceControlTransaction(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
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
