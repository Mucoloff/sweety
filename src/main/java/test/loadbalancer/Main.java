package test.loadbalancer;

import dev.sweety.core.logger.EcstacyLogger;
import dev.sweety.core.time.TimeUtils;
import dev.sweety.network.cloud.impl.file.FilePacket;
import dev.sweety.network.cloud.impl.loadbalancer.ForwardPacket;
import dev.sweety.network.cloud.impl.loadbalancer.WrappedPacket;
import dev.sweety.network.cloud.impl.loadbalancer.MetricsUpdatePacket;
import dev.sweety.network.cloud.impl.text.TextPacket;
import dev.sweety.network.cloud.loadbalancer.LoadBalancerServer;
import dev.sweety.network.cloud.loadbalancer.backend.BackendNode;
import dev.sweety.network.cloud.loadbalancer.backend.pool.BackendPool;
import dev.sweety.network.cloud.loadbalancer.backend.pool.balancer.Balancers;
import dev.sweety.network.cloud.packet.registry.IPacketRegistry;
import dev.sweety.network.cloud.packet.registry.OptimizedPacketRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final String HOST = "127.0.0.1";
    private static final int LB_PORT = 8080;
    private static final int BACKEND_1_PORT = 8081;
    private static final int BACKEND_2_PORT = 8082;

    private static IPacketRegistry packetRegistry;

    public static void main(String[] args) throws Throwable {
        // Modalità CLI per eseguire ogni componente in un processo separato.
        // Usage:
        //  - java -cp <cp> test.loadbalancer.Main local
        //  - java -cp <cp> test.loadbalancer.Main backend <host> <port>
        //  - java -cp <cp> test.loadbalancer.Main lb <host> <port> <backendList>
        //      backendList -> comma separated host:port (es. 127.0.0.1:8081,127.0.0.1:8082)
        //  - java -cp <cp> test.loadbalancer.Main client <host> <port>

        packetRegistry = new OptimizedPacketRegistry(
                TextPacket.class,
                FilePacket.class,
                MetricsUpdatePacket.class,
                ForwardPacket.class,
                WrappedPacket.class
        );

        if (args.length == 0) {
            System.out.println("Nessuna modalità specificata. Avvio in modalità 'local' (embedded threads) per compatibilità.");
            runLocalEmbedded();
            return;
        }

        String mode = args[0].toLowerCase();
        switch (mode) {
            case "local":
                runLocalEmbedded();
                break;

            case "backend":
                if (args.length < 3) {
                    System.out.println("Usage: backend <host> <port>");
                    return;
                }
                String bh = args[1];
                int bp = Integer.parseInt(args[2]);
                new DummyBackend(bh, bp, packetRegistry).start();
                // backend runs indefinitely
                break;

            case "lb":
                if (args.length < 4) {
                    System.out.println("Usage: lb <host> <port> <backendList>");
                    System.out.println("backendList example: 127.0.0.1:8081,127.0.0.1:8082");
                    return;
                }
                String lbHost = args[1];
                int lbPort = Integer.parseInt(args[2]);
                String backendList = args[3];
                List<BackendNode> backends = parseBackendList(backendList);
                LoadBalancerServer loadBalancer = new LoadBalancerServer(lbPort, lbHost,
                        new BackendPool(new EcstacyLogger("pool").fallback(), backends, Balancers.OPTIMIZED_ADAPTIVE.get()), packetRegistry);
                loadBalancer.start();


                break;

            case "client":
                if (args.length < 3) {
                    System.out.println("Usage: client <host> <port>");
                    return;
                }
                String ch = args[1];
                int cp = Integer.parseInt(args[2]);
                runClientAgainst(ch, cp);
                break;

            default:
                System.out.println("Modalità non riconosciuta: " + mode);
                System.out.println("Usa: local | backend | lb | client");
        }
    }

    private static List<BackendNode> parseBackendList(String backendList) {
        String[] parts = backendList.split(",");
        List<BackendNode> out = new ArrayList<>();
        for (String p : parts) {
            String[] hp = p.trim().split(":");
            if (hp.length != 2) continue;
            out.add(new BackendNode(hp[0], Integer.parseInt(hp[1]), packetRegistry));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Modalità "embedded" (stesso comportamento di prima): utile per testing
    // ------------------------------------------------------------------
    private static void runLocalEmbedded() throws InterruptedException {
        // 1. Avvia i server di backend
        new Thread(() -> new DummyBackend(HOST, BACKEND_1_PORT, packetRegistry).start()).start();
        new Thread(() -> new DummyBackend(HOST, BACKEND_2_PORT, packetRegistry).start()).start();
        System.out.println("Backend avviati sulle porte " + BACKEND_1_PORT + " e " + BACKEND_2_PORT);

        TimeUnit.SECONDS.sleep(2);

        // 2. Configura e avvia il Load Balancer
        List<BackendNode> backends = List.of(
                new BackendNode(HOST, BACKEND_1_PORT, packetRegistry),
                new BackendNode(HOST, BACKEND_2_PORT, packetRegistry)
        );
        LoadBalancerServer loadBalancer = new LoadBalancerServer(LB_PORT, HOST, new BackendPool(new EcstacyLogger("pool").fallback(), backends, Balancers.OPTIMIZED_ADAPTIVE.get()), packetRegistry);
        new Thread(loadBalancer::start).start();
        System.out.println("Load Balancer in avvio sulla porta " + LB_PORT);

        TimeUnit.SECONDS.sleep(2);

        // 3. Avvia il client e invia pacchetti
        DummyClient client = new DummyClient(HOST, LB_PORT, packetRegistry);

        final int n = 1;
        client.connect().thenRun(() -> {
            System.out.println("Client sta inviando " + n + " pacchetti di testo...");

            TimeUtils.sleep(3, TimeUnit.SECONDS);

            for (int i = 0; i < n; i++) {
                System.out.println("\n\n\n\nInvio pacchetto #" + (i + 1));
                client.sendPacket(new TextPacket("Messaggio di test numero " + i));
                TimeUtils.sleep(50, TimeUnit.MILLISECONDS);
            }
        });

        TimeUtils.sleep(50, TimeUnit.SECONDS);

        // 4. Arresto
        System.out.println("Arresto dell'applicazione...");
        client.stop();
        loadBalancer.stop();
        System.exit(0);
    }

    private static void runClientAgainst(String host, int port) {
        DummyClient client = new DummyClient(host, port, packetRegistry);
        client.start();
        client.sendPacket(new TextPacket("Messaggio di prova da processo client"));

        Runtime.getRuntime().addShutdownHook(new Thread(client::stop));

        while (true) {
            Thread.onSpinWait();
        }
    }
}