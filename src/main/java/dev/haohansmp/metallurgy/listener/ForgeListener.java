package dev.haohansmp.metallurgy.listener;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.data.PdcUtil;
import dev.haohansmp.metallurgy.gui.forge.ForgeGui;
import dev.haohansmp.metallurgy.machine.Machine;
import dev.haohansmp.metallurgy.machine.MachineState;
import dev.haohansmp.metallurgy.machine.MachineType;
import dev.haohansmp.metallurgy.machine.forge.AncientForge;
import dev.haohansmp.metallurgy.machine.forge.ForgePreview;
import dev.haohansmp.metallurgy.machine.forge.ForgeStructure;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

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
    private final Map<UUID, BukkitTask> forgeMiningTasks = new HashMap<>();
    private static final int BEACON_BREAK_TICKS = 90;
    private static final int FORGE_EXPLOSION_SPREAD_RADIUS = 7;
    private static final Random EXPLOSION_RANDOM = new Random();

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

        // ── Xử lý kích hoạt lò (Click vào block của cấu trúc chưa kích hoạt) ──
        if (ForgeStructure.isStructuralMaterial(blockType)) {
            Location coreLoc = findNearbyCoreCandidate(block);
            if (coreLoc != null) {
                // Chỉ xử lý tương tác lò rèn khi Blast Furnace đã nằm trên đế phẳng 3x3 Mud Bricks
                if (!hasMudBrickBase(coreLoc)) {
                    return;
                }

                Player player = event.getPlayer();

                // Shift + Right-click -> Xem thông tin / gợi ý ghost blocks
                if (player.isSneaking()) {
                    event.setCancelled(true);
                    player.sendMessage("§8[§6Forge§8] " + ForgeStructure.getDescription());
                    preview.showMissing(player, coreLoc);
                    return;
                }

                // Regular Right-click -> Kích hoạt lò rèn
                // Nếu cấu trúc CHƯA đầy đủ, ta KHÔNG cancel event để người chơi vẫn đặt được block để hoàn thành!
                if (!ForgeStructure.validate(coreLoc)) {
                    // Để tránh spam tin nhắn khi đang đặt khối xây dựng, ta chỉ gửi tin nhắn
                    // khi họ click bằng tay không hoặc click trực tiếp vào lò Blast Furnace
                    ItemStack itemInHand = player.getInventory().getItemInMainHand();
                    if (itemInHand.getType() == Material.AIR || block.getType() == ForgeStructure.CONTROLLER_MATERIAL) {
                        player.sendMessage("§8[§6Forge§8] §c⚠ Cấu trúc lò rèn chưa đầy đủ!");
                        player.sendMessage("§8[§6Forge§8] §7Hãy §eShift+Right-click §7vào lò để xem các khối còn thiếu.");
                    }
                    return;
                }

                // Cấu trúc ĐẦY ĐỦ -> Tiến hành kích hoạt lò rèn, lúc này mới cancel event
                event.setCancelled(true);

                if (!player.hasPermission("haohansmp.metallurgy.use")) {
                    player.sendMessage("§cBạn không có quyền dùng Ancient Forge.");
                    return;
                }

                // Kiểm tra xem blast furnace core có chứa vật phẩm nào không
                Block coreBlock = coreLoc.getBlock();
                if (coreBlock.getState() instanceof org.bukkit.block.BlastFurnace bf) {
                    boolean hasItems = java.util.Arrays.stream(bf.getInventory().getContents())
                        .anyMatch(item -> item != null && item.getType() != Material.AIR);
                    if (hasItems) {
                        player.sendMessage("§8[§6Forge§8] §c⚠ Vui lòng lấy hết vật phẩm trong lò Blast Furnace ra trước khi kích hoạt Lò Rèn Cổ Đại!");
                        player.playSound(coreLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                        return;
                    }
                }

                preview.clearFor(player); // Xóa các ghost blocks gợi ý trước đó
                activateForge(player, coreBlock, coreLoc);
            }
        }
    }

    // ── Block Break ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARRIER) return;

        AncientForge activeForge = getActiveForgeFromBlock(block);
        if (activeForge == null) return;

        event.setInstaBreak(false);
        beginForgeMining(activeForge, block, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        cancelForgeMining(event.getPlayer());
    }

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
            plugin.getMachineManager().saveAll();

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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        EntityType type = event.getEntityType();
        if (type != EntityType.TNT && type != EntityType.CREEPER) {
            return;
        }
        handleForgeExplosion(event.getLocation(), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleForgeExplosion(event.getBlock().getLocation(), event.blockList());
    }

    private void activateForge(Player player, Block controllerBlock, Location loc) {
        int rotation = ForgeStructure.getValidRotation(loc);
        if (rotation == -1) {
            player.sendMessage("§8[§6Forge§8] §c⚠ Cấu trúc lò rèn không hợp lệ!");
            return;
        }

        // Lấy hướng xoay của Blast Furnace để áp dụng làm hướng mặt chính của mô hình 3D
        if (controllerBlock.getBlockData() instanceof org.bukkit.block.data.Directional directional) {
            org.bukkit.block.BlockFace facing = directional.getFacing();
            if (facing == org.bukkit.block.BlockFace.WEST) {
                rotation = 90;
            } else if (facing == org.bukkit.block.BlockFace.NORTH) {
                rotation = 180;
            } else if (facing == org.bukkit.block.BlockFace.EAST) {
                rotation = 270;
            } else {
                rotation = 0; // SOUTH
            }
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
        forge.playActivationEffects();
        plugin.getMachineManager().saveAll();
        plugin.getPluginLogger().info(
            "AncientForge activated at " + formatLoc(loc) + " by " + player.getName()
        );

        // Mở GUI ngay lập tức
        plugin.getGuiManager().open(player, new ForgeGui(plugin, forge));
    }

    private void beginForgeMining(AncientForge forge, Block block, Player player) {
        cancelForgeMining(player);

        Location blockLoc = block.getLocation();
        Location coreLoc = forge.getLocation();
        UUID playerId = player.getUniqueId();

        BukkitTask task = new BukkitRunnable() {
            private int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline()
                    || !plugin.getMachineManager().exists(coreLoc)
                    || !block.getWorld().equals(player.getWorld())
                    || player.getLocation().distanceSquared(blockLoc.clone().add(0.5, 0.5, 0.5)) > 36) {
                    forgeMiningTasks.remove(playerId);
                    player.sendActionBar("");
                    cancel();
                    return;
                }

                elapsed++;
                if (elapsed % 10 == 0) {
                    float progress = Math.min(1.0f, elapsed / (float) BEACON_BREAK_TICKS);
                    player.sendActionBar("§6Đang phá Lò Rèn Cổ Đại... §e" + Math.round(progress * 100) + "%");
                    player.playSound(blockLoc, Sound.BLOCK_STONE_HIT, 0.35f, 0.75f);
                }

                if (elapsed >= BEACON_BREAK_TICKS) {
                    forgeMiningTasks.remove(playerId);
                    dismantleActiveForge(forge, block, player);
                    player.playSound(blockLoc, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.8f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        forgeMiningTasks.put(playerId, task);
    }

    private void cancelForgeMining(Player player) {
        BukkitTask task = forgeMiningTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            player.sendActionBar("");
        }
    }

    private void dismantleActiveForge(AncientForge forge, Block brokenBlock, Player player) {
        Location coreLoc = forge.getLocation();
        forge.playDeactivationEffects();
        plugin.getMachineManager().unregister(coreLoc);

        restoreOriginalBlocks(forge, brokenBlock);
        plugin.getMachineManager().saveAll();

        player.sendMessage("§8[§6Forge§8] §eLò Rèn Cổ Đại đã bị hủy kích hoạt. Các vật phẩm đã rơi ra.");
        plugin.getPluginLogger().info(
            "AncientForge deactivated due to player break at " + formatLoc(coreLoc) + " by " + player.getName()
        );
    }

    private void deactivateForge(Location loc, Block block, Player player) {
        var machineOpt = plugin.getMachineManager().get(loc);
        plugin.getMachineManager().unregister(loc);
        PdcUtil.clearMachineData(plugin, block);

        if (machineOpt.isPresent()) {
            dropMachineContents(machineOpt.get());
        }
        plugin.getMachineManager().saveAll();

        player.sendMessage("§8[§6Forge§8] §eAncient Forge đã bị phá hủy. Các vật phẩm trong lò đã rơi ra.");
        plugin.getPluginLogger().info(
            "AncientForge destroyed at " + formatLoc(loc) + " by " + player.getName()
        );
    }

    private void dropMachineContents(Machine machine) {
        Location dropLoc = getForgeMouthLocation(machine);
        World world = dropLoc.getWorld();
        if (world == null) return;
        Vector burstVelocity = getForgeMouthDirection(machine).multiply(0.38).setY(0.22);
        org.bukkit.inventory.Inventory inv = machine.getInventory();
        for (int slot : ForgeGui.INTERACTIVE) {
            ItemStack item = inv.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {
                Item dropped = world.dropItem(dropLoc, item.clone());
                dropped.setVelocity(burstVelocity.clone().add(new Vector(
                    (EXPLOSION_RANDOM.nextDouble() - 0.5) * 0.12,
                    EXPLOSION_RANDOM.nextDouble() * 0.08,
                    (EXPLOSION_RANDOM.nextDouble() - 0.5) * 0.12
                )));
                inv.setItem(slot, null);
            }
        }
    }

    private void handleForgeExplosion(Location source, List<Block> explodedBlocks) {
        Set<AncientForge> directlyHit = new HashSet<>();
        for (Block block : explodedBlocks) {
            AncientForge forge = getActiveForgeFromBlock(block);
            if (forge != null) {
                directlyHit.add(forge);
            }
        }

        for (Machine machine : plugin.getMachineManager().getAll()) {
            if (machine instanceof AncientForge forge
                    && sameWorld(source, forge.getLocation())
                    && source.distanceSquared(forge.getLocation()) <= 16) {
                directlyHit.add(forge);
            }
        }

        if (directlyHit.isEmpty()) {
            return;
        }

        Set<AncientForge> volatileForges = new HashSet<>();
        Set<AncientForge> damagedForges = new HashSet<>();
        for (AncientForge forge : directlyHit) {
            if (isVolatileForge(forge)) {
                volatileForges.add(forge);
            } else {
                damagedForges.add(forge);
            }
        }

        Set<AncientForge> explosionChain = collectExplosionChain(volatileForges);
        damagedForges.removeAll(explosionChain);
        explodedBlocks.removeIf(block -> {
            AncientForge forge = getActiveForgeFromBlock(block);
            return forge != null && (explosionChain.contains(forge) || damagedForges.contains(forge));
        });

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            detonateForgeChain(explosionChain, source);
            damageInactiveForges(damagedForges);
        });
    }

    private Set<AncientForge> collectExplosionChain(Set<AncientForge> initialForges) {
        Set<AncientForge> result = new HashSet<>();
        ArrayDeque<AncientForge> queue = new ArrayDeque<>(initialForges);

        while (!queue.isEmpty()) {
            AncientForge current = queue.removeFirst();
            if (!result.add(current)) {
                continue;
            }

            Location currentLoc = current.getLocation();
            for (Machine machine : plugin.getMachineManager().getAll()) {
                if (!(machine instanceof AncientForge nearby) || result.contains(nearby)) {
                    continue;
                }
                if (isVolatileForge(nearby)
                        && sameWorld(currentLoc, nearby.getLocation())
                        && currentLoc.distanceSquared(nearby.getLocation())
                            <= FORGE_EXPLOSION_SPREAD_RADIUS * FORGE_EXPLOSION_SPREAD_RADIUS) {
                    queue.addLast(nearby);
                }
            }
        }

        return result;
    }

    private void detonateForgeChain(Set<AncientForge> forges, Location source) {
        int delay = 0;
        for (AncientForge forge : forges) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> detonateForge(forge, source), delay);
            delay += 4;
        }
    }

    private void detonateForge(AncientForge forge, Location source) {
        Location coreLoc = forge.getLocation();
        if (!plugin.getMachineManager().exists(coreLoc)) {
            return;
        }

        dropMachineContents(forge);
        plugin.getMachineManager().unregister(coreLoc);
        scorchForgeArea(forge, true);
        playForgeExplosionEffects(forge, source);
        plugin.getMachineManager().saveAll();

        plugin.getPluginLogger().info(
            "AncientForge exploded at " + formatLoc(coreLoc) + " after blast impact."
        );
    }

    private void damageInactiveForges(Set<AncientForge> forges) {
        for (AncientForge forge : forges) {
            Location coreLoc = forge.getLocation();
            if (!plugin.getMachineManager().exists(coreLoc)) {
                continue;
            }

            dropMachineContents(forge);
            plugin.getMachineManager().unregister(coreLoc);
            scorchForgeArea(forge, false);
            playForgeDamageEffects(forge);
            plugin.getPluginLogger().info(
                "AncientForge damaged at " + formatLoc(coreLoc) + " by nearby blast."
            );
        }
        if (!forges.isEmpty()) {
            plugin.getMachineManager().saveAll();
        }
    }

    private boolean isVolatileForge(AncientForge forge) {
        return forge.getFuelTicksRemaining() > 0;
    }

    private void scorchForgeArea(AncientForge forge, boolean severe) {
        Location loc = forge.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        for (Location forgeBlock : getForgeBlockLocations(forge)) {
            int dy = forgeBlock.getBlockY() - loc.getBlockY();
            if (severe && dy >= 1 && EXPLOSION_RANDOM.nextDouble() < (dy >= 2 ? 0.72 : 0.42)) {
                forgeBlock.getBlock().setType(Material.AIR, false);
                Block above = forgeBlock.getBlock().getRelative(BlockFace.UP);
                if (above.getType() == Material.FIRE || above.getType() == Material.SOUL_FIRE) {
                    above.setType(Material.AIR, false);
                }
                continue;
            }
            if (!severe) {
                // Inactive forge:
                // Keep most of the original blocks intact
                double keepChance = dy <= 0 ? 0.85 : 0.60;
                if (EXPLOSION_RANDOM.nextDouble() < keepChance) {
                    Material origMat = getOriginalMaterial(forge, forgeBlock, loc);
                    if (origMat != null) {
                        forgeBlock.getBlock().setType(origMat, false);
                        continue;
                    }
                }

                if (dy >= 2 && EXPLOSION_RANDOM.nextDouble() < 0.24) {
                    forgeBlock.getBlock().setType(Material.AIR, false);
                    Block above = forgeBlock.getBlock().getRelative(BlockFace.UP);
                    if (above.getType() == Material.FIRE || above.getType() == Material.SOUL_FIRE) {
                        above.setType(Material.AIR, false);
                    }
                    continue;
                }
                Material rubble = dy <= 0 || EXPLOSION_RANDOM.nextDouble() < 0.72
                    ? randomStoneRubbleMaterial()
                    : randomScorchedMaterial(0.85);
                if (canReplaceForgeShell(forgeBlock.getBlock())) {
                    forgeBlock.getBlock().setType(rubble, false);
                }
                continue;
            }
            Material scorched = randomScorchedMaterial(0.7);
            if (canReplaceForgeShell(forgeBlock.getBlock())) {
                forgeBlock.getBlock().setType(scorched, false);
            }
        }

        int radius = severe ? 3 : 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.35 || EXPLOSION_RANDOM.nextDouble() > 1.0 - dist * 0.16) {
                    continue;
                }

                Block ground = world.getBlockAt(loc.getBlockX() + dx, loc.getBlockY() - 1, loc.getBlockZ() + dz);
                if (canScorch(ground)) {
                    ground.setType(severe ? randomScorchedMaterial(dist / radius) : randomStoneRubbleMaterial(), false);
                }
            }
        }
    }

    private Material getOriginalMaterial(AncientForge forge, Location blockLoc, Location coreLoc) {
        int rotation = forge.getRotation();
        int dx = blockLoc.getBlockX() - coreLoc.getBlockX();
        int dy = blockLoc.getBlockY() - coreLoc.getBlockY();
        int dz = blockLoc.getBlockZ() - coreLoc.getBlockZ();

        int odx, odz;
        if (rotation == 90) {
            odx = dz;
            odz = -dx;
        } else if (rotation == 180) {
            odx = -dx;
            odz = -dz;
        } else if (rotation == 270) {
            odx = -dz;
            odz = dx;
        } else {
            odx = dx;
            odz = dz;
        }

        for (java.util.Map.Entry<ForgeStructure.BlockOffset, Material> entry : forge.getOriginalBlocks().entrySet()) {
            ForgeStructure.BlockOffset offset = entry.getKey();
            if (offset.dx() == odx && offset.dy() == dy && offset.dz() == odz) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Material randomStoneRubbleMaterial() {
        double roll = EXPLOSION_RANDOM.nextDouble();
        if (roll < 0.30) {
            return Material.COBBLESTONE;
        }
        if (roll < 0.52) {
            return Material.CRACKED_STONE_BRICKS;
        }
        if (roll < 0.72) {
            return Material.STONE;
        }
        if (roll < 0.88) {
            return Material.TUFF;
        }
        return Material.COBBLED_DEEPSLATE;
    }

    private Material randomScorchedMaterial(double edgeBias) {
        double roll = EXPLOSION_RANDOM.nextDouble();
        if (roll < 0.30 - edgeBias * 0.08) {
            return Material.MAGMA_BLOCK;
        }
        if (roll < 0.52) {
            return Material.COAL_BLOCK;
        }
        if (roll < 0.78) {
            return Material.BLACKSTONE;
        }
        return Material.BASALT;
    }

    private boolean canScorch(Block block) {
        Material type = block.getType();
        return type != Material.AIR
            && type != Material.BEDROCK
            && type != Material.END_PORTAL_FRAME
            && type != Material.END_PORTAL
            && type != Material.NETHER_PORTAL
            && type != Material.BARRIER
            && !type.name().contains("COMMAND_BLOCK");
    }

    private boolean canReplaceForgeShell(Block block) {
        Material type = block.getType();
        return type != Material.BEDROCK
            && type != Material.END_PORTAL_FRAME
            && type != Material.END_PORTAL
            && type != Material.NETHER_PORTAL
            && !type.name().contains("COMMAND_BLOCK");
    }

    private void playForgeDamageEffects(AncientForge forge) {
        Location loc = forge.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        Location center = loc.clone().add(0.5, 1.0, 0.5);
        world.spawnParticle(org.bukkit.Particle.SMOKE, center, 18, 0.55, 0.35, 0.55, 0.04);
        world.spawnParticle(org.bukkit.Particle.ASH, center, 20, 0.8, 0.45, 0.8, 0.03);
        world.playSound(center, Sound.BLOCK_STONE_BREAK, 1.0f, 0.55f);
        world.playSound(center, Sound.BLOCK_CHAIN_BREAK, 0.7f, 0.7f);
    }

    private void playForgeExplosionEffects(AncientForge forge, Location source) {
        Location loc = forge.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        Location center = loc.clone().add(0.5, 1.0, 0.5);
        Location mouth = getForgeMouthLocation(forge);
        Location chimney = loc.clone().add(0.5, 3.2, 0.5);

        world.createExplosion(center, 0.0f, false, false);
        world.spawnParticle(org.bukkit.Particle.EXPLOSION, center, 4, 0.45, 0.3, 0.45, 0.02);
        world.spawnParticle(org.bukkit.Particle.FLAME, mouth, 70, 0.35, 0.28, 0.35, 0.18);
        world.spawnParticle(org.bukkit.Particle.LAVA, mouth, 24, 0.35, 0.35, 0.35, 0.08);
        world.spawnParticle(org.bukkit.Particle.SMOKE, mouth, 35, 0.45, 0.35, 0.45, 0.08);
        world.spawnParticle(org.bukkit.Particle.LARGE_SMOKE, chimney, 34, 0.45, 0.75, 0.45, 0.05);
        world.spawnParticle(org.bukkit.Particle.CAMPFIRE_SIGNAL_SMOKE, chimney.clone().add(0, 0.5, 0), 12, 0.5, 0.5, 0.5, 0.04);
        world.spawnParticle(org.bukkit.Particle.ASH, center, 45, 1.25, 0.65, 1.25, 0.05);

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.72f);
        world.playSound(center, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 1.2f, 0.55f);
        world.playSound(center, Sound.BLOCK_LAVA_POP, 1.0f, 0.65f);

        if (source != null && sameWorld(source, loc)) {
            Vector shove = loc.toVector().subtract(source.toVector()).normalize().multiply(0.2);
            world.getNearbyEntities(center, 4.0, 3.0, 4.0).forEach(entity -> entity.setVelocity(entity.getVelocity().add(shove)));
        }
    }

    private List<Location> getForgeBlockLocations(AncientForge forge) {
        Location loc = forge.getLocation();
        java.util.ArrayList<Location> locations = new java.util.ArrayList<>();
        locations.add(loc);
        int rotation = forge.getRotation();

        for (ForgeStructure.BlockOffset offset : ForgeStructure.REQUIRED_BLOCKS) {
            int rx = offset.dx();
            int rz = offset.dz();
            if (rotation == 90) { rx = -offset.dz(); rz = offset.dx(); }
            else if (rotation == 180) { rx = -offset.dx(); rz = -offset.dz(); }
            else if (rotation == 270) { rx = offset.dz(); rz = -offset.dx(); }

            locations.add(loc.clone().add(rx, offset.dy(), rz));
        }

        return locations;
    }

    private Location getForgeMouthLocation(Machine machine) {
        Location loc = machine.getLocation();
        Vector direction = getForgeMouthDirection(machine);
        return loc.clone().add(0.5 + direction.getX() * 1.15, 0.85, 0.5 + direction.getZ() * 1.15);
    }

    private Vector getForgeMouthDirection(Machine machine) {
        if (machine instanceof AncientForge forge) {
            return switch (forge.getRotation()) {
                case 90 -> new Vector(-1, 0, 0);
                case 180 -> new Vector(0, 0, -1);
                case 270 -> new Vector(1, 0, 0);
                default -> new Vector(0, 0, 1);
            };
        }
        return new Vector(0, 0, 1);
    }

    private boolean sameWorld(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld());
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
                Block above = block.getRelative(BlockFace.UP);
                if (above.getType() == Material.FIRE || above.getType() == Material.SOUL_FIRE) {
                    above.setType(Material.AIR, false);
                }
                if (mat != Material.AIR) {
                    loc.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(mat, 1));
                }
            } else {
                block.setType(mat, false);
                if (mat == Material.AIR) {
                    Block above = block.getRelative(BlockFace.UP);
                    if (above.getType() == Material.FIRE || above.getType() == Material.SOUL_FIRE) {
                        above.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void invalidateNearbyForges(Block brokenBlock) {
        AncientForge forge = getActiveForgeFromBlock(brokenBlock);
        if (forge == null) return;

        Location coreLoc = forge.getLocation();
        plugin.getMachineManager().unregister(coreLoc);

        restoreOriginalBlocks(forge, brokenBlock);
        plugin.getMachineManager().saveAll();

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
        var indexedForge = plugin.getMachineManager().getForgeAtBlock(clickedBlock.getLocation());
        if (indexedForge.isPresent()) {
            return indexedForge.get();
        }

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

    private Location findNearbyCoreCandidate(Block clickedBlock) {
        // Quét bán kính nhỏ dy từ -2 đến 1, dx/dz từ -1 đến 1 để tìm Blast Furnace core chưa kích hoạt
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block checkBlock = clickedBlock.getRelative(dx, dy, dz);
                    if (checkBlock.getType() == ForgeStructure.CONTROLLER_MATERIAL) {
                        Location coreLoc = checkBlock.getLocation();
                        if (!plugin.getMachineManager().exists(coreLoc)) {
                            return coreLoc;
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean hasMudBrickBase(Location coreLoc) {
        org.bukkit.World world = coreLoc.getWorld();
        if (world == null) return false;
        int cy = coreLoc.getBlockY() - 1;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block block = world.getBlockAt(coreLoc.getBlockX() + dx, cy, coreLoc.getBlockZ() + dz);
                if (block.getType() != Material.MUD_BRICKS) {
                    return false;
                }
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(org.bukkit.event.block.BlockPistonExtendEvent event) {
        org.bukkit.block.BlockFace direction = event.getDirection();
        Block pistonBlock = event.getBlock();
        Block targetBlock = pistonBlock.getRelative(direction);

        AncientForge forge = getActiveForgeFromBlock(targetBlock);
        if (forge == null) {
            for (Block b : event.getBlocks()) {
                Block pushedTarget = b.getRelative(direction);
                forge = getActiveForgeFromBlock(pushedTarget);
                if (forge != null) {
                    break;
                }
            }
        }

        if (forge == null) return;

        // Piston thổi khí tự động (Blast Bellows)
        if (forge.getState() == MachineState.WORKING) {
            forge.boostTemperature(30);
            forge.boostProgressTicks(5);

            // Hiệu ứng phụt gió
            Location loc = forge.getLocation();
            if (loc.getWorld() != null) {
                loc.getWorld().playSound(loc, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.5f, 1.2f);
                loc.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, loc.clone().add(0.5, 1.2, 0.5), 6, 0.1, 0.1, 0.1, 0.02);
            }
        }
    }
}
