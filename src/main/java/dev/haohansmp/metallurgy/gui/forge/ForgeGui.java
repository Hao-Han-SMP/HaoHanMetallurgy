package dev.haohansmp.metallurgy.gui.forge;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.gui.MetallurgyGui;
import dev.haohansmp.metallurgy.machine.MachineState;
import dev.haohansmp.metallurgy.machine.forge.AncientForge;
import dev.haohansmp.metallurgy.recipe.MetallurgyRecipe;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * GUI cho Ancient Forge — layout 3 rows (27 slots).
 *
 * <pre>
 *  [BG][BG][BG][FL][TP][BG][BG][BG][BG]   (0–8)
 *  [BG][BG][I1][I2][PR][BG][OT][BG][BG]   (9–17)
 *  [BG][BG][BG][BG][BG][BG][BG][BG][BG]   (18–26)
 * </pre>
 *
 * FL=Fuel(3) TP=Temperature(4) I1=Input1(11) I2=Input2(12)
 * PR=Progress(13) OT=Output(15)
 *
 * Fixes:
 * - Real-time refresh mỗi 10 ticks (0.5s) qua startRefreshTask()
 * - Interactive slots để TRỐNG (không placeholder) → không thể lấy nhầm
 * - onClick() cancel tất cả non-interactive interactions kể cả shift-click
 * - Drag events được xử lý bởi GuiManager
 */
@SuppressWarnings("deprecation")
public class ForgeGui extends MetallurgyGui {

    // ── Slot constants ────────────────────────────────────────
    public static final int SLOT_FUEL     = 3;
    public static final int SLOT_TEMP     = 4;
    public static final int SLOT_INPUT_1  = 11;
    public static final int SLOT_INPUT_2  = 12;
    public static final int SLOT_PROGRESS = 13;
    public static final int SLOT_OUTPUT   = 15;

    /**
     * Slots player ĐƯỢC PHÉP đặt / lấy item.
     * Không đặt bất kỳ display item nào ở đây!
     */
    public static final Set<Integer> INTERACTIVE = Set.of(
        SLOT_FUEL, SLOT_INPUT_1, SLOT_INPUT_2, SLOT_OUTPUT
    );

    /** Slots chỉ hiển thị — cancel mọi click. */
    private static final Set<Integer> DISPLAY_ONLY = Set.of(SLOT_TEMP, SLOT_PROGRESS);

    private final AncientForge forge;

    public ForgeGui(HaoHanMetallurgy plugin, AncientForge forge) {
        super(plugin);
        this.forge = forge;
    }

    // ── MetallurgyGui ─────────────────────────────────────────

    @Override
    protected void buildLayout() {
        inventory = forge.getInventory();

        // Background glass panes cho tất cả slots KHÔNG phải interactive
        ItemStack bg = makeDisplay(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (!INTERACTIVE.contains(i) && !DISPLAY_ONLY.contains(i)) {
                ItemStack current = inventory.getItem(i);
                if (current == null || current.getType() == Material.AIR || current.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                    inventory.setItem(i, bg);
                }
            }
        }
    }

    /**
     * Refresh tất cả display slots — gọi mỗi 10 ticks khi GUI đang mở.
     */
    @Override
    public void refresh() {
        if (inventory == null) return;

        // ── Temperature ──────────────────────────────────────
        int temp    = forge.getTemperature();
        int maxTemp = plugin.getConfigManager().getTempMax();
        int fuel    = forge.getFuelTicksRemaining();

        Material tempMat = temp < 400  ? Material.BLUE_STAINED_GLASS_PANE
                         : temp < 1000 ? Material.YELLOW_STAINED_GLASS_PANE
                         :               Material.RED_STAINED_GLASS_PANE;

        inventory.setItem(SLOT_TEMP, makeDisplay(tempMat,
            "§c🌡 " + temp + "§7/§f" + maxTemp + "°C",
            "§7Fuel còn: §f" + formatTicks(fuel),
            "§8Đặt fuel vào ô §6[" + SLOT_FUEL + "]§8 (bên trái)."
        ));

        // ── Progress ─────────────────────────────────────────
        float pct = forge.getProgressPercent();
        MetallurgyRecipe recipe = forge.getCurrentRecipe();
        MachineState state = forge.getState();

        String stateTxt = switch (state) {
            case WORKING -> "§aĐANG CHẠY";
            case PAUSED  -> "§eTẠM DỪNG §7(nhiệt thấp)";
            case ERROR   -> "§cLỖI";
            case IDLE    -> "§7Chờ...";
        };

        List<String> lore = new ArrayList<>();
        lore.add("§7Trạng thái: " + stateTxt);
        if (recipe != null) {
            lore.add("§7Recipe: §e" + recipe.getId());
            lore.add("§7" + buildBar(pct, 16) + " §f" + (int)(pct * 100) + "%");
            int remaining = (int)((1f - pct) * recipe.getTimeSeconds());
            lore.add("§7Còn lại: §f~" + remaining + "s");
        } else {
            lore.add("§7Đặt nguyên liệu vào ô §a[11]§7 và §a[12].");
        }

        Material progMat = state == MachineState.WORKING ? Material.CLOCK : Material.COMPARATOR;
        inventory.setItem(SLOT_PROGRESS, makeDisplayLore(progMat,
            "§b⟶ §f" + (int)(pct * 100) + "%", lore));
    }

    /**
     * open() — start real-time refresh task (10 ticks = 0.5 giây).
     */
    @Override
    public void open(Player player) {
        super.open(player);           // buildLayout + refresh + openInventory
        startRefreshTask(10L);        // refresh mỗi 10 ticks
    }

    /**
     * onClose() — parent tự hủy refreshTask.
     */
    @Override
    public void onClose(InventoryCloseEvent event) {
        super.onClose(event); // hủy refreshTask
    }

    /**
     * onClick() — chặt chẽ:
     * 1. Click trong player inventory → chỉ cancel shift-click (MOVE_TO_OTHER_INVENTORY)
     * 2. Click vào GUI top:
     *    - Display-only / background → luôn cancel
     *    - Interactive → cho phép, sau đó chạy logic
     */
    @Override
    public void onClick(InventoryClickEvent event) {
        // Click ngoài cửa sổ GUI -> Bỏ qua
        if (event.getClickedInventory() == null) {
            return;
        }

        int raw = event.getRawSlot();
        InventoryAction action = event.getAction();

        // ── Player's own inventory (bottom half) ─────────────
        if (raw >= inventory.getSize()) {
            event.setCancelled(false); // Cho phép tương tác trong túi đồ
            
            // Shift-click từ player inventory → cancel để ngăn item bay vào các slot hiển thị
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
            }
            return;
        }

        // ── Top GUI inventory ─────────────────────────────────

        // Display-only (temperature, progress) → luôn cancel
        if (DISPLAY_ONLY.contains(raw)) {
            event.setCancelled(true);
            return;
        }

        // Background (glass panes, v.v.) → luôn cancel
        if (!INTERACTIVE.contains(raw)) {
            event.setCancelled(true);
            return;
        }

        // Interactive slot — cho phép tương tác để đặt/lấy item
        event.setCancelled(false);

        // Schedule logic kiểm tra recipe và fuel sau 1 tick (chờ item được đặt vào slot)
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

        Material mat = fuelItem.getType();

        // 1. Kiểm tra xem có phải chất làm mát (Coolant) không
        var coolants = plugin.getConfigManager().getCoolants();
        if (coolants.containsKey(mat)) {
            int coolAmount = coolants.get(mat);
            
            // Hạ nhiệt độ lò rèn
            forge.coolDown(coolAmount * fuelItem.getAmount());

            // Tiêu thụ vật phẩm làm mát
            if (mat == Material.WATER_BUCKET) {
                inventory.setItem(SLOT_FUEL, new ItemStack(Material.BUCKET, 1));
            } else {
                inventory.setItem(SLOT_FUEL, null);
            }
            return;
        }

        // 2. Xử lý nạp nhiên liệu
        int ticks = plugin.getConfigManager().getFuelTicks(mat);
        if (ticks <= 0) return; // Không phải fuel hợp lệ → để nguyên

        forge.addFuel(ticks * fuelItem.getAmount(), mat);
        inventory.setItem(SLOT_FUEL, null); // consume
        plugin.getPluginLogger().debug(
            "Fuel: " + mat + "×" + fuelItem.getAmount()
            + " → +" + (ticks * fuelItem.getAmount()) + " ticks"
        );
    }

    private void checkAndStartRecipe(Player player) {
        if (forge.getState() != MachineState.IDLE) return;

        ItemStack item1 = inventory.getItem(SLOT_INPUT_1);
        ItemStack item2 = inventory.getItem(SLOT_INPUT_2);

        if (item1 == null || item1.getType() == Material.AIR) return;

        Optional<MetallurgyRecipe> match = findRecipe(item1, item2);
        if (match.isEmpty()) return;

        MetallurgyRecipe recipe = match.get();

        // Kiểm tra Advancement yêu cầu
        String reqAdv = recipe.getRequiredAdvancement();
        if (reqAdv != null && !reqAdv.isEmpty()) {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(reqAdv);
            if (key != null) {
                org.bukkit.advancement.Advancement adv = org.bukkit.Bukkit.getAdvancement(key);
                if (adv != null) {
                    if (!player.getAdvancementProgress(adv).isDone()) {
                        player.sendMessage("§8[§6Forge§8] §cBạn chưa đạt tiến trình rèn công thức này!");
                        return;
                    }
                }
            }
        }

        if (!forge.startRecipe(recipe)) {
            player.sendMessage("§8[§6Forge§8] §cCần §e" + recipe.getMinTemperature()
                + "°C§c, hiện: §e" + forge.getTemperature() + "°C");
            return;
        }

        // Consume inputs
        consumeInput(SLOT_INPUT_1, recipe.getInputs().get(0).amount());
        if (recipe.getInputs().size() > 1) {
            consumeInput(SLOT_INPUT_2, recipe.getInputs().get(1).amount());
        }
        player.sendMessage("§8[§6Forge§8] §aRecipe bắt đầu: §e" + recipe.getId());
    }

    private Optional<MetallurgyRecipe> findRecipe(ItemStack i1, ItemStack i2) {
        return plugin.getRecipeLoader()
            .getForMachine(forge.getType())
            .stream()
            .filter(r -> matchesInputs(r, i1, i2))
            .findFirst();
    }

    private boolean matchesInputs(MetallurgyRecipe r, ItemStack i1, ItemStack i2) {
        var ins = r.getInputs();
        if (ins.isEmpty()) return false;
        if (!ins.get(0).matches(i1.getType(), i1.getAmount())) return false;
        if (ins.size() == 1) return true;
        if (i2 == null || i2.getType() == Material.AIR) return false;
        return ins.get(1).matches(i2.getType(), i2.getAmount());
    }

    private void consumeInput(int slot, int amount) {
        ItemStack item = inventory.getItem(slot);
        if (item == null) return;
        int left = item.getAmount() - amount;
        if (left <= 0) {
            inventory.setItem(slot, null);
        } else {
            item.setAmount(left);
            inventory.setItem(slot, item);
        }
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

    private String buildBar(float pct, int len) {
        int filled = (int)(pct * len);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(i < filled ? "§a█" : "§8█");
        return sb.toString();
    }

    private String formatTicks(int ticks) {
        if (ticks <= 0) return "§cHết fuel";
        int s = ticks / 20;
        return s < 60 ? s + "s" : (s / 60) + "m" + (s % 60) + "s";
    }
}
