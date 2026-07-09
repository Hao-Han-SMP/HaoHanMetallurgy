package dev.haohansmp.metallurgy.config;

public record CustomItemStats(
        int tier,
        int maxDamage,
        double attackDamage,
        double attackSpeed,
        double miningEfficiency,
        double blockBreakSpeed) {

    public static CustomItemStats empty() {
        return new CustomItemStats(0, 0, 0.0, 0.0, 0.0, 0.0);
    }
}
