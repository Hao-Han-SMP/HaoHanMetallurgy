package dev.haohansmp.metallurgy.listener;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import dev.haohansmp.metallurgy.machine.Machine;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Xử lý chunk load/unload để tránh máy bị stuck khi chunk unload.
 *
 * Chunk unload → pause tất cả máy trong chunk đó.
 * Chunk load   → resume các máy từng bị pause do chunk unload.
 *
 * Note: Phase 8 (Persistence) sẽ lưu state vào DB thay vì chỉ pause.
 */
public class ChunkListener implements Listener {

    private final HaoHanMetallurgy plugin;

    public ChunkListener(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        List<Machine> affected = getMachinesInChunk(chunk);

        if (affected.isEmpty()) return;

        for (Machine machine : affected) {
            machine.pause();
            plugin.getPluginLogger().debug(
                "Paused machine " + machine.getType()
                + " at " + formatLoc(machine.getLocation())
                + " (chunk unloaded)"
            );
        }

        plugin.getPluginLogger().debug(
            "Paused " + affected.size() + " machine(s) in chunk ["
            + chunk.getX() + "," + chunk.getZ() + "]"
        );
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        List<Machine> affected = getMachinesInChunk(chunk);

        if (affected.isEmpty()) return;

        for (Machine machine : affected) {
            machine.resume();
            if (machine instanceof dev.haohansmp.metallurgy.machine.forge.AncientForge forge) {
                forge.ensureBarrierBlocks();
                forge.spawnDisplayEntity();
            }
            plugin.getPluginLogger().debug(
                "Resumed machine " + machine.getType()
                + " at " + formatLoc(machine.getLocation())
                + " (chunk loaded)"
            );
        }

        plugin.getPluginLogger().debug(
            "Resumed " + affected.size() + " machine(s) in chunk ["
            + chunk.getX() + "," + chunk.getZ() + "]"
        );
    }

    // ── Internal ──────────────────────────────────────────────

    private List<Machine> getMachinesInChunk(Chunk chunk) {
        List<Machine> result = new ArrayList<>();
        int cx = chunk.getX();
        int cz = chunk.getZ();

        for (Machine machine : plugin.getMachineManager().getAll()) {
            Location loc = machine.getLocation();
            if (loc.getWorld() == null) continue;
            if (!loc.getWorld().equals(chunk.getWorld())) continue;

            // Xác định chunk X và Z của Location bằng bitwise shift (nhanh và an toàn tuyệt đối)
            int machineCx = loc.getBlockX() >> 4;
            int machineCz = loc.getBlockZ() >> 4;

            if (machineCx == cx && machineCz == cz) {
                result.add(machine);
            }
        }
        return result;
    }

    private String formatLoc(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}
