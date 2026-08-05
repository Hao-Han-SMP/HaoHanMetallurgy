/*
 * Copyright (C) 2026 HaoHanSMP
 *
 * This file is part of HaoHan Metallurgy.
 *
 * HaoHan Metallurgy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HaoHan Metallurgy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HaoHan Metallurgy. If not, see <https://www.gnu.org/licenses/>.
 */

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
