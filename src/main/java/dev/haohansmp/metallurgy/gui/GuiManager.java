/*
 * Copyright (C) 2026 HaoHanSMP
 *
 * This file is part of HaoHan Metallurgy.
 *
 * HaoHan Metallurgy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HaoHan Metallurgy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HaoHan Metallurgy. If not, see <https://www.gnu.org/licenses/>.
 */

package dev.haohansmp.metallurgy.gui;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

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

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MetallurgyGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;

        // Đảm bảo click đúng inventory của GUI này
        if (!event.getInventory().equals(gui.getInventory())) return;

        // Default cancel — GUI.onClick() sẽ un-cancel những slot cho phép
        event.setCancelled(true);
        gui.onClick(event);
    }

    /**
     * Chặn drag (kéo thả item) vào bất kỳ slot nào trong GUI top inventory.
     * Drag là click type riêng, không đi qua InventoryClickEvent.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MetallurgyGui gui = openGuis.get(player.getUniqueId());
        if (gui == null) return;
        if (!event.getInventory().equals(gui.getInventory())) return;

        int guiSize = gui.getInventory().getSize();
        // Nếu drag chạm vào bất kỳ slot nào trong GUI top → cancel toàn bộ drag
        boolean touchesGui = event.getRawSlots().stream().anyMatch(s -> s < guiSize);
        if (touchesGui) {
            event.setCancelled(true);
        }
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
