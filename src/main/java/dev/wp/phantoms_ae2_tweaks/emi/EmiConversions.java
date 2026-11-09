package dev.wp.phantoms_ae2_tweaks.emi;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.stack.EmiStack;

import appeng.api.integrations.emi.EmiStackConverter;
import appeng.api.integrations.emi.EmiStackConverters;
import appeng.api.stacks.GenericStack;

/**
 * The converter-list overload of {@code EmiStackHelper.toEmiStack}, which only exists in the AE2 branch: an addon cannot
 * add it to AE2's helper, so the identical loop lives here.
 */
public final class EmiConversions {
    private EmiConversions() {
    }

    @Nullable
    public static EmiStack toEmiStack(GenericStack stack) {
        return toEmiStack(stack, EmiStackConverters.getConverters());
    }

    /**
     * Uses a pre-fetched converter list, for converting many stacks in a loop.
     */
    @Nullable
    public static EmiStack toEmiStack(GenericStack stack, List<EmiStackConverter> converters) {
        for (var converter : converters) {
            var emiStack = converter.toEmiStack(stack);
            if (emiStack != null) {
                return emiStack;
            }
        }

        return null;
    }
}
