package vn.haohan.metallurgy.item;

import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.item.ItemDefinition;
import vn.haohan.itemcore.api.item.ItemType;
import vn.haohan.metallurgy.HaoHanMetallurgy;

/** Registers Metallurgy definitions in the shared HaoHanItemCore registry. */
final class MetallurgyItemRegistry {

    static final String NAMESPACE = "haohanmetallurgy";

    private MetallurgyItemRegistry() { }

    static String id(CustomItem item) {
        return NAMESPACE + ":" + item.getId();
    }

    static void registerAll(HaoHanMetallurgy plugin) {
        var registry = HaoHanItemCore.get().getItemRegistry();
        for (CustomItem item : CustomItem.values()) {
            String id = id(item);
            // Replace an older Metallurgy definition so upgrading from the
            // version without CMD immediately fixes the browser on reload.
            if (registry.exists(id)) {
                registry.unregister(id);
            }

            ItemDefinition.Builder definition = ItemDefinition.builder(id)
                    .material(item.getMaterial())
                    .displayName(item.getDisplayName())
                    .lore(item.getLore())
                    .model("minecraft:" + item.getMaterial().name().toLowerCase(java.util.Locale.ROOT))
                    .maxStackSize(item.getMaterial().name().endsWith("PICKAXE") ? 1 : 64)
                    .type(item.getMaterial().name().endsWith("PICKAXE") ? ItemType.TOOL : ItemType.MATERIAL)
                    // ItemCore retains the gameplay metadata and identity. ItemManager applies
                    // localized text and the configured gameplay stats to each generated stack.
                    .property("metallurgy_item_id", item.getId());

            if (CustomOreBlock.isManagedBlock(item)) {
                String blockData = CustomOreBlock.blockDataStringFor(item);
                if (blockData != null) {
                    definition.property("custom_block_data", blockData);
                    definition.property("hide_additional_tooltip", true);
                }
            }

            // Keep CMD in the definition so ItemCore's browser and recipe viewer
            // can render the same resource-pack variant as a normal Metallurgy stack.
            // The GUI removes only ItemCore's inferred model component afterward.
            if (item.getCustomModelData() > 0) {
                definition.customModelData(item.getCustomModelData());
            }

            definition.component(new MetallurgyStatsComponent(
                    item, plugin.getConfigManager().getCustomItemStats(item)));

            registry.register(definition.build());
        }
        plugin.getLogger().info("Registered " + CustomItem.values().length + " Metallurgy items in HaoHanItemCore.");
    }
}
