package dev.haohansmp.metallurgy.machine;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.item.CustomItem;
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
    private int fuelTicksRemaining2 = 0;
    private Material activeFuelType1 = null;
    private Material activeFuelType2 = null;
    private boolean hasAdditive = false;
    private long processTemperatureTotal = 0L;
    private int processTemperatureTicks = 0;

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
        if (recipe.requiresColdQuench() && !hasColdQuenchEnvironment())
            return false;
        if (recipe.requiresSoulFire() && !hasSoulFireEnvironment())
            return false;

        // Check and consume additive
        int additiveSlot = plugin.getConfigManager().getAdditiveSlot();
        ItemStack additiveItem = inventory.getItem(additiveSlot);
        boolean requiresAdditive = !recipe.getRequiredAdditives().isEmpty();
        if (requiresAdditive && (additiveItem == null
                || !recipe.acceptsAdditive(additiveItem.getType())
                || additiveItem.getAmount() < recipe.getAdditiveAmount())) {
            return false;
        }
        boolean hasCorrectAdditive = additiveItem != null && (requiresAdditive
                ? recipe.acceptsAdditive(additiveItem.getType())
                : plugin.getConfigManager().isDefaultAdditive(additiveItem.getType()));

        if (hasCorrectAdditive) {
            this.hasAdditive = true;
            int consumed = requiresAdditive ? recipe.getAdditiveAmount() : 1;
            int left = additiveItem.getAmount() - consumed;
            if (left > 0) {
                additiveItem.setAmount(left);
                inventory.setItem(additiveSlot, additiveItem);
            } else {
                inventory.setItem(additiveSlot, null);
            }
        } else {
            this.hasAdditive = false;
        }

        this.currentRecipe = recipe;
        this.progressTicks = 0;
        this.processTemperatureTotal = 0L;
        this.processTemperatureTicks = 0;
        // Giảm thời gian rèn theo cấu hình time-speed-multiplier (mặc định = 5.0 để nung cực nhanh)
        double speed = plugin.getConfigManager().getTimeSpeedMultiplier();
        if (speed <= 0.0) speed = 1.0;
        this.totalTicks = Math.max(1, (int) Math.round((recipe.getTimeSeconds() * 20) / speed));
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
        hasAdditive = false;
        processTemperatureTotal = 0L;
        processTemperatureTicks = 0;
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
        return Map.ofEntries(
                Map.entry("type", type.name()),
                Map.entry("state", state.name()),
                Map.entry("recipe", currentRecipe != null ? currentRecipe.getId() : ""),
                Map.entry("progress", progressTicks),
                Map.entry("total", totalTicks),
                Map.entry("temperature", temperature),
                Map.entry("fuel", fuelTicksRemaining),
                Map.entry("fuel2", fuelTicksRemaining2),
                Map.entry("active_fuel_type_1", activeFuelType1 == null ? "" : activeFuelType1.name()),
                Map.entry("active_fuel_type_2", activeFuelType2 == null ? "" : activeFuelType2.name()),
                Map.entry("has_additive", hasAdditive),
                Map.entry("process_temperature_total", processTemperatureTotal),
                Map.entry("process_temperature_ticks", processTemperatureTicks));
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
        return Math.max(fuelTicksRemaining, fuelTicksRemaining2);
    }

    public int getFuelTicksRemaining1() {
        return fuelTicksRemaining;
    }

    public int getFuelTicksRemaining2() {
        return fuelTicksRemaining2;
    }

    public Material getActiveFuelType1() {
        return activeFuelType1;
    }

    public Material getActiveFuelType2() {
        return activeFuelType2;
    }

    public boolean isHasAdditive() {
        return hasAdditive;
    }

    public boolean hasRequiredAdditive(MetallurgyRecipe recipe) {
        if (recipe.getRequiredAdditives().isEmpty()) return true;
        ItemStack additive = inventory.getItem(plugin.getConfigManager().getAdditiveSlot());
        return additive != null && recipe.acceptsAdditive(additive.getType())
                && additive.getAmount() >= recipe.getAdditiveAmount();
    }

    public boolean hasColdQuenchEnvironment() {
        if (location.getBlock().getTemperature() < 0.2) return true;
        return hasNearbyMaterial(java.util.Set.of(
                Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE,
                Material.FROSTED_ICE, Material.SNOW_BLOCK, Material.POWDER_SNOW));
    }

    public boolean hasSoulFireEnvironment() {
        return hasNearbyMaterial(java.util.Set.of(
                Material.SOUL_FIRE, Material.SOUL_CAMPFIRE, Material.SOUL_LANTERN));
    }

    private boolean hasNearbyMaterial(java.util.Set<Material> materials) {
        org.bukkit.World world = location.getWorld();
        if (world == null) return false;
        int centerX = location.getBlockX();
        int centerY = location.getBlockY();
        int centerZ = location.getBlockZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (materials.contains(world.getBlockAt(centerX + dx, centerY + dy, centerZ + dz).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public int getAverageProcessTemperature() {
        return processTemperatureTicks <= 0
                ? temperature
                : (int) Math.round(processTemperatureTotal / (double) processTemperatureTicks);
    }

    public double getEstimatedCleanOutputChance() {
        if (currentRecipe == null) return 0.0;
        if (!plugin.getConfigManager().isFailEnabled()) return 1.0;
        return 1.0 - calculateFinalFailChance(currentRecipe, getAverageProcessTemperature());
    }

    public double getActiveAdditiveCleanOutputBonus() {
        if (currentRecipe == null || !hasAdditive) return 0.0;
        double recipeBonus = currentRecipe.getAdditiveCleanOutputBonus();
        return recipeBonus >= 0.0
                ? recipeBonus
                : plugin.getConfigManager().getDefaultAdditiveCleanOutputBonus();
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
        this.activeFuelType1 = fuelType;
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

        Material activeType1 = fuelTicksRemaining > 0 ? activeFuelType1 : null;
        Material activeType2 = fuelTicksRemaining2 > 0 ? activeFuelType2 : null;
        int combinedLimit = plugin.getConfigManager().getCombinedFuelLimit(activeType1, activeType2);
        int currentLimit = Math.min(combinedLimit, maxTemp);

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

    public void setFuelTicksRemaining2(int fuelTicksRemaining2) {
        this.fuelTicksRemaining2 = fuelTicksRemaining2;
    }

    public void setActiveFuelType1(Material activeFuelType1) {
        this.activeFuelType1 = activeFuelType1;
    }

    public void setActiveFuelType2(Material activeFuelType2) {
        this.activeFuelType2 = activeFuelType2;
    }

    public void setHasAdditive(boolean hasAdditive) {
        this.hasAdditive = hasAdditive;
    }

    public void setProcessTemperatureTotal(long processTemperatureTotal) {
        this.processTemperatureTotal = Math.max(0L, processTemperatureTotal);
    }

    public void setProcessTemperatureTicks(int processTemperatureTicks) {
        this.processTemperatureTicks = Math.max(0, processTemperatureTicks);
    }

    public long getProcessTemperatureTotal() {
        return processTemperatureTotal;
    }

    public int getProcessTemperatureTicks() {
        return processTemperatureTicks;
    }

    public void setCurrentRecipe(MetallurgyRecipe currentRecipe) {
        this.currentRecipe = currentRecipe;
    }

    public void setProgressTicks(int progressTicks) {
        this.progressTicks = progressTicks;
    }

    public void setTotalTicks(int totalTicks) {
        this.totalTicks = totalTicks;
    }

    // ── Internal ───────────────────────────────────────────────

    private void updateTemperature() {
        int maxTemp = plugin.getConfigManager().getTempMax();
        int baseRise = plugin.getConfigManager().getTempRisePerTick();
        int baseFall = plugin.getConfigManager().getTempFallPerTick();
        int elapsedTicks = Math.max(1, plugin.getConfigManager().getTickRate());

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
        if (hotBiome && temperature == 0 && (fuelTicksRemaining > 0 || fuelTicksRemaining2 > 0)) {
            temperature = 100;
        }

        // 3. Tính toán tốc độ tăng/giảm cuối cùng
        int finalRise = (baseRise + conductors * 2) * elapsedTicks;
        int finalFall = Math.max(0,
                baseFall + biomeFallMod + rainFallMod + coolers * 2 - (int) (insulators * 0.2)) * elapsedTicks;

        // 4. Giới hạn nhiệt độ tối đa theo Nhiên liệu hoạt động và khối làm mát
        Material activeType1 = fuelTicksRemaining > 0 ? activeFuelType1 : null;
        Material activeType2 = fuelTicksRemaining2 > 0 ? activeFuelType2 : null;
        int combinedLimit = plugin.getConfigManager().getCombinedFuelLimit(activeType1, activeType2);
        int currentLimit = Math.min(combinedLimit, maxTemp - coolers * 100);

        // Tiêu thụ nhiên liệu động từ slot nhiên liệu 1 (nếu hết ticks)
        if (fuelTicksRemaining <= 0) {
            int fuelSlot = dev.haohansmp.metallurgy.gui.forge.ForgeGui.SLOT_FUEL;
            ItemStack fuelItem = inventory.getItem(fuelSlot);
            if (fuelItem != null && fuelItem.getType() != Material.AIR) {
                Material mat = fuelItem.getType();
                int ticksPerItem = plugin.getConfigManager().getFuelTicks(mat);
                var coolants = plugin.getConfigManager().getCoolants();
                if (ticksPerItem > 0 && !coolants.containsKey(mat)) {
                    if (fuelItem.getAmount() > 1) {
                        fuelItem.setAmount(fuelItem.getAmount() - 1);
                        inventory.setItem(fuelSlot, fuelItem);
                    } else if (mat == Material.LAVA_BUCKET) {
                        inventory.setItem(fuelSlot, new ItemStack(Material.BUCKET));
                    } else {
                        inventory.setItem(fuelSlot, null);
                    }
                    this.fuelTicksRemaining = ticksPerItem;
                    this.activeFuelType1 = mat;

                    activeType1 = activeFuelType1;
                    combinedLimit = plugin.getConfigManager().getCombinedFuelLimit(activeType1, activeType2);
                    currentLimit = Math.min(combinedLimit, maxTemp - coolers * 100);
                    temperature = Math.min(currentLimit,
                            temperature + plugin.getConfigManager().getFuelIgnitionBoost(mat));
                }
            } else {
                this.activeFuelType1 = null;
            }
        }

        // Tiêu thụ nhiên liệu động từ slot nhiên liệu 2 (nếu hết ticks)
        int fuelSlot2 = plugin.getConfigManager().getFuelSlot2();
        if (fuelTicksRemaining2 <= 0) {
            ItemStack fuelItem = inventory.getItem(fuelSlot2);
            if (fuelItem != null && fuelItem.getType() != Material.AIR) {
                Material mat = fuelItem.getType();
                int ticksPerItem = plugin.getConfigManager().getFuelTicks(mat);
                var coolants = plugin.getConfigManager().getCoolants();
                if (ticksPerItem > 0 && !coolants.containsKey(mat)) {
                    if (fuelItem.getAmount() > 1) {
                        fuelItem.setAmount(fuelItem.getAmount() - 1);
                        inventory.setItem(fuelSlot2, fuelItem);
                    } else if (mat == Material.LAVA_BUCKET) {
                        inventory.setItem(fuelSlot2, new ItemStack(Material.BUCKET));
                    } else {
                        inventory.setItem(fuelSlot2, null);
                    }
                    this.fuelTicksRemaining2 = ticksPerItem;
                    this.activeFuelType2 = mat;

                    activeType2 = activeFuelType2;
                    combinedLimit = plugin.getConfigManager().getCombinedFuelLimit(activeType1, activeType2);
                    currentLimit = Math.min(combinedLimit, maxTemp - coolers * 100);
                    temperature = Math.min(currentLimit,
                            temperature + plugin.getConfigManager().getFuelIgnitionBoost(mat));
                }
            } else {
                this.activeFuelType2 = null;
            }
        }

        // Decrement burning fuel ticks
        boolean anyFuelBurning = false;
        if (fuelTicksRemaining > 0) {
            fuelTicksRemaining = Math.max(0, fuelTicksRemaining - elapsedTicks);
            anyFuelBurning = true;
        } else {
            activeFuelType1 = null;
        }
        if (fuelTicksRemaining2 > 0) {
            fuelTicksRemaining2 = Math.max(0, fuelTicksRemaining2 - elapsedTicks);
            anyFuelBurning = true;
        } else {
            activeFuelType2 = null;
        }

        if (anyFuelBurning) {
            if (temperature < currentLimit) {
                temperature = Math.min(temperature + finalRise, currentLimit);
            } else if (temperature > currentLimit) {
                temperature = Math.max(currentLimit, temperature - finalFall);
            }
        } else {
            temperature = Math.max(0, temperature - finalFall);
        }
    }

    private void processRecipe() {
        if (currentRecipe == null)
            return;

        if ((currentRecipe.requiresColdQuench() && !hasColdQuenchEnvironment())
                || (currentRecipe.requiresSoulFire() && !hasSoulFireEnvironment())) {
            pause();
            return;
        }

        if (state == MachineState.PAUSED && temperature >= currentRecipe.getMinTemperature()) {
            resume();
        }
        if (state != MachineState.WORKING) return;

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

        int elapsed = Math.min(totalTicks - progressTicks,
                Math.max(1, plugin.getConfigManager().getTickRate()));
        progressTicks += elapsed;
        processTemperatureTotal += (long) temperature * elapsed;
        processTemperatureTicks += elapsed;

        if (progressTicks >= totalTicks) {
            // Kiểm tra tỉ lệ luyện kim thất bại (chỉ khi được bật cấu hình)
            if (plugin.getConfigManager().isFailEnabled()) {
                int averageTemperature = getAverageProcessTemperature();
                double finalFailChance = calculateFinalFailChance(currentRecipe, averageTemperature);
                if (finalFailChance > 0.0) {
                    double rand = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
                    if (rand < finalFailChance) {
                        String reason = averageTemperature < currentRecipe.getPurificationTemperature()
                                ? "\u00a7cNhi\u1ec7t tinh luy\u1ec7n kh\u00f4ng \u0111\u1ee7, t\u1ea1p ch\u1ea5t k\u1ebft th\u00e0nh s\u1ec9."
                                : "\u00a7cLuy\u1ec7n kim th\u1ea5t b\u1ea1i, ch\u1ec9 thu \u0111\u01b0\u1ee3c s\u1ec9 kim lo\u1ea1i.";
                        ruinRecipe("\u00a7c\u26a0 " + reason, "\u00a7c\u26a0 Luy\u1ec7n kim th\u1ea5t b\u1ea1i!");
                        return;
                    }
                }
            }

            onRecipeComplete(currentRecipe);
            reset();
        }
    }

    private double calculateFinalFailChance(MetallurgyRecipe recipe, int averageTemperature) {
        double temperatureFailChance = recipe.getTemperatureFailChance(averageTemperature);
        if (hasAdditive) {
            return Math.max(0.0, temperatureFailChance - getActiveAdditiveCleanOutputBonus());
        }
        double additivePenalty = plugin.getConfigManager().getNoAdditiveFailChance();
        return Math.min(1.0,
                1.0 - (1.0 - temperatureFailChance) * (1.0 - additivePenalty));
    }

    private void ruinRecipe(String chatMessage, String actionBarMessage) {
        plugin.getPluginLogger().info("Machine at " + formatLocation() + " failed: " + chatMessage);

        // Phát âm thanh cháy xèo xèo tắt lửa
        if (location.getWorld() != null) {
            location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);
            location.getWorld().playSound(location, org.bukkit.Sound.ENTITY_GENERIC_BURN, 0.5f, 1.0f);
        }

        ItemStack slag = buildMetalSlag(currentRecipe);
        boolean customMetalSlag = slag != null;
        if (!customMetalSlag) {
            slag = new ItemStack(Material.RAW_IRON, 1);
        }
        org.bukkit.inventory.meta.ItemMeta meta = slag.getItemMeta();
        if (!customMetalSlag && meta != null) {
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
            int space = existing.getMaxStackSize() - existing.getAmount();
            if (space > 0) {
                existing.setAmount(existing.getAmount() + 1);
            } else if (location.getWorld() != null) {
                location.getWorld().dropItemNaturally(location.clone().add(0.5, 1.2, 0.5), slag);
            }
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

    private ItemStack buildMetalSlag(MetallurgyRecipe recipe) {
        if (recipe == null) {
            return null;
        }

        return getSlagForRecipe(recipe)
                .map(customItem -> plugin.getItemManager().createItem(customItem, 1))
                .orElse(null);
    }

    private java.util.Optional<CustomItem> getSlagForRecipe(MetallurgyRecipe recipe) {
        String key = (recipe.getId() + " " + String.valueOf(recipe.getOutput().customItemId()))
                .toLowerCase(java.util.Locale.ROOT);

        if (key.contains("mithril")) {
            return java.util.Optional.of(CustomItem.MITHRIL_SLAG);
        }
        if (key.contains("soulsteel")) {
            return java.util.Optional.of(CustomItem.SOULSTEEL_SLAG);
        }
        if (key.contains("embersteel")) {
            return java.util.Optional.of(CustomItem.EMBERSTEEL_SLAG);
        }
        if (key.contains("netherite") || key.contains("ancient_debris")) {
            return java.util.Optional.of(CustomItem.NETHERITE_SLAG);
        }
        if (key.contains("gold")) {
            return java.util.Optional.of(CustomItem.GOLD_SLAG);
        }
        if (key.contains("iron")) {
            return java.util.Optional.of(CustomItem.IRON_SLAG);
        }
        if (key.contains("copper")) {
            return java.util.Optional.of(CustomItem.COPPER_SLAG);
        }

        return CustomItem.getSlagForMaterial(recipe.getOutput().material());
    }

    private String formatLocation() {
        return String.format("(%d,%d,%d)", location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
