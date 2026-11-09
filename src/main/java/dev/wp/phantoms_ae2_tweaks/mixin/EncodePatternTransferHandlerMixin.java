package dev.wp.phantoms_ae2_tweaks.mixin;

import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.MEStorageMenu;

import dev.wp.phantoms_ae2_tweaks.api.RevisionedRepo;

/**
 * Same as {@link EmiEncodePatternHandlerMixin}, for the REI transfer handler AE2 ships when REI is installed.
 */
@Pseudo
@Mixin(targets = "appeng.integration.modules.rei.transfer.EncodePatternTransferHandler", remap = false)
abstract class EncodePatternTransferHandlerMixin {
    @WrapOperation(
            method = "transferRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    private Object phantomsae2tweaks$craftableKeys(Stream<?> stream, Collector<?, ?, ?> collector,
            Operation<Object> original, @Local(argsOnly = true) AEBaseMenu menu) {
        IClientRepo repo = menu instanceof MEStorageMenu storageMenu ? storageMenu.getClientRepo() : null;
        if (repo != null) {
            return ((RevisionedRepo) repo).getCraftableKeys();
        }
        return Set.of();
    }
}
