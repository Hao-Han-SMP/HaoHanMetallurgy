package vn.haohan.metallurgy.item;

import org.bukkit.Bukkit;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;

/** Shared physical carrier mapping for placeable custom ores. */
public final class CustomOreBlock {
    private CustomOreBlock() {}

    public static boolean isOre(CustomItem item) {
        if (item == null) return false;
        return switch (item) {
            case BORAX_ORE, MITHRIL_ORE, DEEPSLATE_BORAX_ORE, DEEPSLATE_MITHRIL_ORE -> true;
            default -> false;
        };
    }

    public static boolean isManagedBlock(CustomItem item) {
        return carrierFor(item) != null;
    }

    public static Material carrierFor(CustomItem item) {
        return noteFor(item) > 0 ? Material.NOTE_BLOCK : null;
    }

    public static Material legacyCarrierFor(CustomItem item) {
        if (item == null) return null;
        return switch (item) {
            case BORAX_ORE, MITHRIL_ORE -> Material.STONE;
            case DEEPSLATE_BORAX_ORE, DEEPSLATE_MITHRIL_ORE -> Material.DEEPSLATE;
            case CHARCOAL_BLOCK -> Material.COAL_BLOCK;
            default -> null;
        };
    }

    public static BlockData blockDataFor(CustomItem item) {
        int note = noteFor(item);
        if (note <= 0) throw new IllegalArgumentException("Not a managed custom block: " + item);
        NoteBlock data = (NoteBlock) Bukkit.createBlockData(Material.NOTE_BLOCK);
        data.setInstrument(Instrument.CUSTOM_HEAD);
        data.setNote(new Note(note));
        data.setPowered(false);
        return data;
    }

    public static boolean matches(Block block, CustomItem item) {
        return block != null && matches(block.getBlockData(), item);
    }

    public static boolean matches(BlockData blockData, CustomItem item) {
        if (!(blockData instanceof NoteBlock data)) return false;
        return data.getInstrument() == Instrument.CUSTOM_HEAD
                && data.getNote().getId() == noteFor(item);
    }

    public static String blockDataStringFor(CustomItem item) {
        int note = noteFor(item);
        if (note <= 0) return null;
        return "minecraft:note_block[instrument=custom_head,note=" + note + ",powered=false]";
    }

    public static CustomItem getCustomBlock(Block block) {
        if (block == null) return null;
        return getCustomBlock(block.getBlockData());
    }

    public static CustomItem getCustomBlock(BlockData blockData) {
        if (!(blockData instanceof NoteBlock data)) return null;
        if (data.getInstrument() != Instrument.CUSTOM_HEAD) return null;
        return switch (data.getNote().getId()) {
            case 20 -> CustomItem.BORAX_ORE;
            case 21 -> CustomItem.MITHRIL_ORE;
            case 22 -> CustomItem.DEEPSLATE_BORAX_ORE;
            case 23 -> CustomItem.DEEPSLATE_MITHRIL_ORE;
            case 24 -> CustomItem.CHARCOAL_BLOCK;
            default -> null;
        };
    }

    private static int noteFor(CustomItem item) {
        if (item == null) return -1;
        return switch (item) {
            case BORAX_ORE -> 20;
            case MITHRIL_ORE -> 21;
            case DEEPSLATE_BORAX_ORE -> 22;
            case DEEPSLATE_MITHRIL_ORE -> 23;
            case CHARCOAL_BLOCK -> 24;
            default -> -1;
        };
    }
}
