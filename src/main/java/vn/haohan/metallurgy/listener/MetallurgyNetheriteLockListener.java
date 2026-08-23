package vn.haohan.metallurgy.listener;

import vn.haohan.metallurgy.HaoHanMetallurgy;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Đảm bảo các công thức Netherite vanilla bị khóa để người chơi bắt buộc phải sử dụng
 * quy trình luyện kim của HaoHan Metallurgy.
 */
public class MetallurgyNetheriteLockListener implements Listener {

    private final HaoHanMetallurgy plugin;

    public MetallurgyNetheriteLockListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    /**
     * Xóa các công thức Netherite gốc của vanilla khỏi Bukkit server.
     */
    public void removeVanillaNetheriteRecipes() {
        List<NamespacedKey> keys = new ArrayList<>();
        Iterator<Recipe> recipes = plugin.getServer().recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if (!(recipe instanceof Keyed keyed)
                    || !NamespacedKey.MINECRAFT.equals(keyed.getKey().getNamespace())) {
                continue;
            }
            String id = keyed.getKey().getKey();
            if ("netherite_ingot".equals(id)
                    || "netherite_scrap".equals(id)
                    || "netherite_scrap_from_blasting".equals(id)
                    || (id.startsWith("netherite_") && id.endsWith("_smithing"))) {
                keys.add(keyed.getKey());
            }
        }
        keys.forEach(plugin.getServer()::removeRecipe);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe instanceof Keyed keyed
                && "minecraft".equals(keyed.getKey().getNamespace())
                && "netherite_ingot".equals(keyed.getKey().getKey())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        Recipe recipe = event.getInventory().getRecipe();
        if (recipe instanceof Keyed keyed
                && "minecraft".equals(keyed.getKey().getNamespace())
                && keyed.getKey().getKey().startsWith("netherite_")
                && keyed.getKey().getKey().endsWith("_smithing")) {
            event.setResult(null);
        }
    }
}
