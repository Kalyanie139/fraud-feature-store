package model;

public enum RiskTier {
    GREEN,   // low risk - evicted first when memory is full
    AMBER,   // medium risk
    RED      // high risk - stays in memory longest
}
