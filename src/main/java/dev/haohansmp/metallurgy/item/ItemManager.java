package dev.haohansmp.metallurgy.item;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.config.CustomItemStats;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Quản lý việc tạo và nhận diện các Custom Item trong Metallurgy.
 */
public class ItemManager {

    private final HaoHanMetallurgy plugin;
    private final NamespacedKey itemKey;

    public ItemManager(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item_id");
    }

    /**
     * Tạo ItemStack cho một CustomItem với số lượng cho trước.
     */
    public ItemStack createItem(CustomItem customItem, int amount) {
        ItemStack itemStack = new ItemStack(customItem.getMaterial(), amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(customItem.getDisplayName());
            meta.setLore(customItem.getLore());
            if (customItem.getCustomModelData() > 0) {
                meta.setCustomModelData(customItem.getCustomModelData());
            }
            applyConfiguredStats(customItem, meta);
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, customItem.getId());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    /**
     * Lấy CustomItem tương ứng với ItemStack nếu có.
     */
    public Optional<CustomItem> getCustomItem(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return Optional.empty();
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String id = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        return CustomItem.getById(id);
    }

    /**
     * Kiểm tra xem ItemStack có phải là một CustomItem cụ thể hay không.
     */
    public boolean isCustomItem(ItemStack itemStack, CustomItem target) {
        return getCustomItem(itemStack)
            .map(item -> item == target)
            .orElse(false);
    }

    private void applyConfiguredStats(CustomItem customItem, ItemMeta meta) {
        CustomItemStats stats = plugin.getConfigManager().getCustomItemStats(customItem);

        if (stats.maxDamage() > 0 && meta instanceof Damageable damageable) {
            damageable.setMaxDamage(stats.maxDamage());
        }

        applyAttribute(meta, Attribute.ATTACK_DAMAGE, customItem, "attack_damage", stats.attackDamage());
        applyAttribute(meta, Attribute.ATTACK_SPEED, customItem, "attack_speed", stats.attackSpeed());
        applyAttribute(meta, Attribute.MINING_EFFICIENCY, customItem, "mining_efficiency", stats.miningEfficiency());
        applyAttribute(meta, Attribute.BLOCK_BREAK_SPEED, customItem, "block_break_speed", stats.blockBreakSpeed());
    }

    private void applyAttribute(ItemMeta meta, Attribute attribute, CustomItem customItem, String suffix, double amount) {
        if (amount == 0.0) {
            return;
        }

        NamespacedKey key = new NamespacedKey(plugin, customItem.getId() + "_" + suffix);
        AttributeModifier modifier = new AttributeModifier(
                key,
                amount,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.MAINHAND);
        meta.addAttributeModifier(attribute, modifier);
    }
}

