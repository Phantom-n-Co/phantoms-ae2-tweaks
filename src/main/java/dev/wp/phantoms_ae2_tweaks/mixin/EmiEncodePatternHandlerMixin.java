package dev.wp.phantoms_ae2_tweaks.mixin;

import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;

import dev.wp.phantoms_ae2_tweaks.api.RevisionedRepo;

/**
 * Encoding a pattern needs the network's craftable keys, which AE2 collects by filtering its whole entry set. The
 * repository has the same set cached, so the collection is replaced with a lookup.
 * <p>
 * The stream pipeline is lazy, so not evaluating the end of it skips the filtering, mapping and collection entirely
 * rather than just doing them more cheaply.
 */
@Mixin(targets = "appeng.integration.modules.emi.EmiEncodePatternHandler", remap = false)
abstract class EmiEncodePatternHandlerMixin {
    @WrapOperation(
            method = "transferRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Object phantomsae2tweaks$craftableKeys(Stream<?> stream, Collector<?, ?, ?> collector,
            Operation<Object> original, @Local(argsOnly = true) PatternEncodingTermMenu menu) {
        IClientRepo repo = menu instanceof MEStorageMenu storageMenu ? storageMenu.getClientRepo() : null;
        if (repo != null) {
            return ((RevisionedRepo) repo).getCraftableKeys();
        }
        return Set.of();
    }
}
