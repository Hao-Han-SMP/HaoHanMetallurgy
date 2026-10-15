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
        if (item == null) return false;
        return switch (item) {
            case BORAX_ORE, MITHRIL_ORE, DEEPSLATE_BORAX_ORE, DEEPSLATE_MITHRIL_ORE, CHARCOAL_BLOCK -> true;
            default -> false;
        };
    }

    public static Material carrierFor(CustomItem item) {
        return isManagedBlock(item) ? Material.NOTE_BLOCK : null;
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
        int variation = variationFor(item);
        if (variation < 0) throw new IllegalArgumentException("Not a managed custom block: " + item);
        NoteBlock data = (NoteBlock) Bukkit.createBlockData(Material.NOTE_BLOCK);
        data.setInstrument(Instrument.FLUTE);
        data.setNote(new Note(variation - 100));
        data.setPowered(false);
        return data;
    }

    public static boolean matches(Block block, CustomItem item) {
        if (block == null || item == null) return false;
        try {
            var core = vn.haohan.itemcore.api.HaoHanItemCore.get();
            if (core != null && core.getCustomBlockService() != null) {
                String id = core.getCustomBlockService().getCustomBlockId(block);
                if (id != null) {
                    return id.equals("haohanmetallurgy:" + item.getId()) || id.equals(item.getId());
                }
            }
        } catch (Throwable ignored) {}
        return matches(block.getBlockData(), item);
    }

    public static boolean matches(BlockData blockData, CustomItem item) {
        if (blockData == null || item == null) return false;
        int targetVar = variationFor(item);
        if (targetVar < 0) return false;
        if (!(blockData instanceof NoteBlock data)) return false;
        if (data.getInstrument() != Instrument.FLUTE) return false;
        if (data.isPowered()) return false;
        return data.getNote() != null && data.getNote().getId() == (targetVar - 100);
    }

    public static String blockDataStringFor(CustomItem item) {
        return null;
    }

    public static CustomItem getCustomBlock(Block block) {
        if (block == null) return null;
        try {
            var core = vn.haohan.itemcore.api.HaoHanItemCore.get();
            if (core != null && core.getCustomBlockService() != null) {
                String id = core.getCustomBlockService().getCustomBlockId(block);
                if (id != null) {
                    if (id.startsWith("haohanmetallurgy:")) {
                        id = id.substring("haohanmetallurgy:".length());
                    }
                    var customOpt = CustomItem.getById(id);
                    if (customOpt.isPresent()) return customOpt.get();
                }
            }
        } catch (Throwable ignored) {}
        return getCustomBlock(block.getBlockData());
    }

    public static CustomItem getCustomBlock(BlockData blockData) {
        if (!(blockData instanceof NoteBlock data)) return null;
        if (data.getInstrument() != Instrument.FLUTE) return null;
        if (data.isPowered()) return null;
        if (data.getNote() == null) return null;
        return switch (data.getNote().getId()) {
            case 0 -> CustomItem.BORAX_ORE;
            case 1 -> CustomItem.MITHRIL_ORE;
            case 2 -> CustomItem.DEEPSLATE_BORAX_ORE;
            case 3 -> CustomItem.DEEPSLATE_MITHRIL_ORE;
            case 4 -> CustomItem.CHARCOAL_BLOCK;
            default -> null;
        };
    }

    public static int variationFor(CustomItem item) {
        if (item == null) return -1;
        return switch (item) {
            case BORAX_ORE -> 100;
            case MITHRIL_ORE -> 101;
            case DEEPSLATE_BORAX_ORE -> 102;
            case DEEPSLATE_MITHRIL_ORE -> 103;
            case CHARCOAL_BLOCK -> 104;
            default -> -1;
        };
    }

    public static CustomItem fromVariation(int variation) {
        return switch (variation) {
            case 100 -> CustomItem.BORAX_ORE;
            case 101 -> CustomItem.MITHRIL_ORE;
            case 102 -> CustomItem.DEEPSLATE_BORAX_ORE;
            case 103 -> CustomItem.DEEPSLATE_MITHRIL_ORE;
            case 104 -> CustomItem.CHARCOAL_BLOCK;
            default -> null;
        };
    }
}
