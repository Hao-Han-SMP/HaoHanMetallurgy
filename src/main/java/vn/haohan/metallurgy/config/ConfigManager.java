package vn.haohan.metallurgy.config;

import vn.haohan.metallurgy.HaoHanMetallurgy;
import vn.haohan.metallurgy.item.CustomItem;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.List;
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
    private double timeSpeedMultiplier;
    private Map<Material, Integer> fuelValues;
    private int tempRisePerTick;
    private int tempFallPerTick;
    private int tempMax;
    private int bellowsTemperatureBoost;
    private int pistonBellowsTemperatureBoost;
    private int pistonBellowsProgressTicks;
    private String forgeTitle;
    private boolean forgeCustomGuiEnabled;
    private String forgeCustomGuiPrefix;
    private String forgeCustomGuiGlyph;
    private NamespacedKey forgeCustomGuiFont;
    private boolean failEnabled;
    private double failBaseChance;

    // Progression config cache
    private Map<String, Integer> miningRequirements;
    private Map<CustomItem, CustomItemStats> customItemStats;
    private Map<Material, Integer> vanillaToolTiers;
    private boolean miningWarningsEnabled;
    private double boraxOreHardness;
    private double deepslateBoraxOreHardness;
    private double mithrilOreHardness;
    private double deepslateMithrilOreHardness;

    // Heat & Cooling capacities (Phase 5)
    private Map<Material, Integer> fuelLimits;
    private Map<Material, Integer> coolants;
    private int fallbackFuelTicks;
    private int fallbackFuelLimit;
    private int fuelCombinationBonus;
    private Map<String, Integer> fuelGroupTicks;
    private Map<String, Integer> fuelGroupLimits;
    private Map<Material, Integer> fuelIgnitionBoosts;

    // New configs for combinations and additives
    private int fuelSlot2;
    private int additiveSlot;
    private int boraxPerBatch;
    private double cleanOutputChanceWithBorax;
    private double cleanOutputChanceWithoutBorax;
    private double vanillaFurnaceCleanOutputChance;
    private Map<String, Integer> fuelCombinations;

    // Forge Model Config (Phase 5.5)
    private boolean modelEnabled;
    private Material modelMaterial;
    private NamespacedKey modelItemModel;
    private int modelCustomModelData;
    private double modelScaleX, modelScaleY, modelScaleZ;
    private double modelOffsetX, modelOffsetY, modelOffsetZ;
    private float modelViewRange;

    public ConfigManager(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        migrateConfigIfNeeded();
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
    public double getTimeSpeedMultiplier() { return timeSpeedMultiplier; }
    public int getTempRisePerTick()   { return tempRisePerTick; }
    public int getTempFallPerTick()   { return tempFallPerTick; }
    public int getTempMax()           { return tempMax; }
    public int getBellowsTemperatureBoost() { return bellowsTemperatureBoost; }
    public int getPistonBellowsTemperatureBoost() { return pistonBellowsTemperatureBoost; }
    public int getPistonBellowsProgressTicks() { return pistonBellowsProgressTicks; }
    public String getForgeTitle()     { return forgeTitle; }
    public boolean isForgeCustomGuiEnabled() { return forgeCustomGuiEnabled; }
    public String getForgeCustomGuiPrefix()  { return forgeCustomGuiPrefix; }
    public String getForgeCustomGuiGlyph()   { return forgeCustomGuiGlyph; }
    public NamespacedKey getForgeCustomGuiFont() { return forgeCustomGuiFont; }
    public boolean isFailEnabled()    { return failEnabled; }
    public double getFailBaseChance() { return failBaseChance; }

    public Map<String, Integer> getMiningRequirements() { return miningRequirements; }
    public CustomItemStats getCustomItemStats(CustomItem item) { return customItemStats.getOrDefault(item, CustomItemStats.empty()); }
    public int getVanillaToolTier(Material material) { return vanillaToolTiers.getOrDefault(material, 0); }
    public boolean isMiningWarningsEnabled() { return miningWarningsEnabled; }
    public double getBoraxOreHardness() { return boraxOreHardness; }
    public double getDeepslateBoraxOreHardness() { return deepslateBoraxOreHardness; }
    public double getMithrilOreHardness() { return mithrilOreHardness; }
    public double getDeepslateMithrilOreHardness() { return deepslateMithrilOreHardness; }

    public Map<Material, Integer> getFuelLimits() { return fuelLimits; }
    public Map<Material, Integer> getCoolants()   { return coolants; }
    public Map<String, Integer> getFuelCombinations() {
        return java.util.Collections.unmodifiableMap(fuelCombinations);
    }
    public int getFuelIgnitionBoost(Material material) {
        return fuelIgnitionBoosts.getOrDefault(material, 0);
    }

    public int getFuelSlot2() { return fuelSlot2; }
    public int getAdditiveSlot() { return additiveSlot; }
    public int getBoraxPerBatch() { return boraxPerBatch; }
    public double getCleanOutputChanceWithBorax() { return cleanOutputChanceWithBorax; }
    public double getCleanOutputChanceWithoutBorax() { return cleanOutputChanceWithoutBorax; }
    public double getVanillaFurnaceCleanOutputChance() { return vanillaFurnaceCleanOutputChance; }
    public int getCombinedFuelLimit(Material m1, Material m2) {
        if (m1 == null && m2 == null) return 0;
        if (m1 == null) return getFuelLimit(m2);
        if (m2 == null) return getFuelLimit(m1);

        String key = fuelCombinationKey(m1, m2);
        if (fuelCombinations.containsKey(key)) {
            return fuelCombinations.get(key);
        }

        int limit1 = getFuelLimit(m1);
        int limit2 = getFuelLimit(m2);
        return Math.min(tempMax, Math.max(limit1, limit2) + fuelCombinationBonus);
    }

    private double clampOreHardness(double value) {
        return Math.max(0.1, Math.min(1000.0, value));
    }

    private double clampChance(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public int getFuelLimit(Material material) {
        if (material == null) return 0;
        Integer configured = fuelLimits.get(material);
        if (configured != null) return configured;
        String group = getFuelGroup(material);
        if (group != null) return fuelGroupLimits.getOrDefault(group, fallbackFuelLimit);
        if (!material.isFuel()) return 0;
        return fallbackFuelLimit;
    }

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
    public float getModelViewRange()         { return modelViewRange; }

    /**
     * Trả về số ticks fuel của material này cung cấp.
     * Trả về 0 nếu không phải fuel hợp lệ.
     */
    public int getFuelTicks(Material material) {
        Integer configured = fuelValues.get(material);
        if (configured != null) return configured;
        if (material == null) return 0;
        String group = getFuelGroup(material);
        if (group != null) return fuelGroupTicks.getOrDefault(group, fallbackFuelTicks);
        if (!material.isFuel()) return 0;
        return fallbackFuelTicks;
    }

    public boolean isFuel(Material material) {
        return material != null
                && (fuelValues.containsKey(material) || getFuelGroup(material) != null || material.isFuel());
    }

    private String getFuelGroup(Material material) {
        if (material == null) return null;
        String name = material.name();
        if (name.equals("LAVA_BUCKET")) return "lava";
        if (name.equals("MAGMA_BLOCK") || name.equals("FIRE_CHARGE")
                || name.equals("BLAZE_POWDER") || name.equals("BLAZE_ROD")) return "nether-fire";
        if (name.equals("COAL") || name.equals("CHARCOAL") || name.equals("COAL_BLOCK")
                || name.equals("DRIED_KELP_BLOCK") || name.equals("DEAD_BUSH")
                || name.equals("HAY_BLOCK")) return "carbon";
        if (name.contains("WOOL") || name.contains("CARPET") || name.endsWith("_BED")) return "wool";
        if (isFreshPlant(name)) return "fresh-plant";
        if (isWoodFuel(name)) return "wood";
        return null;
    }

    private boolean isFreshPlant(String name) {
        return name.contains("LEAVES") || name.contains("SAPLING") || name.contains("GRASS")
                || name.contains("VINE") || name.contains("AZALEA") || name.contains("DRIPLEAF")
                || name.contains("FERN") || name.contains("ROOTS") || name.contains("BUSH")
                || name.equals("LILY_PAD") || name.equals("SUGAR_CANE") || name.equals("MOSS_BLOCK")
                || name.equals("MOSS_CARPET") || name.equals("CACTUS") || name.equals("KELP")
                || name.equals("SEAGRASS");
    }

    private boolean isWoodFuel(String name) {
        boolean woodSpecies = name.startsWith("OAK_") || name.startsWith("SPRUCE_")
                || name.startsWith("BIRCH_") || name.startsWith("JUNGLE_")
                || name.startsWith("ACACIA_") || name.startsWith("DARK_OAK_")
                || name.startsWith("MANGROVE_") || name.startsWith("CHERRY_")
                || name.startsWith("PALE_OAK_") || name.startsWith("CRIMSON_")
                || name.startsWith("WARPED_") || name.startsWith("BAMBOO_");
        return (woodSpecies && (name.contains("LOG") || name.endsWith("_WOOD") || name.contains("STEM")
                || name.contains("HYPHAE") || name.contains("PLANKS") || name.contains("SLAB")
                || name.contains("STAIRS") || name.contains("FENCE") || name.contains("BOAT")
                || name.contains("SIGN") || name.contains("BUTTON") || name.contains("PRESSURE_PLATE")
                || name.contains("DOOR") || name.contains("TRAPDOOR")))
                || name.startsWith("WOODEN_") || name.equals("STICK") || name.equals("BOWL")
                || name.equals("SCAFFOLDING") || name.equals("CRAFTING_TABLE")
                || name.equals("CHEST") || name.equals("TRAPPED_CHEST") || name.equals("BARREL")
                || name.equals("LADDER") || name.equals("BOOKSHELF") || name.equals("LECTERN")
                || name.equals("COMPOSTER") || name.equals("BEEHIVE") || name.equals("BEE_NEST");
    }

    // ── Internal ───────────────────────────────────────────────

    private void migrateConfigIfNeeded() {
        FileConfiguration current = plugin.getConfig();
        int currentVersion = current.getInt("config-version", 0);
        if (currentVersion >= 12) return;

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File backupFile = new File(plugin.getDataFolder(), "config.before-v12.yml");
        try {
            if (configFile.exists() && !backupFile.exists()) {
                Files.copy(configFile.toPath(), backupFile.toPath());
            }

            try (var input = plugin.getResource("config.yml")) {
                if (input == null) throw new IllegalStateException("Bundled config.yml is missing");
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(input, StandardCharsets.UTF_8));
                var paths = new java.util.ArrayList<String>();
                if (currentVersion < 4) {
                    paths.addAll(List.of(
                            "fuel-defaults",
                            "fuel-groups",
                            "fuel-values",
                            "temperature.max",
                            "temperature.fuel-limits",
                            "temperature.fuel-combinations",
                            "temperature.ignition-boosts",
                            "temperature.combination-bonus"));
                }
                if (currentVersion < 5) {
                    paths.addAll(List.of(
                            "jute",
                            "charcoal-kiln",
                            "progression.mining-requirements.minecraft:coal_ore",
                            "progression.mining-requirements.minecraft:deepslate_coal_ore",
                            "progression.custom-item-stats.bow_drill"));
                }
                if (currentVersion < 6) {
                    paths.add("jute.drops");
                }
                if (currentVersion < 7) {
                    paths.add("charcoal-kiln.ignition-seconds");
                    paths.add("progression.custom-item-stats.bow_drill");
                }
                if (currentVersion < 8) {
                    paths.add("machines.time-speed-multiplier");
                }
                if (currentVersion < 9) {
                    paths.add("fail.enabled");
                    paths.add("additives.borax-per-batch");
                    paths.add("additives.clean-output-chance-with-borax");
                    paths.add("additives.clean-output-chance-without-borax");
                }
                if (currentVersion < 10) {
                    paths.add("vanilla-furnaces.clean-output-chance");
                }
                if (currentVersion < 11) {
                    // Balance update from config v10 to v11. The v12 balance
                    // update below applies the current furnace rate.
                    paths.add("vanilla-furnaces.clean-output-chance");
                }
                if (currentVersion < 12) {
                    // Balance update: raise clean output across every furnace path.
                    paths.add("additives.clean-output-chance-with-borax");
                    paths.add("additives.clean-output-chance-without-borax");
                    paths.add("vanilla-furnaces.clean-output-chance");
                }
                for (String path : paths) {
                    copyConfigPath(defaults, current, path);
                }
                if (currentVersion < 9) {
                    current.set("additives.default-list", null);
                    current.set("additives.no-additive-fail-chance", null);
                    current.set("additives.default-clean-output-bonus", null);
                }
            }

            current.set("config-version", 12);
            plugin.saveConfig();
            plugin.getPluginLogger().info("Migrated metallurgy config to version 12"
                    + (backupFile.exists() ? " (backup: config.before-v12.yml)" : ""));
        } catch (Exception e) {
            plugin.getPluginLogger().error("Could not migrate config.yml to version 12", e);
        }
    }

    private void copyConfigPath(FileConfiguration source, FileConfiguration target, String path) {
        Object value = source.get(path);
        target.set(path, null);
        if (value instanceof ConfigurationSection section) {
            for (String key : section.getKeys(true)) {
                if (!section.isConfigurationSection(key)) {
                    target.set(path + "." + key, section.get(key));
                }
            }
        } else {
            target.set(path, value);
        }
    }

    private void load() {
        this.config = plugin.getConfig();

        debug            = config.getBoolean("debug", false);
        tickRate         = config.getInt("tick-rate", 20);
        interactionRange = config.getInt("machines.interaction-range", 5);
        timeSpeedMultiplier = config.getDouble("machines.time-speed-multiplier", 0.8);
        tempRisePerTick  = config.getInt("temperature.rise-per-tick", 2);
        tempFallPerTick  = config.getInt("temperature.fall-per-tick", 1);
        tempMax          = config.getInt("temperature.max", 2000);
        bellowsTemperatureBoost = Math.max(0,
                config.getInt("temperature.bellows-boost", 40));
        pistonBellowsTemperatureBoost = Math.max(0,
                config.getInt("temperature.piston-bellows-boost", 30));
        pistonBellowsProgressTicks = Math.max(0,
                config.getInt("temperature.piston-progress-ticks", 5));
        fallbackFuelTicks = config.getInt("fuel-defaults.burn-ticks", 200);
        fallbackFuelLimit = config.getInt("fuel-defaults.temperature-limit", 650);
        fuelCombinationBonus = config.getInt("temperature.combination-bonus", 75);
        forgeTitle       = org.bukkit.ChatColor.translateAlternateColorCodes('&',
            config.getString("gui.forge-title", "&8⚒ &6Ancient Forge"));
        forgeCustomGuiEnabled = config.getBoolean("gui.forge-custom.enabled", true);
        forgeCustomGuiPrefix = config.getString("gui.forge-custom.prefix", "\uE100");
        forgeCustomGuiGlyph = config.getString("gui.forge-custom.glyph", "\uE150");
        forgeCustomGuiFont = parseNamespacedKey(config.getString("gui.forge-custom.font", "haohan:gui"));
        failEnabled      = config.getBoolean("fail.enabled", false);
        failBaseChance   = config.getDouble("fail.base-chance", 0.05);

        // Load progression configs
        miningWarningsEnabled = config.getBoolean("progression.show-mining-warnings", true);
        boraxOreHardness = clampOreHardness(
                config.getDouble("progression.custom-ore-hardness.borax", 3.0));
        deepslateBoraxOreHardness = clampOreHardness(
                config.getDouble("progression.custom-ore-hardness.deepslate-borax", 4.5));
        mithrilOreHardness = clampOreHardness(
                config.getDouble("progression.custom-ore-hardness.mithril", 55.0));
        deepslateMithrilOreHardness = clampOreHardness(
                config.getDouble("progression.custom-ore-hardness.deepslate-mithril", 65.0));
        loadMiningRequirements();
        loadToolProgression();
        loadFuelValues();
        loadFuelLimits();
        loadFuelGroups();
        loadFuelIgnitionBoosts();
        loadCoolants();
        loadModelConfig();

        fuelSlot2 = config.getInt("gui.fuel-slot-2", 2);
        additiveSlot = config.getInt("gui.additive-slot", 10);
        boraxPerBatch = Math.max(1, config.getInt("additives.borax-per-batch", 1));
        cleanOutputChanceWithBorax = clampChance(
                config.getDouble("additives.clean-output-chance-with-borax", 0.98));
        cleanOutputChanceWithoutBorax = clampChance(
                config.getDouble("additives.clean-output-chance-without-borax", 0.35));
        vanillaFurnaceCleanOutputChance = clampChance(
                config.getDouble("vanilla-furnaces.clean-output-chance", 0.25));
        loadFuelCombinations();
    }

    private void loadModelConfig() {
        modelEnabled = config.getBoolean("forge-model.enabled", false);
        String matName = config.getString("forge-model.material", "PAPER");
        modelMaterial = Material.matchMaterial(matName);
        if (modelMaterial == null) {
            modelMaterial = Material.PAPER;
        }
        modelItemModel = parseNamespacedKey(config.getString("forge-model.item-model", "haohan:metallurgy"));
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
        modelOffsetY = config.getDouble("forge-model.offset.y", 0.5);
        if (modelOffsetY <= 0.0) {
            plugin.getPluginLogger().warn("Raising the Ancient Forge model by 0.5 block to align its base with the floor.");
            modelOffsetY = 0.5;
            config.set("forge-model.offset.y", modelOffsetY);
            plugin.saveConfig();
        }
        modelOffsetZ = config.getDouble("forge-model.offset.z", 0.5);
        modelViewRange = (float) Math.max(1.0, Math.min(128.0,
                config.getDouble("forge-model.view-range", 32.0)));
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
        vanillaToolTiers.put(Material.IRON_PICKAXE, 4);
        vanillaToolTiers.put(Material.DIAMOND_PICKAXE, 6);
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
        customItemStats.put(CustomItem.IRON_SLAG_PICKAXE, new CustomItemStats(3, 190, 3.0, -2.8, 3.5, 0.0));
        customItemStats.put(CustomItem.GOLD_SLAG_PICKAXE, new CustomItemStats(1, 24, 1.0, -2.8, 8.0, 0.0));
        customItemStats.put(CustomItem.EMBERSTEEL_SLAG_PICKAXE, new CustomItemStats(4, 120, 2.0, -2.8, 2.0, 0.0));
        customItemStats.put(CustomItem.EMBERSTEEL_PICKAXE, new CustomItemStats(5, 210, 3.0, -2.8, 3.0, 0.0));
        customItemStats.put(CustomItem.SOULSTEEL_SLAG_PICKAXE, new CustomItemStats(7, 1200, 3.0, -2.8, 7.0, 0.0));
        customItemStats.put(CustomItem.SOULSTEEL_PICKAXE, new CustomItemStats(8, 2200, 4.0, -2.8, 9.5, 0.0));
        customItemStats.put(CustomItem.NETHERITE_SLAG_PICKAXE, new CustomItemStats(8, 1500, 3.0, -2.8, 7.5, 0.0));
        customItemStats.put(CustomItem.MITHRIL_PICKAXE, new CustomItemStats(7, 1800, 4.0, -2.8, 8.5, 0.0));
        customItemStats.put(CustomItem.MITHRIL_SLAG_PICKAXE, new CustomItemStats(6, 900, 3.0, -2.8, 6.0, 0.0));
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

    private void loadFuelGroups() {
        fuelGroupTicks = new java.util.HashMap<>();
        fuelGroupLimits = new java.util.HashMap<>();
        var section = config.getConfigurationSection("fuel-groups");
        if (section == null) return;
        for (String group : section.getKeys(false)) {
            String path = "fuel-groups." + group + ".";
            fuelGroupTicks.put(group, config.getInt(path + "burn-ticks", fallbackFuelTicks));
            fuelGroupLimits.put(group, config.getInt(path + "temperature-limit", fallbackFuelLimit));
        }
    }

    private void loadFuelIgnitionBoosts() {
        fuelIgnitionBoosts = new EnumMap<>(Material.class);
        var section = config.getConfigurationSection("temperature.ignition-boosts");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null) {
                fuelIgnitionBoosts.put(material, Math.max(0, section.getInt(key)));
            }
        }
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

    private void loadFuelCombinations() {
        fuelCombinations = new java.util.HashMap<>();
        var section = config.getConfigurationSection("temperature.fuel-combinations");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String[] parts = key.split("\\+", -1);
            if (parts.length != 2) {
                plugin.getPluginLogger().warn("Invalid fuel combination key: " + key);
                continue;
            }
            Material first = Material.matchMaterial(parts[0]);
            Material second = Material.matchMaterial(parts[1]);
            if (first == null || second == null) {
                plugin.getPluginLogger().warn("Unknown material in fuel combination: " + key);
                continue;
            }
            if (!isFuel(first) || !isFuel(second)) {
                plugin.getPluginLogger().warn("Non-fuel material in fuel combination: " + key);
                continue;
            }
            int limit = section.getInt(key);
            if (limit <= 0 || limit > tempMax) {
                plugin.getPluginLogger().warn("Fuel combination temperature must be within 1.."
                        + tempMax + ": " + key);
                continue;
            }
            fuelCombinations.put(fuelCombinationKey(first, second), limit);
        }
        plugin.getPluginLogger().debug("Loaded " + fuelCombinations.size() + " fuel combinations.");
    }

    private String fuelCombinationKey(Material first, Material second) {
        return first.name().compareTo(second.name()) <= 0
                ? first.name() + "+" + second.name()
                : second.name() + "+" + first.name();
    }
}
