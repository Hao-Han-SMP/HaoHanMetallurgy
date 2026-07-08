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
