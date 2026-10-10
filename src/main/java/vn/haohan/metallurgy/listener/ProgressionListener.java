package vn.haohan.metallurgy.listener;

import vn.haohan.metallurgy.HaoHanMetallurgy;
import vn.haohan.metallurgy.item.CustomItem;
import org.bukkit.Location;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

public class ProgressionListener implements Listener {

    private final HaoHanMetallurgy plugin;

    public ProgressionListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack tool = player.getInventory().getItemInMainHand();

        Material blockType = block.getType();
        String oreKey = null;
        if (blockType == Material.ANCIENT_DEBRIS) {
            oreKey = "minecraft:ancient_debris";
        } else {
            // Kiểm tra các quặng vanilla khác có giới hạn trong config không
            String blockKey = blockType.getKey().toString();
            if (plugin.getConfigManager().getMiningRequirements().containsKey(blockKey)) {
                oreKey = blockKey;
            }
        }

        if (oreKey == null)
            return; // Không phải quặng giới hạn

        // Lấy cấp bậc yêu cầu từ cấu hình
        Map<String, Integer> reqs = plugin.getConfigManager().getMiningRequirements();
        int reqTier = reqs.getOrDefault(oreKey, 0);
        int playerToolTier = getPickaxeTier(tool);

        if (playerToolTier < reqTier) {
            // Không đủ cấp bậc công cụ -> Hủy drop đồ và kinh nghiệm của block này
            event.setDropItems(false);
            event.setExpToDrop(0);

            // Phát âm thanh gãy vụn kim loại
            Location loc = block.getLocation();
            if (loc.getWorld() != null) {
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
                loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f);
                // Sinh ra tia lửa/bụi đỏ cảnh báo
                Location particleLoc = loc.clone().add(0.5, 0.5, 0.5);
                loc.getWorld().spawnParticle(Particle.DUST, particleLoc, 15, 0.3, 0.3, 0.3, 0.1, new Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
            }

            // Gửi cảnh báo dưới dạng Action Bar
            if (plugin.getConfigManager().isMiningWarningsEnabled()) {
                String toolNeeded = getToolTierName(reqTier);
                player.sendActionBar("§c⚠ Yêu cầu cúp: " + toolNeeded + " §cđể khai thác quặng này!");
                player.sendMessage("§8[§6Metallurgy§8] §cCông cụ của bạn quá yếu! Cần ít nhất §e" + toolNeeded
                        + " §ctrở lên để thu thập quặng này.");
            }
            return;
        }

        // Vanilla ores retain their native drops, Silk Touch, Fortune and XP behavior.
    }

    /**
     * Xác định cấp bậc của cúp đang sử dụng.
     */
    private int getPickaxeTier(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {
            return 0;
        }

        try {
            var core = vn.haohan.itemcore.api.HaoHanItemCore.get();
            if (core != null && core.getToolTierRegistry() != null) {
                var toolTier = core.getToolTierRegistry().findTier(tool);
                if (toolTier != null) {
                    return toolTier.level();
                }
            }
        } catch (Throwable ignored) {}

        Optional<CustomItem> customOpt = plugin.getItemManager().getCustomItem(tool);
        if (customOpt.isPresent()) {
            int configuredTier = plugin.getConfigManager().getCustomItemStats(customOpt.get()).tier();
            if (configuredTier > 0) {
                return configuredTier;
            }
        }

        return plugin.getConfigManager().getVanillaToolTier(tool.getType());
    }

    /**
     * Tên cúp theo cấp bậc để hiển thị thông báo.
     */
    private String getToolTierName(int tier) {
        return switch (tier) {
            case 1 -> "§fWooden Pickaxe";
            case 2 -> "§7Stone Pickaxe / Gold Pickaxe";
            case 3 -> "§cCopper / Iron Slag Pickaxe";
            case 4 -> "§fIron Pickaxe / Embersteel Slag";
            case 5 -> "§6Embersteel Pickaxe";
            case 6 -> "§bDiamond Pickaxe / Mithril Slag";
            case 7 -> "§bMithril Pickaxe / Soulsteel Slag";
            case 8 -> "§3Soulsteel Pickaxe / Netherite Slag";
            case 9 -> "§5Netherite Pickaxe";
            default -> "§7Any Pickaxe";
        };
    }

}

