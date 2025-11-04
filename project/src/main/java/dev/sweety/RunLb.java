package dev.sweety;

import test.loadbalancer.Main;

public class RunLb {
    public static void main(String[] args) throws Throwable {

        Main.main(new String[]{"lb", "127.0.0.1", "8080", "127.0.0.1:8081,127.0.0.1:8082,127.0.0.1:8083"});
    }
}