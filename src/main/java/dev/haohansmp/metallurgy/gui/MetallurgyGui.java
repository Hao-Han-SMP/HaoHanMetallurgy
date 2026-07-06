package dev.haohansmp.metallurgy.gui;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * Abstract base cho tất cả GUI trong hệ thống metallurgy.
 *
 * Mỗi loại GUI (ForgeGui, ...) extend class này,
 * implement buildLayout() để setup slots và onClick() để handle click.
 */
public abstract class MetallurgyGui {

    protected final HaoHanMetallurgy plugin;
    protected Inventory inventory;

    protected MetallurgyGui(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────

    /**
     * Xây dựng layout ban đầu.
     * Gọi một lần khi tạo GUI.
     */
    protected abstract void buildLayout();

    /**
     * Refresh nội dung GUI (progress bar, nhiệt độ, fuel, ...).
     * Gọi mỗi tick hoặc khi state thay đổi.
     */
    public abstract void refresh();

    /**
     * Mở GUI cho player.
     */
    public void open(Player player) {
        if (inventory == null) buildLayout();
        refresh();
        player.openInventory(inventory);
        plugin.getPluginLogger().debug("Opened " + getClass().getSimpleName() + " for " + player.getName());
    }

    /**
     * Xử lý click event. GuiManager route event vào đây.
     */
    public abstract void onClick(InventoryClickEvent event);

    /**
     * Xử lý khi player đóng inventory.
     * Subclass override nếu cần cleanup.
     */
    public void onClose(InventoryCloseEvent event) {}

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Điền một slot bằng ItemStack.
     * Subclass dùng method này để không truy cập inventory trực tiếp.
     */
    protected void setSlot(int slot, org.bukkit.inventory.ItemStack item) {
        if (inventory != null) {
            inventory.setItem(slot, item);
        }
    }

    /** Lấy inventory đang dùng. */
    public Inventory getInventory() {
        return inventory;
    }
}
