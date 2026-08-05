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

package dev.haohansmp.metallurgy.ignite.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "org.bukkit.craftbukkit.CraftServer")
public abstract class CraftServerInjectMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void haohanMetallurgy$onCraftServerConstructed(CallbackInfo callback) {
        System.setProperty("haohan.metallurgy.ignite", "true");
        System.out.println("[HaoHanMetallurgy/IgnitePatch] Server patch layer injected successfully.");
    }
}
