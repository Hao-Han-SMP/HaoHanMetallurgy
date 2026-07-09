package dev.haohansmp.metallurgy.recipe;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.item.CustomItem;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

public class CraftingRecipeManager {

    private final HaoHanMetallurgy plugin;

    public CraftingRecipeManager(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        registerMithrilCompression();
        registerMithrilPickaxes();
        registerSlagPickaxes();
    }

    private void registerMithrilCompression() {
        ItemStack ingot = plugin.getItemManager().createItem(CustomItem.MITHRIL_INGOT, 1);
        ItemStack shards = plugin.getItemManager().createItem(CustomItem.MITHRIL_SHARD, 9);

        ShapelessRecipe ingotToShards = new ShapelessRecipe(key("mithril_ingot_to_shards"), shards);
        ingotToShards.addIngredient(exact(ingot));
        addRecipe(ingotToShards);

        ShapedRecipe shardsToIngot = new ShapedRecipe(key("mithril_shards_to_ingot"), ingot);
        shardsToIngot.shape("SSS", "SSS", "SSS");
        shardsToIngot.setIngredient('S', exact(plugin.getItemManager().createItem(CustomItem.MITHRIL_SHARD, 1)));
        addRecipe(shardsToIngot);
    }

    private void registerMithrilPickaxes() {
        ShapedRecipe mithrilPickaxe = new ShapedRecipe(
                key("mithril_pickaxe"),
                plugin.getItemManager().createItem(CustomItem.MITHRIL_PICKAXE, 1));
        mithrilPickaxe.shape("MMM", " T ", " T ");
        mithrilPickaxe.setIngredient('M', exact(plugin.getItemManager().createItem(CustomItem.MITHRIL_INGOT, 1)));
        mithrilPickaxe.setIngredient('T', Material.STICK);
        addRecipe(mithrilPickaxe);

    }

    private void registerSlagPickaxes() {
        registerSlagPickaxe(CustomItem.COPPER_SLAG_PICKAXE, CustomItem.COPPER_SLAG);
        registerSlagPickaxe(CustomItem.IRON_SLAG_PICKAXE, CustomItem.IRON_SLAG);
        registerSlagPickaxe(CustomItem.GOLD_SLAG_PICKAXE, CustomItem.GOLD_SLAG);
        registerSlagPickaxe(CustomItem.EMBERSTEEL_SLAG_PICKAXE, CustomItem.EMBERSTEEL_SLAG);
        registerSlagPickaxe(CustomItem.SOULSTEEL_SLAG_PICKAXE, CustomItem.SOULSTEEL_SLAG);
        registerSlagPickaxe(CustomItem.NETHERITE_SLAG_PICKAXE, CustomItem.NETHERITE_SLAG);
        registerSlagPickaxe(CustomItem.MITHRIL_SLAG_PICKAXE, CustomItem.MITHRIL_SLAG);
    }

    private void registerSlagPickaxe(CustomItem result, CustomItem slag) {
        ShapedRecipe recipe = new ShapedRecipe(
                key(result.getId()),
                plugin.getItemManager().createItem(result, 1));
        recipe.shape("SSS", " T ", " T ");
        recipe.setIngredient('S', exact(plugin.getItemManager().createItem(slag, 1)));
        recipe.setIngredient('T', Material.STICK);
        addRecipe(recipe);
    }

    private RecipeChoice.ExactChoice exact(ItemStack item) {
        return new RecipeChoice.ExactChoice(item);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }

    private void addRecipe(org.bukkit.inventory.Recipe recipe) {
        if (recipe instanceof org.bukkit.Keyed keyed) {
            plugin.getServer().removeRecipe(keyed.getKey());
        }
        plugin.getServer().addRecipe(recipe);
    }
}
