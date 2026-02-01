package dev.sweety.project.netty.loadbalancer.main;

import dev.sweety.netty.loadbalancer.server.LoadBalancerServer;
import dev.sweety.netty.loadbalancer.server.backend.BackendNode;
import dev.sweety.netty.loadbalancer.server.balancer.Balancers;
import dev.sweety.netty.loadbalancer.server.pool.IBackendNodePool;
import dev.sweety.netty.loadbalancer.server.pool.BackendNodePool;

public class LB {

    public static void main(String[] args) {

        final BackendNode node1 = new BackendNode(LBSettings.BK1_HOST, LBSettings.BK1_PORT, LBSettings.registry);
        final BackendNode node2 = new BackendNode(LBSettings.BK2_HOST, LBSettings.BK2_PORT, LBSettings.registry);
        final IBackendNodePool pool = new BackendNodePool(Balancers.ROUND_OPTIMIZED_PACKET_ADAPTIVE.get(), node1, node2);

        final LoadBalancerServer loadBalancer = new LoadBalancerServer(LBSettings.LB_HOST, LBSettings.LB_PORT, pool, LBSettings.registry);
        //loadBalancer.useThreadManager();
        loadBalancer.start();

        while (true) {
            Thread.onSpinWait();
        }

    }

}
