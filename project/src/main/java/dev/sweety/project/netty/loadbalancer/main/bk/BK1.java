package dev.sweety.project.netty.loadbalancer.main.bk;

import dev.sweety.project.netty.loadbalancer.impl.BackendTest;
import dev.sweety.project.netty.loadbalancer.main.LBSettings;

public class BK1 {

    public static void main(String[] args) {
        final BackendTest backend1 = new BackendTest(LBSettings.BK1_HOST, LBSettings.BK1_PORT, LBSettings.registry);
        backend1.start();
        while (true){
            Thread.onSpinWait();
        }

    }

}
