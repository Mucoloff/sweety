package dev.sweety.project.netty.loadbalancer.test;

import dev.sweety.project.netty.loadbalancer.Main;

public class RunBackend2 {
    public static void main(String[] args) throws Throwable {

        Main.main(new String[]{"backend", "127.0.0.1", "8082"});
    }
}