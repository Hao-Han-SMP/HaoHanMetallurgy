package vn.haohan.metallurgy.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import vn.haohan.metallurgy.HaoHanMetallurgy;

/** Gives each player the one-time Vietnamese Ancient Forge construction guide. */
public final class MetallurgyGuideBookListener implements Listener {
    private final HaoHanMetallurgy plugin;
    private final NamespacedKey receivedKey;

    public MetallurgyGuideBookListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        this.receivedKey = new NamespacedKey(plugin, "received_guide_book");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getPersistentDataContainer().has(receivedKey, PersistentDataType.BYTE)) return;

        // Wait one tick so the player's inventory and client are fully ready after joining.
        plugin.getServer().getScheduler().runTask(plugin, () -> giveGuide(player));
    }

    public void giveGuide(Player player) {
        if (!player.isOnline()) return;

        ItemStack book = createGuideBook();
        for (ItemStack leftover : player.getInventory().addItem(book).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.getPersistentDataContainer().set(receivedKey, PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(Component.text("Bạn đã nhận được sách hướng dẫn Lò Rèn Cổ Đại!", NamedTextColor.GOLD));
    }

    public void giveGuideAgain(Player player) {
        if (player.isOnline()) giveGuide(player);
    }

    private ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Hướng dẫn luyện kim");
        meta.setAuthor("HaoHan Metallurgy");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        meta.addPages(
                logoPage(),
                page(
                        heading("CÁCH DỰNG LÒ"),
                        line("\nLò Rèn Cổ Đại có 4 tầng, đặt chồng đúng tâm.", NamedTextColor.BLACK),
                        line("\nTrang tiếp theo là danh sách vật phẩm cần chuẩn bị.", NamedTextColor.DARK_GRAY),
                        line("\nSau đó xem sơ đồ và làm theo từng tầng.", NamedTextColor.GOLD)),
                page(
                        heading("VẬT PHẨM CẦN CÓ"),
                        line("\n", NamedTextColor.BLACK),
                        itemLine("\uE230", "Mud Bricks ×9", NamedTextColor.DARK_RED),
                        itemLine("\n\uE231", "Cobblestone ×25", NamedTextColor.DARK_GRAY),
                        itemLine("\n\uE232", "Blast Furnace ×1", NamedTextColor.DARK_GRAY),
                        itemLine("\n\uE233", "Cauldron ×1", NamedTextColor.DARK_GRAY),
                        line("\n\nTổng: 36 khối/vật phẩm", NamedTextColor.GOLD)),
                structurePage(),
                activationPage(),
                oreOverviewPage(),
                basicRecipesPage(),
                advancedRecipesPage(),
                notesPage()
        );
        book.setItemMeta(meta);
        return book;
    }

    private Component page(Component... parts) {
        Component result = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) result = result.append(Component.text("\n"));
            result = result.append(parts[i]);
        }
        return result;
    }

    private Component imagePage(String glyph) {
        // Bitmap font glyphs are tinted by the text color. Keep the original
        // texture colors instead of Minecraft's default black text color.
        return Component.text(glyph)
                .font(Key.key("haohan:guide_book"))
                .color(NamedTextColor.WHITE);
    }

    private Component logoPage() {
        Component rule = Component.text("\uE270")
                .font(Key.key("haohan:guide_book"))
                .color(NamedTextColor.WHITE);
        return page(
                rule,
                Component.text("    ").append(imagePage("\uE260")),
                rule);
    }

    private Component itemLine(String glyph, String text, NamedTextColor color) {
        return Component.text(glyph)
                .font(Key.key("haohan:book_icons"))
                .color(NamedTextColor.WHITE)
                .append(Component.text("  " + text, color)
                        .font(Key.key("minecraft:default")));
    }

    private Component structurePage() {
        return page(
                heading("CẤU TRÚC LÒ RÈN"),
                Component.text("     ").append(imagePage("\uE221")));
    }

    private Component activationPage() {
        return page(
                heading("KÍCH HOẠT LÒ"),
                line("\n1. Đứng trước Blast Furnace ở tầng lò.", NamedTextColor.BLACK),
                line("\n2. Nhấp chuột phải để mở giao diện.", NamedTextColor.BLACK),
                line("\n3. Cho quặng, nhiên liệu và flux theo công thức.", NamedTextColor.BLACK),
                line("\n\nNếu không nhận diện được, hãy kiểm tra:", NamedTextColor.GOLD),
                line("\n• Đủ 3×3×4 và đúng vật liệu.", NamedTextColor.DARK_GRAY),
                line("\n• Cauldron nằm đúng trên Blast Furnace.", NamedTextColor.DARK_GRAY),
                line("\n• Blast Furnace là khối ở chính giữa.", NamedTextColor.DARK_GRAY));
    }

    private Component oreOverviewPage() {
        return page(
                heading("QUẶNG & KIM LOẠI"),
                line("\nQuặng vanilla dùng để luyện kim cơ bản:", NamedTextColor.BLACK),
                line("\n• Đồng thô → Phôi Đồng", NamedTextColor.DARK_RED),
                line("\n• Sắt thô → Phôi Sắt", NamedTextColor.DARK_GRAY),
                line("\n• Vàng thô → Phôi Vàng", NamedTextColor.GOLD),
                line("\n\nKhoáng vật của HaoHan:", NamedTextColor.AQUA),
                itemLine("\uE234", "Quặng Borax → Borax thô → Bột Borax", NamedTextColor.DARK_GRAY),
                itemLine("\n\uE235", "Quặng Mithril → Mảnh/Phôi Mithril", NamedTextColor.DARK_GRAY),
                itemLine("\n\uE236", "Quặng Ilmenite: khoáng vật giàu titan", NamedTextColor.DARK_GRAY),
                itemLine("\n\uE237", "Quặng Pyroxene: khoáng vật silicat", NamedTextColor.DARK_GRAY));
    }

    private Component basicRecipesPage() {
        return page(
                heading("LUYỆN CƠ BẢN"),
                itemLine("\n\uE240\uE241", "Đồng thô → Phôi Đồng", NamedTextColor.DARK_RED),
                line("\n700–950°C  •  10 giây  •  1 Bột Borax", NamedTextColor.DARK_GRAY),
                itemLine("\n\n\uE242\uE243", "Sắt thô → Phôi Sắt", NamedTextColor.DARK_GRAY),
                line("\n900–1150°C  •  10 giây  •  1 Bột Borax", NamedTextColor.DARK_GRAY),
                itemLine("\n\n\uE244\uE245", "Vàng thô → Phôi Vàng", NamedTextColor.GOLD),
                line("\n900–1350°C  •  10 giây  •  1 Bột Borax", NamedTextColor.DARK_GRAY));
    }

    private Component advancedRecipesPage() {
        return page(
                heading("HỢP KIM CAO CẤP"),
                itemLine("\n\uE243\uE245\uE247", "Sắt + Vàng → Embersteel", NamedTextColor.GOLD),
                line("\n1200–1450°C  •  30 giây  •  Borax", NamedTextColor.DARK_GRAY),
                itemLine("\n\n\uE247\uE248\uE249", "Embersteel + Kim cương → Mithril", NamedTextColor.AQUA),
                line("\n1350–1550°C  •  15 giây  •  Borax  •  Làm nguội lạnh", NamedTextColor.DARK_GRAY),
                itemLine("\n\n\uE249\uE24A\uE24B", "Mithril + Soul Soil/Sand → Soulsteel", NamedTextColor.DARK_PURPLE),
                line("\n1550–1750°C  •  25 giây  •  Borax  •  Lửa linh hồn", NamedTextColor.DARK_GRAY),
                itemLine("\n\n\uE24C\uE245\uE24D", "Scrap + Vàng → Netherite", NamedTextColor.DARK_GRAY));
    }

    private Component notesPage() {
        return page(
                heading("LƯU Ý LUYỆN KIM"),
                line("\n• Giữ nhiệt trong khoảng công thức; quá nóng có thể làm hỏng mẻ.", NamedTextColor.BLACK),
                line("\n• Bột Borax giúp tăng cơ hội nhận kim loại sạch.", NamedTextColor.GOLD),
                line("\n• Không có Borax, lượng sỉ tạo ra sẽ cao hơn.", NamedTextColor.DARK_GRAY),
                line("\n• Gom 4 sỉ để tái chế lại trong Ancient Forge.", NamedTextColor.DARK_GRAY),
                line("\n• Mithril cần làm nguội lạnh; Soulsteel cần Lửa linh hồn.", NamedTextColor.AQUA),
                line("\n• Một số công thức cao cấp yêu cầu mở khóa advancement.", NamedTextColor.DARK_PURPLE));
    }

    private Component heading(String text) {
        return Component.text(text, NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    private Component line(String text, NamedTextColor color) {
        return Component.text(text, color);
    }
}
