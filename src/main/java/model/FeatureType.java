package model;

public enum FeatureType {
    RISK_SCORE,             // 0.0 to 1.0 double
    TRANSACTION_VELOCITY,   // count of txns in last N minutes
    DEVICE_FINGERPRINT,     // string hash of device
    ACCOUNT_AGE_DAYS,       // long
    IS_FLAGGED              // boolean
}
