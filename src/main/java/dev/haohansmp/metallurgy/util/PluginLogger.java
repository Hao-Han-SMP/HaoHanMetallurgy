package dev.haohansmp.metallurgy.util;

import dev.haohansmp.metallurgy.HaoHanMetallurgy;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Wrapper quanh Java Logger của Bukkit.
 * Hỗ trợ debug mode: debug() chỉ log khi debugMode = true.
 */
public class PluginLogger {

    private final Logger logger;
    private final HaoHanMetallurgy plugin;

    public PluginLogger(HaoHanMetallurgy plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void info(String message) {
        logger.info(message);
    }

    public void warn(String message) {
        logger.warning(message);
    }

    public void error(String message) {
        logger.severe(message);
    }

    public void error(String message, Throwable t) {
        logger.log(Level.SEVERE, message, t);
    }

    /**
     * Chỉ log nếu config debug: true.
     */
    public void debug(String message) {
        if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebug()) {
            logger.info("[DEBUG] " + message);
        }
    }
}
