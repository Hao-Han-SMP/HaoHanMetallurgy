package dev.haohansmp.metallurgy.machine.forge;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hiển thị ghost blocks (BlockDisplay entity) cho player thấy vị trí
 * các block còn thiếu trong cấu trúc Ancient Forge.
 *
 * Ghost block:
 *   - Material: RED_STAINED_GLASS (mờ, màu đỏ → "block còn thiếu")
 *   - Glow: true
 *   - Chỉ hiển thị cho player đã gọi showMissing() (setVisibleByDefault=false)
 *   - Tự xóa sau 5 giây (100 ticks)
 *   - Không lưu vào disk (setPersistent=false)
 *
 * Yêu cầu: Paper API 1.19.4+ (BlockDisplay entity).
 */
public class ForgePreview {

    private static final long DISPLAY_DURATION_TICKS = 100L; // 5 giây
    private static final Material GHOST_MATERIAL = Material.RED_STAINED_GLASS;

    private final HaoHanMetallurgy plugin;

    /** UUID player → danh sách ghost entities đang hiển thị cho player đó. */
    private final Map<UUID, List<BlockDisplay>> activeDisplays = new HashMap<>();

    public ForgePreview(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Hiển thị ghost blocks đỏ tại các vị trí block còn thiếu.
     * Các ghost block tự xóa sau {@value DISPLAY_DURATION_TICKS} ticks.
     *
     * @param player        Player sẽ thấy ghost blocks
     * @param controllerLoc Vị trí của controller (Blast Furnace)
     */
    public void showMissing(Player player, Location controllerLoc) {
        // Xóa ghost blocks cũ của player này (nếu còn)
        clearFor(player);

        List<BlockDisplay> displays = new ArrayList<>();

        for (ForgeStructure.BlockOffset offset : ForgeStructure.REQUIRED_BLOCKS) {
            if (!offset.matchesAt(controllerLoc)) {
                // Block này còn thiếu → hiển thị ghost
                Location blockLoc = controllerLoc.clone().add(
                    offset.dx(), offset.dy(), offset.dz()
                );
                BlockDisplay display = spawnGhost(player, blockLoc);
                if (display != null) {
                    displays.add(display);
                }
            }
        }

        if (displays.isEmpty()) return;

        activeDisplays.put(player.getUniqueId(), displays);

        int count = displays.size();
        player.sendMessage("§8[§6Forge§8] §c" + count + " §7block còn thiếu "
            + "§8(ghost blocks đỏ hiển thị " + (DISPLAY_DURATION_TICKS / 20) + "s).");

        // Auto-remove sau N giây
        plugin.getServer().getScheduler().runTaskLater(
            plugin,
            () -> clearFor(player),
            DISPLAY_DURATION_TICKS
        );
    }

    /**
     * Xóa tất cả ghost blocks của player này.
     */
    public void clearFor(Player player) {
        List<BlockDisplay> displays = activeDisplays.remove(player.getUniqueId());
        if (displays == null) return;
        for (BlockDisplay d : displays) {
            if (!d.isDead()) {
                player.hideEntity(plugin, d);
                d.remove();
            }
        }
    }

    /**
     * Xóa tất cả ghost blocks (gọi khi plugin disable).
     */
    public void clearAll() {
        activeDisplays.forEach((uuid, displays) -> {
            displays.stream().filter(d -> !d.isDead()).forEach(org.bukkit.entity.Entity::remove);
        });
        activeDisplays.clear();
    }

    // ── Internal ──────────────────────────────────────────────

    private BlockDisplay spawnGhost(Player player, Location missingLoc) {
        World world = missingLoc.getWorld();
        if (world == null) return null;

        // Spawn tại góc nguyên (integer coords) của block cell
        Location spawnLoc = new Location(
            world,
            missingLoc.getBlockX(),
            missingLoc.getBlockY(),
            missingLoc.getBlockZ()
        );

        try {
            BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class, entity -> {
                entity.setBlock(GHOST_MATERIAL.createBlockData());
                entity.setGlowing(true);          // glow effect
                entity.setGravity(false);          // không rơi
                entity.setVisibleByDefault(false); // ẩn với tất cả player khác
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setPersistent(false);       // không lưu vào disk
            });

            // Chỉ cho player này thấy
            player.showEntity(plugin, display);
            return display;

        } catch (Exception e) {
            plugin.getPluginLogger().error(
                "Không thể spawn ghost block tại " + spawnLoc, e
            );
            return null;
        }
    }
}
