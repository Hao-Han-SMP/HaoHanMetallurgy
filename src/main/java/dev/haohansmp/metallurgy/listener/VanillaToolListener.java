package dev.haohansmp.metallurgy.listener;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class VanillaToolListener implements Listener {

    private final HaoHanMetallurgy plugin;
    private final NamespacedKey formatKey;

    public VanillaToolListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        this.formatKey = new NamespacedKey(plugin, "vanilla_pickaxe_formatted");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack result = inv.getResult();
        if (result != null && isVanillaPickaxe(result.getType())) {
            inv.setResult(formatPickaxe(result));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            ItemStack item = event.getItem().getItemStack();
            if (isVanillaPickaxe(item.getType())) {
                formatPickaxe(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item != null && isVanillaPickaxe(item.getType())) {
            formatPickaxe(item);
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && isVanillaPickaxe(cursor.getType())) {
            formatPickaxe(cursor);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item != null && isVanillaPickaxe(item.getType())) {
            formatPickaxe(item);
        }
    }

    private boolean isVanillaPickaxe(Material mat) {
        return mat == Material.WOODEN_PICKAXE
                || mat == Material.STONE_PICKAXE
                || mat == Material.GOLDEN_PICKAXE
                || mat == Material.COPPER_PICKAXE
                || mat == Material.IRON_PICKAXE
                || mat == Material.DIAMOND_PICKAXE
                || mat == Material.NETHERITE_PICKAXE;
    }

    private ItemStack formatPickaxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        if (plugin.getItemManager().getCustomItem(item).isPresent()) {
            return item;
        }

        // Kiểm tra xem đã định dạng chưa
        if (meta.getPersistentDataContainer().has(formatKey, PersistentDataType.BOOLEAN)) {
            return item;
        }

        // Định dạng tên hiển thị và Lore
        int tier = getPickaxeTier(item.getType());
        String name = getToolName(item.getType());
        String tierLore = getToolTierLore(tier);

        meta.setDisplayName(name);

        // Giữ lại mô tả cũ hoặc thông tin enchantments của vật phẩm, chỉ chèn thông tin
        // Tier của chúng ta
        List<String> newLore = new ArrayList<>();
        newLore.add(tierLore);
        newLore.add("§7Vật phẩm game gốc đã được thiết lập thông số.");

        List<String> existingLore = meta.getLore();
        if (existingLore != null) {
            for (String line : existingLore) {
                if (!line.startsWith("§8Tier:") && !line.contains("thiết lập thông số")) {
                    newLore.add(line);
                }
            }
        }
        meta.setLore(newLore);

        // Đánh dấu NBT tag để tránh lặp
        meta.getPersistentDataContainer().set(formatKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private int getPickaxeTier(Material type) {
        if (type == Material.NETHERITE_PICKAXE)
            return 9;
        if (type == Material.DIAMOND_PICKAXE)
            return 7;
        if (type == Material.IRON_PICKAXE)
            return 6;
        if (type == Material.GOLDEN_PICKAXE)
            return 2;
        if (type == Material.STONE_PICKAXE)
            return 2;
        if (type == Material.COPPER_PICKAXE)
            return 4;
        if (type == Material.WOODEN_PICKAXE)
            return 1;
        return 0;
    }

    private String getToolName(Material type) {
        return switch (type) {
            case WOODEN_PICKAXE -> "§fWooden Pickaxe";
            case STONE_PICKAXE -> "§7Stone Pickaxe";
            case GOLDEN_PICKAXE -> "§eGolden Pickaxe";
            case COPPER_PICKAXE -> "§6Copper Pickaxe";
            case IRON_PICKAXE -> "§fIron Pickaxe";
            case DIAMOND_PICKAXE -> "§bDiamond Pickaxe";
            case NETHERITE_PICKAXE -> "§5Netherite Pickaxe";
            default -> type.name();
        };
    }

    private String getToolTierLore(int tier) {
        return switch (tier) {
            case 1 -> "§8Tier: §c1 (Wooden)";
            case 2 -> "§8Tier: §c2 (Stone/Gold)";
            case 4 -> "§8Tier: §c4 (Copper)";
            case 6 -> "§8Tier: §c6 (Iron)";
            case 7 -> "§8Tier: §c7 (Diamond)";
            case 9 -> "§8Tier: §c9 (Netherite)";
            default -> "§8Tier: §c" + tier;
        };
    }
}
