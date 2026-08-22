package vn.haohan.metallurgy.item;

import vn.haohan.metallurgy.HaoHanMetallurgy;
import vn.haohan.metallurgy.config.CustomItemStats;
import vn.haohan.itemcore.api.HaoHanItemCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

/**
 * Quản lý việc tạo và nhận diện các Custom Item trong Metallurgy.
 */
public class ItemManager {

    private static final NamespacedKey BOW_DRILL_MODEL = NamespacedKey.fromString("haohan:bow_drill");

    private final HaoHanMetallurgy plugin;
    private final NamespacedKey itemKey;

    public ItemManager(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item_id");
        MetallurgyItemRegistry.registerAll(plugin);
    }

    /**
     * Tạo ItemStack cho một CustomItem với số lượng cho trước.
     */
    public ItemStack createItem(CustomItem customItem, int amount) {
        // HaoHanItemCore is the authoritative factory for all newly-created
        // Metallurgy stacks. The legacy PDC below is intentionally retained so
        // existing inventories, recipes, and external integrations keep working.
        ItemStack itemStack = HaoHanItemCore.get().getItemService()
                .create(MetallurgyItemRegistry.id(customItem), amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            applyCanonicalPresentation(customItem, meta);
            // Keep Metallurgy's established CMD mappings. Definitions intentionally
            // omit CMD so ItemCore's validator does not invent an ItemCore model ID.
            if (customItem.getCustomModelData() > 0) {
                meta.setCustomModelData(customItem.getCustomModelData());
            }
            if (customItem == CustomItem.BOW_DRILL) {
                meta.setItemModel(BOW_DRILL_MODEL);
            }
            meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, customItem.getId());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    /** Creates a localized display-only copy for a GUI. */
    public ItemStack createGuiItem(CustomItem customItem, int amount) {
        ItemStack itemStack = createItem(customItem, amount);
        ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            applyLocalizedPresentation(customItem, meta);
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void applyCanonicalPresentation(CustomItem customItem, ItemMeta meta) {
        meta.displayName(Component.text(org.bukkit.ChatColor.stripColor(customItem.getDisplayName()))
                .color(nameColor(customItem))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.stream.IntStream.range(0, customItem.getLore().size())
                .mapToObj(index -> Component.text(org.bukkit.ChatColor.stripColor(customItem.getLore().get(index)))
                        .color(index == 0 ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .toList());
        if (CustomOreBlock.isManagedBlock(customItem)) {
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }
    }

    private void applyLocalizedPresentation(CustomItem customItem, ItemMeta meta) {
        meta.displayName(Component.text(plugin.getLanguageManager().itemName(customItem))
                .color(nameColor(customItem))
                .decoration(TextDecoration.ITALIC, false));
        List<String> localizedLore = plugin.getLanguageManager().itemLore(customItem);
        meta.lore(java.util.stream.IntStream.range(0, localizedLore.size())
                .mapToObj(index -> Component.text(localizedLore.get(index))
                        .color(index == 0 ? NamedTextColor.GRAY : NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .toList());
        if (CustomOreBlock.isManagedBlock(customItem)) {
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }
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
        if (id != null) {
            return CustomItem.getById(id);
        }

        // Core-created stacks are also recognizable if another integration has
        // removed the legacy compatibility key.
        String coreId = HaoHanItemCore.get().getItemService().getId(itemStack);
        if (coreId == null || !coreId.startsWith(MetallurgyItemRegistry.NAMESPACE + ":")) {
            return Optional.empty();
        }
        return CustomItem.getById(coreId.substring(MetallurgyItemRegistry.NAMESPACE.length() + 1));
    }

    /**
     * Kiểm tra xem ItemStack có phải là một CustomItem cụ thể hay không.
     */
    public boolean isCustomItem(ItemStack itemStack, CustomItem target) {
        return getCustomItem(itemStack)
            .map(item -> item == target)
            .orElse(false);
    }

    /** Rebuilds legacy carrier stacks with their reserved NoteBlock state. */
    public ItemStack migrateCustomOreItem(ItemStack itemStack) {
        CustomItem customItem = getCustomItem(itemStack).orElse(null);
        if (customItem == null) return itemStack;

        boolean correctMaterial = itemStack.getType() == customItem.getMaterial();
        ItemMeta currentMeta = itemStack.getItemMeta();
        boolean hasLegacyBlockData = currentMeta instanceof BlockDataMeta blockDataMeta
                && blockDataMeta.hasBlockData();
        boolean missingTooltipHide = CustomOreBlock.isManagedBlock(customItem)
                && (currentMeta == null || !currentMeta.hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP));
        boolean needsCarrierMigration = CustomOreBlock.isManagedBlock(customItem)
                && (!correctMaterial || hasLegacyBlockData || missingTooltipHide);
        boolean correctBowDrillModel = currentMeta != null
                && currentMeta.hasItemModel()
                && BOW_DRILL_MODEL.equals(currentMeta.getItemModel());
        boolean needsBowDrillMigration = customItem == CustomItem.BOW_DRILL
                && (!correctMaterial || !correctBowDrillModel);
        if (!needsCarrierMigration && !needsBowDrillMigration) {
            return itemStack;
        }
        ItemStack migrated = createItem(customItem, itemStack.getAmount());
        if (itemStack.getItemMeta() instanceof Damageable oldDamage
                && migrated.getItemMeta() instanceof Damageable newDamage) {
            newDamage.setDamage(Math.min(oldDamage.getDamage(), Math.max(0, newDamage.getMaxDamage() - 1)));
            migrated.setItemMeta(newDamage);
        }
        return migrated;
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

    private NamedTextColor nameColor(CustomItem item) {
        return switch (item) {
            case EMBER_SHARD, EMBERSTEEL_INGOT, EMBERSTEEL_PICKAXE,
                    EMBERSTEEL_SLAG, EMBERSTEEL_SLAG_PICKAXE,
                    GOLD_SLAG, GOLD_SLAG_PICKAXE, BOW_DRILL -> NamedTextColor.GOLD;
            case SOUL_CRYSTAL, SOULSTEEL_INGOT, SOULSTEEL_PICKAXE,
                    MITHRIL_SHARD, MITHRIL_INGOT, MITHRIL_PICKAXE,
                    MITHRIL_ORE, DEEPSLATE_MITHRIL_ORE -> NamedTextColor.AQUA;
            case SOULSTEEL_SLAG, SOULSTEEL_SLAG_PICKAXE,
                    MITHRIL_SLAG_PICKAXE, JUTE_CORD -> NamedTextColor.DARK_AQUA;
            case COPPER_SLAG, COPPER_SLAG_PICKAXE -> NamedTextColor.RED;
            case NETHERITE_SLAG, NETHERITE_SLAG_PICKAXE -> NamedTextColor.DARK_PURPLE;
            case IRON_SLAG, IRON_SLAG_PICKAXE, CHARCOAL_BLOCK -> NamedTextColor.DARK_GRAY;
            default -> NamedTextColor.WHITE;
        };
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

