# FraudFS — Fraud Detection Feature Store

A purpose-built, in-memory feature store for real-time fraud detection systems, written in Java. Supports both a raw TCP protocol and a REST API, backed by a single thread-safe, risk-tier-aware storage engine with TTL expiry and capacity-bound eviction.

## Why this exists

During the PSB Cybersecurity, Fraud & AI Hackathon 2026 (Bank of India x IIT Hyderabad), my team built an XGBoost-based mule account detection pipeline (AUC-ROC ~0.986) using a three-tier risk scoring framework: GREEN, AMBER, RED. That project highlighted a gap: a fraud model in production needs sub-millisecond access to live features (transaction velocity, current risk score, device flags) during inference, and querying a full database on every transaction is too slow for that.

This project is the infrastructure piece a fraud model would actually need: a lightweight, fraud-aware feature store designed around that exact problem, built independently of the hackathon codebase.

## What it does

Stores typed fraud features per account (risk score, transaction velocity, device fingerprint, account age, flagged status). Each entry has a TTL and a risk tier (GREEN, AMBER, RED). When the store hits capacity, it evicts GREEN entries first, then AMBER, and only touches RED as a last resort, verified under concurrent load including the edge case where only RED entries remain. Accessible two ways: a raw TCP server with a simple text protocol, and a REST API over HTTP, both backed by the same shared store. Thread-safe under concurrent access, with a documented fix for a check-then-act race condition found during testing (see below).

## Architecture
'''
fraud-feature-store/
├── model/
│   ├── FeatureType.java       Typed feature categories (RISK_SCORE, IS_FLAGGED, etc.)
│   ├── RiskTier.java          GREEN / AMBER / RED classification
│   └── FeatureEntry.java      A single stored value with TTL and risk tier
├── store/
│   └── FeatureStore.java      Core engine: HashMap + TTL expiry + tier-aware eviction
├── command/
│   └── CommandParser.java     Parses raw TCP text into typed commands
└── server/
    ├── FeatureStoreServer.java   TCP server, custom text protocol
    └── RestApiServer.java        HTTP server, REST endpoints
'''
    
Both servers hold a reference to the same FeatureStore instance, so a value
written over TCP is immediately visible to a read over REST, and vice versa.

'''
                +----------------+
                |   REST Client  |
                +--------+-------+
                         |
                         v
                 +---------------+
                 | RestApiServer |
                 +-------+-------+
                         |
                         |
                         v
+-------------+   +-------------+   +-------------+
| TCP Client  |-->| TCP Server  |-->| FeatureStore|
+-------------+   +-------------+   +------+------+
                                         |
                                         |
                +------------------------+
                |
        +-------+--------+
        | Domain Models  |
        | FeatureEntry   |
        | FeatureType    |
        | RiskTier       |
        +----------------+
```

## Running it

Requires Java 21 and Maven.

mvn compile
java -cp target/classes Main

This starts the REST API on port 8080 and the TCP server on port 9999.

TCP protocol example:
nc localhost 9999
SET acc_123 RISK_SCORE 0.87 RED 300
GET acc_123 RISK_SCORE
SHEET acc_123

REST API example:
curl -X POST http://localhost:8080/features/acc_123/RISK_SCORE -d "0.87,RED,300"
curl http://localhost:8080/features/acc_123/RISK_SCORE
curl http://localhost:8080/sheet/acc_123

## Testing

mvn test

7 JUnit tests cover basic set/get, TTL expiry, tier-aware eviction including the RED-only fallback case, delete behavior, and a concurrency stress test (10 threads, 2000 inserts) confirming the store never exceeds its configured capacity.

## A real bug found and fixed during development

Early testing revealed a check-then-act race condition: set() checked if the store was at capacity and then called evict() as two separate steps, allowing two threads to both observe a full store and both evict, silently violating the capacity invariant under concurrent load. Fixed by making set() synchronized, verified via repeated concurrent stress tests. This is a known tradeoff: synchronized serializes all writes, even across unrelated accounts. A future iteration could use per-key locking for better throughput.

## Known limitations

No persistence: data is in-memory only and lost on restart. No authentication on either the TCP or REST interface. No graceful shutdown; currently stopped via process kill. Single coarse lock rather than per-key locking, as noted above. These are intentional scope cuts for a focused systems project, not oversights, and I'm happy to discuss how I'd address each in a production context.

## Tech

Java 21, Maven, JUnit 5, raw sockets via java.net.ServerSocket, com.sun.net.httpserver.HttpServer with no external web framework.
