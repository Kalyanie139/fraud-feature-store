package server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import store.FeatureStore;
import model.FeatureEntry;
import model.FeatureType;
import model.RiskTier;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

public class RestApiServer {

    private final int port;
    private final FeatureStore store;

    public RestApiServer(int port, FeatureStore store) {
        this.port = port;
        this.store = store;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/features", new FeatureHandler());
        server.createContext("/sheet", new SheetHandler());

        server.setExecutor(null); // uses a default thread-per-request executor
        server.start();

        System.out.println("RestApiServer listening on port " + port);
    }

    // Handles GET and POST for /features/{accountId}/{featureType}
    private class FeatureHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath(); // e.g. /features/acc_123/RISK_SCORE
            String[] parts = path.split("/");

            if (parts.length != 4) {
                sendResponse(exchange, 400, "Expected /features/{accountId}/{featureType}");
                return;
            }

            String accountId = parts[2];
            String featureTypeStr = parts[3];

            try {
                FeatureType featureType = FeatureType.valueOf(featureTypeStr.toUpperCase());

                if ("GET".equals(exchange.getRequestMethod())) {
                    handleGet(exchange, accountId, featureType);
                } else if ("POST".equals(exchange.getRequestMethod())) {
                    handlePost(exchange, accountId, featureType);
                } else {
                    sendResponse(exchange, 405, "Method not allowed");
                }
            } catch (IllegalArgumentException e) {
                sendResponse(exchange, 400, "Unknown feature type: " + featureTypeStr);
            }
        }

        private void handleGet(HttpExchange exchange, String accountId, FeatureType featureType) throws IOException {
            Optional<FeatureEntry> entry = store.get(accountId, featureType);
            if (entry.isPresent()) {
                sendResponse(exchange, 200, entry.get().toString());
            } else {
                sendResponse(exchange, 404, "Not found");
            }
        }

        private void handlePost(HttpExchange exchange, String accountId, FeatureType featureType) throws IOException {
            String body = readBody(exchange); // expected format: value,riskTier,ttlSeconds  e.g. "0.87,RED,300"
            String[] fields = body.trim().split(",");

            if (fields.length != 3) {
                sendResponse(exchange, 400, "Expected body format: value,riskTier,ttlSeconds");
                return;
            }

            try {
                Object value = parseValue(featureType, fields[0]);
                RiskTier riskTier = RiskTier.valueOf(fields[1].toUpperCase());
                long ttlSeconds = Long.parseLong(fields[2]);

                store.set(accountId, featureType, value, riskTier, ttlSeconds);
                sendResponse(exchange, 200, "OK");
            } catch (Exception e) {
                sendResponse(exchange, 400, "Invalid request: " + e.getMessage());
            }
        }
    }

    // Handles GET for /sheet/{accountId}
    private class SheetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath(); // e.g. /sheet/acc_123
            String[] parts = path.split("/");

            if (parts.length != 3 || !"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 400, "Expected GET /sheet/{accountId}");
                return;
            }

            String accountId = parts[2];
            Map<FeatureType, Object> sheet = store.getAccountSheet(accountId);
            sendResponse(exchange, 200, sheet.toString());
        }
    }

    private static Object parseValue(FeatureType type, String raw) {
        switch (type) {
            case RISK_SCORE: return Double.parseDouble(raw);
            case IS_FLAGGED: return Boolean.parseBoolean(raw);
            case TRANSACTION_VELOCITY: return Integer.parseInt(raw);
            case ACCOUNT_AGE_DAYS: return Long.parseLong(raw);
            case DEVICE_FINGERPRINT: return raw;
            default: throw new IllegalArgumentException("Unhandled type: " + type);
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}