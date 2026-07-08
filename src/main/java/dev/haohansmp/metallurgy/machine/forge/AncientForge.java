package dev.haohansmp.metallurgy.machine.forge;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.machine.Machine;
import dev.haohansmp.metallurgy.machine.MachineState;
import dev.haohansmp.metallurgy.machine.MachineType;
import dev.haohansmp.metallurgy.recipe.MetallurgyRecipe;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class AncientForge extends Machine {

    private final int rotation;
    private final java.util.Map<ForgeStructure.BlockOffset, Material> originalBlocks;
    private org.bukkit.entity.Display displayEntity = null;
    private Boolean lastRenderedActiveModel = null;
    private Boolean lastLightActive = null;

    public AncientForge(HaoHanMetallurgy plugin, Location controllerLocation, int rotation) {
        this(plugin, controllerLocation, rotation, new java.util.HashMap<>());
    }

    public AncientForge(HaoHanMetallurgy plugin, Location controllerLocation, int rotation,
            java.util.Map<ForgeStructure.BlockOffset, Material> originalBlocks) {
        super(plugin, controllerLocation, MachineType.ANCIENT_FORGE);
        this.rotation = rotation;
        this.originalBlocks = originalBlocks;
        spawnDisplayEntity();
    }

    public int getRotation() {
        return rotation;
    }

    public java.util.Map<ForgeStructure.BlockOffset, Material> getOriginalBlocks() {
        return originalBlocks;
    }

    public void playActivationEffects() {
        Location center = getForgeMouthLocation();
        org.bukkit.World world = center.getWorld();
        if (world == null)
            return;

        world.spawnParticle(org.bukkit.Particle.FLAME, center, 24, 0.55, 0.35, 0.55, 0.05);
        world.spawnParticle(org.bukkit.Particle.LAVA, center, 8, 0.35, 0.25, 0.35, 0.04);
        world.spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, getForgeChimneyLocation(), 6, 0.35, 0.2, 0.35,
                0.02);
        world.playSound(center, org.bukkit.Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 1.0f, 0.85f);
        world.playSound(center, org.bukkit.Sound.BLOCK_ANVIL_PLACE, 0.45f, 0.7f);
    }

    public void playDeactivationEffects() {
        Location center = getLocation().clone().add(0.5, 1.0, 0.5);
        org.bukkit.World world = center.getWorld();
        if (world == null)
            return;

        world.spawnParticle(org.bukkit.Particle.SMOKE, center, 18, 0.6, 0.35, 0.6, 0.04);
        world.spawnParticle(org.bukkit.Particle.ASH, center, 16, 0.75, 0.45, 0.75, 0.02);
        world.playSound(center, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.85f);
        world.playSound(center, org.bukkit.Sound.BLOCK_CHAIN_BREAK, 0.55f, 0.75f);
    }

    public void refreshDisplayEntity() {
        removeDisplayEntity();
        spawnDisplayEntity();
        updateDisplayModel(true);
    }

    public void ensureBarrierBlocks() {
        Location loc = getLocation();
        if (loc.getWorld() == null)
            return;

        loc.getBlock().setType(Material.BARRIER, false);
        for (ForgeStructure.BlockOffset offset : ForgeStructure.REQUIRED_BLOCKS) {
            int rx = offset.dx();
            int rz = offset.dz();
            if (rotation == 90) {
                rx = -offset.dz();
                rz = offset.dx();
            } else if (rotation == 180) {
                rx = -offset.dx();
                rz = -offset.dz();
            } else if (rotation == 270) {
                rx = offset.dz();
                rz = -offset.dx();
            }

            loc.clone().add(rx, offset.dy(), rz).getBlock().setType(Material.BARRIER, false);
        }
        updateActiveLightBlocks(true);
    }

    public void spawnDisplayEntity() {
        if (!plugin.getConfigManager().isModelEnabled())
            return;

        // Nếu entity đã tồn tại và hợp lệ thì không cần spawn lại
        if (displayEntity != null && displayEntity.isValid()) {
            updateDisplayModel(true);
            updateActiveLightBlocks(true);
            return;
        }

        Location spawnLoc = getLocation().clone().add(
                plugin.getConfigManager().getModelOffsetX(),
                plugin.getConfigManager().getModelOffsetY(),
                plugin.getConfigManager().getModelOffsetZ());

        org.bukkit.World w = spawnLoc.getWorld();
        if (w == null)
            return;

        // Kiểm tra xem thực tế có entity nào đang ở vị trí này chưa để tái sử dụng
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "forge_display");
        for (org.bukkit.entity.BlockDisplay display : w.getEntitiesByClass(org.bukkit.entity.BlockDisplay.class)) {
            if (display.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                String locStr = display.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
                if (formatLoc(getLocation()).equals(locStr)) {
                    displayEntity = display;
                    updateDisplayModel(true);
                    updateActiveLightBlocks(true);
                    return; // Đã tìm thấy, bỏ qua spawn trùng lặp
                }
            }
        }

        // Spawn BlockDisplay mới
        cleanupTaggedDisplays(w, key, org.bukkit.entity.ItemDisplay.class);

        displayEntity = w.spawn(spawnLoc, org.bukkit.entity.BlockDisplay.class, entity -> {
            entity.setPersistent(false); // Không lưu vào world disk để tránh rác entity khi restart/reload

            entity.setBlock(createDisplayBlockData(isFireActiveModel()));

            plugin.getPluginLogger().debug("Spawning forge BlockDisplay model, activeFire=" + isFireActiveModel());

            // Set scale
            double sx = plugin.getConfigManager().getModelScaleX();
            double sy = plugin.getConfigManager().getModelScaleY();
            double sz = plugin.getConfigManager().getModelScaleZ();
            entity.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f((float) (-sx / 2.0), -1.5f, (float) (-sz / 2.0)),
                    new org.joml.Quaternionf(),
                    new org.joml.Vector3f((float) sx, (float) sy, (float) sz),
                    new org.joml.Quaternionf()));

            entity.setInvulnerable(true);
            entity.setGravity(false);

            // Đánh dấu tag nhận diện để cleanup khi cần
            entity.getPersistentDataContainer().set(
                    key,
                    org.bukkit.persistence.PersistentDataType.STRING,
                    formatLoc(getLocation()));
        });
        updateDisplayModel(true);
    }

    public void removeDisplayEntity() {
        clearActiveLightBlocks();
        if (displayEntity != null && displayEntity.isValid()) {
            displayEntity.remove();
            displayEntity = null;
            lastRenderedActiveModel = null;
        } else {
            // Quét dọn fallback cùng vị trí
            Location checkLoc = getLocation().clone().add(
                    plugin.getConfigManager().getModelOffsetX(),
                    plugin.getConfigManager().getModelOffsetY(),
                    plugin.getConfigManager().getModelOffsetZ());
            if (checkLoc.getWorld() != null) {
                org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "forge_display");
                cleanupTaggedDisplays(checkLoc.getWorld(), key, org.bukkit.entity.BlockDisplay.class);
                cleanupTaggedDisplays(checkLoc.getWorld(), key, org.bukkit.entity.ItemDisplay.class);
            }
        }
    }

    // ── Machine overrides ─────────────────────────────────────

    /**
     * Gọi khi recipe hoàn thành.
     * Build output ItemStack và drop tại vị trí forge (y+1).
     */
    @Override
    protected void onRecipeComplete(MetallurgyRecipe recipe) {
        ItemStack result = buildOutputItem(recipe.getOutput());
        Location center = getForgeMouthLocation();
        if (center.getWorld() != null) {
            center.getWorld().spawnParticle(org.bukkit.Particle.FLAME, center, 16, 0.35, 0.25, 0.35, 0.04);
            center.getWorld().spawnParticle(org.bukkit.Particle.LAVA, center, 6, 0.2, 0.2, 0.2, 0.03);
            center.getWorld().playSound(center, org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7f, 1.25f);
            center.getWorld().playSound(center, org.bukkit.Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.8f, 1.1f);
        }

        // Đặt vật phẩm nung thành công vào ô Output (slot 15) của máy
        ItemStack existing = inventory.getItem(dev.haohansmp.metallurgy.gui.forge.ForgeGui.SLOT_OUTPUT);
        if (existing == null || existing.getType() == Material.AIR) {
            inventory.setItem(dev.haohansmp.metallurgy.gui.forge.ForgeGui.SLOT_OUTPUT, result);
        } else if (existing.isSimilar(result)) {
            int newAmount = existing.getAmount() + result.getAmount();
            if (newAmount <= existing.getMaxStackSize()) {
                existing.setAmount(newAmount);
            } else {
                // Đầy -> Rơi ra ngoài
                Location dropLoc = getLocation().clone().add(0.5, 1.2, 0.5);
                if (dropLoc.getWorld() != null) {
                    dropLoc.getWorld().dropItemNaturally(dropLoc, result);
                }
            }
        } else {
            // Khác loại vật phẩm -> Rơi ra ngoài làm fallback
            Location dropLoc = getLocation().clone().add(0.5, 1.2, 0.5);
            if (dropLoc.getWorld() != null) {
                dropLoc.getWorld().dropItemNaturally(dropLoc, result);
            }
        }

        addToSlagOutput(buildMetalSlag(recipe));

        plugin.getPluginLogger().info(
                "[AncientForge] ✔ Completed: §e" + recipe.getId()
                        + " §7at " + formatLoc(getLocation()));
    }

    private void addToSlagOutput(ItemStack result) {
        int slot = dev.haohansmp.metallurgy.gui.forge.ForgeGui.SLOT_SLAG;
        ItemStack existing = inventory.getItem(slot);
        if (existing == null || existing.getType() == Material.AIR) {
            inventory.setItem(slot, result);
        } else if (existing.isSimilar(result)) {
            int newAmount = existing.getAmount() + result.getAmount();
            if (newAmount <= existing.getMaxStackSize()) {
                existing.setAmount(newAmount);
            } else {
                dropOutput(result);
            }
        } else {
            dropOutput(result);
        }
    }

    private void dropOutput(ItemStack result) {
        Location dropLoc = getLocation().clone().add(0.5, 1.2, 0.5);
        if (dropLoc.getWorld() != null) {
            dropLoc.getWorld().dropItemNaturally(dropLoc, result);
        }
    }

    private ItemStack buildMetalSlag(MetallurgyRecipe recipe) {
        ItemStack slag = new ItemStack(Material.RAW_IRON, 1);
        ItemMeta meta = slag.getItemMeta();
        if (meta != null) {
            String metalName = recipe.getOutput().material().name()
                    .toLowerCase(java.util.Locale.ROOT)
                    .replace("_ingot", "")
                    .replace("_", " ");
            meta.setDisplayName("\u00a78S\u1ec9 kim lo\u1ea1i");
            meta.setLore(java.util.List.of(
                    "\u00a77T\u1ea1p ch\u1ea5t c\u00f2n l\u1ea1i sau khi luy\u1ec7n " + metalName + ".",
                    "\u00a77C\u00f3 th\u1ec3 d\u00f9ng cho c\u00e1c c\u00f4ng th\u1ee9c t\u00e1i ch\u1ebf sau n\u00e0y."));
            slag.setItemMeta(meta);
        }
        return slag;
    }

    private final java.util.Random random = new java.util.Random();

    /**
     * Hook tick — Thêm các hiệu ứng hình ảnh (khói đen, bụi tro, tia lửa)
     * và âm thanh ambient (tí tách lửa, ục ục dung nham) khi lò đang nung.
     */
    @Override
    protected void onTick() {
        updateDisplayModel(false);
        updateActiveLightBlocks(false);

        Location warmLoc = getLocation();
        org.bukkit.World warmWorld = warmLoc.getWorld();
        if (warmWorld != null && getTemperature() > 0) {
            Location fireGlow = getForgeMouthLocation();
            Location chimneyGlow = getForgeChimneyLocation();
            if (random.nextInt(4) == 0) {
                warmWorld.spawnParticle(org.bukkit.Particle.SMALL_FLAME, fireGlow, 2, 0.25, 0.15, 0.25, 0.02);
            }
            if (random.nextInt(5) == 0) {
                warmWorld.spawnParticle(org.bukkit.Particle.SMOKE, chimneyGlow, 1, 0.16, 0.1, 0.16, 0.02);
            }
            if (random.nextInt(18) == 0) {
                warmWorld.playSound(fireGlow, org.bukkit.Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.45f,
                        0.9f + random.nextFloat() * 0.25f);
            }
        }

        if (getFuelTicksRemaining() > 0 && getTemperature() > 0) {
            Location loc = getLocation();
            org.bukkit.World world = loc.getWorld();
            if (world != null) {
                // 1. Khói đen và khói ấm bay lên từ đỉnh lò rèn (Y = 3.2 để thoát ra từ nóc)
                Location chimney = getForgeChimneyLocation();
                if (random.nextInt(3) == 0) {
                    world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, chimney, 2, 0.15, 0.1, 0.15, 0.02);
                }
                if (random.nextInt(4) == 0) {
                    world.spawnParticle(org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, chimney, 1, 0.1, 0.1, 0.1, 0.01);
                }
                // Bụi tro rơi lơ lửng xung quanh thân lò
                if (random.nextInt(5) == 0) {
                    Location ashLoc = loc.clone().add(
                            0.5 + (random.nextDouble() - 0.5) * 2.5,
                            1.5 + random.nextDouble() * 1.5,
                            0.5 + (random.nextDouble() - 0.5) * 2.5);
                    world.spawnParticle(org.bukkit.Particle.ASH, ashLoc, 2, 0.2, 0.2, 0.2, 0.01);
                }

                // 2. Tia lửa bắn ra từ trung tâm lò Blast Furnace (Y = 0.5)
                Location fireSource = getForgeMouthLocation();
                if (random.nextInt(20) == 0) { // Thỉnh thoảng bắn tia dung nham popped
                    world.spawnParticle(org.bukkit.Particle.LAVA, fireSource, 3, 0.25, 0.25, 0.25, 0.05);
                }
                if (random.nextInt(6) == 0) { // Bụi lửa nhỏ
                    world.spawnParticle(org.bukkit.Particle.FLAME, fireSource, 2, 0.15, 0.15, 0.15, 0.02);
                }

                // 3. Âm thanh tí tách và sôi ục ục của dung nham/lửa nung
                if (random.nextInt(12) == 0) { // Lửa lò tí tách
                    world.playSound(fireSource, org.bukkit.Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.8f, 1.0f);
                }
                if (random.nextInt(25) == 0) { // Bong bóng dung nham nổ tí tách
                    world.playSound(fireSource, org.bukkit.Sound.BLOCK_LAVA_POP, 0.7f, 1.2f);
                }
                if (random.nextInt(40) == 0) { // Dung nham sôi âm ỉ
                    world.playSound(fireSource, org.bukkit.Sound.BLOCK_LAVA_AMBIENT, 0.6f, 0.8f);
                }
            }

            plugin.getPluginLogger().debug(
                    "[Forge@" + formatLoc(getLocation()) + "] "
                            + (int) (getProgressPercent() * 100) + "% "
                            + getTemperature() + "°C");
        }
    }

    // ── Helper ────────────────────────────────────────────────

    private boolean isFireActiveModel() {
        return getFuelTicksRemaining() > 0
                && getTemperature() > 0;
    }

    private Location getForgeMouthLocation() {
        Location loc = getLocation();
        org.bukkit.util.Vector direction = getForgeMouthDirection();
        return loc.add(0.5 + direction.getX() * 1.65, 1.05, 0.5 + direction.getZ() * 1.65);
    }

    private Location getForgeChimneyLocation() {
        return getLocation().add(0.5, 3.85, 0.5);
    }

    private org.bukkit.util.Vector getForgeMouthDirection() {
        return switch (rotation) {
            case 90 -> new org.bukkit.util.Vector(-1, 0, 0);
            case 180 -> new org.bukkit.util.Vector(0, 0, -1);
            case 270 -> new org.bukkit.util.Vector(1, 0, 0);
            default -> new org.bukkit.util.Vector(0, 0, 1);
        };
    }

    private NamespacedKey getDisplayModelKey(boolean active) {
        NamespacedKey activeKey = plugin.getConfigManager().getModelItemModel();
        if (activeKey == null) {
            activeKey = new NamespacedKey("haohansmp", "metallurgy");
        }
        if (active) {
            return activeKey;
        }
        return new NamespacedKey(activeKey.getNamespace(), activeKey.getKey() + "_inactive");
    }

    private ItemStack createDisplayItem(boolean active) {
        ItemStack displayItem = new ItemStack(plugin.getConfigManager().getModelMaterial());
        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            meta.setItemModel(getDisplayModelKey(active));
            meta.setCustomModelData(plugin.getConfigManager().getModelCustomModelData());
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }

    private org.bukkit.block.data.BlockData createDisplayBlockData(boolean active) {
        String vertical = active ? "up" : "down";
        String horizontal = switch (rotation) {
            case 90 -> "west";
            case 180 -> "north";
            case 270 -> "east";
            default -> "south";
        };
        String orientation = vertical + "_" + horizontal;
        try {
            return org.bukkit.Bukkit.createBlockData("minecraft:jigsaw[orientation=" + orientation + "]");
        } catch (IllegalArgumentException ex) {
            plugin.getPluginLogger().warn("Could not create forge BlockDisplay jigsaw state. Falling back to STONE. " + ex.getMessage());
            return Material.STONE.createBlockData();
        }
    }

    private void updateDisplayModel(boolean force) {
        if (displayEntity == null || !displayEntity.isValid()) {
            return;
        }

        boolean active = isFireActiveModel();
        if (!force && lastRenderedActiveModel != null && lastRenderedActiveModel == active) {
            return;
        }

        if (displayEntity instanceof org.bukkit.entity.BlockDisplay blockDisplay) {
            blockDisplay.setBlock(createDisplayBlockData(active));
        } else if (displayEntity instanceof org.bukkit.entity.ItemDisplay itemDisplay) {
            itemDisplay.setItemStack(createDisplayItem(active));
        }
        lastRenderedActiveModel = active;
    }

    private <T extends org.bukkit.entity.Entity> void cleanupTaggedDisplays(
            org.bukkit.World world,
            org.bukkit.NamespacedKey key,
            Class<T> entityClass) {
        for (T display : world.getEntitiesByClass(entityClass)) {
            if (!display.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                continue;
            }
            String locStr = display.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
            if (formatLoc(getLocation()).equals(locStr)) {
                display.remove();
            }
        }
    }

    private java.util.List<Location> getActiveLightLocations() {
        Location loc = getLocation();
        return java.util.List.of(
                loc.clone().add(0, 1, 0),
                loc.clone().add(0, 2, 0));
    }

    private void updateActiveLightBlocks(boolean force) {
        boolean active = isFireActiveModel();
        if (!force && lastLightActive != null && lastLightActive == active) {
            return;
        }

        if (active) {
            for (Location lightLoc : getActiveLightLocations()) {
                setLightBlock(lightLoc, 15);
            }
        } else {
            clearActiveLightBlocks();
        }
        lastLightActive = active;
    }

    private void setLightBlock(Location lightLoc, int level) {
        if (lightLoc.getWorld() == null) {
            return;
        }
        org.bukkit.block.Block block = lightLoc.getBlock();
        if (block.getType() != Material.LIGHT) {
            block.setType(Material.LIGHT, false);
        }
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Light lightData) {
            lightData.setLevel(Math.max(0, Math.min(15, level)));
            block.setBlockData(lightData, false);
        }
    }

    private void clearActiveLightBlocks() {
        for (Location lightLoc : getActiveLightLocations()) {
            if (lightLoc.getWorld() == null) {
                continue;
            }
            org.bukkit.block.Block block = lightLoc.getBlock();
            if (block.getType() == Material.LIGHT) {
                block.setType(Material.BARRIER, false);
            }
        }
        lastLightActive = null;
    }

    @SuppressWarnings("deprecation")
    private ItemStack buildOutputItem(MetallurgyRecipe.OutputItem out) {
        if (out.customItemId() != null && !out.customItemId().isEmpty()) {
            java.util.Optional<dev.haohansmp.metallurgy.item.CustomItem> ciOpt = dev.haohansmp.metallurgy.item.CustomItem
                    .getById(out.customItemId());
            if (ciOpt.isPresent()) {
                return plugin.getItemManager().createItem(ciOpt.get(), out.amount());
            }
        }

        ItemStack item = new ItemStack(out.material(), out.amount());
        ItemMeta meta = item.getItemMeta();

        if (out.displayName() != null && !out.displayName().isBlank()) {
            meta.setDisplayName(out.displayName().replace("&", "§"));
        }
        if (out.lore() != null && !out.lore().isEmpty()) {
            meta.setLore(out.lore().stream()
                    .map(l -> l.replace("&", "§"))
                    .toList());
        }
        if (out.customModelData() > 0) {
            meta.setCustomModelData(out.customModelData());
        }

        item.setItemMeta(meta);
        return item;
    }

    private String formatLoc(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
