package dev.sweety.saas.service;

import dev.sweety.color.AnsiColor;
import dev.sweety.netty.messaging.model.Messenger;
import dev.sweety.saas.service.config.ServicesConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;

public class ServiceInitializer {

    public static <S extends Messenger> S create(final String[] args, final Path configFile, final BiFunction<ServicesConfig, Integer, S> serviceConstructor) {
        int id;
        if (args.length != 1) id = 0;
        else try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            id = 0;
        }

        try {
            if (!Files.exists(configFile))
                System.err.println("Could not find " + configFile.toAbsolutePath());

            final ServicesConfig config = ServicesConfig.load(configFile);

            System.out.println(AnsiColor.YELLOW_BRIGHT.color() + "Hub: " + config.hubHost() + ":" + config.hubPort() + AnsiColor.RESET.color());

            return serviceConstructor.apply(config, id);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
            return null;
        }
    }

    public static <S extends Messenger> void init(final String[] args, final Path configFile, final BiFunction<ServicesConfig, Integer, S> serviceConstructor) {
        final S service = create(args, configFile, serviceConstructor);
        Messenger.init(service);
    }

}
