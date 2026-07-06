package dev.haohansmp.metallurgy.command;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Handler cho command /metallurgy (alias: /met, /forge).
 *
 * Subcommands:
 *   /metallurgy info    — Hiển thị thông tin plugin
 *   /metallurgy reload  — Reload config + recipes
 *   /metallurgy debug   — Toggle debug mode
 *   /metallurgy list    — List tất cả machines đang active
 */
public class MetallurgyCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§8[§6HaoHan§eForge§8] §r";
    private static final String PERMISSION = "haohansmp.metallurgy.admin";

    private final HaoHanMetallurgy plugin;

    public MetallurgyCommand(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(PREFIX + "§cBạn không có quyền dùng lệnh này.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info"   -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            case "debug"  -> handleDebug(sender);
            case "list"   -> handleList(sender);
            default       -> sendHelp(sender);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("info", "reload", "debug", "list");
        }
        return List.of();
    }

    // ── Handlers ──────────────────────────────────────────────

    private void handleInfo(CommandSender sender) {
        sender.sendMessage(PREFIX + "§eHaoHan Metallurgy §av" + plugin.getDescription().getVersion());
        sender.sendMessage(PREFIX + "§7Machines active: §f" + plugin.getMachineManager().count());
        sender.sendMessage(PREFIX + "§7Recipes loaded:  §f" + plugin.getRecipeLoader().count());
        sender.sendMessage(PREFIX + "§7TickEngine: §f" + (plugin.getTickEngine().isRunning() ? "§aRunning" : "§cStopped"));
        sender.sendMessage(PREFIX + "§7Debug mode: §f" + (plugin.getConfigManager().isDebug() ? "§aON" : "§7OFF"));
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.getConfigManager().reload();
            plugin.getRecipeLoader().loadAll();
            plugin.getTickEngine().restart();
            sender.sendMessage(PREFIX + "§aReload thành công!");
        } catch (Exception e) {
            sender.sendMessage(PREFIX + "§cReload thất bại: " + e.getMessage());
            plugin.getPluginLogger().error("Reload failed", e);
        }
    }

    private void handleDebug(CommandSender sender) {
        // Toggle debug trong memory (không ghi vào file)
        boolean current = plugin.getConfigManager().isDebug();
        plugin.getConfig().set("debug", !current);
        plugin.getConfigManager().reload();
        sender.sendMessage(PREFIX + "§7Debug mode: " + (!current ? "§aON" : "§7OFF"));
    }

    private void handleList(CommandSender sender) {
        var machines = plugin.getMachineManager().getAll();
        if (machines.isEmpty()) {
            sender.sendMessage(PREFIX + "§7Không có machine nào đang active.");
            return;
        }

        sender.sendMessage(PREFIX + "§e" + machines.size() + " machine(s) đang active:");
        machines.forEach(m -> {
            var loc = m.getLocation();
            sender.sendMessage(String.format("  §8- §6%s §7tại §f(%d, %d, %d) §8[§e%s§8]",
                m.getType().name(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                m.getState().name()
            ));
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + "§eCommands:");
        sender.sendMessage("  §6/metallurgy info    §7— Thông tin plugin");
        sender.sendMessage("  §6/metallurgy reload  §7— Reload config + recipe");
        sender.sendMessage("  §6/metallurgy debug   §7— Toggle debug mode");
        sender.sendMessage("  §6/metallurgy list    §7— List machines đang active");
    }
}
