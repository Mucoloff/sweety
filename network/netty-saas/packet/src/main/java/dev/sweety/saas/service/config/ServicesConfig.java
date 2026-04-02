package dev.sweety.saas.service.config;

import dev.sweety.config.common.Configuration;
import dev.sweety.config.yml.YamlConfiguration;
import dev.sweety.saas.service.ServiceType;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Writer;
import java.util.*;
import java.util.function.Supplier;


public class ServicesConfig {

    private static final Supplier<Configuration> get = YamlConfiguration::new;

    private final ServiceNodeConfig hub;
    private final Map<ServiceType, List<ServiceNodeConfig>> services;

    public ServicesConfig(ServiceNodeConfig hub, Map<ServiceType, List<ServiceNodeConfig>> services) {
        this.hub = hub;
        this.services = services;
    }

    public static ServicesConfig load(File configFile) {

        final Configuration configuration = get.get();
        configuration.load(configFile);

        ServiceNodeConfig hub = configuration.getSerializable("hub", ServiceNodeConfig.class);
        if (hub != null) {
            hub.setType(ServiceType.of("hub"));
            hub.setIndex(0);
        }

        final Map<String, ServiceClusterConfig> rawServices = configuration.getSerializableMap("services", ServiceClusterConfig.class);

        Map<ServiceType, List<ServiceNodeConfig>> services = new HashMap<>();

        rawServices.forEach((typeName, cluster) -> {
            final List<ServiceNodeConfig> nodes = new ArrayList<>();
            ServiceType type = ServiceType.of(typeName);


            final List<String> hosts = cluster.getHosts();
            final List<Integer> ports = cluster.getPorts();
            final List<Integer> localPorts = cluster.getExternalPorts();

            int count = cluster.getCount();
            if (hosts != null) count = Math.max(count, hosts.size());
            if (ports != null) count = Math.max(count, ports.size());
            if (localPorts != null) count = Math.max(count, localPorts.size());

            for (int i = 0; i < count; i++) {
                final String host = (hosts != null && hosts.size() > i) ? hosts.get(i) : "127.0.0.1";
                final int port = (ports != null && ports.size() > i) ? ports.get(i) : 0;
                final int localPort = (localPorts != null && localPorts.size() > i) ? localPorts.get(i) : -1;

                final ServiceNodeConfig node = new ServiceNodeConfig(type, host, port, localPort, i);
                nodes.add(node);
            }

            services.put(type, nodes);
        });

        return new ServicesConfig(hub, services);
    }

    public void save(File file) {
        final Configuration configuration = get.get();
        configuration.set("hub", hub);

        final Map<String, ServiceClusterConfig> services = new HashMap<>();

        this.services.forEach((type, nodes) -> {
            final List<String> hosts = new ArrayList<>();
            final List<Integer> ports = new ArrayList<>();
            final List<Integer> localPorts = new ArrayList<>();

            for (ServiceNodeConfig node : nodes) {
                hosts.add(node.getHost());
                ports.add(node.getPort());
                if (node.getExternalPort() != null) localPorts.add(node.getExternalPort());
            }

            ServiceClusterConfig cluster = new ServiceClusterConfig(nodes.size(), hosts, ports, localPorts);
            services.put(type.name(), cluster);
        });

        configuration.set("services", services);
        configuration.save(file);
    }

    public String hubHost() {
        return hub != null ? hub.getHost() : null;
    }

    public int hubPort() {
        return hub != null ? hub.getPort() : 0;
    }

    public int hubHealthPort() {
        return hub != null ? (hub.getExternalPort() != null ? hub.getExternalPort() : (hub.getPort() + 1)) : 0;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        final Configuration configuration = get.get();
        configuration.set("hub", hub);
        configuration.set("services", services);

        configuration.save(new Writer() {
            @Override
            public void write(char @NotNull [] str, int offset, int len) {
                builder.append(str, offset, len);
            }

            @Override
            public void flush() {

            }

            @Override
            public void close() {

            }
        });


        return builder.toString();
    }

    public ServiceNodeConfig service(ServiceType type, int index) {
        final List<ServiceNodeConfig> list = this.services.get(type);
        if (list == null) throw new IllegalArgumentException("No such service type: " + type);
        if (list.isEmpty()) throw new IllegalArgumentException("No nodes for service type: " + type);
        if (index < 0 || index >= list.size()) throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        return list.get(index);
    }

    public List<ServiceNodeConfig> getServiceNodes(ServiceType type) {
        return this.services.getOrDefault(type, Collections.emptyList());
    }

}
