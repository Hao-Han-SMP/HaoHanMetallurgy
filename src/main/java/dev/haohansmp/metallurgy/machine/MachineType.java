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

package dev.haohansmp.metallurgy.machine;

/**
 * Các loại máy trong hệ thống.
 * Thêm máy mới: chỉ cần thêm entry ở đây + tạo class con của Machine.
 */
public enum MachineType {

    /** Lò rèn cổ đại — máy chính của hệ thống. */
    ANCIENT_FORGE,

    /** Placeholder cho các máy tương lai. */
    SOUL_CRUCIBLE,
    EMBER_SMELTER
}
