package dev.wp.phantoms_ae2_tweaks.emi;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import appeng.menu.me.common.IClientRepo;

/**
 * The inventory AE2 hands to EMI. It differs from a plain {@link EmiPlayerInventory} in two ways: the craftables list is
 * kept up to date incrementally (see {@link CraftablesCache}), and the contents map is shared between the instances EMI
 * is given, so a network change patches the map instead of rebuilding it.
 */
public class NetworkEmiPlayerInventory extends EmiPlayerInventory {
    private final List<EmiStack> slotStacks;
    private final CraftablesCache craftables;
    private final long revision;
    private final int slotFingerprint;
    private final @Nullable WeakReference<IClientRepo> repo;

    NetworkEmiPlayerInventory(Map<EmiStack, EmiStack> stacks,
            List<EmiStack> slotStacks,
            CraftablesCache craftables,
            long revision,
            int slotFingerprint,
            @Nullable IClientRepo repo) {
        super(List.of());
        // The map belongs to the screen, not to this instance: EMI only reads it, and sharing it is what makes patching
        // a change cheaper than rebuilding the whole network
        this.inventory = stacks;
        this.slotStacks = slotStacks;
        this.craftables = craftables;
        this.revision = revision;
        this.slotFingerprint = slotFingerprint;
        this.repo = repo != null ? new WeakReference<>(repo) : null;
    }

    @Override
    public List<EmiIngredient> getCraftables() {
        return craftables.getCraftables(this);
    }

    /**
     * EMI compares the inventory against the previous one entry by entry on every poll, which at 40,000 entries and a
     * 50 ms poll cost about 8% of the frame before this returned early.
     */
    @Override
    public boolean isEqual(EmiPlayerInventory other) {
        if (other == this) {
            return true;
        }
        if (other instanceof NetworkEmiPlayerInventory network) {
            // The revision is only meaningful for the repository it came from
            return getRepo() == network.getRepo()
                    && revision == network.revision
                    && slotFingerprint == network.slotFingerprint;
        }
        return super.isEqual(other);
    }

    /**
     * The player inventory and crafting grid contents, without empty stacks.
     */
    List<EmiStack> getSlotStacks() {
        return slotStacks;
    }

    long getRevision() {
        return revision;
    }

    int getSlotFingerprint() {
        return slotFingerprint;
    }

    @Nullable
    IClientRepo getRepo() {
        return repo != null ? repo.get() : null;
    }
}
