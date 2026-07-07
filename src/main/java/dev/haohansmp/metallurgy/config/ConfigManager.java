package dev.haohansmp.metallurgy.config;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bọc FileConfiguration của Bukkit.
 * Mọi truy cập config đều đi qua class này — không ai gọi
 * plugin.getConfig().getString() trực tiếp ngoài đây.
 */
public class ConfigManager {

    private final HaoHanMetallurgy plugin;
    private FileConfiguration config;

    private boolean debug;
    private int tickRate;
    private int interactionRange;
    private Map<Material, Integer> fuelValues;
    private int tempRisePerTick;
    private int tempFallPerTick;
    private int tempMax;
    private String forgeTitle;

    // Progression config cache
    private Material emberOreMaterial;
    private Material soulOreMaterial;
    private Map<String, Integer> miningRequirements;

    // Heat & Cooling capacities (Phase 5)
    private Map<Material, Integer> fuelLimits;
    private Map<Material, Integer> coolants;

    // Forge Model Config (Phase 5.5)
    private boolean modelEnabled;
    private Material modelMaterial;
    private NamespacedKey modelItemModel;
    private int modelCustomModelData;
    private double modelScaleX, modelScaleY, modelScaleZ;
    private double modelOffsetX, modelOffsetY, modelOffsetZ;

    public ConfigManager(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        load();
    }

    // ── Public API ─────────────────────────────────────────────

    /** Reload config từ disk và cache lại giá trị. */
    public void reload() {
        plugin.reloadConfig();
        load();
        plugin.getPluginLogger().info("Config reloaded.");
    }

    public boolean isDebug()          { return debug; }
    public int getTickRate()          { return tickRate; }
    public int getInteractionRange()  { return interactionRange; }
    public int getTempRisePerTick()   { return tempRisePerTick; }
    public int getTempFallPerTick()   { return tempFallPerTick; }
    public int getTempMax()           { return tempMax; }
    public String getForgeTitle()     { return forgeTitle; }

    public Material getEmberOreMaterial() { return emberOreMaterial; }
    public Material getSoulOreMaterial()  { return soulOreMaterial; }
    public Map<String, Integer> getMiningRequirements() { return miningRequirements; }

    public Map<Material, Integer> getFuelLimits() { return fuelLimits; }
    public Map<Material, Integer> getCoolants()   { return coolants; }

    public boolean isModelEnabled()          { return modelEnabled; }
    public Material getModelMaterial()        { return modelMaterial; }
    public NamespacedKey getModelItemModel()  { return modelItemModel; }
    public int getModelCustomModelData()     { return modelCustomModelData; }
    public double getModelScaleX()           { return modelScaleX; }
    public double getModelScaleY()           { return modelScaleY; }
    public double getModelScaleZ()           { return modelScaleZ; }
    public double getModelOffsetX()          { return modelOffsetX; }
    public double getModelOffsetY()          { return modelOffsetY; }
    public double getModelOffsetZ()          { return modelOffsetZ; }

    /**
     * Trả về số ticks fuel của material này cung cấp.
     * Trả về 0 nếu không phải fuel hợp lệ.
     */
    public int getFuelTicks(Material material) {
        return fuelValues.getOrDefault(material, 0);
    }

    public boolean isFuel(Material material) {
        return fuelValues.containsKey(material);
    }

    // ── Internal ───────────────────────────────────────────────

    private void load() {
        this.config = plugin.getConfig();

        debug            = config.getBoolean("debug", false);
        tickRate         = config.getInt("tick-rate", 20);
        interactionRange = config.getInt("machines.interaction-range", 5);
        tempRisePerTick  = config.getInt("temperature.rise-per-tick", 2);
        tempFallPerTick  = config.getInt("temperature.fall-per-tick", 1);
        tempMax          = config.getInt("temperature.max", 2000);
        forgeTitle       = org.bukkit.ChatColor.translateAlternateColorCodes('&',
            config.getString("gui.forge-title", "&8⚒ &6Ancient Forge"));

        // Load progression configs
        String emberOreName = config.getString("progression.ores.ember-ore", "COPPER_ORE");
        emberOreMaterial = Material.matchMaterial(emberOreName);
        if (emberOreMaterial == null) {
            emberOreMaterial = Material.COPPER_ORE;
            plugin.getPluginLogger().warn("Invalid ember-ore material in config. Defaulting to COPPER_ORE.");
        }

        String soulOreName = config.getString("progression.ores.soul-ore", "LAPIS_ORE");
        soulOreMaterial = Material.matchMaterial(soulOreName);
        if (soulOreMaterial == null) {
            soulOreMaterial = Material.LAPIS_ORE;
            plugin.getPluginLogger().warn("Invalid soul-ore material in config. Defaulting to LAPIS_ORE.");
        }

        loadMiningRequirements();
        loadFuelValues();
        loadFuelLimits();
        loadCoolants();
        loadModelConfig();
    }

    private void loadModelConfig() {
        modelEnabled = config.getBoolean("forge-model.enabled", false);
        String matName = config.getString("forge-model.material", "PAPER");
        modelMaterial = Material.matchMaterial(matName);
        if (modelMaterial == null) {
            modelMaterial = Material.PAPER;
        }
        modelItemModel = parseNamespacedKey(config.getString("forge-model.item-model", "haohansmp:metallurgy"));
        modelCustomModelData = config.getInt("forge-model.custom-model-data", 0);
        modelScaleX = config.getDouble("forge-model.scale.x", 3.0);
        modelScaleY = config.getDouble("forge-model.scale.y", 4.0);
        modelScaleZ = config.getDouble("forge-model.scale.z", 3.0);
        if (modelScaleX == 1.0 && modelScaleY == 1.0 && modelScaleZ == 1.0) {
            plugin.getPluginLogger().warn("forge-model.scale is still 1x1x1 in config.yml. Auto-upgrading display scale to 3x4x3 for the Ancient Forge model.");
            modelScaleX = 3.0;
            modelScaleY = 4.0;
            modelScaleZ = 3.0;
        }
        modelOffsetX = config.getDouble("forge-model.offset.x", 0.5);
        modelOffsetY = config.getDouble("forge-model.offset.y", 0.0);
        if (modelOffsetY == -0.5) {
            plugin.getPluginLogger().warn("forge-model.offset.y is still -0.5 in config.yml. Auto-upgrading display Y offset to 0.0 to lift the Ancient Forge model by 0.5 block.");
            modelOffsetY = 0.0;
        }
        modelOffsetZ = config.getDouble("forge-model.offset.z", 0.5);
    }

    private NamespacedKey parseNamespacedKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (!value.contains(":")) {
            value = plugin.getName().toLowerCase(java.util.Locale.ROOT) + ":" + value;
        }

        NamespacedKey key = NamespacedKey.fromString(value);
        if (key == null) {
            plugin.getPluginLogger().warn("Invalid forge-model.item-model in config: " + raw + ". Item model will not be applied.");
        }
        return key;
    }

    private void loadMiningRequirements() {
        miningRequirements = new java.util.HashMap<>();
        var section = config.getConfigurationSection("progression.mining-requirements");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            miningRequirements.put(key, section.getInt(key));
        }
    }

    private void loadFuelValues() {
        fuelValues = new EnumMap<>(Material.class);
        var section = config.getConfigurationSection("fuel-values");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) {
                plugin.getPluginLogger().warn("Unknown fuel material in config: " + key);
                continue;
            }
            fuelValues.put(mat, section.getInt(key));
        }
        plugin.getPluginLogger().debug("Loaded " + fuelValues.size() + " fuel types.");
    }

    private void loadFuelLimits() {
        fuelLimits = new EnumMap<>(Material.class);
        var section = config.getConfigurationSection("temperature.fuel-limits");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) continue;
            fuelLimits.put(mat, section.getInt(key));
        }
        plugin.getPluginLogger().debug("Loaded " + fuelLimits.size() + " fuel thermal limits.");
    }

    private void loadCoolants() {
        coolants = new EnumMap<>(Material.class);
        var section = config.getConfigurationSection("temperature.coolants");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) continue;
            coolants.put(mat, section.getInt(key));
        }
        plugin.getPluginLogger().debug("Loaded " + coolants.size() + " coolant items.");
    }
}
