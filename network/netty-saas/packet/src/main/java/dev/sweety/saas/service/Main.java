package dev.sweety.saas.service;

import dev.sweety.saas.service.config.ServiceNodeConfig;
import dev.sweety.saas.service.config.ServicesConfig;

import java.io.File;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        File file = new File("services.yml");


        new ServicesConfig(new ServiceNodeConfig(ServiceType.of("hub"), "127.0.0.1", 4000),
                Map.of(ServiceType.of("lobby"), List.of(
                                new ServiceNodeConfig(ServiceType.of("lobby"), "127.0.0.1", 5002, 5003),
                                new ServiceNodeConfig(ServiceType.of("lobby"), "127.0.0.1", 5004, 5005)
                        ),
                        ServiceType.of("database"), List.of(new ServiceNodeConfig(ServiceType.of("database"), "127.0.0.1", 5100)),
                        ServiceType.of("service"), List.of(
                                new ServiceNodeConfig(ServiceType.of("service"), "127.0.0.1", 5200),
                                new ServiceNodeConfig(ServiceType.of("service"), "127.0.0.1", 5202))
                )
        ).save(file);

        ServiceInitializer.create(args, file, (config, id) -> {
            System.out.println(config);
            return null;
        });
    }

}
