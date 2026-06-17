package command;

import model.FeatureType;
import model.RiskTier;


public class CommandParser {

    public static class ParsedCommand {
        public String action;       // SET, GET, DEL, EXISTS
        public String accountId;
        public FeatureType featureType;
        public Object value;        // only used for SET
        public RiskTier riskTier;   // only used for SET
        public long ttlSeconds;     // only used for SET
    }

    public static ParsedCommand parse(String rawLine) {
        String[] parts = rawLine.trim().split("\\s+");

        if (parts.length == 0) {
            throw new IllegalArgumentException("Empty command");
        }

        ParsedCommand cmd = new ParsedCommand();
        cmd.action = parts[0].toUpperCase();

        switch (cmd.action) {
            case "SET":
                if (parts.length != 6) {
                    throw new IllegalArgumentException("SET requires: SET <accountId> <featureType> <value> <riskTier> <ttlSeconds>");
                }
                cmd.accountId = parts[1];
                cmd.featureType = FeatureType.valueOf(parts[2].toUpperCase());
                cmd.value = parseValue(cmd.featureType, parts[3]);
                cmd.riskTier = RiskTier.valueOf(parts[4].toUpperCase());
                cmd.ttlSeconds = Long.parseLong(parts[5]);
                break;

            case "GET":
            case "DEL":
            case "EXISTS":
                if (parts.length != 3) {
                    throw new IllegalArgumentException(cmd.action + " requires: " + cmd.action + " <accountId> <featureType>");
                }
                cmd.accountId = parts[1];
                cmd.featureType = FeatureType.valueOf(parts[2].toUpperCase());
                break;

            case "SHEET":
                if (parts.length != 2) {
                    throw new IllegalArgumentException("SHEET requires: SHEET <accountId>");
                }
                cmd.accountId = parts[1];
                break;

            default:
                throw new IllegalArgumentException("Unknown command: " + cmd.action);
        }

        return cmd;
    }

    private static Object parseValue(FeatureType type, String raw) {
        switch (type) {
            case RISK_SCORE:
                return Double.parseDouble(raw);
            case IS_FLAGGED:
                return Boolean.parseBoolean(raw);
            case TRANSACTION_VELOCITY:
                return Integer.parseInt(raw);
            case ACCOUNT_AGE_DAYS:
                return Long.parseLong(raw);
            case DEVICE_FINGERPRINT:
                return raw;
            default:
                throw new IllegalArgumentException("Unhandled feature type: " + type);
        }
    }
}