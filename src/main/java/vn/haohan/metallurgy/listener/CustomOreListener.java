package vn.haohan.metallurgy.listener;

import vn.haohan.metallurgy.HaoHanMetallurgy;
import vn.haohan.metallurgy.item.CustomItem;
import vn.haohan.metallurgy.item.CustomOreBlock;
import io.papermc.paper.event.player.PlayerPickItemEvent;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Handles metallurgy custom block mining speed, tiers, drops, and progression. */
public final class CustomOreListener implements Listener {
    private final HaoHanMetallurgy plugin;
    private final NamespacedKey miningSpeedKey;

    public CustomOreListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        this.miningSpeedKey = new NamespacedKey(plugin, "custom_ore_mining_speed");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateMiningSpeeds, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        CustomItem customBlock = CustomOreBlock.getCustomBlock(block);
        if (customBlock == CustomItem.CHARCOAL_BLOCK) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                drop(block, CustomItem.CHARCOAL_BLOCK, 1);
            }
            return;
        }

        OreType ore = getOre(block);
        if (ore == null) return;

        event.setDropItems(false);
        event.setExpToDrop(0);
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (getPickaxeTier(tool) < ore.requiredTier()) {
            if (plugin.getConfigManager().isMiningWarningsEnabled()) {
                event.getPlayer().sendActionBar(ore.mithril()
                        ? "§c⚠ Cần cúp kim cương trở lên để thu thập Mithril!"
                        : "§c⚠ Cần cúp đá trở lên để thu thập Borax!");
            }
            return;
        }

        if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
            drop(block, ore.blockItem(), 1);
            return;
        }

        int baseAmount = ore.mithril() ? ThreadLocalRandom.current().nextInt(1, 4) : 1;
        int amount = baseAmount * fortuneMultiplier(tool.getEnchantmentLevel(Enchantment.FORTUNE));
        drop(block, ore.resourceItem(), amount);
        event.setExpToDrop(ore.mithril()
                ? ThreadLocalRandom.current().nextInt(3, 8)
                : ThreadLocalRandom.current().nextInt(2, 6));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickBlock(PlayerPickItemEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) return;

        Block targetBlock = event.getPlayer().getTargetBlockExact(5);
        if (targetBlock == null) return;

        CustomItem managedBlock = CustomOreBlock.getCustomBlock(targetBlock);
        if (managedBlock == null) return;

        event.setCancelled(true);
        int targetSlot = event.getTargetSlot();
        if (targetSlot < 0 || targetSlot > 8) {
            targetSlot = event.getPlayer().getInventory().getHeldItemSlot();
        }
        int hotbarSlot = targetSlot;
        plugin.getServer().getScheduler().runTask(plugin, () ->
                event.getPlayer().getInventory().setItem(
                        hotbarSlot, plugin.getItemManager().createItem(managedBlock, 1)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var inventory = event.getPlayer().getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null) continue;
            ItemStack migrated = plugin.getItemManager().migrateCustomOreItem(item);
            inventory.setItem(slot, migrated);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (containsOre(event.getBlocks())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (containsOre(event.getBlocks())) event.setCancelled(true);
    }

    private void updateMiningSpeeds() {
        for (var player : plugin.getServer().getOnlinePlayers()) {
            double modifier = 0.0;
            if (player.getGameMode() != GameMode.CREATIVE) {
                Block target = player.getTargetBlockExact(6);
                if (target != null) {
                    OreType ore = getOre(target);
                    double scale = ore == null ? 1.0
                            : miningSpeedScale(player.getInventory().getItemInMainHand(), ore);
                    modifier = scale - 1.0;
                }
            }
            setMiningSpeedModifier(player.getAttribute(Attribute.BLOCK_BREAK_SPEED), modifier);
        }
    }

    private void setMiningSpeedModifier(AttributeInstance attribute, double amount) {
        if (attribute == null) return;
        attribute.getModifiers().stream()
                .filter(modifier -> modifier.getKey().equals(miningSpeedKey))
                .findFirst()
                .ifPresent(attribute::removeModifier);
        if (amount != 0.0) {
            attribute.addTransientModifier(new AttributeModifier(
                    miningSpeedKey, amount, AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    public void cleanupMiningSpeedModifiers() {
        for (var player : plugin.getServer().getOnlinePlayers()) {
            setMiningSpeedModifier(player.getAttribute(Attribute.BLOCK_BREAK_SPEED), 0.0);
        }
    }

    private OreType getOre(Block block) {
        CustomItem item = CustomOreBlock.getCustomBlock(block);
        if (item == null) return null;
        return switch (item) {
            case BORAX_ORE, DEEPSLATE_BORAX_ORE -> new OreType(item, CustomItem.RAW_BORAX, 2, false);
            case MITHRIL_ORE, DEEPSLATE_MITHRIL_ORE -> new OreType(item, CustomItem.MITHRIL_SHARD, 6, true);
            default -> null;
        };
    }

    private double miningSpeedScale(ItemStack tool, OreType ore) {
        double hardness = switch (ore.blockItem()) {
            case BORAX_ORE -> plugin.getConfigManager().getBoraxOreHardness();
            case DEEPSLATE_BORAX_ORE -> plugin.getConfigManager().getDeepslateBoraxOreHardness();
            case MITHRIL_ORE -> plugin.getConfigManager().getMithrilOreHardness();
            case DEEPSLATE_MITHRIL_ORE -> plugin.getConfigManager().getDeepslateMithrilOreHardness();
            default -> Material.STONE.getHardness();
        };
        Material carrier = CustomOreBlock.carrierFor(ore.blockItem());
        double scale = carrier.getHardness() / hardness * intendedPickaxeSpeed(tool);

        int pickaxeTier = getPickaxeTier(tool);
        if (pickaxeTier > 0 && pickaxeTier < ore.requiredTier()) scale *= 0.3;
        return Math.max(0.001, scale);
    }

    /** Restores pickaxe-like speed because NoteBlock is not in vanilla's mineable/pickaxe tag. */
    private double intendedPickaxeSpeed(ItemStack tool) {
        if (tool == null) return 1.0;
        double speed = switch (tool.getType()) {
            case WOODEN_PICKAXE -> 2.0;
            case STONE_PICKAXE -> 4.0;
            case IRON_PICKAXE -> 6.0;
            case DIAMOND_PICKAXE -> 8.0;
            case NETHERITE_PICKAXE -> 9.0;
            case GOLDEN_PICKAXE -> 12.0;
            default -> 1.0;
        };
        int efficiency = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (speed > 1.0 && efficiency > 0) speed += efficiency * efficiency + 1.0;
        return speed;
    }

    private boolean containsOre(List<Block> blocks) {
        return blocks.stream().anyMatch(block -> CustomOreBlock.getCustomBlock(block) != null);
    }

    private void drop(Block block, CustomItem item, int amount) {
        block.getWorld().dropItemNaturally(
                block.getLocation().add(0.5, 0.5, 0.5),
                plugin.getItemManager().createItem(item, amount));
    }

    private int fortuneMultiplier(int fortuneLevel) {
        if (fortuneLevel <= 0) return 1;
        int bonus = ThreadLocalRandom.current().nextInt(fortuneLevel + 2) - 1;
        return Math.max(1, bonus + 1);
    }

    private int getPickaxeTier(ItemStack tool) {
        if (tool == null || tool.getType().isAir()) return 0;
        CustomItem custom = plugin.getItemManager().getCustomItem(tool).orElse(null);
        if (custom != null) {
            int tier = plugin.getConfigManager().getCustomItemStats(custom).tier();
            if (tier > 0) return tier;
        }
        return plugin.getConfigManager().getVanillaToolTier(tool.getType());
    }

    private record OreType(CustomItem blockItem, CustomItem resourceItem, int requiredTier, boolean mithril) {}
}
