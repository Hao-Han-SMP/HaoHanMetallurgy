package dev.haohansmp.metallurgy.gui;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.*;

/**
 * Quản lý tất cả GUI đang mở.
 * Lắng nghe InventoryClickEvent và InventoryCloseEvent,
 * route về đúng MetallurgyGui instance của player đó.
 */
public class GuiManager implements Listener {

    private final HaoHanMetallurgy plugin;

    /** Player UUID → GUI đang mở của player đó. */
    private final Map<UUID, MetallurgyGui> openGuis = new HashMap<>();

    public GuiManager(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    // ── Public API ─────────────────────────────────────────────

    /**
     * Mở GUI cho player và track nó.
     */
    public void open(Player player, MetallurgyGui gui) {
        openGuis.put(player.getUniqueId(), gui);
        gui.open(player);
    }

    /**
     * Đóng GUI của player programmatically.
     */
    public void close(Player player) {
        openGuis.remove(player.getUniqueId());
        player.closeInventory();
    }

    /**
     * Kiểm tra player có đang mở GUI metallurgy không.
     */
    public boolean hasOpenGui(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }

    /**
     * Lấy GUI đang mở của player.
     */
    public Optional<MetallurgyGui> getGui(Player player) {
        return Optional.ofNullable(openGuis.get(player.getUniqueId()));
    }

    /** Số lượng GUI đang mở. */
    public int count() {
        return openGuis.size();
    }

    // ── Event Listeners ───────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MetallurgyGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;

        // Đảm bảo click đúng inventory của GUI này
        if (!event.getInventory().equals(gui.getInventory())) return;

        event.setCancelled(true); // Default: cancel click, GUI tự xử lý
        gui.onClick(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        MetallurgyGui gui = openGuis.remove(player.getUniqueId());
        if (gui != null) {
            gui.onClose(event);
            plugin.getPluginLogger().debug("Closed GUI for " + player.getName());
        }
    }
}
