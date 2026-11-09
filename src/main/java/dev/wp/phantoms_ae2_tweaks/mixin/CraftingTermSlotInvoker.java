package dev.wp.phantoms_ae2_tweaks.mixin;

import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;

/**
 * The crafting slot's click handling is not visible outside AE2's menu package.
 */
@Mixin(value = CraftingTermSlot.class, remap = false)
public interface CraftingTermSlotInvoker {
    @Invoker("doClick")
    void pae2t$doClick(InventoryAction action, Player player);
}
