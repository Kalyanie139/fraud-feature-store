package server;

import command.CommandParser;
import model.FeatureEntry;
import store.FeatureStore;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private final FeatureStore store;

    public ClientHandler(Socket socket, FeatureStore store) {
        this.socket = socket;
        this.store = store;
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                String response = handleCommand(line);
                out.println(response);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected or error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private String handleCommand(String rawLine) {
        try {
            CommandParser.ParsedCommand cmd = CommandParser.parse(rawLine);

            switch (cmd.action) {
                case "SET":
                    store.set(cmd.accountId, cmd.featureType, cmd.value, cmd.riskTier, cmd.ttlSeconds);
                    return "OK";

                case "GET":
                    Optional<FeatureEntry> entry = store.get(cmd.accountId, cmd.featureType);
                    return entry.isPresent() ? entry.get().toString() : "NULL";

                case "DEL":
                    boolean deleted = store.delete(cmd.accountId, cmd.featureType);
                    return deleted ? "DELETED" : "NOT_FOUND";

                case "EXISTS":
                    return store.exists(cmd.accountId, cmd.featureType) ? "TRUE" : "FALSE";

                case "SHEET":
                    Map<model.FeatureType, Object> sheet = store.getAccountSheet(cmd.accountId);
                    return sheet.toString();

                default:
                    return "ERROR: Unknown command";
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}