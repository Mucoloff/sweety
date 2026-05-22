package dev.sweety.versioning.server;

import java.io.IOException;

/**
 * @deprecated Use {@link dev.sweety.versioning.server.infra.MainServer} directly.
 */
@Deprecated
public class MainServer {

    public static void main(String[] args) throws IOException {
        dev.sweety.versioning.server.infra.MainServer.main(args);
    }
}
