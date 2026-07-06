package dev.haohansmp.metallurgy.machine;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.recipe.MetallurgyRecipe;
import org.bukkit.Location;

import java.util.Map;

/**
 * Abstract base class đại diện cho một máy metallurgy đang hoạt động
 * tại một Location cụ thể trong thế giới.
 *
 * Mỗi loại máy (AncientForge, ...) extend class này và override
 * các method lifecycle để thêm logic riêng.
 */
public abstract class Machine {

    protected final HaoHanMetallurgy plugin;

    // ── Identity ──────────────────────────────────────────────
    private final Location location;
    private final MachineType type;

    // ── State ─────────────────────────────────────────────────
    private MachineState state = MachineState.IDLE;
    private MetallurgyRecipe currentRecipe = null;

    /** Tiến trình hiện tại (0..totalTicks). */
    private int progressTicks = 0;

    /** Tổng số ticks cần để hoàn thành recipe hiện tại. */
    private int totalTicks = 0;

    // ── Temperature & Fuel ────────────────────────────────────
    private int temperature = 0;
    private int fuelTicksRemaining = 0;

    // ── Constructor ───────────────────────────────────────────

    protected Machine(HaoHanMetallurgy plugin, Location location, MachineType type) {
        this.plugin = plugin;
        this.location = location.clone();
        this.type = type;
    }

    // ── Lifecycle (called by TickEngine) ──────────────────────

    /**
     * Gọi mỗi N ticks bởi TickEngine.
     * Logic chung: cập nhật nhiệt độ → xử lý recipe → gọi onTick().
     */
    public final void tick() {
        updateTemperature();
        processRecipe();
        onTick(); // Hook cho subclass
    }

    /**
     * Hook để subclass thêm logic tick riêng (ví dụ: particle effects).
     */
    protected void onTick() {}

    /** Bắt đầu recipe nếu đang IDLE và điều kiện đủ. */
    public boolean startRecipe(MetallurgyRecipe recipe) {
        if (state != MachineState.IDLE) return false;
        if (temperature < recipe.getMinTemperature()) return false;

        this.currentRecipe = recipe;
        this.progressTicks = 0;
        this.totalTicks = recipe.getTimeSeconds() * 20; // 20 ticks/giây
        this.state = MachineState.WORKING;

        plugin.getPluginLogger().debug(
            "Machine at " + formatLocation() + " started recipe: " + recipe.getId()
        );
        return true;
    }

    /** Tạm dừng — giữ nguyên progress. */
    public void pause() {
        if (state == MachineState.WORKING) {
            state = MachineState.PAUSED;
        }
    }

    /** Tiếp tục từ chỗ đã dừng. */
    public void resume() {
        if (state == MachineState.PAUSED) {
            state = MachineState.WORKING;
        }
    }

    /** Reset hoàn toàn về IDLE. */
    public void reset() {
        state = MachineState.IDLE;
        currentRecipe = null;
        progressTicks = 0;
        totalTicks = 0;
    }

    /**
     * Được gọi khi recipe hoàn thành.
     * Subclass override để xử lý output (drop item, fill GUI slot, ...).
     */
    protected abstract void onRecipeComplete(MetallurgyRecipe recipe);

    // ── Serialization ─────────────────────────────────────────

    /**
     * Serialize state để lưu vào YAML/SQLite (Phase 8).
     * Phase 1: trả về Map đơn giản.
     */
    public Map<String, Object> serialize() {
        return Map.of(
            "type", type.name(),
            "state", state.name(),
            "recipe", currentRecipe != null ? currentRecipe.getId() : "",
            "progress", progressTicks,
            "total", totalTicks,
            "temperature", temperature,
            "fuel", fuelTicksRemaining
        );
    }

    // ── Getters ───────────────────────────────────────────────

    public Location getLocation()          { return location.clone(); }
    public MachineType getType()           { return type; }
    public MachineState getState()         { return state; }
    public MetallurgyRecipe getCurrentRecipe() { return currentRecipe; }
    public int getProgressTicks()          { return progressTicks; }
    public int getTotalTicks()             { return totalTicks; }
    public int getTemperature()            { return temperature; }
    public int getFuelTicksRemaining()     { return fuelTicksRemaining; }

    /** Tiến trình 0.0 → 1.0 */
    public float getProgressPercent() {
        return totalTicks == 0 ? 0f : (float) progressTicks / totalTicks;
    }

    // ── Setters (internal use / GUI) ──────────────────────────

    public void addFuel(int ticks) {
        this.fuelTicksRemaining += ticks;
    }

    protected void setState(MachineState state) {
        this.state = state;
    }

    // ── Internal ───────────────────────────────────────────────

    private void updateTemperature() {
        int maxTemp = plugin.getConfigManager().getTempMax();

        if (fuelTicksRemaining > 0) {
            fuelTicksRemaining--;
            temperature = Math.min(temperature + plugin.getConfigManager().getTempRisePerTick(), maxTemp);
        } else {
            temperature = Math.max(0, temperature - plugin.getConfigManager().getTempFallPerTick());
        }
    }

    private void processRecipe() {
        if (state != MachineState.WORKING || currentRecipe == null) return;

        // Kiểm tra nhiệt độ tối thiểu
        if (temperature < currentRecipe.getMinTemperature()) {
            pause();
            plugin.getPluginLogger().debug("Machine paused (low temp) at " + formatLocation());
            return;
        }

        progressTicks++;

        if (progressTicks >= totalTicks) {
            onRecipeComplete(currentRecipe);
            reset();
        }
    }

    private String formatLocation() {
        return String.format("(%d,%d,%d)", location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
