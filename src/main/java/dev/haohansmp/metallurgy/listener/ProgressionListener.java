package dev.haohansmp.metallurgy.listener;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.item.CustomItem;
import org.bukkit.Location;
import org.bukkit.Material;
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
        ItemStack tool = player.getInventory().getItemInMainHand();

        Material blockType = block.getType();
        Material emberOreMat = plugin.getConfigManager().getEmberOreMaterial();
        Material soulOreMat = plugin.getConfigManager().getSoulOreMaterial();
        Material mithrilOreMat = plugin.getConfigManager().getMithrilOreMaterial();

        // Xác định loại quặng luyện kim đang đào
        String oreKey = null;
        if (blockType == emberOreMat) {
            oreKey = "ember_ore";
        } else if (blockType == soulOreMat) {
            oreKey = "soul_ore";
        } else if (blockType == mithrilOreMat) {
            oreKey = "mithril-ore";
        } else if (blockType == Material.ANCIENT_DEBRIS) {
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
            // Không đủ cấp bậc công cụ -> Hủy drop đồ của block này
            event.setDropItems(false);

            // Phát âm thanh gãy vụn kim loại
            Location loc = block.getLocation();
            if (loc.getWorld() != null) {
                loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_BREAK, 1.0f, 0.5f);
                loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f);
            }

            // Gửi cảnh báo dưới dạng Action Bar
            String toolNeeded = getToolTierName(reqTier);
            player.sendActionBar("§c⚠ Yêu cầu cúp: " + toolNeeded + " §cđể khai thác quặng này!");
            player.sendMessage("§8[§6Metallurgy§8] §cCông cụ của bạn quá yếu! Cần ít nhất §e" + toolNeeded
                    + " §ctrở lên để thu thập quặng này.");
            return;
        }

        // Đào thành công -> Thay thế drop thường bằng Custom Item
        if (blockType == emberOreMat) {
            event.setDropItems(false); // Chặn quặng thường (e.g. Copper Ore) rơi ra
            ItemStack shard = plugin.getItemManager().createItem(CustomItem.EMBER_SHARD, getDropAmount(player));
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), shard);
        } else if (blockType == soulOreMat) {
            event.setDropItems(false); // Chặn lapis rơi ra
            ItemStack crystal = plugin.getItemManager().createItem(CustomItem.SOUL_CRYSTAL, getDropAmount(player));
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), crystal);
        } else if (blockType == mithrilOreMat) {
            event.setDropItems(false); // Chặn emerald rơi ra
            ItemStack shard = plugin.getItemManager().createItem(CustomItem.MITHRIL_SHARD, getDropAmount(player));
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), shard);
        }
        // Các khối khác như Sắt, Đồng vanilla vẫn rơi ra bình thường nếu đủ cấp bậc
    }

    /**
     * Xác định cấp bậc của cúp đang sử dụng.
     */
    private int getPickaxeTier(ItemStack tool) {
        if (tool == null || tool.getType() == Material.AIR) {
            return 0;
        }

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
            case 2 -> "§7Stone Pickaxe";
            case 3 -> "§cCopper Slag Pickaxe";
            case 4 -> "§6Copper Pickaxe";
            case 5 -> "§8Iron Slag Pickaxe";
            case 6 -> "§fIron Pickaxe";
            case 7 -> "§3Mithril Slag Pickaxe / §bDiamond Pickaxe";
            case 8 -> "§bMithril Pickaxe";
            case 9 -> "§5Netherite Pickaxe";
            default -> "§7Any Pickaxe";
        };
    }

    /**
     * Tính toán số lượng rơi ra (hỗ trợ Fortune enchantment nếu cúp có).
     */
    private int getDropAmount(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == Material.AIR)
            return 1;

        int fortuneLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);
        if (fortuneLevel <= 0)
            return 1;

        // Công thức Fortune cơ bản của Minecraft: tăng lượng rơi trung bình
        double rand = Math.random();
        // Fortune 1: 33% cơ hội nhân đôi (trung bình 1.33x)
        // Fortune 2: 25% cơ hội nhân đôi, 25% nhân ba (trung bình 1.75x)
        // Fortune 3: 20% nhân đôi, 20% nhân ba, 20% nhân bốn (trung bình 2.2x)
        if (fortuneLevel == 1) {
            return rand < 0.33 ? 2 : 1;
        } else if (fortuneLevel == 2) {
            if (rand < 0.25)
                return 3;
            if (rand < 0.50)
                return 2;
            return 1;
        } else {
            if (rand < 0.20)
                return 4;
            if (rand < 0.40)
                return 3;
            if (rand < 0.60)
                return 2;
            return 1;
        }
    }
}

