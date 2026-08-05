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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

/**
 * Abstract base cho tất cả GUI trong hệ thống metallurgy.
 *
 * Hỗ trợ real-time refresh qua {@link #startRefreshTask(long)} —
 * subclass gọi trong open(), task sẽ tự hủy khi GUI đóng.
 */
public abstract class MetallurgyGui {

    protected final HaoHanMetallurgy plugin;
    protected Inventory inventory;

    /** Task refresh realtime — tự hủy khi onClose(). */
    private BukkitTask refreshTask;

    protected MetallurgyGui(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────

    protected abstract void buildLayout();

    public abstract void refresh();

    /**
     * Mở GUI cho player.
     * Subclass override để thêm startRefreshTask() nếu cần realtime.
     */
    public void open(Player player) {
        if (inventory == null) buildLayout();
        refresh();
        player.openInventory(inventory);
        plugin.getPluginLogger().debug("Opened " + getClass().getSimpleName() + " for " + player.getName());
    }

    public abstract void onClick(InventoryClickEvent event);

    /**
     * Gọi khi player đóng inventory — hủy refresh task tự động.
     * Subclass override nếu cần cleanup thêm, nhưng PHẢI gọi super.onClose().
     */
    public void onClose(InventoryCloseEvent event) {
        stopRefreshTask();
    }

    // ── Refresh task helpers ──────────────────────────────────

    /**
     * Bắt đầu refresh GUI mỗi {@code intervalTicks} ticks.
     * Gọi trong open() của subclass.
     */
    protected final void startRefreshTask(long intervalTicks) {
        stopRefreshTask();
        refreshTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::refresh, intervalTicks, intervalTicks);
    }

    protected final void stopRefreshTask() {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
        }
        refreshTask = null;
    }

    // ── Helpers ───────────────────────────────────────────────

    protected void setSlot(int slot, org.bukkit.inventory.ItemStack item) {
        if (inventory != null) inventory.setItem(slot, item);
    }

    public Inventory getInventory() { return inventory; }
}
