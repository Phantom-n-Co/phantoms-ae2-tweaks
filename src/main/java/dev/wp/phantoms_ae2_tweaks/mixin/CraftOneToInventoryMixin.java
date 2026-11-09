package dev.wp.phantoms_ae2_tweaks.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;
import appeng.menu.slot.CraftingTermSlot;

import dev.wp.phantoms_ae2_tweaks.CraftOneToInventory;

/**
 * Crafts a single batch into the player's inventory, which AE2 cannot ask for on its own: its actions either put the
 * result on the cursor or craft as many as fit in a stack into the inventory.
 * <p>
 * Runs on the logical server, where the crafting slot and the inventory can be changed in one step.
 */
@Mixin(value = AEBaseMenu.class, remap = false)
abstract class CraftOneToInventoryMixin {
    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void pae2t$craftOneToInventory(ServerPlayer player, InventoryAction action, int slot, long id,
            CallbackInfo ci) {
        if (action != InventoryAction.CRAFT_ITEM || id != CraftOneToInventory.ACTION_ID) {
            return;
        }

        var menu = (AEBaseMenu) (Object) this;
        if (slot < 0 || slot >= menu.slots.size()) {
            return;
        }
        if (!(menu.slots.get(slot) instanceof CraftingTermSlot craftingSlot)) {
            return;
        }

        // One batch onto the cursor, then into the inventory
        ((CraftingTermSlotInvoker) (Object) craftingSlot).pae2t$doClick(InventoryAction.CRAFT_ITEM, player);
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            menu.setCarried(player.getInventory().add(carried) ? ItemStack.EMPTY : carried);
        }

        menu.broadcastChanges();
        ci.cancel();
    }
}
