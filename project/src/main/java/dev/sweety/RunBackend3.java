package dev.sweety;

import test.loadbalancer.Main;

public class RunBackend3 {
    public static void main(String[] args) throws Throwable {

        Main.main(new String[]{"backend", "127.0.0.1", "8083"});
    }
}