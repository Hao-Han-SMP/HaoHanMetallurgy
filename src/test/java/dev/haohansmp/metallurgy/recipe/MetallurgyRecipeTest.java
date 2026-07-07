package dev.haohansmp.metallurgy.recipe;

import dev.haohansmp.metallurgy.machine.MachineType;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho MetallurgyRecipe — không cần Bukkit server.
 */
class MetallurgyRecipeTest {

    private MetallurgyRecipe buildTestRecipe() {
        var inputs = List.of(
            new MetallurgyRecipe.Ingredient(Material.IRON_INGOT, 4),
            new MetallurgyRecipe.Ingredient(Material.BLAZE_POWDER, 2)
        );
        var output = new MetallurgyRecipe.OutputItem(
            Material.IRON_INGOT, 2,
            "&6Embersteel Ingot",
            List.of("&7Test lore"),
            1001,
            "embersteel_ingot"
        );
        return new MetallurgyRecipe(
            "ancient_forge/embersteel_ingot",
            MachineType.ANCIENT_FORGE.name(),
            inputs,
            output,
            400,
            30,
            800,
            1200,
            "haohansmp:metallurgy/find_ember_ore"
        );
    }

    @Test
    void testRecipeIdIsSet() {
        var recipe = buildTestRecipe();
        assertEquals("ancient_forge/embersteel_ingot", recipe.getId());
    }

    @Test
    void testMachineTypeIsCorrect() {
        var recipe = buildTestRecipe();
        assertEquals("ANCIENT_FORGE", recipe.getMachineType());
    }

    @Test
    void testInputsAreImmutable() {
        var recipe = buildTestRecipe();
        assertThrows(UnsupportedOperationException.class, () -> {
            recipe.getInputs().add(new MetallurgyRecipe.Ingredient(Material.COAL, 1));
        });
    }

    @Test
    void testIngredientMatches() {
        var ingredient = new MetallurgyRecipe.Ingredient(Material.IRON_INGOT, 4);
        assertTrue(ingredient.matches(Material.IRON_INGOT, 4));
        assertTrue(ingredient.matches(Material.IRON_INGOT, 5)); // có nhiều hơn → ok
        assertFalse(ingredient.matches(Material.IRON_INGOT, 3)); // thiếu
        assertFalse(ingredient.matches(Material.GOLD_INGOT, 4)); // sai material
    }

    @Test
    void testFuelAndTime() {
        var recipe = buildTestRecipe();
        assertEquals(400, recipe.getFuelCost());
        assertEquals(30, recipe.getTimeSeconds());
        assertEquals(800, recipe.getMinTemperature());
        assertEquals(1200, recipe.getMaxTemperature());
        assertEquals("haohansmp:metallurgy/find_ember_ore", recipe.getRequiredAdvancement());
    }

    @Test
    void testOutputFields() {
        var recipe = buildTestRecipe();
        var output = recipe.getOutput();
        assertEquals(Material.IRON_INGOT, output.material());
        assertEquals(2, output.amount());
        assertEquals(1001, output.customModelData());
        assertEquals(1, output.lore().size());
        assertEquals("embersteel_ingot", output.customItemId());
    }
}
