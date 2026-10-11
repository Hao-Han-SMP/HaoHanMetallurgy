package vn.haohan.metallurgy.recipe;

import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.recipe.Ingredient;
import vn.haohan.itemcore.api.recipe.ItemResult;
import vn.haohan.itemcore.api.recipe.RecipeDefinition;
import vn.haohan.itemcore.api.recipe.RecipeType;
import vn.haohan.itemcore.api.recipe.ShapedRecipeDefinition;
import vn.haohan.metallurgy.HaoHanMetallurgy;
import vn.haohan.metallurgy.item.CustomItem;
import org.bukkit.Material;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quản lý và đăng ký toàn bộ công thức chế tạo (Crafting & Machine) của Metallurgy
 * vào hệ thống HaoHanItemCore tập trung.
 */
public class MetallurgyRecipeRegistry {

    private static final Map<String, CustomItem> PICKAXE_INGREDIENTS = Map.ofEntries(
            Map.entry("copper_slag_pickaxe", CustomItem.COPPER_SLAG),
            Map.entry("iron_slag_pickaxe", CustomItem.IRON_SLAG),
            Map.entry("gold_slag_pickaxe", CustomItem.GOLD_SLAG),
            Map.entry("embersteel_slag_pickaxe", CustomItem.EMBERSTEEL_SLAG),
            Map.entry("soulsteel_slag_pickaxe", CustomItem.SOULSTEEL_SLAG),
            Map.entry("netherite_slag_pickaxe", CustomItem.NETHERITE_SLAG),
            Map.entry("mithril_slag_pickaxe", CustomItem.MITHRIL_SLAG),
            Map.entry("embersteel_pickaxe", CustomItem.EMBERSTEEL_INGOT),
            Map.entry("mithril_pickaxe", CustomItem.MITHRIL_INGOT),
            Map.entry("soulsteel_pickaxe", CustomItem.SOULSTEEL_INGOT));

    private final HaoHanMetallurgy plugin;
    private final Set<String> registeredRecipeIds = new LinkedHashSet<>();

    public MetallurgyRecipeRegistry(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    /**
     * Đăng ký tất cả các công thức chế tạo bàn (Crafting Table) và lò nung vào ItemCore.
     */
    public void registerAll() {
        unregisterAll();
        registerCraftingRecipes();
        registerFurnaceRecipes();
        if (plugin != null && plugin.getPluginLogger() != null) {
            plugin.getPluginLogger().info("Registered " + registeredRecipeIds.size() + " Metallurgy recipes in HaoHanItemCore.");
        }
    }

    private void registerFurnaceRecipes() {
        // Controlled metal smelting & blasting recipes registered in ItemCore for viewing
        registerMetalSmeltAndBlast("copper", List.of(Material.RAW_COPPER, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE), Material.COPPER_INGOT);
        registerMetalSmeltAndBlast("iron", List.of(Material.RAW_IRON, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE), Material.IRON_INGOT);
        registerMetalSmeltAndBlast("gold", List.of(Material.RAW_GOLD, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE), Material.GOLD_INGOT);
        registerMetalSmeltAndBlast("netherite", List.of(Material.ANCIENT_DEBRIS), Material.NETHERITE_SCRAP);
    }

    private void registerMetalSmeltAndBlast(String name, List<Material> inputs, Material output) {
        for (Material input : inputs) {
            String suffix = input.name().toLowerCase(java.util.Locale.ROOT);
            register(new RecipeDefinition("haohanmetallurgy:" + name + "_smelt_" + suffix,
                    RecipeType.SMELTING,
                    List.of(new Ingredient.MaterialIngredient(input)),
                    new ItemResult("minecraft:" + output.name().toLowerCase(java.util.Locale.ROOT), 1),
                    0.7f, 200));

            register(new RecipeDefinition("haohanmetallurgy:" + name + "_blast_" + suffix,
                    RecipeType.BLASTING,
                    List.of(new Ingredient.MaterialIngredient(input)),
                    new ItemResult("minecraft:" + output.name().toLowerCase(java.util.Locale.ROOT), 1),
                    0.7f, 100));
        }
    }

    private void registerCraftingRecipes() {
        // 1. Mithril Ingot -> 9 Shards (Shapeless)
        register(new RecipeDefinition("haohanmetallurgy:mithril_ingot_to_shards",
                RecipeType.SHAPELESS,
                List.of(item(CustomItem.MITHRIL_INGOT)),
                result(CustomItem.MITHRIL_SHARD, 9)));

        // 2. 9 Mithril Shards -> Ingot (Shaped 3x3)
        register(new ShapedRecipeDefinition("haohanmetallurgy:mithril_shards_to_ingot",
                List.of("SSS", "SSS", "SSS"),
                Map.of('S', item(CustomItem.MITHRIL_SHARD)),
                result(CustomItem.MITHRIL_INGOT, 1)));

        // 3. Raw Borax -> Borax Powder (Shapeless)
        register(new RecipeDefinition("haohanmetallurgy:raw_borax_to_powder",
                RecipeType.SHAPELESS,
                List.of(item(CustomItem.RAW_BORAX)),
                result(CustomItem.BORAX_POWDER, 1)));

        // 4. Bow Drill: 2 Sticks + 1 Jute Cord (Shapeless)
        register(new RecipeDefinition("haohanmetallurgy:bow_drill",
                RecipeType.SHAPELESS,
                List.of(material(Material.STICK, 2), item(CustomItem.JUTE_CORD)),
                result(CustomItem.BOW_DRILL, 1)));

        // 5. Charcoal Block -> 9 Charcoal (Shapeless)
        register(new RecipeDefinition("haohanmetallurgy:charcoal_block_to_charcoal",
                RecipeType.SHAPELESS,
                List.of(item(CustomItem.CHARCOAL_BLOCK)),
                new ItemResult("minecraft:charcoal", 9)));

        // 6. 9 Charcoal -> Charcoal Block (Shaped 3x3)
        register(new ShapedRecipeDefinition("haohanmetallurgy:charcoal_to_charcoal_block",
                List.of("CCC", "CCC", "CCC"),
                Map.of('C', material(Material.CHARCOAL)),
                result(CustomItem.CHARCOAL_BLOCK, 1)));

        // 7-9. Alloy Pickaxes
        registerPickaxeRecipe(CustomItem.EMBERSTEEL_PICKAXE, CustomItem.EMBERSTEEL_INGOT);
        registerPickaxeRecipe(CustomItem.MITHRIL_PICKAXE, CustomItem.MITHRIL_INGOT);
        registerPickaxeRecipe(CustomItem.SOULSTEEL_PICKAXE, CustomItem.SOULSTEEL_INGOT);

        // 10-16. Slag Pickaxes
        for (Map.Entry<String, CustomItem> entry : PICKAXE_INGREDIENTS.entrySet()) {
            CustomItem resultItem = CustomItem.getById(entry.getKey()).orElse(null);
            if (resultItem != null && entry.getKey().endsWith("_slag_pickaxe")) {
                registerPickaxeRecipe(resultItem, entry.getValue());
            }
        }
    }

    private void registerPickaxeRecipe(CustomItem resultItem, CustomItem ingredient) {
        register(new ShapedRecipeDefinition("haohanmetallurgy:" + resultItem.getId(),
                List.of("III", " T ", " T "),
                Map.of('I', item(ingredient), 'T', material(Material.STICK)),
                result(resultItem, 1)));
    }

    /**
     * Đăng ký các công thức lò máy (Machine recipes) vào ItemCore dưới dạng MACHINE để hiển thị trong Recipe Viewer.
     */
    public void registerMachineRecipes(RecipeLoader loader) {
        for (MetallurgyRecipe recipe : loader.all()) {
            List<Ingredient> ingredients = new java.util.ArrayList<>();
            for (MetallurgyRecipe.Ingredient input : recipe.getInputs()) {
                if (input.customItemId() != null && !input.customItemId().isBlank()) {
                    ingredients.add(new Ingredient.ItemIngredient(
                            "haohanmetallurgy:" + input.customItemId(), input.amount()));
                } else {
                    ingredients.add(new Ingredient.MaterialIngredient(input.material(), input.amount()));
                }
            }

            MetallurgyRecipe.OutputItem output = recipe.getOutput();
            String outputId = output.customItemId() != null && !output.customItemId().isBlank()
                    ? "haohanmetallurgy:" + output.customItemId()
                    : "minecraft:" + output.material().name().toLowerCase(java.util.Locale.ROOT);

            register(new RecipeDefinition(
                    "haohanmetallurgy:" + recipe.getId().replace(':', '_'),
                    RecipeType.MACHINE,
                    ingredients,
                    new ItemResult(outputId, output.amount())));
        }
    }

    private void register(RecipeDefinition recipe) {
        var registry = HaoHanItemCore.get().getRecipeRegistry();
        if (registry.exists(recipe.getId())) {
            registry.unregister(recipe.getId());
        }
        registry.register(recipe);
        registeredRecipeIds.add(recipe.getId());
    }

    public void unregisterAll() {
        var registry = HaoHanItemCore.get().getRecipeRegistry();
        registeredRecipeIds.forEach(registry::unregister);
        registeredRecipeIds.clear();
    }

    public Set<String> getRegisteredRecipeIds() {
        return Set.copyOf(registeredRecipeIds);
    }

    private static Ingredient.ItemIngredient item(CustomItem item) {
        return new Ingredient.ItemIngredient("haohanmetallurgy:" + item.getId());
    }

    private static Ingredient.MaterialIngredient material(Material material) {
        return new Ingredient.MaterialIngredient(material);
    }

    private static Ingredient.MaterialIngredient material(Material material, int amount) {
        return new Ingredient.MaterialIngredient(material, amount);
    }

    private static ItemResult result(CustomItem item, int amount) {
        return new ItemResult("haohanmetallurgy:" + item.getId(), amount);
    }
}
