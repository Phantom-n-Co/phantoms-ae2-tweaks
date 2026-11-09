package dev.wp.phantoms_ae2_tweaks.mixin;

import java.util.List;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;

import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.AEBaseMenu;

import dev.wp.phantoms_ae2_tweaks.CraftOneToInventory;

import dev.wp.phantoms_ae2_tweaks.emi.FastCraftCheck;
import dev.wp.phantoms_ae2_tweaks.emi.NetworkInventoryCache;

/**
 * The EMI side of AE2's recipe handlers, the way AE2's own code does it: the inventory handed to EMI is cached per
 * screen and backed by the whole network, the craftability check is answered without the allocations EMI's generic one
 * makes, and a craft ends with the click on the result slot that actually performs it.
 * <p>
 * Every terminal using one of these handlers gets all three, including the wireless ones other mods add by registering
 * an AE2 handler for their own menu type.
 */
@Mixin(targets = "appeng.integration.modules.emi.AbstractRecipeHandler", remap = false)
abstract class AbstractRecipeHandlerMixin<T extends AEBaseMenu> {
    @Shadow
    public abstract List<Slot> getInputSources(T menu);

    @Shadow
    public abstract Slot getOutputSlot(T menu);

    @Unique
    private NetworkInventoryCache phantomsae2tweaks$inventory;

    /**
     * Replaces the stock implementation, including its config gate: the inventory is cached per screen, so exposing the
     * network costs a revision check. Nothing of the original is used, so this is an overwrite rather than an injection
     * that always cancels.
     *
     * @author Phantom
     * @reason the cached inventory replaces what AE2 builds on every poll
     */
    @Overwrite
    public EmiPlayerInventory getInventory(AbstractContainerScreen<T> screen) {
        if (phantomsae2tweaks$inventory == null) {
            phantomsae2tweaks$inventory = new NetworkInventoryCache();
        }

        return phantomsae2tweaks$inventory.get(screen, getInputSources(screen.getMenu()));
    }

    @Inject(method = "canCraft", at = @At("HEAD"), cancellable = true)
    private void phantomsae2tweaks$fastCanCraft(EmiRecipe recipe, EmiCraftContext<T> context,
            CallbackInfoReturnable<Boolean> cir) {
        if (context.getType() != EmiCraftContext.Type.FILL_BUTTON) {
            // The fill button has to reflect what the transfer would actually do
            cir.setReturnValue(FastCraftCheck.canCraftWith(context.getInventory(), recipe));
        }
    }

    /**
     * EMI asks for the result to be taken for the cursor and inventory destinations, and for the ingredients only for
     * {@code NONE} (its craft and craft-all binds, which are "move the ingredients"). AE2 takes its result when the
     * result slot is clicked, so this sends the click the result slot would have sent.
     */
    @Inject(method = "craft", at = @At("RETURN"))
    private void phantomsae2tweaks$craft(EmiRecipe recipe, EmiCraftContext<T> context,
            CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }

        var outputSlot = getOutputSlot(context.getScreenHandler());
        if (outputSlot == null) {
            return;
        }

        var all = context.getAmount() > 1;
        switch (context.getDestination()) {
            case NONE -> {
                // Ingredients only, the player takes the result
            }
            case CURSOR -> phantomsae2tweaks$send(all ? InventoryAction.CRAFT_STACK : InventoryAction.CRAFT_ITEM,
                    outputSlot.index);
            case INVENTORY -> {
                if (all) {
                    phantomsae2tweaks$send(InventoryAction.CRAFT_ALL, outputSlot.index);
                } else {
                    // AE2 has no single-craft-to-inventory action, so this one carries the marker its handler reads
                    phantomsae2tweaks$send(InventoryAction.CRAFT_ITEM, outputSlot.index,
                            CraftOneToInventory.ACTION_ID);
                }
            }
        }
    }

    @Unique
    private static void phantomsae2tweaks$send(InventoryAction action, int slot) {
        phantomsae2tweaks$send(action, slot, 0);
    }

    @Unique
    private static void phantomsae2tweaks$send(InventoryAction action, int slot, long id) {
        PacketDistributor.sendToServer(new InventoryActionPacket(action, slot, id));
    }
}
