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
    private org.bukkit.entity.ItemDisplay displayEntity = null;

    public AncientForge(HaoHanMetallurgy plugin, Location controllerLocation, int rotation) {
        super(plugin, controllerLocation, MachineType.ANCIENT_FORGE);
        this.rotation = rotation;
        spawnDisplayEntity();
    }

    public int getRotation() {
        return rotation;
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
                meta.setCustomModelData(plugin.getConfigManager().getModelCustomModelData());
                displayItem.setItemMeta(meta);
            }
            entity.setItemStack(displayItem);
            
            // Set scale
            double sx = plugin.getConfigManager().getModelScaleX();
            double sy = plugin.getConfigManager().getModelScaleY();
            double sz = plugin.getConfigManager().getModelScaleZ();
            entity.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0f, 0f, 0f),
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

    /**
     * Hook tick — Phase 5 sẽ thêm particle effects, bossbar, v.v.
     * Phase 3: chỉ log debug nếu cần.
     */
    @Override
    protected void onTick() {
        if (getState() == MachineState.WORKING) {
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
