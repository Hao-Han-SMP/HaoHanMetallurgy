package dev.haohansmp.metallurgy.listener;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.data.PdcUtil;
import dev.haohansmp.metallurgy.gui.forge.ForgeGui;
import dev.haohansmp.metallurgy.machine.Machine;
import dev.haohansmp.metallurgy.machine.MachineType;
import dev.haohansmp.metallurgy.machine.forge.AncientForge;
import dev.haohansmp.metallurgy.machine.forge.ForgePreview;
import dev.haohansmp.metallurgy.machine.forge.ForgeStructure;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Xử lý tương tác của player với Ancient Forge:
 * <ul>
 *   <li>Right-click Blast Furnace → mở GUI (nếu đã active) hoặc validate + activate</li>
 *   <li>Shift + Right-click → xem thông tin cấu trúc</li>
 *   <li>Break controller/structural block → deactivate forge</li>
 * </ul>
 */
public class ForgeListener implements Listener {

    private final HaoHanMetallurgy plugin;
    private final ForgePreview preview;

    public ForgeListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        this.preview = new ForgePreview(plugin);
    }

    // ── Player Interact ───────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Chỉ xử lý right-click trực tiếp vào block, không fire double
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Material blockType = block.getType();

        // ── Xử lý Bellows (Ống Thổi Khí) click ──────────────────
        if (blockType == Material.PISTON || blockType == Material.DISPENSER) {
            org.bukkit.block.BlockFace[] faces = {
                org.bukkit.block.BlockFace.NORTH, 
                org.bukkit.block.BlockFace.SOUTH, 
                org.bukkit.block.BlockFace.EAST, 
                org.bukkit.block.BlockFace.WEST,
                org.bukkit.block.BlockFace.UP,
                org.bukkit.block.BlockFace.DOWN
            };
            for (org.bukkit.block.BlockFace face : faces) {
                Block adj = block.getRelative(face);
                Location adjLoc = adj.getLocation();
                var machineOpt = plugin.getMachineManager().get(adjLoc);
                if (machineOpt.isPresent() && machineOpt.get() instanceof AncientForge forge) {
                    event.setCancelled(true);
                    // Thổi khí tăng nhiệt
                    forge.boostTemperature(40);
                    event.getPlayer().sendActionBar("§6💨 Phùuu! Đã thổi khí vào lò rèn lân cận.");
                    return;
                }
            }
            return;
        }

        // ── Xử lý lò Blast Furnace click ─────────────────────
        if (blockType != ForgeStructure.CONTROLLER_MATERIAL) return;

        Player player = event.getPlayer();
        Location loc = block.getLocation();

        // ── Shift + right-click → show structure info + ghost blocks ────
        if (player.isSneaking()) {
            event.setCancelled(true);
            boolean isActive = plugin.getMachineManager().exists(loc);
            if (isActive) {
                var forge = (AncientForge) plugin.getMachineManager().get(loc).orElse(null);
                if (forge != null) {
                    player.sendMessage("§8[§6Forge§8] §7Trạng thái: §e" + forge.getState()
                        + " §7| §cTemp: §f" + forge.getTemperature() + "°C"
                        + " §7| §6Fuel: §f" + forge.getFuelTicksRemaining() + " ticks");
                }
            } else {
                player.sendMessage("§8[§6Forge§8] " + ForgeStructure.getDescription());
                // Hiển thị ghost blocks đỏ cho các block còn thiếu
                preview.showMissing(player, loc);
            }
            return;
        }

        // ── Regular right-click ──────────────────────────────

        // 1. Đã là machine → mở GUI
        if (plugin.getMachineManager().exists(loc)) {
            event.setCancelled(true);
            AncientForge forge = (AncientForge) plugin.getMachineManager().get(loc).orElse(null);
            if (forge == null) return;
            plugin.getGuiManager().open(player, new ForgeGui(plugin, forge));
            return;
        }

        // 2. Chưa phải machine → kiểm tra quyền
        if (!player.hasPermission("haohansmp.metallurgy.use")) {
            player.sendMessage("§cBạn không có quyền dùng Ancient Forge.");
            return;
        }

        // 3. Cấu trúc không hợp lệ → hint + ghost blocks
        if (!ForgeStructure.validate(loc)) {
            player.sendMessage("§8[§6Forge§8] §7Cấu trúc chưa đủ. "
                + "§eShift+Right-click §7để xem ghost blocks.");
            return;
        }

        // Kiểm tra xem blast furnace có chứa vật phẩm nào bên trong không
        if (block.getState() instanceof org.bukkit.block.BlastFurnace bf) {
            boolean hasItems = java.util.Arrays.stream(bf.getInventory().getContents())
                .anyMatch(item -> item != null && item.getType() != Material.AIR);
            if (hasItems) {
                player.sendMessage("§8[§6Forge§8] §c⚠ Vui lòng lấy hết vật phẩm trong lò Blast Furnace ra trước khi kích hoạt Lò Rèn Cổ Đại!");
                player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                return;
            }
        }

        // 4. Cấu trúc hợp lệ → activate
        event.setCancelled(true);
        preview.clearFor(player); // xóa ghost blocks trước khi activate
        activateForge(player, block, loc);
    }

    // ── Block Break ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();

        // 1. Phá controller block → deactivate hoàn toàn
        if (plugin.getMachineManager().exists(loc)) {
            deactivateForge(loc, block, event.getPlayer());
            return;
        }

        // 2. Phá structural block → kiểm tra forge lân cận
        if (ForgeStructure.isStructuralMaterial(block.getType())) {
            invalidateNearbyForges(block);
        }
    }

    // ── Internal ──────────────────────────────────────────────

    private void activateForge(Player player, Block controllerBlock, Location loc) {
        AncientForge forge = new AncientForge(plugin, loc);

        if (!plugin.getMachineManager().register(forge)) {
            player.sendMessage("§cKhông thể kích hoạt forge (đã tồn tại?).");
            return;
        }

        PdcUtil.setMachineType(plugin, controllerBlock, MachineType.ANCIENT_FORGE);
        player.sendMessage("§8[§6Forge§8] §a✔ Ancient Forge kích hoạt thành công!");
        plugin.getPluginLogger().info(
            "AncientForge activated at " + formatLoc(loc) + " by " + player.getName()
        );

        // Mở GUI ngay lập tức
        plugin.getGuiManager().open(player, new ForgeGui(plugin, forge));
    }

    private void deactivateForge(Location loc, Block block, Player player) {
        var machineOpt = plugin.getMachineManager().get(loc);
        plugin.getMachineManager().unregister(loc);
        PdcUtil.clearMachineData(plugin, block);

        if (machineOpt.isPresent()) {
            dropMachineContents(machineOpt.get());
        }

        player.sendMessage("§8[§6Forge§8] §eAncient Forge đã bị phá hủy. Các vật phẩm trong lò đã rơi ra.");
        plugin.getPluginLogger().info(
            "AncientForge destroyed at " + formatLoc(loc) + " by " + player.getName()
        );
    }

    private void dropMachineContents(Machine machine) {
        Location loc = machine.getLocation();
        if (loc.getWorld() == null) return;
        org.bukkit.inventory.Inventory inv = machine.getInventory();
        for (int slot : ForgeGui.INTERACTIVE) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                // Lọc bỏ các block hiển thị/background nếu có (mặc dù interactive slots không có background)
                loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), item);
            }
        }
    }

    /**
     * Khi player phá một structural block, kiểm tra tất cả forge trong bán kính 2
     * để xem có forge nào bị mất cấu trúc không.
     */
    private void invalidateNearbyForges(Block brokenBlock) {
        Location center = brokenBlock.getLocation();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Location nearby = center.clone().add(dx, 0, dz);
                if (!plugin.getMachineManager().exists(nearby)) continue;

                if (!ForgeStructure.validate(nearby)) {
                    var machineOpt = plugin.getMachineManager().get(nearby);
                    plugin.getMachineManager().unregister(nearby);
                    Block controller = nearby.getBlock();
                    PdcUtil.clearMachineData(plugin, controller);

                    if (machineOpt.isPresent()) {
                        dropMachineContents(machineOpt.get());
                    }

                    nearby.getWorld().getPlayers().stream()
                        .filter(p -> p.getLocation().distanceSquared(nearby) < 50 * 50)
                        .forEach(p -> p.sendMessage(
                            "§8[§6Forge§8] §c⚠ Forge lân cận bị phá vỡ cấu trúc!"
                        ));

                    plugin.getPluginLogger().info(
                        "AncientForge invalidated (structural break) at " + formatLoc(nearby)
                    );
                }
            }
        }
    }

    private String formatLoc(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
