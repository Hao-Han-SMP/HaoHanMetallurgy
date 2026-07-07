package dev.haohansmp.metallurgy.config;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.Material;
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
