package dev.haohansmp.metallurgy.data;

import dev.haohansmp.metallurgy.machine.MachineType;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Utility class cho PersistentDataContainer.
 * Tất cả đọc/ghi PDC đi qua đây — không ai tự tạo NamespacedKey ngoài class này.
 *
 * PDC được lưu trên TileEntity của block máy (VD: Barrel, Chest).
 * Namespace: "haohansmp"
 */
public final class PdcUtil {

    // ── Namespace ─────────────────────────────────────────────
    public static final String NAMESPACE = "haohansmp";

    // ── Key constants ─────────────────────────────────────────
    private static final String KEY_MACHINE_TYPE  = "machine_type";
    private static final String KEY_MACHINE_ID    = "machine_id";

    // Prevent instantiation
    private PdcUtil() {}

    // ── Machine Type ──────────────────────────────────────────

    /** Ghi loại máy vào block. */
    public static void setMachineType(Plugin plugin, Block block, MachineType type) {
        withPdc(block, pdc -> {
            NamespacedKey key = new NamespacedKey(plugin, KEY_MACHINE_TYPE);
            pdc.set(key, PersistentDataType.STRING, type.name());
        });
    }

    /** Đọc loại máy từ block. */
    public static Optional<MachineType> getMachineType(Plugin plugin, Block block) {
        return readPdc(block, pdc -> {
            NamespacedKey key = new NamespacedKey(plugin, KEY_MACHINE_TYPE);
            String value = pdc.get(key, PersistentDataType.STRING);
            if (value == null) return Optional.<MachineType>empty();
            try {
                return Optional.of(MachineType.valueOf(value));
            } catch (IllegalArgumentException e) {
                return Optional.<MachineType>empty();
            }
        });
    }

    /** Kiểm tra block có phải là máy metallurgy không. */
    public static boolean isMachine(Plugin plugin, Block block) {
        return getMachineType(plugin, block).isPresent();
    }

    /** Xóa dữ liệu máy khỏi block (khi phá máy). */
    public static void clearMachineData(Plugin plugin, Block block) {
        withPdc(block, pdc -> {
            pdc.remove(new NamespacedKey(plugin, KEY_MACHINE_TYPE));
            pdc.remove(new NamespacedKey(plugin, KEY_MACHINE_ID));
        });
    }

    // ── Generic helpers ───────────────────────────────────────

    public static void setString(Plugin plugin, Block block, String keyName, String value) {
        withPdc(block, pdc -> {
            pdc.set(new NamespacedKey(plugin, keyName), PersistentDataType.STRING, value);
        });
    }

    public static Optional<String> getString(Plugin plugin, Block block, String keyName) {
        return readPdc(block, pdc ->
            Optional.ofNullable(pdc.get(new NamespacedKey(plugin, keyName), PersistentDataType.STRING))
        );
    }

    public static void setInt(Plugin plugin, Block block, String keyName, int value) {
        withPdc(block, pdc -> {
            pdc.set(new NamespacedKey(plugin, keyName), PersistentDataType.INTEGER, value);
        });
    }

    public static Optional<Integer> getInt(Plugin plugin, Block block, String keyName) {
        return readPdc(block, pdc ->
            Optional.ofNullable(pdc.get(new NamespacedKey(plugin, keyName), PersistentDataType.INTEGER))
        );
    }

    // ── Internal ───────────────────────────────────────────────

    @FunctionalInterface
    private interface PdcConsumer {
        void accept(PersistentDataContainer pdc);
    }

    @FunctionalInterface
    private interface PdcFunction<R> {
        R apply(PersistentDataContainer pdc);
    }

    private static void withPdc(Block block, PdcConsumer action) {
        if (!(block.getState() instanceof TileState ts)) return;
        action.accept(ts.getPersistentDataContainer());
        ts.update();
    }

    private static <R> R readPdc(Block block, PdcFunction<R> action) {
        if (!(block.getState() instanceof TileState ts)) return null;
        return action.apply(ts.getPersistentDataContainer());
    }
}
