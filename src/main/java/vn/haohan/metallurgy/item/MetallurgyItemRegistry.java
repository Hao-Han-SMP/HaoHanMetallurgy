package vn.haohan.metallurgy.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.block.BlockDropProvider;
import vn.haohan.itemcore.api.block.CustomBlockDefinition;
import vn.haohan.itemcore.api.block.CustomBlockModel;
import vn.haohan.itemcore.api.block.CustomBlockSoundGroup;
import vn.haohan.itemcore.api.block.MiningCondition;
import vn.haohan.itemcore.api.block.ToolTier;
import vn.haohan.itemcore.api.block.ToolType;
import vn.haohan.itemcore.api.item.ItemDefinition;
import vn.haohan.itemcore.api.item.ItemType;
import vn.haohan.itemcore.api.item.TierComponent;
import vn.haohan.metallurgy.HaoHanMetallurgy;
import vn.haohan.metallurgy.config.CustomItemStats;

import java.util.Map;

/** Registers Metallurgy definitions, Tool Tiers, and Custom Blocks in the shared HaoHanItemCore registry. */
public final class MetallurgyItemRegistry {

    public static final String NAMESPACE = "haohanmetallurgy";

    private MetallurgyItemRegistry() { }

    public static String id(CustomItem item) {
        return NAMESPACE + ":" + item.getId();
    }

    public static void registerAll(HaoHanMetallurgy plugin) {
        var core = HaoHanItemCore.get();
        if (core == null) return;

        registerToolTiers(core);

        var registry = core.getItemRegistry();
        var customBlockService = core.getCustomBlockService();

        for (CustomItem item : CustomItem.values()) {
            String id = id(item);
            if (registry.exists(id)) {
                registry.unregister(id);
            }

            boolean isPickaxe = item.getMaterial().name().endsWith("PICKAXE") || item.getId().endsWith("_pickaxe");
            boolean isBlock = CustomOreBlock.isManagedBlock(item);

            ItemDefinition.Builder definition = ItemDefinition.builder(id)
                    .material(item.getMaterial())
                    .displayName(item.getDisplayName())
                    .lore(item.getLore())
                    .model("haohan:" + item.getId())
                    .maxStackSize(isPickaxe ? 1 : 64)
                    .type(isBlock ? ItemType.BLOCK : (isPickaxe ? ItemType.TOOL : ItemType.MATERIAL))
                    .property("metallurgy_item_id", item.getId());

            CustomItemStats stats = plugin.getConfigManager().getCustomItemStats(item);

            if (isPickaxe) {
                definition.toolType(ToolType.PICKAXE);
                definition.property("tool_type", "PICKAXE");
                int tierLevel = stats.tier() > 0 ? stats.tier() : 1;
                String tierName = getTierName(item, tierLevel);
                definition.component(new TierComponent(tierName, tierLevel));
                definition.tier(tierName, tierLevel);
                if (stats.miningEfficiency() > 0) {
                    definition.miningSpeed((float) stats.miningEfficiency());
                }
            }

            if (isBlock) {
                String blockDataStr = CustomOreBlock.blockDataStringFor(item);
                float hardness = getHardnessFor(item, plugin);
                String dropId = getDropItemIdFor(item);

                definition.property("custom_block_id", id);
                if (blockDataStr != null) {
                    definition.property("custom_block_model", blockDataStr);
                    definition.property("custom_block_data", blockDataStr);
                }
                definition.property("custom_block_drop", dropId);
                definition.property("custom_block_hardness", hardness);
                definition.property("custom_block_tool_type", "PICKAXE");
                definition.property("hide_additional_tooltip", true);

                if (customBlockService != null) {
                    registerCustomBlock(customBlockService, item, id, blockDataStr, dropId, hardness);
                }
            }

            if (item.getCustomModelData() > 0) {
                definition.customModelData(item.getCustomModelData());
            }

            definition.component(new MetallurgyStatsComponent(item, stats));
            registry.register(definition.build());
        }
        plugin.getLogger().info("Registered " + CustomItem.values().length + " Metallurgy items in HaoHanItemCore.");
    }

    private static void registerToolTiers(HaoHanItemCore core) {
        var tierRegistry = core.getToolTierRegistry();
        if (tierRegistry == null) return;

        // Register metallurgy's custom tool tiers into ItemCore's ToolTierRegistry
        tierRegistry.register("copper", 3, 5.0f);
        tierRegistry.register("embersteel_slag", 4, 6.0f);
        tierRegistry.register("embersteel", 5, 7.0f);
        tierRegistry.register("mithril_slag", 6, 8.0f);
        tierRegistry.register("mithril", 7, 10.0f);
        tierRegistry.register("soulsteel_slag", 7, 9.0f);
        tierRegistry.register("soulsteel", 8, 11.0f);
        tierRegistry.register("netherite_slag", 8, 9.5f);
    }

    private static String getTierName(CustomItem item, int level) {
        return switch (item) {
            case COPPER_SLAG_PICKAXE -> "copper";
            case IRON_SLAG_PICKAXE -> "copper";
            case EMBERSTEEL_SLAG_PICKAXE -> "embersteel_slag";
            case EMBERSTEEL_PICKAXE -> "embersteel";
            case MITHRIL_SLAG_PICKAXE -> "mithril_slag";
            case MITHRIL_PICKAXE -> "mithril";
            case SOULSTEEL_SLAG_PICKAXE -> "soulsteel_slag";
            case SOULSTEEL_PICKAXE -> "soulsteel";
            case NETHERITE_SLAG_PICKAXE -> "netherite_slag";
            case GOLD_SLAG_PICKAXE -> "gold";
            default -> "tier_" + level;
        };
    }

    private static float getHardnessFor(CustomItem item, HaoHanMetallurgy plugin) {
        return switch (item) {
            case BORAX_ORE -> (float) plugin.getConfigManager().getBoraxOreHardness();
            case DEEPSLATE_BORAX_ORE -> (float) plugin.getConfigManager().getDeepslateBoraxOreHardness();
            case MITHRIL_ORE -> (float) plugin.getConfigManager().getMithrilOreHardness();
            case DEEPSLATE_MITHRIL_ORE -> (float) plugin.getConfigManager().getDeepslateMithrilOreHardness();
            case CHARCOAL_BLOCK -> 2.0f;
            default -> 3.0f;
        };
    }

    private static String getDropItemIdFor(CustomItem item) {
        return switch (item) {
            case BORAX_ORE, DEEPSLATE_BORAX_ORE -> id(CustomItem.RAW_BORAX);
            case MITHRIL_ORE, DEEPSLATE_MITHRIL_ORE -> id(CustomItem.MITHRIL_SHARD);
            case CHARCOAL_BLOCK -> id(CustomItem.CHARCOAL_BLOCK);
            default -> id(item);
        };
    }

    private static void registerCustomBlock(vn.haohan.itemcore.api.block.CustomBlockService service,
                                            CustomItem item, String id, String blockDataStr,
                                            String dropItemId, float hardness) {
        var cbRegistry = service.getRegistry();
        if (cbRegistry == null) return;

        org.bukkit.block.data.BlockData blockData = null;
        if (blockDataStr != null) {
            try {
                blockData = Bukkit.createBlockData(blockDataStr);
            } catch (Throwable ignored) {}
        }
        if (blockData == null) {
            blockData = Bukkit.createBlockData(Material.STONE);
        }

        // Drop provider
        BlockDropProvider.Builder dropBuilder = BlockDropProvider.builder()
                .silkTouchDrop(id)
                .silkTouchDropsSelf(true);

        if (item == CustomItem.MITHRIL_ORE || item == CustomItem.DEEPSLATE_MITHRIL_ORE) {
            dropBuilder.defaultDrop(dropItemId, 1, 3).fortune(true);
        } else if (item == CustomItem.BORAX_ORE || item == CustomItem.DEEPSLATE_BORAX_ORE) {
            dropBuilder.defaultDrop(dropItemId, 1, 1).fortune(true);
        } else {
            dropBuilder.defaultDrop(dropItemId, 1);
        }

        // Required ToolTier: Borax needs Stone (level 2), Mithril needs Diamond (level 4 vanilla / 6 in metallurgy chain)
        ToolTier requiredTier = (item == CustomItem.MITHRIL_ORE || item == CustomItem.DEEPSLATE_MITHRIL_ORE)
                ? ToolTier.DIAMOND
                : (CustomOreBlock.isOre(item) ? ToolTier.STONE : ToolTier.WOOD);

        CustomBlockSoundGroup soundGroup = (item == CustomItem.DEEPSLATE_BORAX_ORE || item == CustomItem.DEEPSLATE_MITHRIL_ORE)
                ? CustomBlockSoundGroup.builder()
                    .breakSound("minecraft:block.deepslate.break", 1.0f, 0.8f)
                    .stepSound("minecraft:block.deepslate.step", 1.0f, 1.0f)
                    .placeSound("minecraft:block.deepslate.place", 1.0f, 0.8f)
                    .hitSound("minecraft:block.deepslate.hit", 1.0f, 0.5f)
                    .fallSound("minecraft:block.deepslate.fall", 1.0f, 1.0f)
                    .build()
                : CustomBlockSoundGroup.STONE;

        CustomBlockDefinition def = CustomBlockDefinition.builder(id)
                .model(new CustomBlockModel(blockData))
                .hardness(hardness)
                .mineableWith(ToolType.PICKAXE, requiredTier)
                .miningCondition(MiningCondition.requiresTool(ToolType.PICKAXE, requiredTier))
                .dropProvider(dropBuilder.build())
                .dropItemId(dropItemId)
                .soundGroup(soundGroup)
                .build();

        cbRegistry.register(def);
    }
}
