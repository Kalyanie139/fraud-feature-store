package model;

public class FeatureEntry {
    private final String accountId;
    private final FeatureType featureType;
    private final Object value;           // typed value
    private final RiskTier riskTier;
    private final long expiryTimeMs;      // System.currentTimeMillis() + ttl

    public FeatureEntry(String accountId, FeatureType featureType,
                        Object value, RiskTier riskTier, long ttlSeconds) {
        this.accountId = accountId;
        this.featureType = featureType;
        this.value = value;
        this.riskTier = riskTier;
        this.expiryTimeMs = System.currentTimeMillis() + (ttlSeconds * 1000);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTimeMs;
    }

    // Getters
    public String getAccountId() { return accountId; }
    public FeatureType getFeatureType() { return featureType; }
    public Object getValue() { return value; }
    public RiskTier getRiskTier() { return riskTier; }
    public long getExpiryTimeMs() { return expiryTimeMs; }

    @Override
    public String toString() {
        return String.format("[%s | %s | %s | %s | expires in %ds]",
            accountId, featureType, value, riskTier,
            (expiryTimeMs - System.currentTimeMillis()) / 1000);
    }
}
