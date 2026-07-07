package dev.haohansmp.metallurgy.item;

import org.bukkit.Material;
import java.util.List;

/**
 * Định nghĩa danh sách các Custom Items trong hệ thống HaoHan Metallurgy.
 */
public enum CustomItem {

    EMBER_SHARD(
        "ember_shard",
        Material.MAGMA_CREAM,
        1001,
        "§6Ember Shard",
        List.of("§7Mảnh tinh thể chứa năng lượng lửa Nether.", "§8Dùng để làm chất xúc tác rèn Embersteel.")
    ),
    EMBERSTEEL_INGOT(
        "embersteel_ingot",
        Material.IRON_INGOT,
        1002,
        "§6Embersteel Ingot",
        List.of("§7Hợp kim rèn dưới nhiệt độ cực cao của lửa Ember.", "§8Tier: §c2")
    ),
    SOUL_CRYSTAL(
        "soul_crystal",
        Material.ECHO_SHARD,
        1003,
        "§bSoul Crystal",
        List.of("§7Tinh thể ngưng tụ từ linh hồn nơi u tối.", "§8Dùng để rèn thép Soulsteel.")
    ),
    SOULSTEEL_INGOT(
        "soulsteel_ingot",
        Material.NETHERITE_INGOT,
        1004,
        "§bSoulsteel Ingot",
        List.of("§7Hợp kim thép hòa quyện cùng năng lượng tâm linh.", "§8Tier: §c3")
    ),
    EMBERSTEEL_PICKAXE(
        "embersteel_pickaxe",
        Material.IRON_PICKAXE,
        1005,
        "§6Embersteel Pickaxe",
        List.of("§7Cúp thép Ember cứng cáp.", "§7Đủ sức khai thác §bSoul Ore§7.", "§8Tier: §c2")
    ),
    SOULSTEEL_PICKAXE(
        "soulsteel_pickaxe",
        Material.DIAMOND_PICKAXE,
        1006,
        "§bSoulsteel Pickaxe",
        List.of("§7Cúp tâm linh tối thượng.", "§7Đủ sức khai thác §dAncient Debris§7.", "§8Tier: §c3")
    ),
    COPPER_SLAG(
        "copper_slag",
        Material.BRICK,
        1011,
        "§cCopper Slag",
        List.of("§7Sỉ đồng thu được từ nung quặng hao hụt.", "§8Dùng để làm cúp sỉ đồng.")
    ),
    IRON_SLAG(
        "iron_slag",
        Material.CLAY_BALL,
        1012,
        "§8Iron Slag",
        List.of("§7Sỉ sắt thu được từ nung quặng hao hụt.", "§8Dùng để làm cúp sỉ sắt.")
    ),
    COPPER_SLAG_PICKAXE(
        "copper_slag_pickaxe",
        Material.STONE_PICKAXE,
        1013,
        "§cCopper Slag Pickaxe",
        List.of("§7Cúp được đúc tạm thời từ sỉ đồng.", "§8Tier: §c3")
    ),
    COPPER_PICKAXE(
        "copper_pickaxe",
        Material.STONE_PICKAXE,
        1014,
        "§6Copper Pickaxe",
        List.of("§7Cúp đồng tinh luyện chắc chắn.", "§8Tier: §c4")
    ),
    IRON_SLAG_PICKAXE(
        "iron_slag_pickaxe",
        Material.IRON_PICKAXE,
        1015,
        "§8Iron Slag Pickaxe",
        List.of("§7Cúp được đúc tạm thời từ sỉ sắt.", "§8Tier: §c5")
    ),
    MITHRIL_SHARD(
        "mithril_shard",
        Material.PRISMARINE_SHARD,
        1017,
        "§bMithril Shard",
        List.of("§7Mảnh quặng Mithril thô.", "§8Dùng để nung luyện Mithril Ingot.")
    ),
    MITHRIL_INGOT(
        "mithril_ingot",
        Material.IRON_INGOT,
        1018,
        "§bMithril Ingot",
        List.of("§7Kim loại quý hiếm có độ bền vượt trội.", "§8Tier: §c8")
    ),
    MITHRIL_PICKAXE(
        "mithril_pickaxe",
        Material.DIAMOND_PICKAXE,
        1019,
        "§bMithril Pickaxe",
        List.of("§7Cúp rèn từ Mithril tinh khiết.", "§7Đủ sức khai thác §5Ancient Debris§7.", "§8Tier: §c8")
    );

    private final String id;
    private final Material material;
    private final int customModelData;
    private final String displayName;
    private final List<String> lore;

    CustomItem(String id, Material material, int customModelData, String displayName, List<String> lore) {
        this.id = id;
        this.material = material;
        this.customModelData = customModelData;
        this.displayName = displayName;
        this.lore = lore;
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    /**
     * Tìm CustomItem theo ID.
     */
    public static java.util.Optional<CustomItem> getById(String id) {
        for (CustomItem item : values()) {
            if (item.getId().equalsIgnoreCase(id)) {
                return java.util.Optional.of(item);
            }
        }
        return java.util.Optional.empty();
    }
}
