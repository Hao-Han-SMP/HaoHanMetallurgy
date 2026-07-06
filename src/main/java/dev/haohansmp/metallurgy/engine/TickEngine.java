package dev.haohansmp.metallurgy.engine;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Heartbeat của toàn bộ hệ thống metallurgy.
 * Chạy mỗi N ticks (configurable), gọi MachineManager.tickAll().
 *
 * Được khởi động trong onEnable và dừng lại trong onDisable.
 */
public class TickEngine {

    private final HaoHanMetallurgy plugin;
    private BukkitTask task;
    private boolean running = false;

    public TickEngine(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    // ── Lifecycle ─────────────────────────────────────────────

    /**
     * Bắt đầu tick engine.
     * Tick rate lấy từ config (mặc định 20 ticks = 1 giây).
     */
    public void start() {
        if (running) {
            plugin.getPluginLogger().warn("TickEngine is already running!");
            return;
        }

        int tickRate = plugin.getConfigManager().getTickRate();

        task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    plugin.getMachineManager().tickAll();
                } catch (Exception e) {
                    plugin.getPluginLogger().error("Critical error in TickEngine!", e);
                }
            }
        }.runTaskTimer(plugin, tickRate, tickRate);

        running = true;
        plugin.getPluginLogger().info("TickEngine started (rate=" + tickRate + " ticks).");
    }

    /**
     * Hủy task khi server shutdown.
     */
    public void stop() {
        if (!running || task == null) return;

        task.cancel();
        running = false;
        plugin.getPluginLogger().info("TickEngine stopped.");
    }

    /**
     * Restart engine (dùng sau khi reload config để áp dụng tick rate mới).
     */
    public void restart() {
        stop();
        start();
    }

    public boolean isRunning() {
        return running;
    }
}
