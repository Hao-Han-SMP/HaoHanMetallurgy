package dev.haohansmp.metallurgy.recipe;

import com.google.gson.*;
import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.machine.MachineType;
import org.bukkit.Material;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Đọc và parse tất cả file *.json trong thư mục recipes/.
 * Lưu vào Map theo MachineType để tra cứu nhanh.
 */
public class RecipeLoader {

    private final HaoHanMetallurgy plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    /** id → recipe */
    private final Map<String, MetallurgyRecipe> recipesById = new LinkedHashMap<>();

    /** machineType → list of recipes */
    private final Map<MachineType, List<MetallurgyRecipe>> recipesByMachine = new EnumMap<>(MachineType.class);

    public RecipeLoader(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    // ── Public API ─────────────────────────────────────────────

    /** Load (hoặc reload) tất cả recipes từ disk. */
    public void loadAll() {
        recipesById.clear();
        recipesByMachine.clear();

        File recipeDir = new File(plugin.getDataFolder(), "recipes");
        if (!recipeDir.exists()) {
            recipeDir.mkdirs();
            // Copy default recipes từ resources
            plugin.saveResource("recipes/example_forge.json", false);
            plugin.saveResource("recipes/raw_iron_smelting.json", false);
            plugin.saveResource("recipes/raw_copper_smelting.json", false);
            plugin.saveResource("recipes/raw_gold_smelting.json", false);
            plugin.saveResource("recipes/netherite_ingot_smelting.json", false);
            plugin.saveResource("recipes/mithril_ingot_smelting.json", false);
        }

        File[] files;
        try {
            files = recipeDir.listFiles((dir, name) -> name.endsWith(".json"));
        } catch (SecurityException e) {
            plugin.getPluginLogger().error("Cannot read recipes directory: " + recipeDir.getPath(), e);
            return;
        }

        if (files == null || files.length == 0) {
            plugin.getPluginLogger().warn("No recipe files found in " + recipeDir.getPath());
            return;
        }

        int loaded = 0;
        for (File file : files) {
            try {
                MetallurgyRecipe recipe = parseFile(file);
                if (recipe != null) {
                    register(recipe);
                    loaded++;
                }
            } catch (Exception e) {
                plugin.getPluginLogger().error("Failed to load recipe file: " + file.getName(), e);
            }
        }

        plugin.getPluginLogger().info("Loaded " + loaded + " recipe(s) from " + files.length + " file(s).");
    }

    /** Lấy recipe theo ID. */
    public Optional<MetallurgyRecipe> getById(String id) {
        return Optional.ofNullable(recipesById.get(id));
    }

    /** Lấy tất cả recipe của một loại máy. */
    public List<MetallurgyRecipe> getForMachine(MachineType type) {
        return recipesByMachine.getOrDefault(type, Collections.emptyList());
    }

    /** Tổng số recipe đã load. */
    public int count() {
        return recipesById.size();
    }

    // ── Internal ───────────────────────────────────────────────

    private MetallurgyRecipe parseFile(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JsonObject obj = JsonParser.parseString(content).getAsJsonObject();

        String id = obj.get("id").getAsString();
        String machineTypeStr = obj.get("machine_type").getAsString();

        // Parse inputs
        List<MetallurgyRecipe.Ingredient> inputs = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("inputs")) {
            JsonObject inp = el.getAsJsonObject();
            Material mat = requireMaterial(inp.get("material").getAsString(), file);
            if (mat == null) return null;
            inputs.add(new MetallurgyRecipe.Ingredient(mat, inp.get("amount").getAsInt()));
        }

        // Parse output
        JsonObject outObj = obj.getAsJsonObject("output");
        String customItemId = outObj.has("custom_item") ? outObj.get("custom_item").getAsString() : null;
        Material outMat = null;
        
        if (customItemId != null) {
            java.util.Optional<dev.haohansmp.metallurgy.item.CustomItem> ciOpt = dev.haohansmp.metallurgy.item.CustomItem.getById(customItemId);
            if (ciOpt.isPresent()) {
                outMat = ciOpt.get().getMaterial();
            } else {
                plugin.getPluginLogger().error("Unknown custom item ID '" + customItemId + "' in " + file.getName());
                return null;
            }
        } else {
            outMat = requireMaterial(outObj.get("material").getAsString(), file);
            if (outMat == null) return null;
        }

        String displayName = outObj.has("display_name") ? outObj.get("display_name").getAsString() : null;
        List<String> lore = new ArrayList<>();
        if (outObj.has("lore")) {
            outObj.getAsJsonArray("lore").forEach(l -> lore.add(l.getAsString()));
        }
        int cmd = outObj.has("custom_model_data") ? outObj.get("custom_model_data").getAsInt() : 0;

        MetallurgyRecipe.OutputItem output = new MetallurgyRecipe.OutputItem(
                outMat,
                outObj.get("amount").getAsInt(),
                displayName,
                lore,
                cmd,
                customItemId
        );

        int fuelCost     = obj.has("fuel_cost")       ? obj.get("fuel_cost").getAsInt()       : 0;
        int timeSeconds  = obj.has("time_seconds")    ? obj.get("time_seconds").getAsInt()    : 10;
        int minTemp      = obj.has("min_temperature") ? obj.get("min_temperature").getAsInt() : 0;
        int maxTemp      = obj.has("max_temperature") ? obj.get("max_temperature").getAsInt() : (minTemp + 400);
        String reqAdv    = obj.has("required_advancement") ? obj.get("required_advancement").getAsString() : null;

        double failChance = 0.0;
        if (obj.has("fail_chance")) {
            failChance = obj.get("fail_chance").getAsDouble();
        } else {
            // Tự động tính toán theo quy tắc vật chất
            Material m = output.material();
            String idStr = id.toLowerCase();
            if (m != null) {
                String matName = m.name();
                if (matName.contains("GOLD")) {
                    failChance = 0.0;
                } else if (matName.contains("IRON") || matName.contains("COPPER") || matName.contains("NETHERITE") || idStr.contains("steel")) {
                    failChance = plugin.getConfigManager().getFailBaseChance();
                }
            }
        }

        return new MetallurgyRecipe(id, machineTypeStr, inputs, output, fuelCost, timeSeconds, minTemp, maxTemp, reqAdv, failChance);
    }

    private Material requireMaterial(String name, File file) {
        Material mat = Material.matchMaterial(name);
        if (mat == null) {
            plugin.getPluginLogger().error("Unknown material '" + name + "' in " + file.getName());
        }
        return mat;
    }

    private void register(MetallurgyRecipe recipe) {
        recipesById.put(recipe.getId(), recipe);

        MachineType type;
        try {
            type = MachineType.valueOf(recipe.getMachineType().toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getPluginLogger().warn("Unknown machine type '" + recipe.getMachineType()
                    + "' in recipe '" + recipe.getId() + "' — skipping.");
            return;
        }

        recipesByMachine.computeIfAbsent(type, k -> new ArrayList<>()).add(recipe);
        plugin.getPluginLogger().debug("Registered recipe: " + recipe.getId());
    }
}
