package dev.haohansmp.metallurgy.config;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.item.CustomItem;
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
    private boolean forgeCustomGuiEnabled;
    private String forgeCustomGuiPrefix;
    private String forgeCustomGuiGlyph;
    private NamespacedKey forgeCustomGuiFont;
    private boolean failEnabled;
    private double failBaseChance;

    // Progression config cache
    private Material emberOreMaterial;
    private Material soulOreMaterial;
    private Material mithrilOreMaterial;
    private Map<String, Integer> miningRequirements;
    private Map<CustomItem, CustomItemStats> customItemStats;
    private Map<Material, Integer> vanillaToolTiers;

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
    public boolean isForgeCustomGuiEnabled() { return forgeCustomGuiEnabled; }
    public String getForgeCustomGuiPrefix()  { return forgeCustomGuiPrefix; }
    public String getForgeCustomGuiGlyph()   { return forgeCustomGuiGlyph; }
    public NamespacedKey getForgeCustomGuiFont() { return forgeCustomGuiFont; }
    public boolean isFailEnabled()    { return failEnabled; }
    public double getFailBaseChance() { return failBaseChance; }

    public Material getEmberOreMaterial() { return emberOreMaterial; }
    public Material getSoulOreMaterial()  { return soulOreMaterial; }
    public Material getMithrilOreMaterial() { return mithrilOreMaterial; }
    public Map<String, Integer> getMiningRequirements() { return miningRequirements; }
    public CustomItemStats getCustomItemStats(CustomItem item) { return customItemStats.getOrDefault(item, CustomItemStats.empty()); }
    public int getVanillaToolTier(Material material) { return vanillaToolTiers.getOrDefault(material, 0); }

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
        forgeCustomGuiEnabled = config.getBoolean("gui.forge-custom.enabled", true);
        forgeCustomGuiPrefix = config.getString("gui.forge-custom.prefix", "\uE100");
        forgeCustomGuiGlyph = config.getString("gui.forge-custom.glyph", "\uE101");
        forgeCustomGuiFont = parseNamespacedKey(config.getString("gui.forge-custom.font", "haohansmp:gui"));
        failEnabled      = config.getBoolean("fail.enabled", false);
        failBaseChance   = config.getDouble("fail.base-chance", 0.05);

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

        String mithrilOreName = config.getString("progression.ores.mithril-ore", "EMERALD_ORE");
        mithrilOreMaterial = Material.matchMaterial(mithrilOreName);
        if (mithrilOreMaterial == null) {
            mithrilOreMaterial = Material.EMERALD_ORE;
            plugin.getPluginLogger().warn("Invalid mithril-ore material in config. Defaulting to EMERALD_ORE.");
        }

        loadMiningRequirements();
        loadToolProgression();
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

    private void loadToolProgression() {
        vanillaToolTiers = new EnumMap<>(Material.class);
        vanillaToolTiers.put(Material.WOODEN_PICKAXE, 1);
        vanillaToolTiers.put(Material.STONE_PICKAXE, 2);
        vanillaToolTiers.put(Material.GOLDEN_PICKAXE, 2);
        vanillaToolTiers.put(Material.COPPER_PICKAXE, 4);
        vanillaToolTiers.put(Material.IRON_PICKAXE, 6);
        vanillaToolTiers.put(Material.DIAMOND_PICKAXE, 7);
        vanillaToolTiers.put(Material.NETHERITE_PICKAXE, 9);
        var vanillaSection = config.getConfigurationSection("progression.vanilla-tool-tiers");
        if (vanillaSection != null) {
            for (String key : vanillaSection.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null) {
                    plugin.getPluginLogger().warn("Unknown vanilla tool material in config: " + key);
                    continue;
                }
                vanillaToolTiers.put(mat, vanillaSection.getInt(key));
            }
        }

        customItemStats = new EnumMap<>(CustomItem.class);
        customItemStats.put(CustomItem.COPPER_SLAG_PICKAXE, new CustomItemStats(3, 95, 2.0, -2.8, 1.5, 0.0));
        customItemStats.put(CustomItem.COPPER_PICKAXE, new CustomItemStats(4, 165, 2.0, -2.8, 2.5, 0.0));
        customItemStats.put(CustomItem.IRON_SLAG_PICKAXE, new CustomItemStats(5, 190, 3.0, -2.8, 3.5, 0.0));
        customItemStats.put(CustomItem.GOLD_SLAG_PICKAXE, new CustomItemStats(1, 24, 1.0, -2.8, 8.0, 0.0));
        customItemStats.put(CustomItem.EMBERSTEEL_SLAG_PICKAXE, new CustomItemStats(3, 120, 2.0, -2.8, 2.0, 0.0));
        customItemStats.put(CustomItem.EMBERSTEEL_PICKAXE, new CustomItemStats(4, 210, 3.0, -2.8, 3.0, 0.0));
        customItemStats.put(CustomItem.SOULSTEEL_SLAG_PICKAXE, new CustomItemStats(7, 850, 3.0, -2.8, 5.0, 0.0));
        customItemStats.put(CustomItem.SOULSTEEL_PICKAXE, new CustomItemStats(8, 1420, 4.0, -2.8, 6.0, 0.0));
        customItemStats.put(CustomItem.NETHERITE_SLAG_PICKAXE, new CustomItemStats(8, 1200, 3.0, -2.8, 7.0, 0.0));
        customItemStats.put(CustomItem.MITHRIL_PICKAXE, new CustomItemStats(8, 1800, 4.0, -2.8, 7.0, 0.0));
        customItemStats.put(CustomItem.MITHRIL_SLAG_PICKAXE, new CustomItemStats(7, 900, 3.0, -2.8, 5.5, 0.0));
        var customSection = config.getConfigurationSection("progression.custom-item-stats");
        if (customSection != null) {
            for (String key : customSection.getKeys(false)) {
                java.util.Optional<CustomItem> itemOpt = CustomItem.getById(key);
                if (itemOpt.isEmpty()) {
                    plugin.getPluginLogger().warn("Unknown custom item stats entry in config: " + key);
                    continue;
                }

                String path = "progression.custom-item-stats." + key + ".";
                CustomItemStats stats = new CustomItemStats(
                        config.getInt(path + "tier", 0),
                        config.getInt(path + "max-damage", 0),
                        config.getDouble(path + "attack-damage", 0.0),
                        config.getDouble(path + "attack-speed", 0.0),
                        config.getDouble(path + "mining-efficiency", 0.0),
                        config.getDouble(path + "block-break-speed", 0.0));
                customItemStats.put(itemOpt.get(), stats);
            }
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
