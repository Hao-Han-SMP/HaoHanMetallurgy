package vn.haohan.metallurgy;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import vn.haohan.displayui.api.DisplayUiService;
import vn.haohan.metallurgy.command.MetallurgyCommand;
import vn.haohan.metallurgy.config.ConfigManager;
import vn.haohan.metallurgy.engine.TickEngine;
import vn.haohan.metallurgy.gui.GuiManager;
import vn.haohan.metallurgy.i18n.LanguageManager;
import vn.haohan.metallurgy.item.ItemManager;
import vn.haohan.metallurgy.item.CustomItem;
import vn.haohan.metallurgy.listener.ChunkListener;
import vn.haohan.metallurgy.listener.CharcoalKilnListener;
import vn.haohan.metallurgy.listener.CustomOreListener;
import vn.haohan.metallurgy.listener.ForgeListener;
import vn.haohan.metallurgy.listener.ProgressionListener;
import vn.haohan.metallurgy.listener.JuteHarvestListener;
import vn.haohan.metallurgy.listener.VanillaFurnaceListener;
import vn.haohan.metallurgy.listener.VanillaToolListener;
import vn.haohan.metallurgy.listener.ManagedDisplayProtectionListener;
import vn.haohan.metallurgy.listener.FurnaceGuideListener;
import vn.haohan.metallurgy.machine.MachineManager;
import vn.haohan.metallurgy.progression.ProgressionManager;
import vn.haohan.metallurgy.recipe.CraftingRecipeManager;
import vn.haohan.metallurgy.recipe.RecipeLoader;
import vn.haohan.metallurgy.util.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;

public final class HaoHanMetallurgy extends JavaPlugin {

    private static final String REQUIRED_DISPLAY_UI_VERSION = "1.1.0";

    // ── Singleton ──────────────────────────────────────────────
    private static HaoHanMetallurgy instance;

    public static HaoHanMetallurgy getInstance() {
        return instance;
    }

    // ── Managers ───────────────────────────────────────────────
    private PluginLogger pluginLogger;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private ItemManager itemManager;
    private CraftingRecipeManager craftingRecipeManager;
    private RecipeLoader recipeLoader;
    private MachineManager machineManager;
    private ProgressionManager progressionManager;
    private GuiManager guiManager;
    private TickEngine tickEngine;
    private CustomOreListener customOreListener;
    private CharcoalKilnListener charcoalKilnListener;
    private FurnaceGuideListener furnaceGuideListener;

    // ── Lifecycle ──────────────────────────────────────────────

    @Override
    public void onEnable() {
        instance = this;

        // 1. Logger (phải đầu tiên)
        pluginLogger = new PluginLogger(this);
        pluginLogger.info("=== HaoHan Metallurgy ===");
        pluginLogger.info("Initializing Core Engine...");
        if (Boolean.getBoolean("haohan.metallurgy.ignite")) {
            pluginLogger.info("Ignite patch layer detected. Optional server internals bridge is active.");
        }
        if (!isDisplayUiCompatible()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Config
        configManager = new ConfigManager(this);
        languageManager = new LanguageManager(this);

        // 2.5 Item Manager
        itemManager = new ItemManager(this);
        progressionManager = new ProgressionManager(this);
        registerDisplayIcons();
        craftingRecipeManager = new CraftingRecipeManager(this);
        getServer().getPluginManager().registerEvents(craftingRecipeManager, this);
        craftingRecipeManager.registerAll();

        // 3. Recipe Loader
        recipeLoader = new RecipeLoader(this);
        recipeLoader.loadAll();
        craftingRecipeManager.registerMachineBrowserRecipes(recipeLoader);

        // 4. Machine Manager
        machineManager = new MachineManager(this);

        // 5. GUI Manager (cần register listener)
        guiManager = new GuiManager(this);
        getServer().getPluginManager().registerEvents(guiManager, this);

        getServer().getPluginManager().registerEvents(new ForgeListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkListener(this), this);
        customOreListener = new CustomOreListener(this);
        getServer().getPluginManager().registerEvents(customOreListener, this);
        getServer().getPluginManager().registerEvents(new ProgressionListener(this), this);
        getServer().getPluginManager().registerEvents(new JuteHarvestListener(this), this);
        charcoalKilnListener = new CharcoalKilnListener(this);
        getServer().getPluginManager().registerEvents(charcoalKilnListener, this);
        VanillaFurnaceListener vanillaFurnaceListener = new VanillaFurnaceListener(this);
        vanillaFurnaceListener.registerMetalRecipes();
        getServer().getPluginManager().registerEvents(vanillaFurnaceListener, this);
        getServer().getPluginManager().registerEvents(new VanillaToolListener(this), this);
        getServer().getPluginManager().registerEvents(new ManagedDisplayProtectionListener(), this);
        furnaceGuideListener = new FurnaceGuideListener(this);
        getServer().getPluginManager().registerEvents(furnaceGuideListener, this);

        // 7. Tick Engine (khởi động sau khi managers sẵn sàng)
        tickEngine = new TickEngine(this);
        tickEngine.start();

        // 7. Commands
        var cmd = new MetallurgyCommand(this);
        registerCommand("metallurgy", "HaoHan Metallurgy main command",
                java.util.List.of("met", "forge"), new BasicCommand() {
                    @Override
                    public void execute(CommandSourceStack source, String[] args) {
                        cmd.execute(source.getSender(), "metallurgy", args);
                    }

                    @Override
                    public java.util.Collection<String> suggest(CommandSourceStack source, String[] args) {
                        return cmd.suggest(source.getSender(), "metallurgy", args);
                    }

                    @Override
                    public String permission() {
                        return "haohansmp.metallurgy.admin";
                    }
                });

        // 8. Restore active machines sau 1 tick để world/chunk state ổn định hơn khi restart.
        getServer().getScheduler().runTask(this, () -> {
            machineManager.loadAll();
            pluginLogger.info("Restored delayed machine state. Machines: " + machineManager.count());
        });

        pluginLogger.info("Core Engine enabled. Recipes: " + recipeLoader.count()
            + " | Machines: " + machineManager.count());
        pluginLogger.info("=========================");
    }

    private void registerDisplayIcons() {
        DisplayUiService displayUi = Bukkit.getServicesManager().load(DisplayUiService.class);
        if (displayUi == null) {
            throw new IllegalStateException("HaoHanDisplayUI service is unavailable");
        }
        for (CustomItem item : CustomItem.values()) {
            NamespacedKey key = new NamespacedKey(this, "item/" + item.getId());
            displayUi.icons().register(this, key, () -> itemManager.createItem(item, 1));
        }
    }

    private boolean isDisplayUiCompatible() {
        Plugin displayUiPlugin = getServer().getPluginManager().getPlugin("HaoHanDisplayUI");
        String installedVersion = displayUiPlugin == null
                ? "not installed"
                : displayUiPlugin.getPluginMeta().getVersion();
        try {
            DisplayUiService.class.getMethod("icons");
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            pluginLogger.error("Incompatible HaoHanDisplayUI " + installedVersion
                    + ". HaoHanMetallurgy requires HaoHanDisplayUI "
                    + REQUIRED_DISPLAY_UI_VERSION + " or newer. Replace the old DisplayUI JAR and restart the server.");
            return false;
        }
    }

    @Override
    public void onDisable() {
        pluginLogger.info("Shutting down HaoHan Metallurgy...");

        if (tickEngine != null) tickEngine.stop();
        if (charcoalKilnListener != null) charcoalKilnListener.shutdown();
        if (furnaceGuideListener != null) furnaceGuideListener.shutdown();
        if (customOreListener != null) customOreListener.cleanupMiningSpeedModifiers();

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
    public LanguageManager getLanguageManager() { return languageManager; }
    public ItemManager getItemManager()     { return itemManager; }
    public CraftingRecipeManager getCraftingRecipeManager() { return craftingRecipeManager; }
    public RecipeLoader getRecipeLoader()   { return recipeLoader; }
    public MachineManager getMachineManager() { return machineManager; }
    public ProgressionManager getProgressionManager() { return progressionManager; }
    public GuiManager getGuiManager()       { return guiManager; }
    public TickEngine getTickEngine()       { return tickEngine; }
}
