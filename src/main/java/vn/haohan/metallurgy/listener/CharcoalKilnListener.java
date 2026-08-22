package vn.haohan.metallurgy.listener;

import vn.haohan.metallurgy.HaoHanMetallurgy;
import vn.haohan.metallurgy.item.CustomItem;
import vn.haohan.metallurgy.item.CustomOreBlock;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Handles mud-sealed log charcoal kilns and persists active batches across restarts. */
public final class CharcoalKilnListener implements Listener {
    private static final BlockFace[] MUD_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN
    };

    private final HaoHanMetallurgy plugin;
    private final Map<KilnKey, ActiveKiln> activeKilns = new LinkedHashMap<>();
    private final Map<UUID, IgnitionAttempt> ignitionAttempts = new LinkedHashMap<>();
    private final BukkitTask tickTask;
    private final BukkitTask ignitionTask;

    public CharcoalKilnListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        load();
        this.tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        this.ignitionTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickIgnitions, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        ItemStack ignitionItem = event.getItem();
        if (plugin.getItemManager().isCustomItem(ignitionItem, CustomItem.BOW_DRILL)) {
            ItemStack migrated = plugin.getItemManager().migrateCustomOreItem(ignitionItem);
            if (migrated != ignitionItem) {
                event.getPlayer().getInventory().setItemInMainHand(migrated);
                ignitionItem = migrated;
            }
        }
        boolean bowDrill = plugin.getItemManager().isCustomItem(ignitionItem, CustomItem.BOW_DRILL);
        boolean vanillaFlintAndSteel = ignitionItem != null
                && ignitionItem.getType() == Material.FLINT_AND_STEEL
                && plugin.getItemManager().getCustomItem(ignitionItem).isEmpty();
        if (!bowDrill && !vanillaFlintAndSteel) return;

        Block log = event.getClickedBlock();
        if (log == null || !Tag.LOGS.isTagged(log.getType())) return;

        Player player = event.getPlayer();
        KilnKey key = KilnKey.from(log);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.ALLOW);

        if (activeKilns.containsKey(key)) {
            player.sendActionBar("§eKhúc gỗ này đang được ủ than.");
            return;
        }
        if (!hasValidStructure(log)) {
            player.sendActionBar("§cCần mud ở 4 mặt xung quanh và mặt đáy; mặt trên phải để hở.");
            return;
        }

        if (vanillaFlintAndSteel) {
            event.setCancelled(true);
            beginKiln(log, player);
            return;
        }

        IgnitionAttempt existing = ignitionAttempts.get(player.getUniqueId());
        if (existing != null && existing.kiln().equals(key)) return;

        double configuredSeconds = plugin.getConfig().getDouble("charcoal-kiln.ignition-seconds", 4.0);
        int durationTicks = (int) Math.ceil(Math.max(3.0, Math.min(5.0, configuredSeconds)) * 20.0);
        int nowTick = Bukkit.getCurrentTick();
        ignitionAttempts.put(player.getUniqueId(), new IgnitionAttempt(
                key, nowTick, nowTick + durationTicks, player.getInventory().getHeldItemSlot()));
        player.startUsingItem(EquipmentSlot.HAND);

        Location center = log.getLocation().add(0.5, 1.05, 0.5);
        log.getWorld().playSound(center, Sound.BLOCK_WOOD_HIT, 0.7f, 1.4f);
        player.sendActionBar("§6Giữ chuột phải để nhóm lửa...");
    }

    public int count() {
        return activeKilns.size();
    }

    public void shutdown() {
        tickTask.cancel();
        ignitionTask.cancel();
        for (UUID playerId : ignitionAttempts.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) player.clearActiveItem();
        }
        ignitionAttempts.clear();
        save();
    }

    private void tickIgnitions() {
        int nowTick = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, IgnitionAttempt>> iterator = ignitionAttempts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, IgnitionAttempt> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            IgnitionAttempt attempt = entry.getValue();
            Block log = attempt.kiln().loadedBlock();

            boolean validPlayer = player != null && player.isOnline()
                    && player.getInventory().getHeldItemSlot() == attempt.heldSlot()
                    && plugin.getItemManager().isCustomItem(
                            player.getInventory().getItemInMainHand(), CustomItem.BOW_DRILL);
            boolean stillHolding = validPlayer
                    && (nowTick <= attempt.startedAtTick() + 3
                    || (player.hasActiveItem() && player.getActiveItemHand() == EquipmentSlot.HAND));
            Block target = validPlayer ? player.getTargetBlockExact(6) : null;
            boolean validTarget = log != null && target != null
                    && target.getLocation().equals(log.getLocation())
                    && Tag.LOGS.isTagged(log.getType()) && hasValidStructure(log);

            if (!stillHolding || !validTarget || activeKilns.containsKey(attempt.kiln())) {
                if (player != null) {
                    player.clearActiveItem();
                    player.sendActionBar("§7Đã dừng nhóm lửa.");
                }
                iterator.remove();
                continue;
            }

            int elapsed = nowTick - attempt.startedAtTick();
            int total = attempt.finishAtTick() - attempt.startedAtTick();
            int percent = Math.max(0, Math.min(100, elapsed * 100 / total));
            if (elapsed % 5 == 0) {
                player.sendActionBar("§6Đang nhóm lửa... §e" + percent + "%");
                log.getWorld().spawnParticle(Particle.SMOKE,
                        log.getLocation().add(0.5, 1.02, 0.5), 1, 0.08, 0.02, 0.08, 0.005);
            }
            if (elapsed % 10 == 0) {
                log.getWorld().playSound(log.getLocation(), Sound.BLOCK_WOOD_HIT, 0.35f, 1.5f);
            }
            if (nowTick < attempt.finishAtTick()) continue;

            player.clearActiveItem();
            beginKiln(log, player);
            iterator.remove();
        }
    }

    private void beginKiln(Block log, Player player) {
        long durationMillis = Math.max(1L,
                plugin.getConfig().getLong("charcoal-kiln.duration-seconds", 120L)) * 1000L;
        activeKilns.put(KilnKey.from(log),
                new ActiveKiln(System.currentTimeMillis() + durationMillis, player.getUniqueId()));
        damageIgnitionTool(player.getInventory().getItemInMainHand(), player);
        save();

        Location center = log.getLocation().add(0.5, 1.05, 0.5);
        log.getWorld().playSound(center, Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 0.8f);
        log.getWorld().spawnParticle(Particle.FLAME, center, 8, 0.18, 0.05, 0.18, 0.015);
        player.sendActionBar("§6Đã nhóm lò ủ. Giữ nguyên lớp mud cho đến khi than chín.");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<KilnKey, ActiveKiln>> iterator = activeKilns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<KilnKey, ActiveKiln> entry = iterator.next();
            KilnKey key = entry.getKey();
            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) continue;

            Block log = world.getBlockAt(key.x(), key.y(), key.z());
            if (!Tag.LOGS.isTagged(log.getType()) || !hasValidStructure(log)) {
                notifyPlayer(entry.getValue().owner(), "§cMẻ than đã hỏng vì khúc gỗ hoặc lớp mud bị thay đổi.");
                iterator.remove();
                changed = true;
                continue;
            }

            Location smoke = log.getLocation().add(0.5, 1.08, 0.5);
            log.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, smoke, 1, 0.08, 0.04, 0.08, 0.005);
            if (now < entry.getValue().finishAt()) continue;

            log.setBlockData(CustomOreBlock.blockDataFor(CustomItem.CHARCOAL_BLOCK), false);
            dryMudShell(log);
            log.getWorld().playSound(smoke, Sound.BLOCK_FIRE_EXTINGUISH, 0.9f, 0.75f);
            log.getWorld().spawnParticle(Particle.LARGE_SMOKE, smoke, 10, 0.22, 0.15, 0.22, 0.015);
            notifyPlayer(entry.getValue().owner(), "§aMẻ than đã hoàn tất: thu được 1 khối than gỗ.");
            iterator.remove();
            changed = true;
        }
        if (changed) save();
    }

    private boolean hasValidStructure(Block log) {
        if (!log.getRelative(BlockFace.UP).getType().isAir()) return false;
        for (BlockFace face : MUD_FACES) {
            if (log.getRelative(face).getType() != Material.MUD) return false;
        }
        return true;
    }

    private void dryMudShell(Block kilnCenter) {
        for (BlockFace face : MUD_FACES) {
            Block shell = kilnCenter.getRelative(face);
            if (shell.getType() == Material.MUD) {
                shell.setType(Material.CLAY, false);
            }
        }
    }

    private void damageIgnitionTool(ItemStack item, Player player) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        int nextDamage = damageable.getDamage() + 1;
        if (maxDamage > 0 && nextDamage >= maxDamage) {
            item.setAmount(0);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
            return;
        }
        damageable.setDamage(nextDamage);
        item.setItemMeta(meta);
    }

    private void notifyPlayer(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) player.sendMessage("§8[§6Metallurgy§8] " + message);
    }

    private void load() {
        File file = dataFile();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("kilns");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String path = "kilns." + id;
            World world = Bukkit.getWorld(yaml.getString(path + ".world", ""));
            String ownerValue = yaml.getString(path + ".owner", "");
            if (world == null) continue;
            try {
                KilnKey key = new KilnKey(world.getName(), yaml.getInt(path + ".x"),
                        yaml.getInt(path + ".y"), yaml.getInt(path + ".z"));
                activeKilns.put(key, new ActiveKiln(yaml.getLong(path + ".finish-at"), UUID.fromString(ownerValue)));
            } catch (IllegalArgumentException ignored) {
                plugin.getPluginLogger().warn("Ignored invalid charcoal kiln entry: " + id);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (var entry : activeKilns.entrySet()) {
            String path = "kilns." + index++;
            KilnKey key = entry.getKey();
            yaml.set(path + ".world", key.world());
            yaml.set(path + ".x", key.x());
            yaml.set(path + ".y", key.y());
            yaml.set(path + ".z", key.z());
            yaml.set(path + ".finish-at", entry.getValue().finishAt());
            yaml.set(path + ".owner", entry.getValue().owner().toString());
        }
        try {
            yaml.save(dataFile());
        } catch (IOException e) {
            plugin.getPluginLogger().error("Failed to save active charcoal kilns", e);
        }
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "charcoal_kilns.yml");
    }

    private record ActiveKiln(long finishAt, UUID owner) {}

    private record IgnitionAttempt(KilnKey kiln, int startedAtTick, int finishAtTick, int heldSlot) {}

    private record KilnKey(String world, int x, int y, int z) {
        static KilnKey from(Block block) {
            return new KilnKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }

        Block loadedBlock() {
            World targetWorld = Bukkit.getWorld(world);
            if (targetWorld == null || !targetWorld.isChunkLoaded(x >> 4, z >> 4)) return null;
            return targetWorld.getBlockAt(x, y, z);
        }
    }
}
