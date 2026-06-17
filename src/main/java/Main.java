import server.FeatureStoreServer;
import server.RestApiServer;
import store.FeatureStore;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        FeatureStore store = new FeatureStore(100);

        RestApiServer restServer = new RestApiServer(8080, store);
        restServer.start();

        FeatureStoreServer tcpServer = new FeatureStoreServer(9999, store);
        tcpServer.start(); // blocks forever, so put this last
    }
}