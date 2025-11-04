package dev.sweety;

import test.loadbalancer.Main;

public class RunClient {
    public static void main(String[] args) throws Throwable {

        Main.main(new String[]{"client", "127.0.0.1", "8080"});
    }
}