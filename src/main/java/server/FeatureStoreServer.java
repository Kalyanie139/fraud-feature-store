package server;

import store.FeatureStore;

import java.io.*;
import java.net.*;

public class FeatureStoreServer {

    private final int port;
    private final FeatureStore store;

    public FeatureStoreServer(int port, FeatureStore store) {
        this.port = port;
        this.store = store;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("FeatureStoreServer listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept(); // blocks until a client connects
                System.out.println("New client connected: " + clientSocket.getInetAddress());

                Thread clientThread = new Thread(new ClientHandler(clientSocket, store));
                clientThread.start();
            }
        }
    }
}