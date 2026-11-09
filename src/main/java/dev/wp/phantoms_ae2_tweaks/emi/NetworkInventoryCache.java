package dev.wp.phantoms_ae2_tweaks.emi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.stack.EmiStack;

import appeng.api.integrations.emi.EmiStackConverters;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.common.MEStorageMenu;

import dev.wp.phantoms_ae2_tweaks.api.RevisionedRepo;

/**
 * Everything EMI derives from one terminal screen, kept only while that screen is open. The screens are weakly
 * referenced so that closing a terminal releases the converted stacks of a large network instead of retaining them
 * until the next terminal is opened.
 */
public final class NetworkInventoryCache {
    private final Map<AbstractContainerScreen<?>, ScreenCache> screenCaches = new WeakHashMap<>();

    public EmiPlayerInventory get(AbstractContainerScreen<?> screen, List<Slot> slots) {
        var menu = screen.getMenu();
        var carried = menu.getCarried();
        var repo = menu instanceof MEStorageMenu storageMenu ? storageMenu.getClientRepo() : null;
        var revision = repo != null ? ((RevisionedRepo) repo).getRevision() : -1;

        var cache = screenCaches.computeIfAbsent(screen, s -> new ScreenCache());
        return cache.get(slots, carried, repo, revision);
    }

    /**
     * The state EMI derives from a screen, and nothing else. The inventory is not retained anywhere but here, so it is
     * released together with the screen.
     */
    private static final class ScreenCache {
        private @Nullable NetworkEmiPlayerInventory inventory;
        private @Nullable IClientRepo repo;
        private long revision = Long.MIN_VALUE;
        private int slotFingerprint;

        /**
         * The contents of every inventory this screen was given, patched in place instead of rebuilt.
         */
        private final Map<EmiStack, EmiStack> stacks = new HashMap<>();

        /**
         * The player inventory and crafting grid contents, which the craftables cache diffs separately.
         */
        private final List<EmiStack> slotStacks = new ArrayList<>();

        /**
         * One converted stack per network key, reused across rebuilds and patches. Each of them holds the network amount
         * plus whatever the slots hold of the same thing, since that is what the contents map needs.
         */
        private final Map<AEKey, EmiStack> networkStacks = new HashMap<>();

        /**
         * Outlives the inventory instances EMI asks for.
         */
        private final CraftablesCache craftables = new CraftablesCache();

        NetworkEmiPlayerInventory get(List<Slot> slots, ItemStack carried, @Nullable IClientRepo repo, long revision) {
            var fingerprint = fingerprintOf(slots, carried);

            if (inventory != null && this.repo == repo && this.revision == revision
                    && slotFingerprint == fingerprint) {
                return inventory;
            }

            // A network change can be patched: the slots are the same, so only the changed keys move
            if (inventory != null && this.repo == repo && repo != null && slotFingerprint == fingerprint) {
                var changed = ((RevisionedRepo) repo).pendingChangedKeys();
                if (changed != null) {
                    patch(repo, revision, changed);
                    return inventory;
                }
            }

            rebuild(slots, carried, repo, revision, fingerprint);
            return inventory;
        }

        /**
         * Touches only the keys that changed, so a network update costs what it changed instead of what it holds.
         */
        private void patch(IClientRepo repo, long revision, Set<AEKey> changed) {
            var converters = changed.isEmpty() ? null : EmiStackConverters.getConverters();

            for (var key : changed) {
                var amount = ((RevisionedRepo) repo).amountOf(key);
                var stack = networkStacks.get(key);

                if (amount <= 0) {
                    if (stack != null) {
                        networkStacks.remove(key);
                        remerge(stack, 0);
                    }
                    continue;
                }

                if (stack == null) {
                    EmiStack converted = converters != null
                            ? EmiConversions.toEmiStack(new GenericStack(key, amount), converters)
                            : null;
                    if (converted == null) {
                        continue;
                    }
                    networkStacks.put(key, converted);
                    stack = converted;
                }

                remerge(stack, amount);
            }

            this.repo = repo;
            this.revision = revision;
            inventory = new NetworkEmiPlayerInventory(stacks, slotStacks, craftables, revision, slotFingerprint, repo);
        }

        private void rebuild(List<Slot> slots, ItemStack carried, @Nullable IClientRepo repo, long revision,
                int fingerprint) {
            stacks.clear();
            networkStacks.clear();
            slotStacks.clear();

            for (var slot : slots) {
                var stack = EmiStack.of(slot.getItem());
                if (!stack.isEmpty()) {
                    slotStacks.add(stack);
                }
            }
            var carriedStack = EmiStack.of(carried);
            if (!carriedStack.isEmpty()) {
                slotStacks.add(carriedStack);
            }

            if (repo != null) {
                var converters = EmiStackConverters.getConverters();
                for (var entry : repo.getAllEntries()) {
                    var amount = entry.getStoredAmount();
                    if (amount <= 0) {
                        continue; // Skip items that are only craftable
                    }

                    var what = entry.getWhat();
                    var stack = EmiConversions.toEmiStack(new GenericStack(what, amount), converters);
                    if (stack == null) {
                        continue;
                    }
                    networkStacks.put(what, stack);
                    remerge(stack, amount);
                }
            }

            // Slot contents the network does not hold as well
            for (var slotStack : slotStacks) {
                var existing = stacks.get(slotStack);
                if (existing == null) {
                    stacks.put(slotStack, slotStack);
                } else if (existing != slotStack) {
                    existing.setAmount(existing.getAmount() + slotStack.getAmount());
                }
            }

            this.repo = repo;
            this.revision = revision;
            slotFingerprint = fingerprint;
            inventory = new NetworkEmiPlayerInventory(stacks, slotStacks, craftables, revision, fingerprint, repo);
        }

        /**
         * Points one entry of the contents map at what the network stores plus what the slots hold of the same thing,
         * which is what EMI's own stack merging produces.
         */
        private void remerge(EmiStack stack, long networkAmount) {
            stacks.remove(stack);

            var total = networkAmount;
            for (var slotStack : slotStacks) {
                if (slotStack.equals(stack)) {
                    total += slotStack.getAmount();
                }
            }

            if (total <= 0) {
                return;
            }
            stack.setAmount(total);
            stacks.put(stack, stack);
        }
    }

    /**
     * Cheap enough to run on every poll: most slots are empty, and only stacks that carry components are hashed.
     */
    private static int fingerprintOf(List<Slot> slots, ItemStack carried) {
        var fingerprint = 17;
        for (var slot : slots) {
            fingerprint = 31 * fingerprint + stackHash(slot.getItem());
        }
        return 31 * fingerprint + stackHash(carried);
    }

    private static int stackHash(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        var components = stack.getComponents();
        return 31 * (31 * System.identityHashCode(stack.getItem()) + stack.getCount())
                + (components.isEmpty() ? 0 : components.hashCode());
    }
}
