package dev.sweety.saas.service.packet.global.monitoring.transaction;


import dev.sweety.netty.packet.model.PacketTransaction;
import dev.sweety.saas.service.packet.global.monitoring.request.MonitoringMetricReportRequest;
import dev.sweety.saas.service.packet.global.monitoring.response.MonitoringMetricReportResponse;

public class MonitoringMetricReportTransaction extends PacketTransaction<MonitoringMetricReportRequest, MonitoringMetricReportResponse> {


    public MonitoringMetricReportTransaction(MonitoringMetricReportRequest request) {
        super(request);
    }

    public MonitoringMetricReportTransaction(long id, MonitoringMetricReportResponse response) {
        super(id, response);
    }

    public MonitoringMetricReportTransaction(int _id, long _timestamp, byte[] _data) {
        super(_id, _timestamp, _data);
    }

    @Override
    protected MonitoringMetricReportRequest request() {
        return new MonitoringMetricReportRequest();
    }

    @Override
    protected MonitoringMetricReportResponse response() {
        return new MonitoringMetricReportResponse();
    }
}
