package dev.haohansmp.metallurgy;

import dev.haohansmp.metallurgy.command.MetallurgyCommand;
import dev.haohansmp.metallurgy.config.ConfigManager;
import dev.haohansmp.metallurgy.engine.TickEngine;
import dev.haohansmp.metallurgy.gui.GuiManager;
import dev.haohansmp.metallurgy.item.ItemManager;
import dev.haohansmp.metallurgy.listener.ChunkListener;
import dev.haohansmp.metallurgy.listener.ForgeListener;
import dev.haohansmp.metallurgy.listener.ProgressionListener;
import dev.haohansmp.metallurgy.listener.VanillaFurnaceListener;
import dev.haohansmp.metallurgy.machine.MachineManager;
import dev.haohansmp.metallurgy.recipe.RecipeLoader;
import dev.haohansmp.metallurgy.util.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point của HaoHan Metallurgy Plugin.
 *
 * Khởi tạo các manager theo đúng thứ tự dependency:
 *   1. PluginLogger  (cần trước hết để log)
 *   2. ConfigManager (cần trước recipe/machine)
 *   3. RecipeLoader  (data-driven, không phụ thuộc gì khác)
 *   4. MachineManager
 *   5. GuiManager    (register event listener)
 *   6. TickEngine    (start sau cùng)
 *   7. Commands
 */
public final class HaoHanMetallurgy extends JavaPlugin {

    // ── Singleton ──────────────────────────────────────────────
    private static HaoHanMetallurgy instance;

    public static HaoHanMetallurgy getInstance() {
        return instance;
    }

    // ── Managers ───────────────────────────────────────────────
    private PluginLogger pluginLogger;
    private ConfigManager configManager;
    private ItemManager itemManager;
    private RecipeLoader recipeLoader;
    private MachineManager machineManager;
    private GuiManager guiManager;
    private TickEngine tickEngine;

    // ── Lifecycle ──────────────────────────────────────────────

    @Override
    public void onEnable() {
        instance = this;

        // 1. Logger (phải đầu tiên)
        pluginLogger = new PluginLogger(this);
        pluginLogger.info("=== HaoHan Metallurgy ===");
        pluginLogger.info("Initializing Core Engine...");

        // 2. Config
        configManager = new ConfigManager(this);

        // 2.5 Item Manager
        itemManager = new ItemManager(this);

        // 3. Recipe Loader
        recipeLoader = new RecipeLoader(this);
        recipeLoader.loadAll();

        // 4. Machine Manager
        machineManager = new MachineManager(this);

        // 5. GUI Manager (cần register listener)
        guiManager = new GuiManager(this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        getServer().getPluginManager().registerEvents(new ForgeListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(this), this);
        getServer().getPluginManager().registerEvents(new ProgressionListener(this), this);
        getServer().getPluginManager().registerEvents(new VanillaFurnaceListener(this), this);

        // 7. Tick Engine (khởi động sau khi managers sẵn sàng)
        tickEngine = new TickEngine(this);
        tickEngine.start();

        // 7. Commands
        var cmd = new MetallurgyCommand(this);
        var metallurgyCmd = getCommand("metallurgy");
        if (metallurgyCmd != null) {
            metallurgyCmd.setExecutor(cmd);
            metallurgyCmd.setTabCompleter(cmd);
        }

        // 8. Restore active machines sau 1 tick để world/chunk state ổn định hơn khi restart.
        getServer().getScheduler().runTask(this, () -> {
            machineManager.loadAll();
            pluginLogger.info("Restored delayed machine state. Machines: " + machineManager.count());
        });

        pluginLogger.info("Core Engine enabled. Recipes: " + recipeLoader.count()
            + " | Machines: " + machineManager.count());
        pluginLogger.info("=========================");
    }

    @Override
    public void onDisable() {
        pluginLogger.info("Shutting down HaoHan Metallurgy...");

        if (tickEngine != null) tickEngine.stop();

        if (machineManager != null) {
            machineManager.saveAll();
            machineManager.pauseAll();
        }

        pluginLogger.info("HaoHan Metallurgy disabled. Goodbye!");
        instance = null;
    }

    // ── Getters ───────────────────────────────────────────────

    public PluginLogger getPluginLogger()   { return pluginLogger; }
    public ConfigManager getConfigManager() { return configManager; }
    public ItemManager getItemManager()     { return itemManager; }
    public RecipeLoader getRecipeLoader()   { return recipeLoader; }
    public MachineManager getMachineManager() { return machineManager; }
    public GuiManager getGuiManager()       { return guiManager; }
    public TickEngine getTickEngine()       { return tickEngine; }
}
