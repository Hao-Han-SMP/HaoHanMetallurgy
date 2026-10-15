package vn.haohan.metallurgy.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.block.AlternativeCustomBlock;
import vn.haohan.itemcore.api.block.BlockDropProvider;
import vn.haohan.itemcore.api.block.CustomBlockDefinition;
import vn.haohan.itemcore.api.block.CustomBlockModel;
import vn.haohan.itemcore.api.block.CustomBlockRegistry;
import vn.haohan.itemcore.api.block.CustomBlockSoundGroup;
import vn.haohan.itemcore.api.block.NativeCustomBlock;
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
        registerVanillaPickaxes(core.getItemRegistry());
        registerVanillaOverwrites(core, plugin);

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

            CustomItemStats stats = (plugin != null && plugin.getConfigManager() != null)
                    ? plugin.getConfigManager().getCustomItemStats(item)
                    : new CustomItemStats(1, 100, 1.0, 1.0, 1.0, 0.0);

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
                float hardness = getHardnessFor(item, plugin);
                String dropId = getDropItemIdFor(item);
                int variation = CustomOreBlock.variationFor(item);

                definition.property("custom_block_id", id);
                definition.property("custom_block_model", "haohan:" + item.getId());
                definition.property("custom_block_drop", dropId);
                definition.property("custom_block_hardness", hardness);
                definition.property("custom_block_tool_type", "PICKAXE");
                definition.property("hide_additional_tooltip", true);
                if (variation >= 0) {
                    definition.property("custom_variation", variation);
                }

                if (customBlockService != null) {
                    registerCustomBlock(customBlockService, item, id, dropId, hardness, variation);
                }
            }

            if (item.getCustomModelData() > 0) {
                definition.customModelData(item.getCustomModelData());
            }

            if (stats.maxDamage() > 0) {
                definition.property("max_damage", stats.maxDamage());
            }
            if (stats.attackDamage() != 0.0) {
                definition.attribute(org.bukkit.NamespacedKey.minecraft("generic.attack_damage"), stats.attackDamage());
            }
            if (stats.attackSpeed() != 0.0) {
                definition.attribute(org.bukkit.NamespacedKey.minecraft("generic.attack_speed"), stats.attackSpeed());
            }
            if (stats.miningEfficiency() != 0.0) {
                definition.attribute(org.bukkit.NamespacedKey.minecraft("player.mining_efficiency"), stats.miningEfficiency());
            }
            if (stats.blockBreakSpeed() != 0.0) {
                definition.attribute(org.bukkit.NamespacedKey.minecraft("player.block_break_speed"), stats.blockBreakSpeed());
            }

            registry.register(definition.build());
        }
        if (plugin != null && plugin.getLogger() != null) {
            plugin.getLogger().info("Registered " + CustomItem.values().length + " Metallurgy items in HaoHanItemCore.");
        }
    }

    private static void registerToolTiers(HaoHanItemCore core) {
        var tierRegistry = core.getToolTierRegistry();
        if (tierRegistry == null) return;

        // Standard vanilla tiers adjusted to Metallurgy progression
        tierRegistry.register("wood", 1, 2.0f);
        tierRegistry.register("stone", 2, 4.0f);
        tierRegistry.register("gold", 2, 12.0f);
        tierRegistry.register("copper", 3, 5.0f);
        tierRegistry.register("iron", 4, 6.0f);
        tierRegistry.register("embersteel_slag", 4, 6.0f);
        tierRegistry.register("embersteel", 5, 7.0f);
        tierRegistry.register("diamond", 6, 8.0f);
        tierRegistry.register("mithril_slag", 6, 8.0f);
        tierRegistry.register("mithril", 7, 10.0f);
        tierRegistry.register("soulsteel_slag", 7, 9.0f);
        tierRegistry.register("soulsteel", 8, 11.0f);
        tierRegistry.register("netherite_slag", 8, 9.5f);
        tierRegistry.register("netherite", 9, 9.0f);
    }

    private static void registerVanillaPickaxes(vn.haohan.itemcore.api.item.ItemRegistry registry) {
        if (registry == null) return;

        registerVanillaPickaxe(registry, Material.WOODEN_PICKAXE, "§fWooden Pickaxe", 1, "wood", 2.0f);
        registerVanillaPickaxe(registry, Material.STONE_PICKAXE, "§7Stone Pickaxe", 2, "stone", 4.0f);
        registerVanillaPickaxe(registry, Material.GOLDEN_PICKAXE, "§eGolden Pickaxe", 2, "gold", 12.0f);
        Material copperPick = Material.getMaterial("COPPER_PICKAXE");
        if (copperPick != null) {
            registerVanillaPickaxe(registry, copperPick, "§6Copper Pickaxe", 3, "copper", 5.0f);
        }
        registerVanillaPickaxe(registry, Material.IRON_PICKAXE, "§fIron Pickaxe", 4, "iron", 6.0f);
        registerVanillaPickaxe(registry, Material.DIAMOND_PICKAXE, "§bDiamond Pickaxe", 6, "diamond", 8.0f);
        registerVanillaPickaxe(registry, Material.NETHERITE_PICKAXE, "§5Netherite Pickaxe", 9, "netherite", 9.0f);
    }

    private static void registerVanillaPickaxe(vn.haohan.itemcore.api.item.ItemRegistry registry,
                                               Material material, String displayName,
                                               int tierLevel, String tierName, float speed) {
        String id = "minecraft:" + material.name().toLowerCase(java.util.Locale.ROOT);
        if (registry.exists(id)) {
            registry.unregister(id);
        }

        String tierLore = getVanillaTierLore(tierLevel, tierName);

        ItemDefinition definition = ItemDefinition.builder(id)
                .material(material)
                .displayName(displayName)
                .lore(java.util.List.of(tierLore, "§7Vật phẩm game gốc đã được thiết lập thông số."))
                .maxStackSize(1)
                .type(ItemType.TOOL)
                .toolType(ToolType.PICKAXE)
                .property("tool_type", "PICKAXE")
                .component(new TierComponent(tierName, tierLevel))
                .tier(tierName, tierLevel)
                .miningSpeed(speed)
                .build();

        registry.register(definition);
    }

    private static String getVanillaTierLore(int tier, String name) {
        return switch (tier) {
            case 1 -> "§8Tier: §c1 (Wooden)";
            case 2 -> "§8Tier: §c2 (" + (name.equalsIgnoreCase("gold") ? "Gold" : "Stone") + ")";
            case 3 -> "§8Tier: §c3 (Copper)";
            case 4 -> "§8Tier: §c4 (Iron)";
            case 5 -> "§8Tier: §c5 (Embersteel)";
            case 6 -> "§8Tier: §c6 (Diamond)";
            case 7 -> "§8Tier: §c7 (Mithril)";
            case 8 -> "§8Tier: §c8 (Soulsteel)";
            case 9 -> "§8Tier: §c9 (Netherite)";
            default -> "§8Tier: §c" + tier;
        };
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
        if (plugin == null || plugin.getConfigManager() == null) {
            return switch (item) {
                case BORAX_ORE -> 3.0f;
                case DEEPSLATE_BORAX_ORE -> 4.5f;
                case MITHRIL_ORE -> 4.0f;
                case DEEPSLATE_MITHRIL_ORE -> 6.0f;
                case CHARCOAL_BLOCK -> 2.0f;
                default -> 3.0f;
            };
        }
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
                                            CustomItem item, String id,
                                            String dropItemId, float hardness,
                                            int variation) {
        var cbRegistry = service.getRegistry();
        if (cbRegistry == null) return;

        org.bukkit.inventory.ItemStack itemStack = null;
        if (HaoHanItemCore.get() != null && HaoHanItemCore.get().getItemFactory() != null) {
            try {
                itemStack = HaoHanItemCore.get().getItemFactory().create(id);
            } catch (Throwable ignored) {}
        }
        if (itemStack == null) {
            try {
                itemStack = new org.bukkit.inventory.ItemStack(item.getMaterial() != null ? item.getMaterial() : Material.NOTE_BLOCK);
                var meta = itemStack.getItemMeta();
                if (meta != null) {
                    try {
                        meta.setItemModel(org.bukkit.NamespacedKey.fromString("haohan:" + item.getId()));
                    } catch (Throwable ignored) {}
                    itemStack.setItemMeta(meta);
                }
            } catch (Throwable ignored) {}
        }
        CustomBlockModel model = CustomBlockModel.of(itemStack);

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

        // Required ToolTier resolved by name from the registry so levels follow the Metallurgy scale:
        // Borax needs stone (2), Mithril needs diamond (6).
        var tierRegistry = HaoHanItemCore.get() != null ? HaoHanItemCore.get().getToolTierRegistry() : null;
        boolean mithril = item == CustomItem.MITHRIL_ORE || item == CustomItem.DEEPSLATE_MITHRIL_ORE;
        ToolTier requiredTier;
        if (mithril) {
            requiredTier = tierRegistry != null ? tierRegistry.getOrDefault("diamond", ToolTier.DIAMOND) : ToolTier.DIAMOND;
        } else if (CustomOreBlock.isOre(item)) {
            requiredTier = tierRegistry != null ? tierRegistry.getOrDefault("stone", ToolTier.STONE) : ToolTier.STONE;
        } else {
            requiredTier = tierRegistry != null ? tierRegistry.getOrDefault("wood", ToolTier.WOOD) : ToolTier.WOOD;
        }

        CustomBlockSoundGroup soundGroup = (item == CustomItem.DEEPSLATE_BORAX_ORE || item == CustomItem.DEEPSLATE_MITHRIL_ORE)
                ? CustomBlockSoundGroup.builder()
                    .breakSound("minecraft:block.deepslate.break", 1.0f, 0.8f)
                    .stepSound("minecraft:block.deepslate.step", 1.0f, 1.0f)
                    .placeSound("minecraft:block.deepslate.place", 1.0f, 0.8f)
                    .hitSound("minecraft:block.deepslate.hit", 1.0f, 0.5f)
                    .fallSound("minecraft:block.deepslate.fall", 1.0f, 1.0f)
                    .build()
                : CustomBlockSoundGroup.STONE;

        CustomBlockDefinition.AbstractBuilder<?, ?> blockBuilder;
        if (variation >= 0) {
            blockBuilder = NativeCustomBlock.builder(id).customVariation(variation);
        } else {
            blockBuilder = AlternativeCustomBlock.builder(id);
        }

        CustomBlockDefinition def = blockBuilder
                .model(model)
                .hardness(hardness)
                .mineableWith(ToolType.PICKAXE, requiredTier)
                .miningCondition(MiningCondition.requiresTool(ToolType.PICKAXE, requiredTier))
                .dropProvider(dropBuilder.build())
                .dropItemId(dropItemId)
                .soundGroup(soundGroup)
                .build();

        cbRegistry.register(def);
    }

    private static void registerVanillaOverwrites(HaoHanItemCore core, HaoHanMetallurgy plugin) {
        var customBlockService = core.getCustomBlockService();
        if (customBlockService == null || customBlockService.getRegistry() == null) return;
        var cbRegistry = customBlockService.getRegistry();
        var tierRegistry = core.getToolTierRegistry();

        ToolTier tierWood = tierRegistry.getOrDefault("wood", ToolTier.WOOD);
        ToolTier tierStone = tierRegistry.getOrDefault("stone", ToolTier.STONE);
        ToolTier tierCopper = tierRegistry.getOrDefault("copper", ToolTier.of("copper", 3, 5.0f));
        ToolTier tierEmbersteel = tierRegistry.getOrDefault("embersteel", ToolTier.of("embersteel", 5, 7.0f));
        ToolTier tierMithril = tierRegistry.getOrDefault("mithril", ToolTier.of("mithril", 7, 10.0f));
        ToolTier tierSoulsteel = tierRegistry.getOrDefault("soulsteel", ToolTier.of("soulsteel", 8, 11.0f));

        // 1. Stone (Level 1 - Wood)
        registerVanillaBlock(cbRegistry, Material.STONE, 1.5f, tierWood);

        // 2. Copper & Iron ores / raw blocks (Level 2 - Stone)
        registerVanillaBlock(cbRegistry, Material.COPPER_ORE, 3.0f, tierStone);
        registerVanillaBlock(cbRegistry, Material.DEEPSLATE_COPPER_ORE, 4.5f, tierStone);
        registerVanillaBlock(cbRegistry, Material.RAW_COPPER_BLOCK, 5.0f, tierStone);
        registerVanillaBlock(cbRegistry, Material.IRON_ORE, 3.0f, tierStone);
        registerVanillaBlock(cbRegistry, Material.DEEPSLATE_IRON_ORE, 4.5f, tierStone);
        registerVanillaBlock(cbRegistry, Material.RAW_IRON_BLOCK, 5.0f, tierStone);

        // 3. Coal & Lapis (Level 3 - Copper)
        registerVanillaBlock(cbRegistry, Material.COAL_ORE, 3.0f, tierCopper);
        registerVanillaBlock(cbRegistry, Material.DEEPSLATE_COAL_ORE, 4.5f, tierCopper);
        registerVanillaBlock(cbRegistry, Material.LAPIS_ORE, 3.0f, tierCopper);
        registerVanillaBlock(cbRegistry, Material.DEEPSLATE_LAPIS_ORE, 4.5f, tierCopper);

        // 4. Diamond ores (Level 5 - Embersteel pickaxe required)
        registerVanillaBlock(cbRegistry, Material.DIAMOND_ORE, 3.0f, tierEmbersteel);
        registerVanillaBlock(cbRegistry, Material.DEEPSLATE_DIAMOND_ORE, 4.5f, tierEmbersteel);

        // 5. Obsidian (Level 7 - Mithril)
        registerVanillaBlock(cbRegistry, Material.OBSIDIAN, 50.0f, tierMithril);
        registerVanillaBlock(cbRegistry, Material.CRYING_OBSIDIAN, 50.0f, tierMithril);

        // 6. Ancient Debris & Netherite block (Level 8 - Soulsteel)
        registerVanillaBlock(cbRegistry, Material.ANCIENT_DEBRIS, 30.0f, tierSoulsteel);
        registerVanillaBlock(cbRegistry, Material.NETHERITE_BLOCK, 50.0f, tierSoulsteel);
    }

    private static void registerVanillaBlock(CustomBlockRegistry registry, Material material, float hardness, ToolTier requiredTier) {
        String id = "minecraft:" + material.name().toLowerCase();
        if (registry.has(id)) {
            return;
        }

        CustomBlockSoundGroup soundGroup = material.name().startsWith("DEEPSLATE")
                ? CustomBlockSoundGroup.builder()
                    .breakSound("minecraft:block.deepslate.break", 1.0f, 0.8f)
                    .stepSound("minecraft:block.deepslate.step", 1.0f, 1.0f)
                    .placeSound("minecraft:block.deepslate.place", 1.0f, 0.8f)
                    .hitSound("minecraft:block.deepslate.hit", 1.0f, 0.5f)
                    .fallSound("minecraft:block.deepslate.fall", 1.0f, 1.0f)
                    .build()
                : CustomBlockSoundGroup.STONE;

        CustomBlockDefinition def = CustomBlockDefinition.builder(id)
                .baseBlock(material)
                .hardness(hardness)
                .mineableWith(ToolType.PICKAXE, requiredTier)
                .miningCondition(MiningCondition.requiresTool(ToolType.PICKAXE, requiredTier))
                .soundGroup(soundGroup)
                .build();

        registry.register(def);
    }
}
