package dev.sweety.project.netty.loadbalancer.main;

import dev.sweety.netty.loadbalancer.server.LoadBalancerServer;
import dev.sweety.netty.loadbalancer.server.backend.Node;
import dev.sweety.netty.loadbalancer.server.balancer.Balancers;
import dev.sweety.netty.loadbalancer.server.pool.INodePool;
import dev.sweety.netty.loadbalancer.server.pool.NodePool;

public class LB {

    public static void main(String[] args) {

        final Node node1 = new Node(LBSettings.BK1_HOST, LBSettings.BK1_PORT, LBSettings.registry);
        final Node node2 = new Node(LBSettings.BK2_HOST, LBSettings.BK2_PORT, LBSettings.registry);
        final INodePool pool = new NodePool(Balancers.ROUND_OPTIMIZED_PACKET_ADAPTIVE.get(), node1, node2);

        final LoadBalancerServer loadBalancer = new LoadBalancerServer(LBSettings.LB_HOST, LBSettings.LB_PORT, pool, LBSettings.registry);

        loadBalancer.start();

        while (true) {
            Thread.onSpinWait();
        }

    }

}
