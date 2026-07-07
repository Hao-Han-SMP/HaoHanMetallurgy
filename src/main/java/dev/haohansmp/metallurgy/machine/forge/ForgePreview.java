package dev.haohansmp.metallurgy.machine.forge;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ForgePreview {

    private static final long DISPLAY_DURATION_TICKS = 100L; // 5 giây

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

        // Tìm rotation tốt nhất (thiếu ít block nhất) để hiện ghost blocks
        int bestRot = 0;
        int minMissing = Integer.MAX_VALUE;
        List<ForgeStructure.BlockOffset> bestMissingList = new ArrayList<>();

        for (int rot : List.of(0, 90, 180, 270)) {
            List<ForgeStructure.BlockOffset> missing = new ArrayList<>();
            for (ForgeStructure.BlockOffset offset : ForgeStructure.REQUIRED_BLOCKS) {
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
        for (ForgeStructure.BlockOffset offset : bestMissingList) {
            int rx = offset.dx();
            int rz = offset.dz();
            if (finalRot == 90) {
                rx = -offset.dz();
                rz = offset.dx();
            } else if (finalRot == 180) {
                rx = -offset.dx();
                rz = -offset.dz();
            } else if (finalRot == 270) {
                rx = offset.dz();
                rz = -offset.dx();
            }

            Location blockLoc = controllerLoc.clone().add(rx, offset.dy(), rz);
            BlockDisplay display = spawnGhost(player, blockLoc, offset.material());
            if (display != null) {
                displays.add(display);
            }
        }

        if (displays.isEmpty())
            return;

        activeDisplays.put(player.getUniqueId(), displays);

        int count = displays.size();
        player.sendMessage("§8[§6Forge§8] §c" + count + " §7block còn thiếu "
                + "§8(hiển thị preview " + (DISPLAY_DURATION_TICKS / 20) + "s).");

        // Auto-remove sau N giây (bắt đầu quá trình fade out)
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> fadeOutAndClearFor(player),
                DISPLAY_DURATION_TICKS);
    }

    /**
     * Xóa tất cả ghost blocks của player này.
     */
    public void clearFor(Player player) {
        List<BlockDisplay> displays = activeDisplays.remove(player.getUniqueId());
        if (displays == null)
            return;
        for (BlockDisplay d : displays) {
            if (!d.isDead()) {
                player.hideEntity(plugin, d);
                d.remove();
            }
        }
    }

    /**
     * Bắt đầu fade out các ghost blocks của player này bằng hiệu ứng thu nhỏ về 0,
     * sau đó dọn dẹp các thực thể này.
     */
    public void fadeOutAndClearFor(Player player) {
        List<BlockDisplay> displays = activeDisplays.get(player.getUniqueId());
        if (displays == null)
            return;

        int fadeTicks = 15; // 0.75 giây để thu nhỏ về 0
        for (BlockDisplay d : displays) {
            if (!d.isDead()) {
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(fadeTicks);
                Transformation current = d.getTransformation();
                Transformation target = new Transformation(
                        new Vector3f(0.5f, 0.5f, 0.5f), // Di chuyển tâm về giữa block space
                        current.getLeftRotation(),
                        new Vector3f(0.0f, 0.0f, 0.0f), // Thu nhỏ kích thước về 0
                        current.getRightRotation());
                d.setTransformation(target);
            }
        }

        // Đợi fade-out hoàn tất rồi mới xóa thực tế
        plugin.getServer().getScheduler().runTaskLater(
                plugin,
                () -> {
                    List<BlockDisplay> currentDisplays = activeDisplays.get(player.getUniqueId());
                    if (currentDisplays == displays) {
                        clearFor(player);
                    }
                },
                fadeTicks);
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

    private BlockDisplay spawnGhost(Player player, Location missingLoc, Material material) {
        World world = missingLoc.getWorld();
        if (world == null)
            return null;

        // Spawn tại góc nguyên (integer coords) của block cell
        Location spawnLoc = new Location(
                world,
                missingLoc.getBlockX(),
                missingLoc.getBlockY(),
                missingLoc.getBlockZ());

        try {
            BlockDisplay display = world.spawn(spawnLoc, BlockDisplay.class, entity -> {
                entity.setBlock(material.createBlockData());
                entity.setGlowing(true); // glow effect
                entity.setGlowColorOverride(org.bukkit.Color.RED); // glow màu đỏ báo hiệu thiếu block
                entity.setGravity(false); // không rơi
                entity.setVisibleByDefault(false); // ẩn với tất cả player khác
                entity.setInvulnerable(true);
                entity.setSilent(true);
                entity.setPersistent(false); // không lưu vào disk

                // Thu nhỏ và căn giữa để tạo cảm giác ảo ảnh/mờ nhạt
                Transformation trans = entity.getTransformation();
                Transformation newTrans = new Transformation(
                        new Vector3f(0.1f, 0.1f, 0.1f), // Căn giữa
                        trans.getLeftRotation(),
                        new Vector3f(0.8f, 0.8f, 0.8f), // Kích thước 80%
                        trans.getRightRotation());
                entity.setTransformation(newTrans);
            });

            // Chỉ cho player này thấy
            player.showEntity(plugin, display);
            return display;

        } catch (Exception e) {
            plugin.getPluginLogger().error(
                    "Không thể spawn ghost block tại " + spawnLoc, e);
            return null;
        }
    }
}
