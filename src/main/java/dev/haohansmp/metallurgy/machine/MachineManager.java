package dev.haohansmp.metallurgy.machine;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.Location;

import java.util.*;

/**
 * Quản lý tất cả Machine đang hoạt động trong server.
 * TickEngine gọi tickAll() mỗi N ticks.
 */
public class MachineManager {

    private final HaoHanMetallurgy plugin;

    /** Map từ location của block chính → Machine instance. */
    private final Map<Location, Machine> machines = new LinkedHashMap<>();

    public MachineManager(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    // ── Registration ──────────────────────────────────────────

    /**
     * Đăng ký machine mới.
     * @return false nếu đã có machine tại location đó.
     */
    public boolean register(Machine machine) {
        Location loc = machine.getLocation();
        if (machines.containsKey(loc)) {
            plugin.getPluginLogger().warn("Machine already exists at " + loc);
            return false;
        }
        machines.put(loc, machine);
        plugin.getPluginLogger().debug("Registered machine " + machine.getType() + " at " + loc);
        return true;
    }

    /**
     * Xóa machine tại location (khi player phá máy).
     * @return machine đã xóa, hoặc null nếu không tồn tại.
     */
    public Machine unregister(Location location) {
        Machine removed = machines.remove(location);
        if (removed != null) {
            plugin.getPluginLogger().debug("Unregistered machine at " + location);
            if (removed instanceof dev.haohansmp.metallurgy.machine.forge.AncientForge forge) {
                forge.removeDisplayEntity();
            }
        }
        return removed;
    }

    /** Lấy machine tại location, trả về Optional. */
    public Optional<Machine> get(Location location) {
        return Optional.ofNullable(machines.get(location));
    }

    /** Kiểm tra có machine tại location không. */
    public boolean exists(Location location) {
        return machines.containsKey(location);
    }

    /** Tất cả machines đang active (unmodifiable). */
    public Collection<Machine> getAll() {
        return Collections.unmodifiableCollection(machines.values());
    }

    /** Số lượng machine đang active. */
    public int count() {
        return machines.size();
    }

    // ── Tick ──────────────────────────────────────────────────

    /**
     * Gọi mỗi N ticks bởi TickEngine.
     * Tick từng machine, bắt exception để 1 machine lỗi không làm sập engine.
     */
    public void tickAll() {
        for (Machine machine : machines.values()) {
            try {
                machine.tick();
            } catch (Exception e) {
                plugin.getPluginLogger().error(
                    "Error ticking machine " + machine.getType()
                    + " at " + machine.getLocation(), e
                );
            }
        }
    }

    // ── Shutdown ──────────────────────────────────────────────

    /**
     * Gọi khi server shutdown (onDisable).
     * Pause tất cả machines đang chạy để serialize đúng state.
     */
    public void pauseAll() {
        machines.values().forEach(machine -> {
            machine.pause();
            if (machine instanceof dev.haohansmp.metallurgy.machine.forge.AncientForge forge) {
                forge.removeDisplayEntity();
            }
        });
        plugin.getPluginLogger().info("Paused " + machines.size() + " machine(s) for shutdown and cleared display models.");
    }
}
