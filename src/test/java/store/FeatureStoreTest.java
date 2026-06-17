package store;

import model.FeatureType;
import model.RiskTier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class FeatureStoreTest {

    @Test
    void testSetAndGet() {
        FeatureStore store = new FeatureStore(10);
        store.set("acc_1", FeatureType.RISK_SCORE, 0.75, RiskTier.RED, 60);

        Optional<model.FeatureEntry> result = store.get("acc_1", FeatureType.RISK_SCORE);

        assertTrue(result.isPresent());
        assertEquals(0.75, result.get().getValue());
    }

    @Test
    void testGetNonExistentReturnsEmpty() {
        FeatureStore store = new FeatureStore(10);
        Optional<model.FeatureEntry> result = store.get("acc_unknown", FeatureType.RISK_SCORE);
        assertFalse(result.isPresent());
    }

    @Test
    void testTTLExpiry() throws InterruptedException {
        FeatureStore store = new FeatureStore(10);
        store.set("acc_1", FeatureType.RISK_SCORE, 0.5, RiskTier.GREEN, 1); // 1 second TTL

        assertTrue(store.exists("acc_1", FeatureType.RISK_SCORE));

        Thread.sleep(1500); // wait past expiry

        assertFalse(store.exists("acc_1", FeatureType.RISK_SCORE));
    }

    @Test
    void testEvictionPrefersGreenFirst() {
        FeatureStore store = new FeatureStore(3);
        store.set("acc_green", FeatureType.RISK_SCORE, 0.1, RiskTier.GREEN, 60);
        store.set("acc_amber", FeatureType.RISK_SCORE, 0.5, RiskTier.AMBER, 60);
        store.set("acc_red", FeatureType.RISK_SCORE, 0.9, RiskTier.RED, 60);

        // store is full (3/3); this should evict GREEN
        store.set("acc_new", FeatureType.RISK_SCORE, 0.3, RiskTier.GREEN, 60);

        assertFalse(store.exists("acc_green", FeatureType.RISK_SCORE), "GREEN should be evicted first");
        assertTrue(store.exists("acc_amber", FeatureType.RISK_SCORE), "AMBER should survive");
        assertTrue(store.exists("acc_red", FeatureType.RISK_SCORE), "RED should survive");
    }

    @Test
    void testEvictionFallsBackToRedWhenNoChoice() {
        FeatureStore store = new FeatureStore(2);
        store.set("acc_red1", FeatureType.RISK_SCORE, 0.9, RiskTier.RED, 60);
        store.set("acc_red2", FeatureType.RISK_SCORE, 0.9, RiskTier.RED, 60);

        // no GREEN or AMBER exists; this MUST evict a RED entry
        store.set("acc_red3", FeatureType.RISK_SCORE, 0.9, RiskTier.RED, 60);

        assertEquals(2, store.size(), "Size should never exceed capacity, even under RED-only pressure");
    }

    @Test
    void testConcurrentInsertsNeverExceedCapacity() throws InterruptedException {
        FeatureStore store = new FeatureStore(5);
        int threadCount = 10;
        int opsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    store.set("acc_t" + threadId + "_" + i, FeatureType.RISK_SCORE,
                               Math.random(), RiskTier.values()[i % 3], 60);
                }
            });
        }

        for (Thread th : threads) th.start();
        for (Thread th : threads) th.join();

        assertTrue(store.size() <= store.capacity(),
            "Store size (" + store.size() + ") must never exceed capacity (" + store.capacity() + ")");
    }

    @Test
    void testDeleteRemovesEntry() {
        FeatureStore store = new FeatureStore(10);
        store.set("acc_1", FeatureType.IS_FLAGGED, true, RiskTier.RED, 60);

        assertTrue(store.delete("acc_1", FeatureType.IS_FLAGGED));
        assertFalse(store.exists("acc_1", FeatureType.IS_FLAGGED));
        assertFalse(store.delete("acc_1", FeatureType.IS_FLAGGED), "Deleting again should return false");
    }
}