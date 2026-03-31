package dev.sweety.saas.service.config;

import dev.sweety.config.common.serialization.ConfigSerializable;
import dev.sweety.saas.service.ServiceType;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ServiceNodeConfig implements ConfigSerializable {

    private ServiceType type;

    private String host;

    private int port;

    private Integer externalPort;

    private Integer index;

    public ServiceNodeConfig(ServiceType type, String host, int port, Integer externalPort, Integer index) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.externalPort = externalPort;
        this.index = index;
    }

    public ServiceNodeConfig(ServiceType type, String host, int port, Integer externalPort) {
        this(type, host, port, externalPort, null);
    }

    public ServiceNodeConfig(ServiceType type, String host, int port) {
        this(type, host, port, null);
    }

    public ServiceNodeConfig(Map<String, Object> me) {
        final Object portValue = me.get("port");
        if (portValue == null) throw new IllegalArgumentException("port is required");

        this.host = me.get("host") != null ? String.valueOf(me.get("host")) : null;
        this.port = Integer.parseInt(String.valueOf(portValue));

        final Object externalPortValue = me.get("externalPort");
        this.externalPort = externalPortValue != null
                ? Integer.parseInt(String.valueOf(externalPortValue))
                : null;
    }

    @Override
    public Map<String, Object> serialize() {
        final Map<String, Object> me = new HashMap<>(3);
        me.put("host", this.host);
        me.put("port", this.port);
        if (this.externalPort != null) me.put("externalPort", this.externalPort);
        return me;
    }
}