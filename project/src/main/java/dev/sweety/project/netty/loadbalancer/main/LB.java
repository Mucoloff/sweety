package dev.sweety.project.netty.loadbalancer.main;

import dev.sweety.netty.loadbalancer.refact.lb.LBServer;
import dev.sweety.netty.loadbalancer.refact.lb.backend.Node;
import dev.sweety.netty.loadbalancer.refact.lb.pool.INodePool;
import dev.sweety.netty.loadbalancer.refact.lb.pool.NodePool;

public class LB {

    public static void main(String[] args) {

        final Node node1 = new Node(LBSettings.BK1_HOST, LBSettings.BK1_PORT, LBSettings.registry);
        final Node node2 = new Node(LBSettings.BK2_HOST, LBSettings.BK2_PORT, LBSettings.registry);
        final INodePool pool = new NodePool(null, node1, node2);

        final LBServer loadBalancer = new LBServer(LBSettings.LB_HOST, LBSettings.LB_PORT, pool, LBSettings.registry);

        loadBalancer.start();

        while (true) {
            Thread.onSpinWait();
        }

    }

}
