package vn.haohan.metallurgy.command;

import vn.haohan.metallurgy.HaoHanMetallurgy;
import vn.haohan.metallurgy.gui.CreativeMenuGui;
import vn.haohan.metallurgy.i18n.LanguageManager;
import vn.haohan.metallurgy.item.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MetallurgyCommand implements CommandExecutor, TabCompleter {
    private static final String PERMISSION = "haohansmp.metallurgy.admin";
    private final HaoHanMetallurgy plugin;

    public MetallurgyCommand(HaoHanMetallurgy plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        return execute(sender, label, args);
    }

    /**
     * Command entry point shared by Bukkit's legacy adapter and Paper's
     * BasicCommand registration.
     */
    public boolean execute(@NotNull CommandSender sender, @NotNull String label,
                           @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION)) return reply(sender, "error.permission");
        if (args.length == 0) return help(sender);

        return switch (args[0].toLowerCase()) {
            case "info" -> info(sender);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender);
            case "list" -> list(sender);
            case "give" -> give(sender, args);
            case "menu", "creative" -> menu(sender);
            case "language", "lang" -> language(sender, args);
            default -> help(sender);
        };
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return suggest(sender, alias, args);
    }

    public List<String> suggest(@NotNull CommandSender sender, @NotNull String alias,
                                @NotNull String[] args) {
        if (args.length == 1) return List.of("info", "menu", "give", "list", "reload", "debug", "language");
        if (args.length == 2 && args[0].equalsIgnoreCase("give"))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("language") || args[0].equalsIgnoreCase("lang")))
            return plugin.getLanguageManager().getSupportedLanguages();
        if (args.length == 3 && args[0].equalsIgnoreCase("give"))
            return Arrays.stream(CustomItem.values()).map(CustomItem::getId).toList();
        return List.of();
    }

    private boolean info(CommandSender sender) {
        var lang = lang();
        reply(sender, "info.title", Map.of("version", plugin.getDescription().getVersion()));
        reply(sender, "info.machines", Map.of("count", plugin.getMachineManager().count()));
        reply(sender, "info.recipes", Map.of("count", plugin.getRecipeLoader().count()));
        reply(sender, "info.engine", Map.of("status", lang.text(plugin.getTickEngine().isRunning() ? "status.running" : "status.stopped")));
        return reply(sender, "info.debug", Map.of("status", lang.text(plugin.getConfigManager().isDebug() ? "status.on" : "status.off")));
    }

    private boolean reload(CommandSender sender) {
        try {
            plugin.getConfigManager().reload();
            lang().reload();
            plugin.getMetallurgyRecipeRegistry().registerAll();
            plugin.getRecipeLoader().loadAll();
            plugin.getMetallurgyRecipeRegistry().registerMachineRecipes(plugin.getRecipeLoader());
            plugin.getTickEngine().restart();
            return reply(sender, "reload.success", Map.of("count", plugin.getMachineManager().refreshForgeDisplays()));
        } catch (Exception e) {
            plugin.getPluginLogger().error("Reload failed", e);
            return reply(sender, "reload.failed", Map.of("error", e.getMessage()));
        }
    }

    private boolean debug(CommandSender sender) {
        boolean enabled = !plugin.getConfigManager().isDebug();
        plugin.getConfig().set("debug", enabled);
        plugin.saveConfig();
        plugin.getConfigManager().reload();
        return reply(sender, "debug", Map.of("status", lang().text(enabled ? "status.on" : "status.off")));
    }

    private boolean list(CommandSender sender) {
        var machines = plugin.getMachineManager().getAll();
        if (machines.isEmpty()) return reply(sender, "machines.empty");
        reply(sender, "machines.header", Map.of("count", machines.size()));
        machines.forEach(machine -> {
            var location = machine.getLocation();
            sender.sendMessage(lang().text("machines.entry", Map.of(
                    "type", machine.getType().name(), "state", machine.getState().name(),
                    "x", location.getBlockX(), "y", location.getBlockY(), "z", location.getBlockZ())));
        });
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 3) return reply(sender, "give.usage");
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) return reply(sender, "error.player-not-found", Map.of("player", args[1]));
        var item = CustomItem.getById(args[2]);
        if (item.isEmpty()) return reply(sender, "error.item-not-found", Map.of("item", args[2]));
        int amount = 1;
        if (args.length >= 4) {
            try { amount = Integer.parseInt(args[3]); }
            catch (NumberFormatException ignored) { return reply(sender, "error.invalid-amount"); }
        }
        if (amount < 1) return reply(sender, "error.invalid-amount");
        target.getInventory().addItem(plugin.getItemManager().createItem(item.get(), amount));
        String localizedItemName = lang().itemName(item.get());
        reply(sender, "give.sent", Map.of("player", target.getName(), "amount", amount,
                "item", localizedItemName));
        return reply(target, "give.received", Map.of("amount", amount,
                "item", localizedItemName));
    }

    private boolean menu(CommandSender sender) {
        if (!(sender instanceof Player player)) return reply(sender, "error.player-only");
        plugin.getGuiManager().open(player, new CreativeMenuGui(plugin));
        return true;
    }

    private boolean language(CommandSender sender, String[] args) {
        if (args.length != 2 || !lang().setLanguage(args[1])) return reply(sender, "language.usage");
        plugin.getMetallurgyRecipeRegistry().registerAll();
        plugin.getPluginLogger().info("Language changed to " + lang().getLanguage()
                + "; existing item stacks were left unchanged.");
        return reply(sender, "language.changed");
    }

    private boolean help(CommandSender sender) {
        String prefix = lang().text("prefix");
        lang().list("help").forEach(line -> sender.sendMessage(prefix + line));
        return true;
    }

    private LanguageManager lang() { return plugin.getLanguageManager(); }
    private boolean reply(CommandSender sender, String key) { return reply(sender, key, Map.of()); }
    private boolean reply(CommandSender sender, String key, Map<String, ?> values) {
        sender.sendMessage(lang().text("prefix") + lang().text(key, values));
        return true;
    }
}
