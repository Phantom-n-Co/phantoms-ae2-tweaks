package dev.wp.phantoms_ae2_tweaks.mixin;

import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import appeng.api.stacks.AEItemKey;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.CraftingTermMenu;

import dev.wp.phantoms_ae2_tweaks.api.RevisionedRepo;

/**
 * The craftable check behind the terminal's craftable filter scans every entry of the network on each call, once per
 * slot and frame. The repository caches the craftable keys, so this is a set lookup.
 */
@Mixin(targets = "appeng.menu.me.items.CraftingTermMenu", remap = false)
abstract class CraftingTermMenuMixin {
    /**
     * Replaces the scan over every entry, which this runs per slot per frame in the craftable view.
     *
     * @author Phantom
     * @reason the repository caches the craftable keys
     */
    @Overwrite
    protected boolean isCraftable(ItemStack itemStack) {
        IClientRepo repo = ((MEStorageMenu) (Object) this).getClientRepo();
        if (repo == null) {
            return false;
        }

        var key = AEItemKey.of(itemStack);
        return key != null && ((RevisionedRepo) repo).isCraftable(key);
    }
}
