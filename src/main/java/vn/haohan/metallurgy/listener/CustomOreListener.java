package vn.haohan.metallurgy.listener;

import vn.haohan.metallurgy.HaoHanMetallurgy;
import vn.haohan.metallurgy.item.CustomItem;
import vn.haohan.metallurgy.item.CustomOreBlock;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/** Handles Metallurgy inventory migration and creative pick block. Mining speed and drops are managed by ItemCore. */
public final class CustomOreListener implements Listener {
    private final HaoHanMetallurgy plugin;

    public CustomOreListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickBlock(PlayerPickItemEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) return;

        Block targetBlock = event.getPlayer().getTargetBlockExact(5);
        if (targetBlock == null) return;

        CustomItem managedBlock = CustomOreBlock.getCustomBlock(targetBlock);
        if (managedBlock == null) return;

        event.setCancelled(true);
        int targetSlot = event.getTargetSlot();
        if (targetSlot < 0 || targetSlot > 8) {
            targetSlot = event.getPlayer().getInventory().getHeldItemSlot();
        }
        int hotbarSlot = targetSlot;
        plugin.getServer().getScheduler().runTask(plugin, () ->
                event.getPlayer().getInventory().setItem(
                        hotbarSlot, plugin.getItemManager().createItem(managedBlock, 1)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var inventory = event.getPlayer().getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) continue;
            ItemStack migrated = plugin.getItemManager().migrateCustomOreItem(item);
            inventory.setItem(slot, migrated);
        }
    }

    public void cleanupMiningSpeedModifiers() {
        // Mining speed is now managed completely by HaoHanItemCore
    }
}
