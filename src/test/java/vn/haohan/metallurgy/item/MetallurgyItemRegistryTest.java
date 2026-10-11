package vn.haohan.metallurgy.item;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.haohan.itemcore.api.HaoHanItemCore;
import vn.haohan.itemcore.api.block.ToolTier;
import vn.haohan.itemcore.api.block.ToolTierRegistry;
import vn.haohan.itemcore.api.item.ItemDefinition;
import vn.haohan.itemcore.api.item.TierComponent;
import vn.haohan.itemcore.internal.item.DefaultItemRegistry;

import static org.junit.jupiter.api.Assertions.*;

class MetallurgyItemRegistryTest {

    private DefaultItemRegistry itemRegistry;
    private ToolTierRegistry tierRegistry;

    @BeforeEach
    void setUp() {
        itemRegistry = new DefaultItemRegistry(null);
        tierRegistry = new ToolTierRegistry();
        HaoHanItemCore core = new HaoHanItemCore(
                itemRegistry, null, null, null, null, null, null, tierRegistry);
        HaoHanItemCore.setInstance(core);
    }

    @Test
    void testToolTierRegistrationAndOverrides() {
        MetallurgyItemRegistry.registerAll(null);

        // Verify metallurgy-adjusted vanilla tiers
        assertEquals(1, tierRegistry.get("wood").map(ToolTier::level).orElse(0));
        assertEquals(2, tierRegistry.get("stone").map(ToolTier::level).orElse(0));
        assertEquals(2, tierRegistry.get("gold").map(ToolTier::level).orElse(0));
        assertEquals(4, tierRegistry.get("iron").map(ToolTier::level).orElse(0));
        assertEquals(6, tierRegistry.get("diamond").map(ToolTier::level).orElse(0));
        assertEquals(9, tierRegistry.get("netherite").map(ToolTier::level).orElse(0));

        // Verify metallurgy custom tiers
        assertEquals(3, tierRegistry.get("copper").map(ToolTier::level).orElse(0));
        assertEquals(4, tierRegistry.get("embersteel_slag").map(ToolTier::level).orElse(0));
        assertEquals(5, tierRegistry.get("embersteel").map(ToolTier::level).orElse(0));
        assertEquals(6, tierRegistry.get("mithril_slag").map(ToolTier::level).orElse(0));
        assertEquals(7, tierRegistry.get("mithril").map(ToolTier::level).orElse(0));
        assertEquals(7, tierRegistry.get("soulsteel_slag").map(ToolTier::level).orElse(0));
        assertEquals(8, tierRegistry.get("soulsteel").map(ToolTier::level).orElse(0));
        assertEquals(8, tierRegistry.get("netherite_slag").map(ToolTier::level).orElse(0));
    }

    @Test
    void testVanillaPickaxesRegisteredAsOverwrites() {
        MetallurgyItemRegistry.registerAll(null);

        ItemDefinition diamondPick = itemRegistry.get("minecraft:diamond_pickaxe");
        assertNotNull(diamondPick, "diamond_pickaxe should be registered in ItemCore");
        assertTrue(diamondPick.isVanillaOverwrite());
        assertEquals(Material.DIAMOND_PICKAXE, diamondPick.getMaterial());
        assertEquals("§bDiamond Pickaxe", diamondPick.getDisplayName());
        
        TierComponent diamondTierComp = diamondPick.getComponents().stream()
                .filter(TierComponent.class::isInstance)
                .map(TierComponent.class::cast)
                .findFirst().orElse(null);
        assertNotNull(diamondTierComp);
        assertEquals(6, diamondTierComp.tier());
        assertEquals("diamond", diamondTierComp.tierName());
        assertTrue(diamondPick.getLore().contains("§8Tier: §c6 (Diamond)"));

        ItemDefinition ironPick = itemRegistry.get("minecraft:iron_pickaxe");
        assertNotNull(ironPick);
        assertTrue(ironPick.isVanillaOverwrite());
        TierComponent ironTierComp = ironPick.getComponents().stream()
                .filter(TierComponent.class::isInstance)
                .map(TierComponent.class::cast)
                .findFirst().orElse(null);
        assertNotNull(ironTierComp);
        assertEquals(4, ironTierComp.tier());

        ItemDefinition netheritePick = itemRegistry.get("minecraft:netherite_pickaxe");
        assertNotNull(netheritePick);
        assertTrue(netheritePick.isVanillaOverwrite());
        TierComponent netheriteTierComp = netheritePick.getComponents().stream()
                .filter(TierComponent.class::isInstance)
                .map(TierComponent.class::cast)
                .findFirst().orElse(null);
        assertNotNull(netheriteTierComp);
        assertEquals(9, netheriteTierComp.tier());
    }
}
