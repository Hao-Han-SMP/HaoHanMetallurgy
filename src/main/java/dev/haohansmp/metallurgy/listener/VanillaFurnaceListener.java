package dev.haohansmp.metallurgy.listener;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.item.CustomItem;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Furnace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.inventory.ItemStack;

public class VanillaFurnaceListener implements Listener {

    private final HaoHanMetallurgy plugin;
    private static final int VANILLA_FURNACE_COOK_TIME_MULTIPLIER = 5;
    private static final int VANILLA_FURNACE_FUEL_BURN_DIVIDER = 5;
    private static final double VANILLA_FURNACE_FAIL_CHANCE = 0.30;

    public VanillaFurnaceListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceStartSmelt(FurnaceStartSmeltEvent event) {
        // Chỉ áp dụng khi nung các loại quặng thô/quặng thỏi trong lò nung cơ bản
        if (isSmeltableOre(event.getSource().getType())) {
            // Lò nung vanilla sẽ nung cực lâu (tổng thời gian nung tăng gấp 5 lần)
            int newTotalTime = event.getTotalCookTime() * VANILLA_FURNACE_COOK_TIME_MULTIPLIER;
            event.setTotalCookTime(newTotalTime);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (event.getBlock().getState() instanceof Furnace furnace) {
            ItemStack smelting = furnace.getInventory().getSmelting();
            if (smelting != null && isSmeltableOre(smelting.getType())) {
                // Nhiên liệu cháy nhanh gấp 5 lần -> Cực kỳ tốn nhiên liệu khi nung quặng ở lò thường
                int newBurnTime = Math.max(1, event.getBurnTime() / VANILLA_FURNACE_FUEL_BURN_DIVIDER);
                event.setBurnTime(newBurnTime);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        Material source = event.getSource().getType();
        if (isSmeltableOre(source)) {
            // Có tỷ lệ hao hụt / thất bại nhất định (30%)
            double rand = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
            if (rand < VANILLA_FURNACE_FAIL_CHANCE) {
                ItemStack slagResult;
                java.util.Optional<CustomItem> customSlag = CustomItem.getSlagForMaterial(source);
                if (customSlag.isPresent()) {
                    slagResult = plugin.getItemManager().createItem(customSlag.get(), 1);
                } else {
                    slagResult = new ItemStack(Material.CHARCOAL, 1);
                    org.bukkit.inventory.meta.ItemMeta meta = slagResult.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§8Slag (Xỉ Thải)");
                        meta.setLore(java.util.List.of("§7Nung quặng thất bại trong lò nung cơ bản."));
                        slagResult.setItemMeta(meta);
                    }
                }
                
                event.setResult(slagResult);
                
                // Hiệu ứng và âm thanh xì lò/cháy hỏng
                event.getBlock().getWorld().playSound(
                    event.getBlock().getLocation(), 
                    Sound.BLOCK_LAVA_EXTINGUISH, 
                    1.0f, 
                    1.0f
                );
            }
        }
    }

    private boolean isSmeltableOre(Material material) {
        if (material == null) return false;
        String name = material.name();
        return name.contains("ORE") || name.startsWith("RAW_");
    }
}
