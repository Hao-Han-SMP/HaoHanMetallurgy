package vn.haohan.metallurgy.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomOreBlockTest {
    @Test
    void mapsEveryCustomOreToItsCarrier() {
        assertEquals(org.bukkit.Material.NOTE_BLOCK, CustomOreBlock.carrierFor(CustomItem.BORAX_ORE));
        assertEquals(org.bukkit.Material.NOTE_BLOCK, CustomOreBlock.carrierFor(CustomItem.DEEPSLATE_BORAX_ORE));
        assertEquals(org.bukkit.Material.NOTE_BLOCK, CustomOreBlock.carrierFor(CustomItem.MITHRIL_ORE));
        assertEquals(org.bukkit.Material.NOTE_BLOCK, CustomOreBlock.carrierFor(CustomItem.DEEPSLATE_MITHRIL_ORE));
        assertEquals(org.bukkit.Material.STONE, CustomOreBlock.legacyCarrierFor(CustomItem.BORAX_ORE));
        assertEquals(org.bukkit.Material.DEEPSLATE,
                CustomOreBlock.legacyCarrierFor(CustomItem.DEEPSLATE_MITHRIL_ORE));
    }

    @Test
    void rejectsNonOreItems() {
        assertEquals(null, CustomOreBlock.carrierFor(CustomItem.RAW_BORAX));
        assertEquals(null, CustomOreBlock.carrierFor(null));
    }

    @Test
    void charcoalBlockUsesAManagedNoteBlockCarrier() {
        assertEquals(org.bukkit.Material.NOTE_BLOCK, CustomOreBlock.carrierFor(CustomItem.CHARCOAL_BLOCK));
        assertEquals(org.bukkit.Material.COAL_BLOCK, CustomOreBlock.legacyCarrierFor(CustomItem.CHARCOAL_BLOCK));
        assertTrue(CustomOreBlock.isManagedBlock(CustomItem.CHARCOAL_BLOCK));
        assertFalse(CustomOreBlock.isOre(CustomItem.CHARCOAL_BLOCK));
    }

    @Test
    void generatesCorrectCustomBlockDataStrings() {
        assertEquals("minecraft:note_block[instrument=custom_head,note=20,powered=false]",
                CustomOreBlock.blockDataStringFor(CustomItem.BORAX_ORE));
        assertEquals("minecraft:note_block[instrument=custom_head,note=21,powered=false]",
                CustomOreBlock.blockDataStringFor(CustomItem.MITHRIL_ORE));
        assertEquals("minecraft:note_block[instrument=custom_head,note=22,powered=false]",
                CustomOreBlock.blockDataStringFor(CustomItem.DEEPSLATE_BORAX_ORE));
        assertEquals("minecraft:note_block[instrument=custom_head,note=23,powered=false]",
                CustomOreBlock.blockDataStringFor(CustomItem.DEEPSLATE_MITHRIL_ORE));
        assertEquals("minecraft:note_block[instrument=custom_head,note=24,powered=false]",
                CustomOreBlock.blockDataStringFor(CustomItem.CHARCOAL_BLOCK));
        assertEquals(null, CustomOreBlock.blockDataStringFor(CustomItem.RAW_BORAX));
    }
}
