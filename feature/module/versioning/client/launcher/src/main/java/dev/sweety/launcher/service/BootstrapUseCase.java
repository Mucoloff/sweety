package dev.sweety.launcher.service;

/**
 * Driving port: bootstrap the launcher (connect to update server, launch managed process).
 */
public interface BootstrapUseCase {

    void bootstrap(String[] args) throws Exception;
}
