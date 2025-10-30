package test.loadbalancer;

import dev.sweety.core.time.TimeUtils;
import dev.sweety.network.cloud.impl.text.TextPacket;
import dev.sweety.network.cloud.loadbalancer.BackendNode;
import dev.sweety.network.cloud.loadbalancer.LoadBalancerServer;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final String HOST = "127.0.0.1";
    private static final int LB_PORT = 8080;
    private static final int BACKEND_1_PORT = 8081;
    private static final int BACKEND_2_PORT = 8082;

    public static void main(String[] args) throws InterruptedException {
        // 1. Avvia i server di backend
        new Thread(() -> new DummyBackend(HOST, BACKEND_1_PORT).start()).start();
        new Thread(() -> new DummyBackend(HOST, BACKEND_2_PORT).start()).start();
        System.out.println("Backend avviati sulle porte " + BACKEND_1_PORT + " e " + BACKEND_2_PORT);

        TimeUnit.SECONDS.sleep(2);

        // 2. Configura e avvia il Load Balancer
        List<BackendNode> backends = List.of(
                new BackendNode(HOST, BACKEND_1_PORT),
                new BackendNode(HOST, BACKEND_2_PORT)
        );
        LoadBalancerServer loadBalancer = new LoadBalancerServer(LB_PORT, HOST, backends);
        new Thread(loadBalancer::start).start();
        System.out.println("Load Balancer in avvio sulla porta " + LB_PORT);

        TimeUnit.SECONDS.sleep(2);

        // 3. Avvia il client e invia pacchetti
        DummyClient client = new DummyClient(HOST, LB_PORT);

        final int n = 3;
        client.connect().thenRun(() -> {
            System.out.println("Client sta inviando " + n + " pacchetti di testo...");

            TimeUtils.sleep(5,TimeUnit.SECONDS);

            for (int i = 0; i < n; i++) {
                System.out.println("\n\n\n\n\n\nInvio pacchetto #" + (i + 1));
                // Usiamo un pacchetto concreto come TextPacket
                client.sendPacket(new TextPacket.Out("Messaggio di test numero " + i));
                TimeUtils.sleep(5,TimeUnit.SECONDS);
            }
        });

        TimeUtils.sleep(5,TimeUnit.MINUTES);

        // 4. Arresto
        System.out.println("Arresto dell'applicazione...");
        client.stop();
        loadBalancer.stop();
        System.exit(0);
    }
}