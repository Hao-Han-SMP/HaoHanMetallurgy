package dev.haohansmp.metallurgy.machine;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.recipe.MetallurgyRecipe;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

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
        if (plugin.getConfigManager().isForgeCustomGuiEnabled()) {
            String prefix = plugin.getConfigManager().getForgeCustomGuiPrefix();
            String glyph = plugin.getConfigManager().getForgeCustomGuiGlyph();
            org.bukkit.NamespacedKey fontKey = plugin.getConfigManager().getForgeCustomGuiFont();
            if (prefix != null && glyph != null && fontKey != null) {
                String fullText = prefix + glyph;
                net.kyori.adventure.text.Component titleComp = net.kyori.adventure.text.Component.text(fullText)
                    .font(net.kyori.adventure.key.Key.key(fontKey.getNamespace(), fontKey.getKey()));
                this.inventory = Bukkit.createInventory(null, 27, titleComp);
            } else {
                String title = plugin.getConfigManager().getForgeTitle();
                this.inventory = Bukkit.createInventory(null, 27, title);
            }
        } else {
            String title = plugin.getConfigManager().getForgeTitle();
            this.inventory = Bukkit.createInventory(null, 27, title);
        }
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
    protected void onTick() {
    }

    /** Bắt đầu recipe nếu đang IDLE và điều kiện đủ. */
    public boolean startRecipe(MetallurgyRecipe recipe) {
        if (state != MachineState.IDLE)
            return false;
        if (temperature < recipe.getMinTemperature())
            return false;

        this.currentRecipe = recipe;
        this.progressTicks = 0;
        // Giảm thời gian rèn xuống 1 phần 5 (time-multiplier = 0.2) để nung cực nhanh
        this.totalTicks = Math.max(1, (recipe.getTimeSeconds() * 20) / 5);
        this.state = MachineState.WORKING;

        plugin.getPluginLogger().debug(
                "Machine at " + formatLocation() + " started recipe: " + recipe.getId());
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
                "fuel", fuelTicksRemaining);
    }

    // ── Getters ───────────────────────────────────────────────

    public Location getLocation() {
        return location.clone();
    }

    public MachineType getType() {
        return type;
    }

    public MachineState getState() {
        return state;
    }

    public MetallurgyRecipe getCurrentRecipe() {
        return currentRecipe;
    }

    public int getProgressTicks() {
        return progressTicks;
    }

    public int getTotalTicks() {
        return totalTicks;
    }

    public int getTemperature() {
        return temperature;
    }

    public int getFuelTicksRemaining() {
        return fuelTicksRemaining;
    }

    public Inventory getInventory() {
        return inventory;
    }

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
            location.getWorld().spawnParticle(org.bukkit.Particle.SNOWFLAKE, location.clone().add(0.5, 1.2, 0.5), 10,
                    0.2, 0.2, 0.2, 0.05);
            location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
        }
    }

    public void boostTemperature(int amount) {
        int maxTemp = plugin.getConfigManager().getTempMax();
        int currentLimit = Math.min(activeFuelLimit, maxTemp);

        // Thổi khí cho phép nhiệt tăng vượt giới hạn nhiên liệu 150°C nhưng không quá
        // maxTemp
        int cap = Math.min(currentLimit + 150, maxTemp);
        this.temperature = Math.min(cap, this.temperature + amount);

        // Hiển thị hiệu ứng gió/khói bay khi thổi khí
        if (location.getWorld() != null) {
            location.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, location.clone().add(0.5, 1.2, 0.5), 8, 0.1,
                    0.1, 0.1, 0.02);
            location.getWorld().playSound(location, org.bukkit.Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.6f, 1.2f);
        }
    }

    public void boostProgressTicks(int amount) {
        if (state == MachineState.WORKING) {
            this.progressTicks = Math.min(totalTicks, this.progressTicks + amount);
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
                    { -1, 0, -1 }, { 0, 0, -1 }, { 1, 0, -1 },
                    { -1, 0, 0 }, { 1, 0, 0 },
                    { -1, 0, 1 }, { 0, 0, 1 }, { 1, 0, 1 }
            };
            for (int[] offset : ringOffsets) {
                Material type = w.getBlockAt(
                        location.getBlockX() + offset[0],
                        location.getBlockY() + offset[1],
                        location.getBlockZ() + offset[2]).getType();

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
        int finalFall = Math.max(0, baseFall + biomeFallMod + rainFallMod + coolers * 2 - (int) (insulators * 0.2));

        // 4. Giới hạn nhiệt độ tối đa theo Nhiên liệu hoạt động và khối làm mát
        int currentLimit = Math.min(activeFuelLimit, maxTemp - coolers * 100);

        // Tiêu thụ nhiên liệu động từ slot nhiên liệu (nếu hết ticks)
        if (fuelTicksRemaining <= 0 && (state == MachineState.WORKING || temperature < currentLimit)) {
            int fuelSlot = dev.haohansmp.metallurgy.gui.forge.ForgeGui.SLOT_FUEL;
            ItemStack fuelItem = inventory.getItem(fuelSlot);
            if (fuelItem != null && fuelItem.getType() != Material.AIR) {
                Material mat = fuelItem.getType();
                int ticksPerItem = plugin.getConfigManager().getFuelTicks(mat);
                var coolants = plugin.getConfigManager().getCoolants();
                if (ticksPerItem > 0 && !coolants.containsKey(mat)) {
                    // Tiêu thụ đúng 1 vật phẩm
                    if (fuelItem.getAmount() > 1) {
                        fuelItem.setAmount(fuelItem.getAmount() - 1);
                        inventory.setItem(fuelSlot, fuelItem);
                    } else {
                        inventory.setItem(fuelSlot, null);
                    }
                    this.fuelTicksRemaining = ticksPerItem;
                    this.activeFuelLimit = plugin.getConfigManager().getFuelLimits().getOrDefault(mat, 2000);
                    currentLimit = Math.min(activeFuelLimit, maxTemp - coolers * 100);
                }
            }
        }

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
        if (state != MachineState.WORKING || currentRecipe == null)
            return;

        // Kiểm tra nhiệt độ tối thiểu
        if (temperature < currentRecipe.getMinTemperature()) {
            pause();
            plugin.getPluginLogger().debug("Machine paused (low temp) at " + formatLocation());
            return;
        }

        // Kiểm tra nhiệt độ tối đa (Quá nhiệt -> Hỏng quặng thành Xỉ)
        if (temperature > currentRecipe.getMaxTemperature()) {
            // Thử tự làm mát khẩn cấp bằng vạc nước xung quanh trước khi làm hỏng công thức
            boolean cooled = false;
            int[][] adjacentOffsets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
            for (int[] offset : adjacentOffsets) {
                org.bukkit.block.Block adjBlock = location.clone().add(offset[0], offset[1], offset[2]).getBlock();
                if (adjBlock.getType() == org.bukkit.Material.WATER_CAULDRON) {
                    if (adjBlock.getBlockData() instanceof org.bukkit.block.data.Levelled levelled) {
                        int level = levelled.getLevel();
                        if (level > 1) {
                            levelled.setLevel(level - 1);
                            adjBlock.setBlockData(levelled, false);
                        } else {
                            adjBlock.setType(org.bukkit.Material.CAULDRON, false);
                        }
                        this.temperature = Math.max(0, this.temperature - 200);
                        if (location.getWorld() != null) {
                            location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
                            location.getWorld().spawnParticle(org.bukkit.Particle.LARGE_SMOKE, location.clone().add(0.5, 1.5, 0.5), 15, 0.25, 0.4, 0.25, 0.02);
                        }
                        cooled = true;
                        break;
                    }
                }
            }

            if (cooled) {
                return; // Đã hạ nhiệt thành công, bỏ qua quá nhiệt ở tick này
            }

            ruinRecipe("\u00a7c\u26a0 L\u00f2 qu\u00e1 nhi\u1ec7t! Ph\u1ea7n kim lo\u1ea1i t\u1ed1t b\u1ecb m\u1ea5t, ch\u1ec9 c\u00f2n s\u1ec9 kim lo\u1ea1i.", "\u00a7c\u26a0 L\u00f2 qu\u00e1 nhi\u1ec7t!");
            return;
        }

        progressTicks++;

        if (progressTicks >= totalTicks) {
            // Kiểm tra tỉ lệ luyện kim thất bại (chỉ khi được bật cấu hình và recipe có tỷ lệ thất bại)
            if (plugin.getConfigManager().isFailEnabled() && currentRecipe.getFailChance() > 0.0) {
                double rand = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
                if (rand < currentRecipe.getFailChance()) {
                    ruinRecipe("\u00a7c\u26a0 Luy\u1ec7n kim th\u1ea5t b\u1ea1i! Ch\u1ec9 thu \u0111\u01b0\u1ee3c s\u1ec9 kim lo\u1ea1i.", "\u00a7c\u26a0 Luy\u1ec7n kim th\u1ea5t b\u1ea1i!");
                    return;
                }
            }

            onRecipeComplete(currentRecipe);
            reset();
        }
    }

    private void ruinRecipe(String chatMessage, String actionBarMessage) {
        plugin.getPluginLogger().info("Machine at " + formatLocation() + " failed: " + chatMessage);

        // Phát âm thanh cháy xèo xèo tắt lửa
        if (location.getWorld() != null) {
            location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
            location.getWorld().playSound(location, org.bukkit.Sound.ENTITY_GENERIC_BURN, 0.5f, 1.0f);
        }

        // Tạo vật phẩm Slag (Xỉ Thải) bằng Than củi
        ItemStack slag = new ItemStack(Material.RAW_IRON, 1);
        org.bukkit.inventory.meta.ItemMeta meta = slag.getItemMeta();
        if (meta != null) {
            String metalName = currentRecipe == null ? "kim lo\u1ea1i" : currentRecipe.getOutput().material().name()
                    .toLowerCase(java.util.Locale.ROOT)
                    .replace("_ingot", "")
                    .replace("_", " ");
            meta.setDisplayName("\u00a78S\u1ec9 kim lo\u1ea1i");
            meta.setLore(java.util.List.of(
                    "\u00a77T\u1ea1p ch\u1ea5t c\u00f2n l\u1ea1i sau khi luy\u1ec7n " + metalName + ".",
                    "\u00a77Kh\u00f4ng ph\u1ea3i ph\u1ebf ph\u1ea9m h\u1ecfng ho\u00e0n to\u00e0n."));
            slag.setItemMeta(meta);
        }

        // Đặt vào ô output (slot 15) hoặc drop ra đất nếu đầy
        int slagSlot = dev.haohansmp.metallurgy.gui.forge.ForgeGui.SLOT_SLAG;
        ItemStack existing = inventory.getItem(slagSlot);
        if (existing == null || existing.getType() == Material.AIR) {
            inventory.setItem(slagSlot, slag);
        } else if (existing.isSimilar(slag)) {
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
                        p.sendMessage("\u00a78[\u00a76Forge\u00a78] " + chatMessage);
                        p.sendActionBar(actionBarMessage);
                    });
        }

        reset();
    }

    private String formatLocation() {
        return String.format("(%d,%d,%d)", location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
