package dev.sweety.project.netty.loadbalancer.main.bk;

import dev.sweety.project.netty.loadbalancer.impl.BackendTest;
import dev.sweety.project.netty.loadbalancer.main.LBSettings;

public class BK2 {

    public static void main(String[] args) {
        final BackendTest backend2 = new BackendTest(LBSettings.BK2_HOST, LBSettings.BK2_PORT, LBSettings.registry);
        backend2.start();
        while (true) {
            Thread.onSpinWait();
        }

    }

}
