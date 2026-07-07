package dev.haohansmp.metallurgy.machine;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.io.File;
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

    public void saveAll() {
        File file = new File(plugin.getDataFolder(), "machines.yml");
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();

        int i = 0;
        for (Machine machine : machines.values()) {
            String path = "machines." + i;
            yaml.set(path + ".type", machine.getType().name());
            yaml.set(path + ".location", machine.getLocation());
            yaml.set(path + ".temperature", machine.getTemperature());
            yaml.set(path + ".fuel", machine.getFuelTicksRemaining());
            yaml.set(path + ".state", machine.getState().name());

            if (machine instanceof dev.haohansmp.metallurgy.machine.forge.AncientForge forge) {
                yaml.set(path + ".rotation", forge.getRotation());
                yaml.set(path + ".inventory", forge.getInventory().getContents());

                List<Map<String, Object>> origList = new ArrayList<>();
                for (Map.Entry<dev.haohansmp.metallurgy.machine.forge.ForgeStructure.BlockOffset, Material> entry : forge.getOriginalBlocks().entrySet()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("dx", entry.getKey().dx());
                    map.put("dy", entry.getKey().dy());
                    map.put("dz", entry.getKey().dz());
                    map.put("material", entry.getValue().name());
                    origList.add(map);
                }
                yaml.set(path + ".original_blocks", origList);
            }
            i++;
        }

        try {
            yaml.save(file);
            plugin.getPluginLogger().info("Saved " + machines.size() + " active machine(s) to machines.yml.");
        } catch (java.io.IOException e) {
            plugin.getPluginLogger().error("Failed to save machines.yml", e);
        }
    }

    public void loadAll() {
        File file = new File(plugin.getDataFolder(), "machines.yml");
        if (!file.exists()) return;

        org.bukkit.configuration.file.YamlConfiguration yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        var section = yaml.getConfigurationSection("machines");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String path = "machines." + key;
            String typeStr = yaml.getString(path + ".type");
            Location loc = yaml.getLocation(path + ".location");
            if (loc == null || typeStr == null) continue;

            if (typeStr.equals(MachineType.ANCIENT_FORGE.name())) {
                int rotation = yaml.getInt(path + ".rotation");

                Map<dev.haohansmp.metallurgy.machine.forge.ForgeStructure.BlockOffset, Material> originalBlocks = new HashMap<>();
                List<?> list = yaml.getList(path + ".original_blocks");
                if (list != null) {
                    for (Object obj : list) {
                        if (obj instanceof Map<?, ?> map) {
                            int dx = ((Number) map.get("dx")).intValue();
                            int dy = ((Number) map.get("dy")).intValue();
                            int dz = ((Number) map.get("dz")).intValue();
                            Material mat = Material.valueOf((String) map.get("material"));
                            originalBlocks.put(new dev.haohansmp.metallurgy.machine.forge.ForgeStructure.BlockOffset(dx, dy, dz, mat), mat);
                        }
                    }
                }

                dev.haohansmp.metallurgy.machine.forge.AncientForge forge = new dev.haohansmp.metallurgy.machine.forge.AncientForge(plugin, loc, rotation, originalBlocks);

                List<?> invList = yaml.getList(path + ".inventory");
                if (invList != null) {
                    ItemStack[] contents = new ItemStack[forge.getInventory().getSize()];
                    for (int j = 0; j < invList.size() && j < contents.length; j++) {
                        Object itemObj = invList.get(j);
                        if (itemObj instanceof ItemStack itemStack) {
                            contents[j] = itemStack;
                        }
                    }
                    forge.getInventory().setContents(contents);
                }

                int temp = yaml.getInt(path + ".temperature");
                int fuel = yaml.getInt(path + ".fuel");
                String stateStr = yaml.getString(path + ".state", "IDLE");

                forge.setTemperature(temp);
                forge.setFuelTicksRemaining(fuel);
                try {
                    forge.setState(MachineState.valueOf(stateStr));
                } catch (Exception e) {}

                register(forge);
            }
        }

        file.delete();
        plugin.getPluginLogger().info("Restored all active machines from machines.yml.");
    }
}
