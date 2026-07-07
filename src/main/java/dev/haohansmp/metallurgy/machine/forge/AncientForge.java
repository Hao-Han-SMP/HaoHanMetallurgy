package dev.haohansmp.metallurgy.machine.forge;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.machine.Machine;
import dev.haohansmp.metallurgy.machine.MachineState;
import dev.haohansmp.metallurgy.machine.MachineType;
import dev.haohansmp.metallurgy.recipe.MetallurgyRecipe;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Ancient Forge machine — loại máy chính của hệ thống.
 *
 * Extends {@link Machine} và implement onRecipeComplete() để:
 *   - Build ItemStack output từ recipe
 *   - Drop item tại vị trí forge
 *
 * Phase 3: output drop tự nhiên tại forge.
 * Phase 6+: output vào GUI inventory slot thay vì drop.
 */
public class AncientForge extends Machine {

    private final int rotation;
    private final java.util.Map<ForgeStructure.BlockOffset, Material> originalBlocks;
    private org.bukkit.entity.ItemDisplay displayEntity = null;

    public AncientForge(HaoHanMetallurgy plugin, Location controllerLocation, int rotation) {
        this(plugin, controllerLocation, rotation, new java.util.HashMap<>());
    }

    public AncientForge(HaoHanMetallurgy plugin, Location controllerLocation, int rotation, java.util.Map<ForgeStructure.BlockOffset, Material> originalBlocks) {
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

    public void refreshDisplayEntity() {
        removeDisplayEntity();
        spawnDisplayEntity();
    }

    public void ensureBarrierBlocks() {
        Location loc = getLocation();
        if (loc.getWorld() == null) return;

        loc.getBlock().setType(Material.BARRIER, false);
        for (ForgeStructure.BlockOffset offset : ForgeStructure.REQUIRED_BLOCKS) {
            int rx = offset.dx();
            int rz = offset.dz();
            if (rotation == 90) { rx = -offset.dz(); rz = offset.dx(); }
            else if (rotation == 180) { rx = -offset.dx(); rz = -offset.dz(); }
            else if (rotation == 270) { rx = offset.dz(); rz = -offset.dx(); }

            loc.clone().add(rx, offset.dy(), rz).getBlock().setType(Material.BARRIER, false);
        }
    }

    private void spawnDisplayEntity() {
        if (!plugin.getConfigManager().isModelEnabled()) return;
        
        Location spawnLoc = getLocation().clone().add(
            plugin.getConfigManager().getModelOffsetX(),
            plugin.getConfigManager().getModelOffsetY(),
            plugin.getConfigManager().getModelOffsetZ()
        );
        
        org.bukkit.World w = spawnLoc.getWorld();
        if (w == null) return;
        
        // Spawn ItemDisplay
        displayEntity = w.spawn(spawnLoc, org.bukkit.entity.ItemDisplay.class, entity -> {
            entity.setPersistent(false); // Không lưu vào world disk để tránh rác entity khi restart/reload
            
            // Set item display
            ItemStack displayItem = new ItemStack(plugin.getConfigManager().getModelMaterial());
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                if (plugin.getConfigManager().getModelItemModel() != null) {
                    meta.setItemModel(plugin.getConfigManager().getModelItemModel());
                }
                meta.setCustomModelData(plugin.getConfigManager().getModelCustomModelData());
                displayItem.setItemMeta(meta);
            }
            entity.setItemStack(displayItem);
            entity.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.FIXED);
            
            plugin.getPluginLogger().info("Spawning forge model: material=" + displayItem.getType()
                + ", itemModel=" + (meta != null && meta.hasItemModel() ? meta.getItemModel() : "none")
                + ", customModelData=" + (meta != null && meta.hasCustomModelData() ? meta.getCustomModelData() : "none"));
            
            // Set scale
            double sx = plugin.getConfigManager().getModelScaleX();
            double sy = plugin.getConfigManager().getModelScaleY();
            double sz = plugin.getConfigManager().getModelScaleZ();
            entity.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0f, 0.5f, 0f),
                new org.joml.Quaternionf(),
                new org.joml.Vector3f((float) sx, (float) sy, (float) sz),
                new org.joml.Quaternionf()
            ));
            
            // Set rotation
            float yaw = 0f;
            if (rotation == 90) yaw = 90f;
            else if (rotation == 180) yaw = 180f;
            else if (rotation == 270) yaw = 270f;
            entity.setRotation(yaw, 0f);
            
            entity.setInvulnerable(true);
            entity.setGravity(false);
            
            // Đánh dấu tag nhận diện để cleanup khi cần
            entity.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "forge_display"),
                org.bukkit.persistence.PersistentDataType.STRING,
                formatLoc(getLocation())
            );
        });
    }

    public void removeDisplayEntity() {
        if (displayEntity != null && displayEntity.isValid()) {
            displayEntity.remove();
            displayEntity = null;
        } else {
            // Quét dọn fallback cùng vị trí
            Location checkLoc = getLocation().clone().add(
                plugin.getConfigManager().getModelOffsetX(),
                plugin.getConfigManager().getModelOffsetY(),
                plugin.getConfigManager().getModelOffsetZ()
            );
            if (checkLoc.getWorld() != null) {
                for (org.bukkit.entity.ItemDisplay display : checkLoc.getWorld().getEntitiesByClass(org.bukkit.entity.ItemDisplay.class)) {
                    if (display.getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "forge_display"), org.bukkit.persistence.PersistentDataType.STRING)) {
                        String locStr = display.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "forge_display"), org.bukkit.persistence.PersistentDataType.STRING);
                        if (formatLoc(getLocation()).equals(locStr)) {
                            display.remove();
                        }
                    }
                }
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

        // Đặt vật phẩm nung thành công vào ô Output (slot 15) của máy
        ItemStack existing = inventory.getItem(15);
        if (existing == null || existing.getType() == Material.AIR) {
            inventory.setItem(15, result);
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

        plugin.getPluginLogger().info(
            "[AncientForge] ✔ Completed: §e" + recipe.getId()
            + " §7at " + formatLoc(getLocation())
        );
    }

    private final java.util.Random random = new java.util.Random();

    /**
     * Hook tick — Thêm các hiệu ứng hình ảnh (khói đen, bụi tro, tia lửa)
     * và âm thanh ambient (tí tách lửa, ục ục dung nham) khi lò đang nung.
     */
    @Override
    protected void onTick() {
        if (getState() == MachineState.WORKING) {
            Location loc = getLocation();
            org.bukkit.World world = loc.getWorld();
            if (world != null) {
                // 1. Khói đen và khói ấm bay lên từ đỉnh lò rèn (Y = 3.2 để thoát ra từ nóc)
                Location chimney = loc.clone().add(0.5, 3.2, 0.5);
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
                        0.5 + (random.nextDouble() - 0.5) * 2.5
                    );
                    world.spawnParticle(org.bukkit.Particle.ASH, ashLoc, 2, 0.2, 0.2, 0.2, 0.01);
                }

                // 2. Tia lửa bắn ra từ trung tâm lò Blast Furnace (Y = 0.5)
                Location fireSource = loc.clone().add(0.5, 0.5, 0.5);
                if (random.nextInt(20) == 0) { // Thỉnh thoảng bắn tia dung nham popped
                    world.spawnParticle(org.bukkit.Particle.LAVA, fireSource, 3, 0.25, 0.25, 0.25, 0.05);
                }
                if (random.nextInt(6) == 0) { // Bụi lửa nhỏ
                    world.spawnParticle(org.bukkit.Particle.FLAME, fireSource, 2, 0.15, 0.15, 0.15, 0.02);
                }

                // 3. Âm thanh tí tách và sôi ục ục của dung nham/lửa nung
                if (random.nextInt(12) == 0) { // Lửa lò tí tách
                    world.playSound(fireSource, org.bukkit.Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.8f, 1.0f);
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
                + (int)(getProgressPercent() * 100) + "% "
                + getTemperature() + "°C"
            );
        }
    }

    // ── Helper ────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    private ItemStack buildOutputItem(MetallurgyRecipe.OutputItem out) {
        if (out.customItemId() != null && !out.customItemId().isEmpty()) {
            java.util.Optional<dev.haohansmp.metallurgy.item.CustomItem> ciOpt = dev.haohansmp.metallurgy.item.CustomItem.getById(out.customItemId());
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
