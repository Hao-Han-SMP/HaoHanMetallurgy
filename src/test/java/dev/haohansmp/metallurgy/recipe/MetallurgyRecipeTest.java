package dev.haohansmp.metallurgy.recipe;

import dev.haohansmp.metallurgy.machine.MachineType;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

    @Test
    void testRequiredAdditive() {
        var inputs = List.of(new MetallurgyRecipe.Ingredient(Material.IRON_INGOT, 4));
        var output = new MetallurgyRecipe.OutputItem(Material.IRON_INGOT, 2, "Name", List.of(), 0, null);
        var recipe = new MetallurgyRecipe(
            "test_additive",
            "ANCIENT_FORGE",
            inputs,
            output,
            100,
            10,
            500,
            800,
            null,
            0.1,
            Material.SOUL_SOIL
        );
        assertEquals(Material.SOUL_SOIL, recipe.getRequiredAdditive());
    }

    @Test
    void temperatureBelowPurificationIncreasesSlagChance() {
        var recipe = new MetallurgyRecipe(
            "temperature_quality",
            "ANCIENT_FORGE",
            List.of(new MetallurgyRecipe.Ingredient(Material.RAW_IRON, 1)),
            new MetallurgyRecipe.OutputItem(Material.IRON_INGOT, 1, null, List.of(), 0, null),
            100,
            10,
            1000,
            1500,
            1750,
            null,
            0.10,
            0.90,
            List.of(),
            1,
            -1.0,
            false,
            false
        );

        assertEquals(0.90, recipe.getTemperatureFailChance(1000), 0.0001);
        assertEquals(0.50, recipe.getTemperatureFailChance(1250), 0.0001);
        assertEquals(0.10, recipe.getTemperatureFailChance(1500), 0.0001);
        assertEquals(0.10, recipe.getTemperatureFailChance(1700), 0.0001);
    }

    @Test
    void recipeAcceptsAlternativeSpecialAdditives() {
        var recipe = new MetallurgyRecipe(
            "mithril_flux",
            "ANCIENT_FORGE",
            List.of(new MetallurgyRecipe.Ingredient(Material.PRISMARINE_SHARD, 4)),
            new MetallurgyRecipe.OutputItem(Material.IRON_INGOT, 1, null, List.of(), 0, "mithril_ingot"),
            300,
            15,
            1300,
            1600,
            1800,
            null,
            0.10,
            0.90,
            List.of(Material.QUARTZ, Material.GLOWSTONE_DUST),
            2,
            0.25,
            true,
            false
        );

        assertTrue(recipe.acceptsAdditive(Material.QUARTZ));
        assertTrue(recipe.acceptsAdditive(Material.GLOWSTONE_DUST));
        assertFalse(recipe.acceptsAdditive(Material.REDSTONE));
        assertEquals(2, recipe.getAdditiveAmount());
        assertEquals(0.25, recipe.getAdditiveCleanOutputBonus(), 0.0001);
        assertTrue(recipe.requiresColdQuench());
    }

    @Test
    void bundledConfigContainsTieredFuelProfiles() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            assertNotNull(stream);
            var config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));

            assertEquals(4, config.getInt("config-version"));
            assertEquals(150, config.getInt("fuel-groups.fresh-plant.temperature-limit"));
            assertEquals(200, config.getInt("fuel-groups.wool.temperature-limit"));
            assertEquals(400, config.getInt("fuel-groups.wood.temperature-limit"));
            assertEquals(800, config.getInt("fuel-groups.carbon.temperature-limit"));
            assertEquals(1100, config.getInt("fuel-groups.lava.temperature-limit"));
            assertEquals(1600, config.getInt("temperature.fuel-limits.BLAZE_ROD"));
            assertEquals(800, config.getInt("temperature.ignition-boosts.BLAZE_POWDER"));
            assertEquals(2050, config.getInt("temperature.fuel-combinations.BLAZE_ROD+BLAZE_ROD"));
            assertEquals(0.20, config.getDouble("additives.default-clean-output-bonus"), 0.0001);
        }
    }
}
