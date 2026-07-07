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

        // ── Xử lý tương tác đa điểm (Click vào block bất kỳ thuộc cấu trúc lò đang hoạt động) ──
        AncientForge activeForge = getActiveForgeFromBlock(block);
        if (activeForge != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (player.isSneaking()) {
                player.sendMessage("§8[§6Forge§8] §7Trạng thái: §e" + activeForge.getState()
                    + " §7| §cTemp: §f" + activeForge.getTemperature() + "°C"
                    + " §7| §6Fuel: §f" + activeForge.getFuelTicksRemaining() + " ticks");
            } else {
                plugin.getGuiManager().open(player, new ForgeGui(plugin, activeForge));
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

        // Kiểm tra xem block bị phá hủy có thuộc cấu trúc của lò rèn nào đang chạy không
        AncientForge activeForge = getActiveForgeFromBlock(block);
        if (activeForge != null) {
            // Hủy sự kiện phá block mặc định của Minecraft để tự xử lý
            event.setCancelled(true);
            
            Location coreLoc = activeForge.getLocation();
            plugin.getMachineManager().unregister(coreLoc);
            
            // Hoàn trả lại các block cũ của lò rèn về trạng thái ban đầu
            restoreOriginalBlocks(activeForge, block);

            // Gửi tin nhắn
            event.getPlayer().sendMessage("§8[§6Forge§8] §eLò Rèn Cổ Đại đã bị hủy kích hoạt. Các vật phẩm đã rơi ra.");
            plugin.getPluginLogger().info(
                "AncientForge deactivated due to block break at " + formatLoc(coreLoc) + " by " + event.getPlayer().getName()
            );
            return;
        }

        // 2. Phá controller block thông thường (khi chưa được kích hoạt thành lò)
        if (plugin.getMachineManager().exists(loc)) {
            deactivateForge(loc, block, event.getPlayer());
            return;
        }

        // 3. Phá structural block khi chưa được kích hoạt
        if (ForgeStructure.isStructuralMaterial(block.getType())) {
            invalidateNearbyForges(block);
        }
    }

    // ── Internal ──────────────────────────────────────────────

    private void activateForge(Player player, Block controllerBlock, Location loc) {
        int rotation = ForgeStructure.getValidRotation(loc);
        if (rotation == -1) {
            player.sendMessage("§8[§6Forge§8] §c⚠ Cấu trúc lò rèn không hợp lệ!");
            return;
        }

        // 1. Chụp lại toàn bộ các khối cấu trúc thật để phục hồi sau này
        java.util.Map<ForgeStructure.BlockOffset, Material> originalBlocks = new java.util.HashMap<>();
        originalBlocks.put(new ForgeStructure.BlockOffset(0, 0, 0, ForgeStructure.CONTROLLER_MATERIAL), ForgeStructure.CONTROLLER_MATERIAL);
        for (ForgeStructure.BlockOffset offset : ForgeStructure.REQUIRED_BLOCKS) {
            int rx = offset.dx();
            int rz = offset.dz();
            if (rotation == 90) { rx = -offset.dz(); rz = offset.dx(); }
            else if (rotation == 180) { rx = -offset.dx(); rz = -offset.dz(); }
            else if (rotation == 270) { rx = offset.dz(); rz = -offset.dx(); }

            Material mat = loc.clone().add(rx, offset.dy(), rz).getBlock().getType();
            originalBlocks.put(offset, mat);
        }

        // 2. Tạo instance lò rèn
        AncientForge forge = new AncientForge(plugin, loc, rotation, originalBlocks);

        if (!plugin.getMachineManager().register(forge)) {
            player.sendMessage("§cKhông thể kích hoạt forge (đã tồn tại?).");
            return;
        }

        // 3. Thay thế toàn bộ 35 block bằng BARRIER để làm chúng hoàn toàn vô hình
        loc.getBlock().setType(Material.BARRIER, false);
        for (ForgeStructure.BlockOffset offset : ForgeStructure.REQUIRED_BLOCKS) {
            int rx = offset.dx();
            int rz = offset.dz();
            if (rotation == 90) { rx = -offset.dz(); rz = offset.dx(); }
            else if (rotation == 180) { rx = -offset.dx(); rz = -offset.dz(); }
            else if (rotation == 270) { rx = offset.dz(); rz = -offset.dx(); }

            loc.clone().add(rx, offset.dy(), rz).getBlock().setType(Material.BARRIER, false);
        }

        player.sendMessage("§8[§6Forge§8] §a✔ Kích hoạt Lò Rèn Cổ Đại thành công! Mô hình 3D đã được thiết lập.");
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
                loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), item);
            }
        }
    }

    private void restoreOriginalBlocks(AncientForge forge, Block brokenBlock) {
        Location loc = forge.getLocation();
        int rotation = forge.getRotation();

        // Rơi nguyên liệu chứa bên trong ra ngoài
        dropMachineContents(forge);

        for (java.util.Map.Entry<ForgeStructure.BlockOffset, Material> entry : forge.getOriginalBlocks().entrySet()) {
            ForgeStructure.BlockOffset offset = entry.getKey();
            Material mat = entry.getValue();

            int rx = offset.dx();
            int rz = offset.dz();
            if (rotation == 90) { rx = -offset.dz(); rz = offset.dx(); }
            else if (rotation == 180) { rx = -offset.dx(); rz = -offset.dz(); }
            else if (rotation == 270) { rx = offset.dz(); rz = -offset.dx(); }

            Block block = loc.clone().add(rx, offset.dy(), rz).getBlock();
            if (block.equals(brokenBlock)) {
                block.setType(Material.AIR, false);
                if (mat != Material.AIR) {
                    loc.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(mat, 1));
                }
            } else {
                block.setType(mat, false);
            }
        }
    }

    private void invalidateNearbyForges(Block brokenBlock) {
        AncientForge forge = getActiveForgeFromBlock(brokenBlock);
        if (forge == null) return;

        Location coreLoc = forge.getLocation();
        plugin.getMachineManager().unregister(coreLoc);

        restoreOriginalBlocks(forge, brokenBlock);

        coreLoc.getWorld().getPlayers().stream()
            .filter(p -> p.getLocation().distanceSquared(coreLoc) < 50 * 50)
            .forEach(p -> p.sendMessage(
                "§8[§6Forge§8] §c⚠ Lò Rèn Cổ Đại đã bị phá vỡ cấu trúc!"
            ));

        plugin.getPluginLogger().info(
            "AncientForge invalidated (structural break) at " + formatLoc(coreLoc)
        );
    }

    private AncientForge getActiveForgeFromBlock(Block clickedBlock) {
        Location clickedLoc = clickedBlock.getLocation();
        for (Machine machine : plugin.getMachineManager().getAll()) {
            if (machine instanceof AncientForge forge) {
                Location coreLoc = forge.getLocation();
                if (coreLoc.getBlock().equals(clickedBlock)) {
                    return forge;
                }
                int rotation = forge.getRotation();
                for (ForgeStructure.BlockOffset offset : ForgeStructure.REQUIRED_BLOCKS) {
                    int rx = offset.dx();
                    int rz = offset.dz();
                    if (rotation == 90) { rx = -offset.dz(); rz = offset.dx(); }
                    else if (rotation == 180) { rx = -offset.dx(); rz = -offset.dz(); }
                    else if (rotation == 270) { rx = offset.dz(); rz = -offset.dx(); }

                    int bx = coreLoc.getBlockX() + rx;
                    int by = coreLoc.getBlockY() + offset.dy();
                    int bz = coreLoc.getBlockZ() + rz;

                    if (clickedLoc.getBlockX() == bx && clickedLoc.getBlockY() == by && clickedLoc.getBlockZ() == bz) {
                        return forge;
                    }
                }
            }
        }
        return null;
    }

    private String formatLoc(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
