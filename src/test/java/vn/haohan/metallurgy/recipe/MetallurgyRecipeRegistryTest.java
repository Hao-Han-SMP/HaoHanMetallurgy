package vn.haohan.metallurgy.recipe;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.recipe.Ingredient;
import vn.haohan.itemcore.api.recipe.RecipeDefinition;
import vn.haohan.itemcore.api.recipe.RecipeRegistry;
import vn.haohan.itemcore.api.recipe.RecipeType;
import vn.haohan.itemcore.api.recipe.ShapedRecipeDefinition;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class MetallurgyRecipeRegistryTest {

    private InMemoryRecipeRegistry recipeRegistry;

    static class InMemoryRecipeRegistry implements RecipeRegistry {
        final Map<String, RecipeDefinition> map = new ConcurrentHashMap<>();

        @Override
        public void register(RecipeDefinition recipe) {
            map.put(recipe.getId(), recipe);
        }

        @Override
        public RecipeDefinition get(String id) {
            return map.get(id);
        }

        @Override
        public RecipeDefinition require(String id) {
            RecipeDefinition recipe = map.get(id);
            if (recipe == null) throw new IllegalArgumentException("Not found: " + id);
            return recipe;
        }

        @Override
        public boolean exists(String id) {
            return map.containsKey(id);
        }

        @Override
        public void unregister(String id) {
            map.remove(id);
        }

        @Override
        public Collection<RecipeDefinition> all() {
            return map.values();
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public void clear() {
            map.clear();
        }
    }

    @BeforeEach
    void setUp() {
        recipeRegistry = new InMemoryRecipeRegistry();
        HaoHanItemCore core = new HaoHanItemCore(
                null, null, null, recipeRegistry, null, null);
        HaoHanItemCore.setInstance(core);
    }

    @Test
    void testRegisterAllCraftingRecipes() {
        MetallurgyRecipeRegistry registry = new MetallurgyRecipeRegistry(null);
        registry.registerAll();

        // Check total count: 16 crafting + 22 smelting/blasting = 38 recipes
        assertEquals(38, registry.getRegisteredRecipeIds().size());
        assertEquals(38, recipeRegistry.size());

        // 1. Mithril ingot to shards (Shapeless)
        RecipeDefinition ingotToShards = recipeRegistry.require("haohanmetallurgy:mithril_ingot_to_shards");
        assertEquals(RecipeType.SHAPELESS, ingotToShards.getType());
        assertEquals(1, ingotToShards.getIngredients().size());
        assertEquals("haohanmetallurgy:mithril_shard", ingotToShards.getResult().item());
        assertEquals(9, ingotToShards.getResult().amount());

        // 2. Mithril shards to ingot (Shaped 3x3)
        RecipeDefinition shardsToIngot = recipeRegistry.require("haohanmetallurgy:mithril_shards_to_ingot");
        assertEquals(RecipeType.SHAPED, shardsToIngot.getType());
        assertInstanceOf(ShapedRecipeDefinition.class, shardsToIngot);
        ShapedRecipeDefinition shapedShards = (ShapedRecipeDefinition) shardsToIngot;
        assertEquals("SSS", shapedShards.getPattern().get(0));
        assertEquals("haohanmetallurgy:mithril_ingot", shardsToIngot.getResult().item());

        // 3. Raw borax to powder
        RecipeDefinition borax = recipeRegistry.require("haohanmetallurgy:raw_borax_to_powder");
        assertEquals(RecipeType.SHAPELESS, borax.getType());
        assertEquals("haohanmetallurgy:borax_powder", borax.getResult().item());

        // 4. Bow drill (2 sticks, 1 jute cord)
        RecipeDefinition bowDrill = recipeRegistry.require("haohanmetallurgy:bow_drill");
        assertEquals(RecipeType.SHAPELESS, bowDrill.getType());
        assertEquals(2, bowDrill.getIngredients().size());
        boolean hasStick = false;
        boolean hasCord = false;
        for (Ingredient ing : bowDrill.getIngredients()) {
            if (ing instanceof Ingredient.MaterialIngredient mat && mat.material() == Material.STICK) {
                assertEquals(2, mat.amount());
                hasStick = true;
            } else if (ing instanceof Ingredient.ItemIngredient item && "haohanmetallurgy:jute_cord".equals(item.id())) {
                hasCord = true;
            }
        }
        assertTrue(hasStick, "Bow drill must require 2 sticks");
        assertTrue(hasCord, "Bow drill must require 1 jute cord");

        // 5. Charcoal block unpacking
        RecipeDefinition unpack = recipeRegistry.require("haohanmetallurgy:charcoal_block_to_charcoal");
        assertEquals("minecraft:charcoal", unpack.getResult().item());
        assertEquals(9, unpack.getResult().amount());

        // 6. Charcoal block packing
        RecipeDefinition pack = recipeRegistry.require("haohanmetallurgy:charcoal_to_charcoal_block");
        assertEquals(RecipeType.SHAPED, pack.getType());
        assertEquals("haohanmetallurgy:charcoal_block", pack.getResult().item());

        // Pickaxes (3 alloy + 7 slag = 10 pickaxes)
        String[] pickaxes = {
            "embersteel_pickaxe", "mithril_pickaxe", "soulsteel_pickaxe",
            "copper_slag_pickaxe", "iron_slag_pickaxe", "gold_slag_pickaxe",
            "embersteel_slag_pickaxe", "soulsteel_slag_pickaxe",
            "netherite_slag_pickaxe", "mithril_slag_pickaxe"
        };
        for (String pickaxe : pickaxes) {
            RecipeDefinition def = recipeRegistry.require("haohanmetallurgy:" + pickaxe);
            assertEquals(RecipeType.SHAPED, def.getType());
            assertInstanceOf(ShapedRecipeDefinition.class, def);
            ShapedRecipeDefinition shaped = (ShapedRecipeDefinition) def;
            assertEquals("III", shaped.getPattern().get(0));
            assertEquals(" T ", shaped.getPattern().get(1));
            assertEquals(" T ", shaped.getPattern().get(2));
            assertEquals("haohanmetallurgy:" + pickaxe, def.getResult().item());
        }
    }

    @Test
    void testUnregisterAll() {
        MetallurgyRecipeRegistry registry = new MetallurgyRecipeRegistry(null);
        registry.registerAll();
        assertEquals(38, recipeRegistry.size());

        registry.unregisterAll();
        assertEquals(0, recipeRegistry.size());
        assertEquals(0, registry.getRegisteredRecipeIds().size());
    }
}
