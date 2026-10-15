package vn.haohan.metallurgy.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(null, CustomOreBlock.blockDataStringFor(CustomItem.BORAX_ORE));
        assertEquals(null, CustomOreBlock.blockDataStringFor(CustomItem.MITHRIL_ORE));
        assertEquals(null, CustomOreBlock.blockDataStringFor(CustomItem.DEEPSLATE_BORAX_ORE));
        assertEquals(null, CustomOreBlock.blockDataStringFor(CustomItem.DEEPSLATE_MITHRIL_ORE));
        assertEquals(null, CustomOreBlock.blockDataStringFor(CustomItem.CHARCOAL_BLOCK));
        assertEquals(null, CustomOreBlock.blockDataStringFor(CustomItem.RAW_BORAX));
    }

    @Test
    void mapsVariationsCorrectly() {
        assertEquals(100, CustomOreBlock.variationFor(CustomItem.BORAX_ORE));
        assertEquals(101, CustomOreBlock.variationFor(CustomItem.MITHRIL_ORE));
        assertEquals(102, CustomOreBlock.variationFor(CustomItem.DEEPSLATE_BORAX_ORE));
        assertEquals(103, CustomOreBlock.variationFor(CustomItem.DEEPSLATE_MITHRIL_ORE));
        assertEquals(104, CustomOreBlock.variationFor(CustomItem.CHARCOAL_BLOCK));
        assertEquals(-1, CustomOreBlock.variationFor(CustomItem.RAW_BORAX));

        assertEquals(CustomItem.BORAX_ORE, CustomOreBlock.fromVariation(100));
        assertEquals(CustomItem.MITHRIL_ORE, CustomOreBlock.fromVariation(101));
        assertEquals(CustomItem.DEEPSLATE_BORAX_ORE, CustomOreBlock.fromVariation(102));
        assertEquals(CustomItem.DEEPSLATE_MITHRIL_ORE, CustomOreBlock.fromVariation(103));
        assertEquals(CustomItem.CHARCOAL_BLOCK, CustomOreBlock.fromVariation(104));
        assertEquals(null, CustomOreBlock.fromVariation(99));
    }

    @Test
    void matchesAndIdentifiesNoteBlockVariations() {
        org.bukkit.block.data.type.NoteBlock noteBlock = org.mockito.Mockito.mock(org.bukkit.block.data.type.NoteBlock.class);
        org.mockito.Mockito.when(noteBlock.getInstrument()).thenReturn(org.bukkit.Instrument.FLUTE);
        org.mockito.Mockito.when(noteBlock.isPowered()).thenReturn(false);

        // Note 0 -> BORAX_ORE (100)
        org.mockito.Mockito.when(noteBlock.getNote()).thenReturn(new org.bukkit.Note(0));
        assertTrue(CustomOreBlock.matches(noteBlock, CustomItem.BORAX_ORE));
        assertFalse(CustomOreBlock.matches(noteBlock, CustomItem.MITHRIL_ORE));
        assertEquals(CustomItem.BORAX_ORE, CustomOreBlock.getCustomBlock(noteBlock));

        // Note 1 -> MITHRIL_ORE (101)
        org.mockito.Mockito.when(noteBlock.getNote()).thenReturn(new org.bukkit.Note(1));
        assertTrue(CustomOreBlock.matches(noteBlock, CustomItem.MITHRIL_ORE));
        assertEquals(CustomItem.MITHRIL_ORE, CustomOreBlock.getCustomBlock(noteBlock));

        // Note 2 -> DEEPSLATE_BORAX_ORE (102)
        org.mockito.Mockito.when(noteBlock.getNote()).thenReturn(new org.bukkit.Note(2));
        assertTrue(CustomOreBlock.matches(noteBlock, CustomItem.DEEPSLATE_BORAX_ORE));
        assertEquals(CustomItem.DEEPSLATE_BORAX_ORE, CustomOreBlock.getCustomBlock(noteBlock));

        // Note 3 -> DEEPSLATE_MITHRIL_ORE (103)
        org.mockito.Mockito.when(noteBlock.getNote()).thenReturn(new org.bukkit.Note(3));
        assertTrue(CustomOreBlock.matches(noteBlock, CustomItem.DEEPSLATE_MITHRIL_ORE));
        assertEquals(CustomItem.DEEPSLATE_MITHRIL_ORE, CustomOreBlock.getCustomBlock(noteBlock));

        // Note 4 -> CHARCOAL_BLOCK (104)
        org.mockito.Mockito.when(noteBlock.getNote()).thenReturn(new org.bukkit.Note(4));
        assertTrue(CustomOreBlock.matches(noteBlock, CustomItem.CHARCOAL_BLOCK));
        assertEquals(CustomItem.CHARCOAL_BLOCK, CustomOreBlock.getCustomBlock(noteBlock));

        // Wrong instrument or powered
        org.mockito.Mockito.when(noteBlock.isPowered()).thenReturn(true);
        assertFalse(CustomOreBlock.matches(noteBlock, CustomItem.CHARCOAL_BLOCK));
        assertNull(CustomOreBlock.getCustomBlock(noteBlock));
    }
}
