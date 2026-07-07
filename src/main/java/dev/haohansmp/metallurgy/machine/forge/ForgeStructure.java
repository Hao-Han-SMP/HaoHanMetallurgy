package dev.haohansmp.metallurgy.machine.forge;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Định nghĩa cấu trúc multiblock của Ancient Forge mới (3x2x4).
 */
public final class ForgeStructure {

    /** Block mà player right-click để mở/kích hoạt forge. */
    public static final Material CONTROLLER_MATERIAL = Material.BLAST_FURNACE;

    /**
     * Một block bắt buộc trong cấu trúc, tính tương đối so với controller và góc xoay.
     */
    public record BlockOffset(int dx, int dy, int dz, Material material) {
        public boolean matchesAt(Location controllerLoc, int rotation) {
            int rx = dx;
            int rz = dz;
            if (rotation == 90) {
                rx = -dz;
                rz = dx;
            } else if (rotation == 180) {
                rx = -dx;
                rz = -dz;
            } else if (rotation == 270) {
                rx = dz;
                rz = -dx;
            }
            Block block = controllerLoc.getWorld().getBlockAt(
                controllerLoc.getBlockX() + rx,
                controllerLoc.getBlockY() + dy,
                controllerLoc.getBlockZ() + rz
            );
            return block.getType() == material;
        }
    }

    /** Danh sách tất cả blocks bắt buộc (không kể controller). */
    public static final List<BlockOffset> REQUIRED_BLOCKS = List.of(
        // Base (Y = -1): 9 Mud Bricks
        new BlockOffset(-1, -1, 0, Material.MUD_BRICKS),
        new BlockOffset( 0, -1, 0, Material.MUD_BRICKS),
        new BlockOffset( 1, -1, 0, Material.MUD_BRICKS),
        new BlockOffset(-1, -1, 1, Material.MUD_BRICKS),
        new BlockOffset( 0, -1, 1, Material.MUD_BRICKS),
        new BlockOffset( 1, -1, 1, Material.MUD_BRICKS),
        new BlockOffset(-1, -1, 2, Material.MUD_BRICKS),
        new BlockOffset( 0, -1, 2, Material.MUD_BRICKS),
        new BlockOffset( 1, -1, 2, Material.MUD_BRICKS),

        // Y = 0: 8 Cobblestones (Controller là Blast Furnace ở 0,0,0)
        new BlockOffset(-1,  0, 0, Material.COBBLESTONE),
        new BlockOffset( 1,  0, 0, Material.COBBLESTONE),
        new BlockOffset(-1,  0, 1, Material.COBBLESTONE),
        new BlockOffset( 0,  0, 1, Material.COBBLESTONE),
        new BlockOffset( 1,  0, 1, Material.COBBLESTONE),
        new BlockOffset(-1,  0, 2, Material.COBBLESTONE),
        new BlockOffset( 0,  0, 2, Material.COBBLESTONE),
        new BlockOffset( 1,  0, 2, Material.COBBLESTONE),

        // Y = 1: Cauldron + 8 Cobblestones
        new BlockOffset( 0,  1, 0, Material.CAULDRON),
        new BlockOffset(-1,  1, 0, Material.COBBLESTONE),
        new BlockOffset( 1,  1, 0, Material.COBBLESTONE),
        new BlockOffset(-1,  1, 1, Material.COBBLESTONE),
        new BlockOffset( 0,  1, 1, Material.COBBLESTONE),
        new BlockOffset( 1,  1, 1, Material.COBBLESTONE),
        new BlockOffset(-1,  1, 2, Material.COBBLESTONE),
        new BlockOffset( 0,  1, 2, Material.COBBLESTONE),
        new BlockOffset( 1,  1, 2, Material.COBBLESTONE),

        // Y = 2: 9 Cobblestones
        new BlockOffset(-1,  2, 0, Material.COBBLESTONE),
        new BlockOffset( 0,  2, 0, Material.COBBLESTONE),
        new BlockOffset( 1,  2, 0, Material.COBBLESTONE),
        new BlockOffset(-1,  2, 1, Material.COBBLESTONE),
        new BlockOffset( 0,  2, 1, Material.COBBLESTONE),
        new BlockOffset( 1,  2, 1, Material.COBBLESTONE),
        new BlockOffset(-1,  2, 2, Material.COBBLESTONE),
        new BlockOffset( 0,  2, 2, Material.COBBLESTONE),
        new BlockOffset( 1,  2, 2, Material.COBBLESTONE)
    );

    // Prevent instantiation
    private ForgeStructure() {}

    /**
     * Xác định góc xoay hợp lệ của cấu trúc (0, 90, 180, 270).
     * @return góc xoay, hoặc -1 nếu không hợp lệ.
     */
    public static int getValidRotation(Location controllerLoc) {
        if (controllerLoc.getBlock().getType() != CONTROLLER_MATERIAL) return -1;
        for (int rot : List.of(0, 90, 180, 270)) {
            boolean ok = true;
            for (BlockOffset offset : REQUIRED_BLOCKS) {
                if (!offset.matchesAt(controllerLoc, rot)) {
                    ok = false;
                    break;
                }
            }
            if (ok) return rot;
        }
        return -1;
    }

    /**
     * Kiểm tra cấu trúc có đầy đủ không tại vị trí controller.
     * @return true nếu cấu trúc đúng tại ít nhất 1 góc xoay.
     */
    public static boolean validate(Location controllerLoc) {
        return getValidRotation(controllerLoc) != -1;
    }

    /**
     * Lấy danh sách block bị thiếu của hướng xoay sát nhất.
     */
    public static List<String> getMissingBlocks(Location controllerLoc) {
        int bestRot = 0;
        int minMissing = Integer.MAX_VALUE;
        List<BlockOffset> bestMissingList = new ArrayList<>();

        for (int rot : List.of(0, 90, 180, 270)) {
            List<BlockOffset> missing = new ArrayList<>();
            for (BlockOffset offset : REQUIRED_BLOCKS) {
                if (!offset.matchesAt(controllerLoc, rot)) {
                    missing.add(offset);
                }
            }
            if (missing.size() < minMissing) {
                minMissing = missing.size();
                bestRot = rot;
                bestMissingList = missing;
            }
        }

        final int finalRot = bestRot;
        return bestMissingList.stream()
            .map(o -> {
                int rx = o.dx();
                int rz = o.dz();
                if (finalRot == 90) { rx = -o.dz(); rz = o.dx(); }
                else if (finalRot == 180) { rx = -o.dx(); rz = -o.dz(); }
                else if (finalRot == 270) { rx = o.dz(); rz = -o.dx(); }
                return String.format("§c✗ §7%s tại (%+d, %+d, %+d)",
                    o.material().name(), rx, o.dy(), rz);
            })
            .toList();
    }

    /**
     * Kiểm tra một block có phải là thành phần của cấu trúc forge không.
     */
    public static boolean isStructuralMaterial(Material material) {
        return material == CONTROLLER_MATERIAL
            || material == Material.MUD_BRICKS
            || material == Material.COBBLESTONE
            || material == Material.CAULDRON;
    }

    /** Mô tả cấu trúc cho player. */
    public static String getDescription() {
        return """
                §6§lCấu trúc Lò Rèn Cổ Đại mới (3x2x4):
                §7• §eBlast Furnace (Core) §7tại Y=0
                §7• §e6× Mud Bricks §7làm đế tại Y=-1
                §7• §eCauldron §7ngay trên Blast Furnace tại Y=1
                §7• §e16× Cobblestone §7bọc xung quanh
                §7Shift + Right-click Blast Furnace để xem gợi ý xây lò.""";
    }
}
