package store;


import model.FeatureEntry;
import model.FeatureType;
import model.RiskTier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FeatureStore {

    private final ConcurrentHashMap<String, FeatureEntry> store;
    private final int maxCapacity;

    public FeatureStore(int maxCapacity) {
        this.store = new ConcurrentHashMap<>();
        this.maxCapacity = maxCapacity;
    }

    // ─── Internal Key ────────────────────────────────────────────
    private String buildKey(String accountId, FeatureType featureType) {
        return accountId + ":" + featureType.name();
    }

    // ─── SET ─────────────────────────────────────────────────────
   public synchronized void set(String accountId, FeatureType featureType,
                Object value, RiskTier riskTier, long ttlSeconds) {

    if (store.size() >= maxCapacity) {
        evict();
    }

    String key = buildKey(accountId, featureType);
    FeatureEntry entry = new FeatureEntry(accountId, featureType,
                                           value, riskTier, ttlSeconds);
    store.put(key, entry);
    }

    // ─── GET ─────────────────────────────────────────────────────
    public Optional<FeatureEntry> get(String accountId, FeatureType featureType) {
        String key = buildKey(accountId, featureType);
        FeatureEntry entry = store.get(key);

        if (entry == null) return Optional.empty();

        // Lazy expiry check — delete if stale
        if (entry.isExpired()) {
            store.remove(key);
            return Optional.empty();
        }

        return Optional.of(entry);
    }

    // ─── DELETE ──────────────────────────────────────────────────
    public boolean delete(String accountId, FeatureType featureType) {
        String key = buildKey(accountId, featureType);
        return store.remove(key) != null;
    }

    // ─── EXISTS ──────────────────────────────────────────────────
    public boolean exists(String accountId, FeatureType featureType) {
        return get(accountId, featureType).isPresent();
    }

    // ─── GET ALL FEATURES FOR AN ACCOUNT ─────────────────────────
    public Map<FeatureType, Object> getAccountSheet(String accountId) {
        Map<FeatureType, Object> sheet = new HashMap<>();

        for (Map.Entry<String, FeatureEntry> entry : store.entrySet()) {
            FeatureEntry fe = entry.getValue();
            if (fe.getAccountId().equals(accountId) && !fe.isExpired()) {
                sheet.put(fe.getFeatureType(), fe.getValue());
            }
        }
        return sheet;
    }

    // ─── EVICTION ────────────────────────────────────────────────
    private void evict() {
        // Try evicting GREEN first, then AMBER, leave RED last
        for (RiskTier tier : new RiskTier[]{RiskTier.GREEN, RiskTier.AMBER, RiskTier.RED}) {
            for (Map.Entry<String, FeatureEntry> entry : store.entrySet()) {
                if (entry.getValue().getRiskTier() == tier) {
                    store.remove(entry.getKey());
                    return; // evict one at a time
                }
            }
        }
    }

    // ─── PURGE EXPIRED ───────────────────────────────────────────
    public void purgeExpired() {
        store.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    // ─── STATS ───────────────────────────────────────────────────
    public int size() { return store.size(); }
    public int capacity() { return maxCapacity; }

    public void printStats() {
        System.out.println("=== FeatureStore Stats ===");
        System.out.println("Entries: " + store.size() + "/" + maxCapacity);
        store.forEach((k, v) -> System.out.println("  " + v));
    }
}