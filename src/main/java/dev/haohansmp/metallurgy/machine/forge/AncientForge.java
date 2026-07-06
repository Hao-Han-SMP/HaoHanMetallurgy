package dev.haohansmp.metallurgy.machine.forge;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.machine.Machine;
import dev.haohansmp.metallurgy.machine.MachineState;
import dev.haohansmp.metallurgy.machine.MachineType;
import dev.haohansmp.metallurgy.recipe.MetallurgyRecipe;
import org.bukkit.Location;
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

    public AncientForge(HaoHanMetallurgy plugin, Location controllerLocation) {
        super(plugin, controllerLocation, MachineType.ANCIENT_FORGE);
    }

    // ── Machine overrides ─────────────────────────────────────

    /**
     * Gọi khi recipe hoàn thành.
     * Build output ItemStack và drop tại vị trí forge (y+1).
     */
    @Override
    protected void onRecipeComplete(MetallurgyRecipe recipe) {
        ItemStack result = buildOutputItem(recipe.getOutput());

        // Drop output tại tâm block, y+1 để không bị kẹt trong block
        Location dropLoc = getLocation().clone().add(0.5, 1.2, 0.5);
        if (dropLoc.getWorld() != null) {
            dropLoc.getWorld().dropItemNaturally(dropLoc, result);
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
