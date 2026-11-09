package dev.wp.phantoms_ae2_tweaks.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import appeng.api.integrations.emi.EmiStackConverter;
import appeng.api.integrations.emi.EmiStackConverters;

/**
 * The converter list is read for every stack converted, so AE2's synchronized getter takes a lock per call.
 */
@Mixin(value = EmiStackConverters.class, remap = false)
abstract class EmiStackConvertersMixin {
    @Shadow
    private static List<EmiStackConverter> converters;

    /**
     * @author Phantom
     * @reason The list is only replaced under the lock taken by {@link EmiStackConverters#register}; readers don't need
     *         one.
     */
    @Overwrite
    public static List<EmiStackConverter> getConverters() {
        return converters;
    }
}
