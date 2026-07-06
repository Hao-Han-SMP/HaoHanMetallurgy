package dev.haohansmp.metallurgy.gui.forge;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.gui.MetallurgyGui;
import dev.haohansmp.metallurgy.machine.MachineState;
import dev.haohansmp.metallurgy.machine.forge.AncientForge;
import dev.haohansmp.metallurgy.recipe.MetallurgyRecipe;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * GUI cho Ancient Forge.
 *
 * Layout (3 rows = 27 slots):
 * <pre>
 *  [BG][BG][BG][FL][TP][BG][BG][BG][BG]   ← row 0 (0–8)
 *  [BG][BG][I1][I2][PR][BG][OT][BG][BG]   ← row 1 (9–17)
 *  [BG][BG][BG][BG][BG][BG][BG][BG][BG]   ← row 2 (18–26)
 *
 *  BG = background (gray glass pane)
 *  FL = Fuel slot        (slot 3)
 *  TP = Temperature      (slot 4)
 *  I1 = Input 1          (slot 11)
 *  I2 = Input 2          (slot 12)
 *  PR = Progress         (slot 13)
 *  OT = Output           (slot 15)
 * </pre>
 */
@SuppressWarnings("deprecation")
public class ForgeGui extends MetallurgyGui {

    // ── Slot indices ──────────────────────────────────────────
    private static final int SLOT_FUEL     = 3;
    private static final int SLOT_TEMP     = 4;
    private static final int SLOT_INPUT_1  = 11;
    private static final int SLOT_INPUT_2  = 12;
    private static final int SLOT_PROGRESS = 13;
    private static final int SLOT_OUTPUT   = 15;

    /** Slots player được phép đặt / lấy item. */
    private static final Set<Integer> INTERACTIVE = Set.of(
        SLOT_FUEL, SLOT_INPUT_1, SLOT_INPUT_2, SLOT_OUTPUT
    );

    /** Slots chỉ hiển thị, không cho tương tác. */
    private static final Set<Integer> DISPLAY_ONLY = Set.of(SLOT_TEMP, SLOT_PROGRESS);

    private final AncientForge forge;

    public ForgeGui(HaoHanMetallurgy plugin, AncientForge forge) {
        super(plugin);
        this.forge = forge;
    }

    // ── MetallurgyGui ─────────────────────────────────────────

    @Override
    protected void buildLayout() {
        String title = plugin.getConfigManager().getForgeTitle();
        inventory = Bukkit.createInventory(null, 27, title);

        // Background glass panes
        ItemStack bg = makeDisplay(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (!INTERACTIVE.contains(i) && !DISPLAY_ONLY.contains(i)) {
                inventory.setItem(i, bg);
            }
        }

        // Placeholder labels for interactive slots
        inventory.setItem(SLOT_FUEL, makeDisplay(Material.COAL,
            "§6⬡ Nhiên liệu",
            "§7Đặt nhiên liệu vào đây.",
            "§8Coal, Blaze Rod, Lava Bucket..."
        ));
        inventory.setItem(SLOT_INPUT_1, makeDisplay(Material.LIME_STAINED_GLASS_PANE,
            "§a① Nguyên liệu 1",
            "§7Đặt nguyên liệu vào đây."
        ));
        inventory.setItem(SLOT_INPUT_2, makeDisplay(Material.LIME_STAINED_GLASS_PANE,
            "§a② Nguyên liệu 2",
            "§7Nguyên liệu phụ (nếu có)."
        ));
        inventory.setItem(SLOT_OUTPUT, makeDisplay(Material.YELLOW_STAINED_GLASS_PANE,
            "§e⬡ Output",
            "§7Lấy sản phẩm từ đây."
        ));
    }

    @Override
    public void refresh() {
        if (inventory == null) buildLayout();

        // ── Temperature display ──────────────────────────────
        int temp    = forge.getTemperature();
        int maxTemp = plugin.getConfigManager().getTempMax();
        int fuel    = forge.getFuelTicksRemaining();

        Material tempMat = temp < 400  ? Material.BLUE_STAINED_GLASS_PANE
                         : temp < 1000 ? Material.YELLOW_STAINED_GLASS_PANE
                         :               Material.RED_STAINED_GLASS_PANE;

        inventory.setItem(SLOT_TEMP, makeDisplay(tempMat,
            "§c🌡 Nhiệt độ: §f" + temp + "§7/§f" + maxTemp + "°C",
            "§7Fuel còn: §f" + formatTicks(fuel),
            "§8Nhiên liệu tiêu thụ khi chạy recipe."
        ));

        // ── Progress display ─────────────────────────────────
        float pct = forge.getProgressPercent();
        MetallurgyRecipe recipe = forge.getCurrentRecipe();
        MachineState state = forge.getState();

        String stateLine = switch (state) {
            case WORKING -> "§aĐANG CHẠY";
            case PAUSED  -> "§eĐÃ TẠM DỪNG §7(nhiệt độ thấp)";
            case ERROR   -> "§cLỖI";
            case IDLE    -> "§7Chờ nguyên liệu...";
        };

        List<String> progressLore = new ArrayList<>();
        progressLore.add("§7Trạng thái: " + stateLine);
        if (recipe != null) {
            progressLore.add("§7Recipe: §e" + recipe.getId());
            progressLore.add("§7Tiến trình: §f" + (int)(pct * 100) + "%");
            progressLore.add("§7" + buildBar(pct, 14));
        } else {
            progressLore.add("§7Chưa có recipe nào phù hợp.");
        }

        Material progressMat = state == MachineState.WORKING ? Material.CLOCK : Material.COMPARATOR;
        inventory.setItem(SLOT_PROGRESS, makeDisplayLore(progressMat,
            "§b⟶ Tiến trình §f" + (int)(pct * 100) + "%", progressLore));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // Click ngoài inventory (trong túi player) → cho phép
        if (slot >= 27) {
            event.setCancelled(false);
            return;
        }

        // Display-only slot → luôn cancel
        if (DISPLAY_ONLY.contains(slot)) {
            event.setCancelled(true);
            return;
        }

        // Background slots → cancel
        if (!INTERACTIVE.contains(slot)) {
            event.setCancelled(true);
            return;
        }

        // Interactive slot → cho phép, schedule xử lý sau khi click resolve
        event.setCancelled(false);
        Player player = (Player) event.getWhoClicked();
        Bukkit.getScheduler().runTask(plugin, () -> {
            handleFuelDeposit();
            checkAndStartRecipe(player);
            refresh();
        });
    }

    // ── Internal logic ─────────────────────────────────────────

    private void handleFuelDeposit() {
        ItemStack fuelItem = inventory.getItem(SLOT_FUEL);
        if (fuelItem == null || fuelItem.getType() == Material.AIR) return;
        // Skip placeholder items
        if (fuelItem.getType() == Material.COAL && hasCustomName(fuelItem)) return;

        int ticks = plugin.getConfigManager().getFuelTicks(fuelItem.getType());
        if (ticks <= 0) return;

        int total = ticks * fuelItem.getAmount();
        forge.addFuel(total);
        inventory.setItem(SLOT_FUEL, null); // consume fuel item

        plugin.getPluginLogger().debug(
            "Fuel added to forge: " + fuelItem.getType() + "×" + fuelItem.getAmount()
            + " = " + total + " ticks"
        );
    }

    private void checkAndStartRecipe(Player player) {
        if (forge.getState() != MachineState.IDLE) return;

        ItemStack item1 = inventory.getItem(SLOT_INPUT_1);
        ItemStack item2 = inventory.getItem(SLOT_INPUT_2);

        // Ignore empty or placeholder slots
        if (isPlaceholder(item1)) return;
        if (item1 == null || item1.getType() == Material.AIR) return;

        Optional<MetallurgyRecipe> match = findRecipe(item1, item2);
        if (match.isEmpty()) return;

        MetallurgyRecipe recipe = match.get();
        if (!forge.startRecipe(recipe)) {
            // Machine is IDLE but temp too low
            player.sendMessage("§8[§6Forge§8] §cNhiệt độ chưa đủ! "
                + "Cần §e" + recipe.getMinTemperature() + "°C§c, "
                + "hiện: §e" + forge.getTemperature() + "°C");
            return;
        }

        // Consume inputs
        consumeInput(SLOT_INPUT_1, recipe.getInputs().get(0).amount());
        if (recipe.getInputs().size() > 1) {
            consumeInput(SLOT_INPUT_2, recipe.getInputs().get(1).amount());
        }

        player.sendMessage("§8[§6Forge§8] §aRecipe bắt đầu: §e" + recipe.getId());
    }

    private Optional<MetallurgyRecipe> findRecipe(ItemStack item1, ItemStack item2) {
        return plugin.getRecipeLoader()
            .getForMachine(forge.getType())
            .stream()
            .filter(r -> matchesInputs(r, item1, item2))
            .findFirst();
    }

    private boolean matchesInputs(MetallurgyRecipe r, ItemStack i1, ItemStack i2) {
        var ins = r.getInputs();
        if (ins.isEmpty()) return false;

        if (!ins.get(0).matches(i1.getType(), i1.getAmount())) return false;
        if (ins.size() == 1) return true;

        if (isPlaceholder(i2) || i2 == null || i2.getType() == Material.AIR) return false;
        return ins.get(1).matches(i2.getType(), i2.getAmount());
    }

    private void consumeInput(int slot, int amount) {
        ItemStack item = inventory.getItem(slot);
        if (item == null) return;
        int remaining = item.getAmount() - amount;
        if (remaining <= 0) {
            inventory.setItem(slot, null);
        } else {
            item.setAmount(remaining);
        }
    }

    private boolean isPlaceholder(ItemStack item) {
        return item != null && item.hasItemMeta() && hasCustomName(item)
            && (item.getType() == Material.LIME_STAINED_GLASS_PANE
            || item.getType() == Material.COAL);
    }

    private boolean hasCustomName(ItemStack item) {
        return item.hasItemMeta() && item.getItemMeta() != null
            && item.getItemMeta().hasDisplayName();
    }

    // ── Item builder helpers ───────────────────────────────────

    private ItemStack makeDisplay(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeDisplayLore(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String buildBar(float percent, int length) {
        int filled = (int) (percent * length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(i < filled ? "§a█" : "§8█");
        }
        return sb.toString();
    }

    private String formatTicks(int ticks) {
        if (ticks <= 0) return "§cHết fuel";
        int seconds = ticks / 20;
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
