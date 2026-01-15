package dev.sweety.project.netty.lb;

import dev.sweety.core.thread.ProfileThread;
import dev.sweety.core.thread.ThreadManager;
import dev.sweety.netty.loadbalancer.packets.InternalPacket;
import dev.sweety.netty.loadbalancer.v2.LBServer;
import dev.sweety.netty.loadbalancer.v2.Node;
import dev.sweety.netty.loadbalancer.v2.NodePool;
import dev.sweety.netty.packet.model.Packet;
import dev.sweety.netty.packet.registry.IPacketRegistry;
import dev.sweety.netty.packet.registry.OptimizedPacketRegistry;
import dev.sweety.project.netty.packet.text.TextPacket;

public class LBTest {

    private final ThreadManager threadManager = new ThreadManager();

    private final IPacketRegistry registry = new OptimizedPacketRegistry(TextPacket.class, InternalPacket.class);

    private final BackendTest backend1 = new BackendTest("127.0.0.1", 30001, registry);
    private final BackendTest backend2 = new BackendTest("127.0.0.1", 30002, registry);

    private final Node node1 = new Node("127.0.0.1", 30001, registry);
    private final Node node2 = new Node("127.0.0.2", 30002, registry);

    private final NodePool pool = new NodePool(null, node1, node2);

    private final LBServer loadBalancer = new LBServer("127.0.0.1", 25565, pool, registry);

    private final ClientTest client = new ClientTest("127.0.0.1", 25565, registry);

    LBTest() throws Throwable {
    }

    private ProfileThread newThread() {
        return this.threadManager.getAvailableProfileThread();
    }

    public void run() throws Throwable {
        newThread().execute(backend1::start);
        newThread().execute(backend2::start);
        newThread().execute(loadBalancer::start);

        Thread.sleep(2000L);

        newThread().execute(client::start);

        Thread.sleep(2000L);

        Packet[] packets = new Packet[]{
                new TextPacket("Ciao, questo è un messaggio di prova 1"),
                new TextPacket("Ciao, questo è un messaggio di prova 2"),
                new TextPacket("Ciao, questo è un messaggio di prova 3"),
                new TextPacket("Ciao, questo è un messaggio di prova 4"),
                new TextPacket("Ciao, questo è un messaggio di prova 5"),
        };

        client.sendPacket(packets);
    }

    public static void main(String[] args) throws Throwable {
        new LBTest().run();

        while (true) {
            Thread.onSpinWait();
        }
    }

}
