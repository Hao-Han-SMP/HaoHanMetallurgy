package dev.haohansmp.metallurgy.gui.forge;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.gui.MetallurgyGui;
import dev.haohansmp.metallurgy.machine.MachineState;
import dev.haohansmp.metallurgy.machine.forge.AncientForge;
import dev.haohansmp.metallurgy.recipe.MetallurgyRecipe;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
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
 *  [BG][BG][I1][I2][PR][BG][SL][OT][BG]   (9–17)
 *  [BG][BG][BG][BG][BG][BG][BG][BG][BG]   (18–26)
 * </pre>
 *
 * FL=Fuel(3) TP=Temperature(4) I1=Input1(11) I2=Input2(12)
 * PR=Progress(13) SL=Slag output(15) OT=Ingot output(16)
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
    public static final int SLOT_FUEL_2   = 2;
    public static final int SLOT_FUEL     = 3;
    public static final int SLOT_TEMP     = 4;
    public static final int SLOT_ADDITIVE = 10;
    public static final int SLOT_INPUT_1  = 11;
    public static final int SLOT_INPUT_2  = 12;
    public static final int SLOT_PROGRESS = 13;
    public static final int SLOT_SLAG     = 15;
    public static final int SLOT_OUTPUT   = 16;

    /**
     * Slots player ĐƯỢC PHÉP đặt / lấy item.
     * Không đặt bất kỳ display item nào ở đây!
     */
    public static final Set<Integer> INTERACTIVE = Set.of(
        SLOT_FUEL, SLOT_FUEL_2, SLOT_INPUT_1, SLOT_INPUT_2, SLOT_ADDITIVE, SLOT_OUTPUT, SLOT_SLAG
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
        if (plugin.getConfigManager().isForgeCustomGuiEnabled()) {
            for (int i = 0; i < 27; i++) {
                if (!INTERACTIVE.contains(i) && !DISPLAY_ONLY.contains(i)) {
                    ItemStack current = inventory.getItem(i);
                    if (current == null || current.getType() == Material.AIR || current.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                        inventory.setItem(i, null);
                    }
                }
            }
            return;
        }

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
        int fuel1   = forge.getFuelTicksRemaining1();
        int fuel2   = forge.getFuelTicksRemaining2();
        int fuelLimit = plugin.getConfigManager().getCombinedFuelLimit(
                fuel1 > 0 ? forge.getActiveFuelType1() : null,
                fuel2 > 0 ? forge.getActiveFuelType2() : null);

        Material tempMat = temp < 400  ? Material.BLUE_STAINED_GLASS_PANE
                         : temp < 1000 ? Material.YELLOW_STAINED_GLASS_PANE
                         :               Material.RED_STAINED_GLASS_PANE;

        String tempStatus = temp < 400  ? "§bLạnh (Chưa sẵn sàng)"
                          : temp < 1000 ? "§eẤm (Đang gia nhiệt)"
                          :               "§cNóng Rực (Tối đa)";

        inventory.setItem(SLOT_TEMP, makeDisplay(tempMat,
            "§c🌡 Nhiệt độ: " + tempStatus + " §7(" + temp + "§7/§f" + maxTemp + "°C)",
            "§7Buồng 1: " + formatFuel(forge.getActiveFuelType1(), fuel1),
            "§7Buồng 2: " + formatFuel(forge.getActiveFuelType2(), fuel2),
            "§7Giới hạn tổ hợp: §f" + fuelLimit + "°C"
        ));

        // ── Progress ─────────────────────────────────────────
        float pct = forge.getProgressPercent();
        MetallurgyRecipe recipe = forge.getCurrentRecipe();
        MachineState state = forge.getState();

        String stateTxt = switch (state) {
            case WORKING -> "§aĐANG CHẠY";
            case PAUSED  -> getPauseStatus(recipe);
            case ERROR   -> "§cLỖI";
            case IDLE    -> "§7Chờ nguyên liệu...";
        };

        List<String> lore = new ArrayList<>();
        lore.add("§7Trạng thái: " + stateTxt);
        if (recipe != null) {
            lore.add("§7Công thức: §e" + recipe.getId());
            lore.add("§7" + buildBar(pct, 16) + " §f" + (int)(pct * 100) + "%");
            int remaining = (int) Math.ceil((forge.getTotalTicks() - forge.getProgressTicks()) / 20.0);
            lore.add("§7Còn lại: §f~" + remaining + "s");
            int averageTemperature = forge.getAverageProcessTemperature();
            int purificationTemperature = recipe.getPurificationTemperature();
            lore.add("§7Nhiệt trung bình: §f" + averageTemperature + "°C");
            lore.add("§7Nhiệt tinh luyện: §f" + purificationTemperature + "°C");
            lore.add(averageTemperature >= purificationTemperature
                    ? "§7Độ tinh khiết: §aĐạt chuẩn"
                    : "§7Độ tinh khiết: §cNhiều tạp chất → dễ ra sỉ");
            if (recipe.requiresColdQuench()) {
                lore.add("§7Quench lạnh: " + (forge.hasColdQuenchEnvironment() ? "§aSẵn sàng" : "§cCòn thiếu"));
            }
            if (recipe.requiresSoulFire()) {
                lore.add("§7Soul Fire: " + (forge.hasSoulFireEnvironment() ? "§aĐang cộng hưởng" : "§cCòn thiếu"));
            }
            lore.add(forge.isHasAdditive()
                    ? "§7Trợ dung: §a+" + (int) Math.round(forge.getActiveAdditiveCleanOutputBonus() * 100)
                            + "% quặng sạch"
                    : "§7Trợ dung: §cKhông có §8(rủi ro sỉ cao)");
            lore.add("§7Ước tính quặng sạch: §f"
                    + (int) Math.round(forge.getEstimatedCleanOutputChance() * 100) + "%");
        } else {
            lore.add("§7Đặt nguyên liệu tương ứng vào các ô IN.");
        }

        Material progMat = (state == MachineState.WORKING || state == MachineState.PAUSED) ? Material.CLOCK : Material.COMPARATOR;
        inventory.setItem(SLOT_PROGRESS, makeDisplayLore(progMat,
            "§b⟶ Tiến trình rèn: §f" + (int)(pct * 100) + "%", lore));
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
        plugin.getMachineManager().saveAll();
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
        if (raw == SLOT_OUTPUT || raw == SLOT_SLAG) {
            event.setCancelled(!isOutputExtraction(action));
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            if ((raw == SLOT_FUEL || raw == SLOT_FUEL_2)
                    && !plugin.getConfigManager().isFuel(cursor.getType())
                    && !plugin.getConfigManager().getCoolants().containsKey(cursor.getType())) {
                event.setCancelled(true);
                return;
            }
            if (raw == SLOT_ADDITIVE && !isKnownAdditive(cursor.getType())) {
                event.setCancelled(true);
                return;
            }
        }

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
        handleFuelSlotDeposit(SLOT_FUEL);
        handleFuelSlotDeposit(SLOT_FUEL_2);
    }

    private void handleFuelSlotDeposit(int slot) {
        ItemStack fuelItem = inventory.getItem(slot);
        if (fuelItem == null || fuelItem.getType() == Material.AIR) return;

        Material mat = fuelItem.getType();

        // 1. Kiểm tra xem có phải chất làm mát (Coolant) không
        var coolants = plugin.getConfigManager().getCoolants();
        if (coolants.containsKey(mat)) {
            int coolAmount = coolants.get(mat);
            
            // Hạ nhiệt độ lò rèn
            forge.coolDown(coolAmount);

            // Mỗi thao tác chỉ dùng một đơn vị coolant, tránh xóa cả stack.
            if (mat == Material.WATER_BUCKET) {
                inventory.setItem(slot, new ItemStack(Material.BUCKET, 1));
            } else if (fuelItem.getAmount() > 1) {
                fuelItem.setAmount(fuelItem.getAmount() - 1);
                inventory.setItem(slot, fuelItem);
            } else {
                inventory.setItem(slot, null);
            }
        }
    }

    private void checkAndStartRecipe(Player player) {
        if (forge.getState() != MachineState.IDLE) return;

        ItemStack item1 = inventory.getItem(SLOT_INPUT_1);
        ItemStack item2 = inventory.getItem(SLOT_INPUT_2);

        boolean item1Empty = item1 == null || item1.getType() == Material.AIR;
        boolean item2Empty = item2 == null || item2.getType() == Material.AIR;
        if (item1Empty && item2Empty) return;

        Optional<MetallurgyRecipe> match = findRecipe(item1, item2);
        if (match.isEmpty()) return;

        MetallurgyRecipe recipe = match.get();

        if (recipe.requiresColdQuench() && !forge.hasColdQuenchEnvironment()) {
            player.sendMessage("§8[§6Forge§8] §cMithril cần quench lạnh: đặt Ice/Packed Ice/Blue Ice"
                    + " gần lò hoặc xây lò trong biome lạnh.");
            return;
        }
        if (recipe.requiresSoulFire() && !forge.hasSoulFireEnvironment()) {
            player.sendMessage("§8[§6Forge§8] §cCông thức này cần Soul Fire hoặc Soul Campfire gần lò.");
            return;
        }

        if (!forge.hasRequiredAdditive(recipe)) {
            String accepted = recipe.getRequiredAdditives().stream()
                    .map(Material::name)
                    .collect(java.util.stream.Collectors.joining(" hoặc "));
            player.sendMessage("§8[§6Forge§8] §cCông thức này bắt buộc cần trợ dung §e"
                    + accepted + " x" + recipe.getAdditiveAmount() + "§c ở ô FLUX.");
            return;
        }

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
            player.sendMessage("§8[§6Forge§8] §cChưa đủ nhiệt nóng chảy. Cần §e" + recipe.getMinTemperature()
                + "°C§c, hiện tại: §e" + forge.getTemperature() + "°C");
            return;
        }

        consumeMatchedInputs(recipe, item1, item2);
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
        if (ins.isEmpty() || ins.size() > 2) return false;

        boolean i1Empty = i1 == null || i1.getType() == Material.AIR;
        boolean i2Empty = i2 == null || i2.getType() == Material.AIR;

        if (ins.size() == 1) {
            return (ins.get(0).matches(i1) && i2Empty)
                    || (ins.get(0).matches(i2) && i1Empty);
        } else { // ins.size() == 2
            if (i1Empty || i2Empty) return false;
            boolean order1 = ins.get(0).matches(i1) && ins.get(1).matches(i2);
            boolean order2 = ins.get(0).matches(i2) && ins.get(1).matches(i1);
            return order1 || order2;
        }
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

    private void consumeMatchedInputs(MetallurgyRecipe recipe, ItemStack item1, ItemStack item2) {
        var inputs = recipe.getInputs();
        if (inputs.size() == 1) {
            int slot = inputs.get(0).matches(item1) ? SLOT_INPUT_1 : SLOT_INPUT_2;
            consumeInput(slot, inputs.get(0).amount());
            return;
        }

        if (inputs.get(0).matches(item1) && inputs.get(1).matches(item2)) {
            consumeInput(SLOT_INPUT_1, inputs.get(0).amount());
            consumeInput(SLOT_INPUT_2, inputs.get(1).amount());
        } else {
            consumeInput(SLOT_INPUT_1, inputs.get(1).amount());
            consumeInput(SLOT_INPUT_2, inputs.get(0).amount());
        }
    }

    private boolean isKnownAdditive(Material material) {
        if (plugin.getConfigManager().isDefaultAdditive(material)) return true;
        return plugin.getRecipeLoader().getForMachine(forge.getType()).stream()
                .anyMatch(recipe -> recipe.acceptsAdditive(material));
    }

    private boolean isOutputExtraction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME,
                    MOVE_TO_OTHER_INVENTORY, DROP_ALL_SLOT, DROP_ONE_SLOT -> true;
            default -> false;
        };
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

    private String formatFuel(Material material, int ticks) {
        if (material == null || ticks <= 0) return "§cTắt";
        return "§e" + material.name() + " §7(" + formatTicks(ticks) + "§7)";
    }

    private String getPauseStatus(MetallurgyRecipe recipe) {
        if (recipe != null && recipe.requiresColdQuench() && !forge.hasColdQuenchEnvironment()) {
            return "§eTẠM DỪNG §7(thiếu quench lạnh)";
        }
        if (recipe != null && recipe.requiresSoulFire() && !forge.hasSoulFireEnvironment()) {
            return "§eTẠM DỪNG §7(thiếu Soul Fire)";
        }
        return "§eTẠM DỪNG §7(nhiệt thấp)";
    }
}
