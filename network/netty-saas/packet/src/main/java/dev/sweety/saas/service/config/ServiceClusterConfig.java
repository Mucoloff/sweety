package dev.sweety.saas.service.config;

import dev.sweety.config.common.serialization.ConfigSerializable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceClusterConfig implements ConfigSerializable {

    private final int count;
    private final List<String> hosts;
    private final List<Integer> ports;
    private final List<Integer> externalPorts;

    public ServiceClusterConfig(int count, List<String> hosts, List<Integer> ports, List<Integer> externalPorts) {
        this.count = count;
        this.hosts = hosts;
        this.ports = ports;
        this.externalPorts = externalPorts;
    }

    public ServiceClusterConfig(Map<String, Object> me) {
        this.count = Integer.parseInt(String.valueOf(me.get("count")));
        this.hosts = me.get("hosts") instanceof List<?> list ? list.stream().map(String::valueOf).toList() : new ArrayList<>(0);
        this.ports = me.get("ports") instanceof List<?> list ? list.stream().map(String::valueOf).map(Integer::parseInt).toList() : new ArrayList<>(0);
        this.externalPorts = me.get("externalPorts") instanceof List<?> list ? list.stream().map(String::valueOf).map(Integer::parseInt).toList() : new ArrayList<>(0);
    }

    public int getCount() { return count; }
    public List<String> getHosts() { return hosts; }
    public List<Integer> getPorts() { return ports; }
    public List<Integer> getExternalPorts() { return externalPorts; }

    @Override
    public Map<String, Object> serialize() {
        final Map<String, Object> me = new HashMap<>(4);
        me.put("count", count);
        me.put("hosts", hosts);
        me.put("ports", ports);
        if (!externalPorts.isEmpty()) me.put("externalPorts", externalPorts);
        return me;
    }
}
