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
            case "give"   -> handleGive(sender, args);
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
            return List.of("info", "reload", "debug", "list", "give");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return null; // Bukkit tự động điền tên online players
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return java.util.Arrays.stream(dev.haohansmp.metallurgy.item.CustomItem.values())
                .map(dev.haohansmp.metallurgy.item.CustomItem::getId)
                .toList();
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
            int refreshed = plugin.getMachineManager().refreshForgeDisplays();
            sender.sendMessage(PREFIX + "§aReload thành công! §7Refreshed models: §f" + refreshed);
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

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + "§cSử dụng: /metallurgy give <player> <item_id> [amount]");
            return;
        }

        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + "§cKhông tìm thấy người chơi: " + args[1]);
            return;
        }

        String itemId = args[2];
        java.util.Optional<dev.haohansmp.metallurgy.item.CustomItem> itemOpt = dev.haohansmp.metallurgy.item.CustomItem.getById(itemId);
        if (itemOpt.isEmpty()) {
            sender.sendMessage(PREFIX + "§cKhông tìm thấy custom item: " + itemId);
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "§cSố lượng không hợp lệ. Mặc định là 1.");
            }
        }

        org.bukkit.inventory.ItemStack itemStack = plugin.getItemManager().createItem(itemOpt.get(), amount);
        target.getInventory().addItem(itemStack);
        sender.sendMessage(PREFIX + "§aĐã cho §e" + target.getName() + " §f" + amount + "x " + itemOpt.get().getDisplayName());
        target.sendMessage(PREFIX + "§aBạn nhận được §f" + amount + "x " + itemOpt.get().getDisplayName() + " §atừ Admin.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(PREFIX + "§eCommands:");
        sender.sendMessage("  §6/metallurgy info    §7— Thông tin plugin");
        sender.sendMessage("  §6/metallurgy reload  §7— Reload config + recipe");
        sender.sendMessage("  §6/metallurgy debug   §7— Toggle debug mode");
        sender.sendMessage("  §6/metallurgy list    §7— List machines đang active");
        sender.sendMessage("  §6/metallurgy give    §7— Nhận custom metallurgy items");
    }
}
