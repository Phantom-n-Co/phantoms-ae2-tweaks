package dev.wp.phantoms_ae2_tweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.wp.phantoms_ae2_tweaks.sort.CreativeOrder;

/**
 * EMI rebuilds the creative tabs as part of its reload, which is when the creative order can change.
 */
@Mixin(targets = "dev.emi.emi.runtime.EmiReloadManager$ReloadWorker")
abstract class EmiReloadMixin {
    @Inject(method = "run", at = @At("RETURN"))
    private void phantomsae2tweaks$invalidateCreativeOrder(CallbackInfo ci) {
        CreativeOrder.invalidate();
    }
}
