package io.riskplatform.vertx;

import java.io.IOException;
import java.net.ServerSocket;

final class TestPorts {
    private TestPorts() {
    }

    static int freePort() {
        try (var socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not allocate a free test port", ex);
        }
    }
}
