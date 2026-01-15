package dev.sweety.project.netty.loadbalancer;

import dev.sweety.core.logger.SimpleLogger;
import dev.sweety.core.time.TimeUtils;
import dev.sweety.netty.loadbalancer.LoadBalancerServer;
import dev.sweety.netty.loadbalancer.backend.BackendNode;
import dev.sweety.netty.loadbalancer.backend.pool.BackendPool;
import dev.sweety.netty.loadbalancer.backend.pool.balancer.Balancers;
import dev.sweety.netty.loadbalancer.packets.ForwardPacket;
import dev.sweety.netty.loadbalancer.packets.MetricsUpdatePacket;
import dev.sweety.netty.loadbalancer.packets.WrappedPacket;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.project.netty.packet.file.FilePacket;
import dev.sweety.project.netty.packet.text.TextPacket;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final String HOST = "127.0.0.1";
    private static final int LB_PORT = 8080;

    private static final int[] BACKENDS = {8081, 8082, 8083};

    public static final Balancers balancerSystem = Balancers.OPTIMIZED_ADAPTIVE;

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
                WrappedPacket.class,
                PlayerPacket.class
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
                        new BackendPool(new SimpleLogger("pool"), backends, new ACBalancer(balancerSystem)), packetRegistry);
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
        for (int backend : BACKENDS) {
            new Thread(() -> new DummyBackend(HOST, backend, packetRegistry).start()).start();
        }

        System.out.println("Backend avviati sulle porte " + Arrays.toString(BACKENDS));

        TimeUnit.SECONDS.sleep(2);

        // 2. Configura e avvia il Load Balancer
        List<BackendNode> backends = Arrays.stream(BACKENDS).mapToObj(port -> new BackendNode(HOST, port, packetRegistry)).toList();

        LoadBalancerServer loadBalancer = new LoadBalancerServer(LB_PORT, HOST, new BackendPool(new SimpleLogger("pool"), backends, new ACBalancer(balancerSystem)), packetRegistry);
        new Thread(loadBalancer::start).start();
        System.out.println("Load Balancer in avvio sulla porta " + LB_PORT);

        TimeUnit.SECONDS.sleep(2);

        // 3. Avvia il client e invia pacchetti
        DummyClient client = new DummyClient(HOST, LB_PORT, packetRegistry);

        final int n = 8;
        client.start();
        System.out.println("Ogni player sta inviando " + n + " pacchetti di testo...");

        /*
        TimeUtils.sleep(3, TimeUnit.SECONDS);

        for (int i = 0; i < n; i++) {
            System.out.println("\n\n\n\nInvio pacchetto #" + (i + 1));
            client.sendPacket(new TextPacket("Messaggio di test numero " + i));
            TimeUtils.sleep(50, TimeUnit.MILLISECONDS);
        }
         */

        PlayerPacket[] packets = new PlayerPacket[n * n];

        for (int i = 0; i < n; i++) {
            UUID id = UUID.randomUUID();
            for (int j = 0; j < n; j++) {
                packets[i * n + j] = new PlayerPacket(id, "Player #" + i + " - Messaggio #" + (i * n + j));
            }
        }

        Collections.shuffle(Arrays.asList(packets));

        for (PlayerPacket packet : packets) {
            System.out.println("\n\n");
            client.sendPacket(packet);
            TimeUtils.sleep(50, TimeUnit.MILLISECONDS);
        }

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