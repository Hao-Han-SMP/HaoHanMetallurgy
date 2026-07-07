package dev.haohansmp.metallurgy.machine;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.recipe.MetallurgyRecipe;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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
    private int activeFuelLimit = 2000;

    // ── Persistent Inventory ──────────────────────────────────
    protected Inventory inventory;

    // ── Constructor ───────────────────────────────────────────

    protected Machine(HaoHanMetallurgy plugin, Location location, MachineType type) {
        this.plugin = plugin;
        this.location = location.clone();
        this.type = type;
        
        // Khởi tạo inventory cố định cho máy này (27 slots)
        String title = plugin.getConfigManager().getForgeTitle();
        this.inventory = Bukkit.createInventory(null, 27, title);
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
        // Giảm thời gian rèn đi 1 nửa (time-multiplier = 0.5)
        this.totalTicks = (recipe.getTimeSeconds() * 20) / 2;
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
    public Inventory getInventory()        { return inventory; }

    /** Tiến trình 0.0 → 1.0 */
    public float getProgressPercent() {
        return totalTicks == 0 ? 0f : (float) progressTicks / totalTicks;
    }

    // ── Setters (internal use / GUI) ──────────────────────────

    public void addFuel(int ticks, Material fuelType) {
        this.fuelTicksRemaining += ticks;
        // Cập nhật giới hạn nhiệt độ theo cấu hình của nhiên liệu
        int limit = plugin.getConfigManager().getFuelLimits().getOrDefault(fuelType, 2000);
        this.activeFuelLimit = limit;
    }

    public void coolDown(int amount) {
        this.temperature = Math.max(0, this.temperature - amount);
        // Hiển thị hiệu ứng xì khói lạnh khi hạ nhiệt
        if (location.getWorld() != null) {
            location.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE, location.clone().add(0.5, 1.2, 0.5), 10, 0.2, 0.2, 0.2, 0.05);
            location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
        }
    }

    public void boostTemperature(int amount) {
        int maxTemp = plugin.getConfigManager().getTempMax();
        int currentLimit = Math.min(activeFuelLimit, maxTemp);
        
        // Thổi khí cho phép nhiệt tăng vượt giới hạn nhiên liệu 150°C nhưng không quá maxTemp
        int cap = Math.min(currentLimit + 150, maxTemp);
        this.temperature = Math.min(cap, this.temperature + amount);

        // Hiển thị hiệu ứng gió/khói bay khi thổi khí
        if (location.getWorld() != null) {
            location.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, location.clone().add(0.5, 1.2, 0.5), 8, 0.1, 0.1, 0.1, 0.02);
            location.getWorld().playSound(location, org.bukkit.Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.6f, 1.2f);
        }
    }

    public void setState(MachineState state) {
        this.state = state;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    public void setFuelTicksRemaining(int fuelTicksRemaining) {
        this.fuelTicksRemaining = fuelTicksRemaining;
    }

    // ── Internal ───────────────────────────────────────────────

    private void updateTemperature() {
        int maxTemp = plugin.getConfigManager().getTempMax();
        int baseRise = plugin.getConfigManager().getTempRisePerTick();
        int baseFall = plugin.getConfigManager().getTempFallPerTick();

        // 1. Quét cấu trúc vòng 8 block xung quanh lò
        int insulators = 0;
        int conductors = 0;
        int coolers = 0;

        org.bukkit.World w = location.getWorld();
        if (w != null) {
            int[][] ringOffsets = {
                {-1, 0, -1}, {0, 0, -1}, {1, 0, -1},
                {-1, 0, 0},             {1, 0, 0},
                {-1, 0, 1},  {0, 0, 1},  {1, 0, 1}
            };
            for (int[] offset : ringOffsets) {
                Material type = w.getBlockAt(
                    location.getBlockX() + offset[0],
                    location.getBlockY() + offset[1],
                    location.getBlockZ() + offset[2]
                ).getType();

                if (type == Material.NETHER_BRICKS || type == Material.RED_NETHER_BRICKS 
                    || type == Material.MAGMA_BLOCK || type == Material.OBSIDIAN) {
                    insulators++;
                } else if (type == Material.IRON_BLOCK || type == Material.COPPER_BLOCK 
                    || type == Material.GOLD_BLOCK || type == Material.EXPOSED_COPPER 
                    || type == Material.WEATHERED_COPPER || type == Material.OXIDIZED_COPPER) {
                    conductors++;
                } else if (type == Material.PACKED_ICE || type == Material.BLUE_ICE 
                    || type == Material.ICE) {
                    coolers++;
                }
            }
        }

        // 2. Kiểm tra Biome & Rain
        int biomeFallMod = 0;
        boolean hotBiome = false;
        if (w != null) {
            double biomeTemp = location.getBlock().getTemperature();
            if (biomeTemp < 0.2) {
                biomeFallMod = 2; // Vùng lạnh hạ nhiệt nhanh hơn
            } else if (biomeTemp >= 1.0) {
                biomeFallMod = -1; // Vùng nóng hạ nhiệt chậm hơn
                hotBiome = true;
            }
        }

        int rainFallMod = 0;
        if (w != null && w.hasStorm()) {
            Location checkLoc = location.clone().add(0, 2, 0);
            if (checkLoc.getBlock().getLightFromSky() == 15) {
                rainFallMod = 3; // Mưa dập tắt/hạ nhiệt cực nhanh
            }
        }

        // Khởi động nhiệt độ tối thiểu tại vùng cực nóng (Nether/Desert)
        if (hotBiome && temperature == 0 && fuelTicksRemaining > 0) {
            temperature = 100;
        }

        // 3. Tính toán tốc độ tăng/giảm cuối cùng
        int finalRise = baseRise + conductors * 2;
        int finalFall = Math.max(0, baseFall + biomeFallMod + rainFallMod + coolers * 2 - (int)(insulators * 0.2));

        // 4. Giới hạn nhiệt độ tối đa theo Nhiên liệu hoạt động và khối làm mát
        int currentLimit = Math.min(activeFuelLimit, maxTemp - coolers * 100);

        if (fuelTicksRemaining > 0) {
            fuelTicksRemaining--;
            if (temperature < currentLimit) {
                temperature = Math.min(temperature + finalRise, currentLimit);
            } else if (temperature > currentLimit) {
                // Nhiệt độ cao hơn giới hạn nhiên liệu -> nguội về giới hạn
                temperature = Math.max(currentLimit, temperature - finalFall);
            }
        } else {
            temperature = Math.max(0, temperature - finalFall);
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

        // Kiểm tra nhiệt độ tối đa (Quá nhiệt -> Hỏng quặng thành Xỉ)
        if (temperature > currentRecipe.getMaxTemperature()) {
            ruinRecipe();
            return;
        }

        progressTicks++;

        if (progressTicks >= totalTicks) {
            onRecipeComplete(currentRecipe);
            reset();
        }
    }

    private void ruinRecipe() {
        plugin.getPluginLogger().info("Machine at " + formatLocation() + " overheated! Recipe ruined.");

        // Phát âm thanh cháy xèo xèo tắt lửa
        if (location.getWorld() != null) {
            location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
            location.getWorld().playSound(location, org.bukkit.Sound.ENTITY_GENERIC_BURN, 0.5f, 1.0f);
        }

        // Tạo vật phẩm Slag (Xỉ Thải) bằng Than củi
        ItemStack slag = new ItemStack(Material.CHARCOAL, 1);
        org.bukkit.inventory.meta.ItemMeta meta = slag.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8Slag (Xỉ Thải)");
            meta.setLore(java.util.List.of(
                "§7Sản phẩm hỏng do lò quá nhiệt!",
                "§7Hãy giữ nhiệt độ ổn định trong khoảng an toàn."
            ));
            slag.setItemMeta(meta);
        }

        // Đặt vào ô output (slot 15) hoặc drop ra đất nếu đầy
        ItemStack existing = inventory.getItem(15);
        if (existing == null || existing.getType() == Material.AIR) {
            inventory.setItem(15, slag);
        } else if (existing.getType() == Material.CHARCOAL && existing.hasItemMeta() && "§8Slag (Xỉ Thải)".equals(existing.getItemMeta().getDisplayName())) {
            existing.setAmount(Math.min(existing.getAmount() + 1, existing.getMaxStackSize()));
        } else {
            if (location.getWorld() != null) {
                location.getWorld().dropItemNaturally(location.clone().add(0.5, 1.2, 0.5), slag);
            }
        }

        // Gửi cảnh báo người chơi lân cận
        if (location.getWorld() != null) {
            location.getWorld().getPlayers().stream()
                .filter(p -> p.getLocation().distanceSquared(location) < 15 * 15)
                .forEach(p -> {
                    p.sendMessage("§8[§6Forge§8] §c⚠ Lò quá nhiệt! Nguyên liệu của bạn đã bị thiêu cháy thành xỉ.");
                    p.sendActionBar("§c⚠ Lò quá nhiệt! Quặng đã bị cháy hỏng!");
                });
        }

        reset();
    }

    private String formatLocation() {
        return String.format("(%d,%d,%d)", location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
