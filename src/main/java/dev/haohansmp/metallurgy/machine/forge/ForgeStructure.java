package dev.haohansmp.metallurgy.machine.forge;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.List;

/**
 * Định nghĩa cấu trúc multiblock của Ancient Forge.
 *
 * Layout (nhìn từ trên xuống):
 * <pre>
 *   [SB][SB][SB]        y+1: [  ][SCF][  ]
 *   [SB][BF][SB]              (Soul Campfire on top)
 *   [SB][SB][SB]
 *
 *   SB  = Stone Bricks
 *   BF  = Blast Furnace (CONTROLLER — player right-click vào đây)
 *   SCF = Soul Campfire
 * </pre>
 *
 * Tổng: 8 Stone Bricks + 1 Blast Furnace + 1 Soul Campfire = 10 blocks.
 */
public final class ForgeStructure {

    /** Block mà player right-click để mở/kích hoạt forge. */
    public static final Material CONTROLLER_MATERIAL = Material.BLAST_FURNACE;

    /**
     * Một block bắt buộc trong cấu trúc, tính tương đối so với controller.
     */
    public record BlockOffset(int dx, int dy, int dz, Material material) {
        public boolean matchesAt(Location controllerLoc) {
            Block block = controllerLoc.getWorld().getBlockAt(
                controllerLoc.getBlockX() + dx,
                controllerLoc.getBlockY() + dy,
                controllerLoc.getBlockZ() + dz
            );
            return block.getType() == material;
        }
    }

    /** Danh sách tất cả blocks bắt buộc (không kể controller). */
    public static final List<BlockOffset> REQUIRED_BLOCKS = List.of(
        // Ring xung quanh controller (cùng Y)
        new BlockOffset(-1, 0, -1, Material.STONE_BRICKS),
        new BlockOffset( 0, 0, -1, Material.STONE_BRICKS),
        new BlockOffset( 1, 0, -1, Material.STONE_BRICKS),
        new BlockOffset(-1, 0,  0, Material.STONE_BRICKS),
        new BlockOffset( 1, 0,  0, Material.STONE_BRICKS),
        new BlockOffset(-1, 0,  1, Material.STONE_BRICKS),
        new BlockOffset( 0, 0,  1, Material.STONE_BRICKS),
        new BlockOffset( 1, 0,  1, Material.STONE_BRICKS),
        // Block phía trên controller
        new BlockOffset( 0, 1,  0, Material.SOUL_CAMPFIRE)
    );

    // Prevent instantiation
    private ForgeStructure() {}

    // ── Validation ────────────────────────────────────────────

    /**
     * Kiểm tra cấu trúc có đầy đủ không tại vị trí controller.
     * @return true nếu tất cả blocks đúng.
     */
    public static boolean validate(Location controllerLoc) {
        if (controllerLoc.getBlock().getType() != CONTROLLER_MATERIAL) return false;
        for (BlockOffset offset : REQUIRED_BLOCKS) {
            if (!offset.matchesAt(controllerLoc)) return false;
        }
        return true;
    }

    /**
     * Lấy danh sách block bị thiếu (để hiển thị cho player).
     * @return List mô tả các block còn thiếu.
     */
    public static List<String> getMissingBlocks(Location controllerLoc) {
        return REQUIRED_BLOCKS.stream()
            .filter(o -> !o.matchesAt(controllerLoc))
            .map(o -> String.format("§c✗ §7%s tại (%+d, %+d, %+d)",
                o.material().name(), o.dx(), o.dy(), o.dz()))
            .toList();
    }

    /**
     * Kiểm tra một block có phải là thành phần của cấu trúc forge không.
     * Dùng khi player phá block để kiểm tra máy lân cận có bị ảnh hưởng không.
     */
    public static boolean isStructuralMaterial(Material material) {
        return material == CONTROLLER_MATERIAL
            || material == Material.STONE_BRICKS
            || material == Material.SOUL_CAMPFIRE;
    }

    /** Mô tả cấu trúc cho player (dùng trong /metallurgy guide hoặc hint). */
    public static String getDescription() {
        return """
                §6§lAncient Forge Structure:
                §7• §eBlast Furnace §7(center) ← right-click
                §7• §e8× Stone Bricks §7(surrounding ring, same level)
                §7• §eSoul Campfire §7(directly above Blast Furnace)
                §7Shift + right-click Blast Furnace để xem trạng thái.""";
    }
}
