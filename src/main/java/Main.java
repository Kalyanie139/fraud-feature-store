import server.FeatureStoreServer;
import server.RestApiServer;
import store.FeatureStore;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        FeatureStore store = new FeatureStore(100);

        // Load persisted data on startup (we'll add this in Step 2)
        store.loadFromDisk();

        RestApiServer restServer = new RestApiServer(8080, store);
        restServer.start();

        FeatureStoreServer tcpServer = new FeatureStoreServer(9999, store);

        // Register shutdown hook BEFORE starting the blocking TCP server
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutdown signal received. Saving state...");
            store.saveToDisk();
            System.out.println("Shutdown complete.");
        }));

        tcpServer.start(); // blocks forever
    }
}