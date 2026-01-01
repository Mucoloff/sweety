package dev.sweety.loadbalancer.test;

import dev.sweety.loadbalancer.Main;

public class RunBackend2 {
    public static void main(String[] args) throws Throwable {

        Main.main(new String[]{"backend", "127.0.0.1", "8082"});
    }
}