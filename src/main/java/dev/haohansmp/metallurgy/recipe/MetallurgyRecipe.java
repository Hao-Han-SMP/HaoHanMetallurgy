package dev.haohansmp.metallurgy.recipe;

import org.bukkit.Material;

import java.util.List;

/**
 * Đại diện cho một recipe trong hệ thống metallurgy.
 * Được load từ file JSON trong thư mục recipes/.
 * Immutable sau khi tạo — không được thay đổi runtime.
 */
public final class MetallurgyRecipe {

    // ── Fields (khớp với JSON) ─────────────────────────────────
    private final String id;
    private final String machineType;
    private final List<Ingredient> inputs;
    private final OutputItem output;
    private final int fuelCost;
    private final int timeSeconds;
    private final int minTemperature;

    public MetallurgyRecipe(String id,
                            String machineType,
                            List<Ingredient> inputs,
                            OutputItem output,
                            int fuelCost,
                            int timeSeconds,
                            int minTemperature) {
        this.id = id;
        this.machineType = machineType;
        this.inputs = List.copyOf(inputs);
        this.output = output;
        this.fuelCost = fuelCost;
        this.timeSeconds = timeSeconds;
        this.minTemperature = minTemperature;
    }

    // ── Getters ────────────────────────────────────────────────
    public String getId()            { return id; }
    public String getMachineType()   { return machineType; }
    public List<Ingredient> getInputs() { return inputs; }
    public OutputItem getOutput()    { return output; }
    public int getFuelCost()         { return fuelCost; }
    public int getTimeSeconds()      { return timeSeconds; }
    public int getMinTemperature()   { return minTemperature; }

    @Override
    public String toString() {
        return "MetallurgyRecipe{id='" + id + "', machine=" + machineType + "}";
    }

    // ── Nested: Ingredient ─────────────────────────────────────

    public record Ingredient(Material material, int amount) {
        public boolean matches(Material mat, int qty) {
            return this.material == mat && qty >= this.amount;
        }
    }

    // ── Nested: OutputItem ─────────────────────────────────────

    public record OutputItem(Material material,
                              int amount,
                              String displayName,
                              List<String> lore,
                              int customModelData) {}
}
