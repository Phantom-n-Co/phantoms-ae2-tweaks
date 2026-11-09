package dev.wp.phantoms_ae2_tweaks.mixin;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.BiMap;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;
import appeng.client.gui.me.common.Repo;
import appeng.menu.me.common.GridInventoryEntry;

import dev.wp.phantoms_ae2_tweaks.ClientConfig;
import dev.wp.phantoms_ae2_tweaks.api.RevisionedRepo;
import dev.wp.phantoms_ae2_tweaks.sort.CreativeOrder;

/**
 * Adds change tracking to AE2's client repository so the EMI integration can update what it derives from it
 * incrementally instead of re-reading every entry on every poll.
 * <p>
 * It also drops incremental updates that carry no entries: AE2 rebuilds and re-sorts its whole view for those, which at
 * tens of thousands of entries is the most expensive thing an empty update can do.
 * <p>
 * Finally it swaps the comparator for the mod order, so that order lists items the way the creative inventory does.
 */
@Mixin(Repo.class)
public abstract class RepoMixin implements RevisionedRepo {
    @Shadow
    private BiMap<Long, GridInventoryEntry> entries;

    /**
     * Changed since the last {@link #drainChangedKeys()}.
     */
    @Unique
    private final Set<AEKey> pae2t$changedKeys = new ObjectOpenHashSet<>();
    /**
     * Set when the contents were replaced, so every key counts as changed.
     */
    @Unique
    private boolean pae2t$changedKeysUnknown;
    /**
     * Rebuilt lazily after the contents change.
     */
    @Unique
    private @Nullable Set<AEKey> pae2t$craftableKeys;
    @Unique
    private long pae2t$revision;
    /**
     * The stored amount per key, so callers do not have to scan the entries to find one.
     */
    @Unique
    private final Map<AEKey, Long> pae2t$amounts = new HashMap<>();
    @Unique
    private boolean pae2t$amountsStale = true;
    /**
     * Carries the key of the entry being updated from the head of the update to its return.
     */
    @Unique
    private @Nullable AEKey pae2t$updatingKey;

    @Inject(method = "handleUpdate(ZLjava/util/List;)V", at = @At("HEAD"), cancellable = true)
    private void pae2t$skipEmptyUpdate(boolean fullUpdate, List<GridInventoryEntry> updatedEntries,
            CallbackInfo ci) {
        if (!fullUpdate && updatedEntries.isEmpty()) {
            // Nothing changed, so there is nothing to invalidate or re-sort
            ci.cancel();
        }
    }

    /**
     * Everything before {@code updateView}, which is where the update is applied and the view is rebuilt.
     */
    @Inject(method = "handleUpdate(ZLjava/util/List;)V",
            at = @At(value = "INVOKE", target = "Lappeng/client/gui/me/common/Repo;updateView()V"))
    private void pae2t$onUpdate(boolean fullUpdate, List<GridInventoryEntry> updatedEntries, CallbackInfo ci) {
        pae2t$revision++;
        pae2t$craftableKeys = null;
        if (fullUpdate) {
            pae2t$amountsStale = true;
        }
    }

    @Inject(method = "handleUpdate(Lappeng/menu/me/common/GridInventoryEntry;)V", at = @At("HEAD"))
    private void pae2t$onEntryUpdate(GridInventoryEntry serverEntry, CallbackInfo ci) {
        // Marks the same keys the update will touch, before it is applied; a removal is only known from the local entry
        var localEntry = this.entries.get(serverEntry.getSerial());
        if (localEntry == null) {
            if (serverEntry.getWhat() != null && serverEntry.isMeaningful()) {
                pae2t$markChanged(serverEntry.getWhat());
            }
        } else if (!serverEntry.isMeaningful() || serverEntry.getWhat() == null) {
            pae2t$markChanged(localEntry.getWhat());
        } else {
            pae2t$markChanged(serverEntry.getWhat());
        }

        pae2t$updatingKey = localEntry != null && localEntry.getWhat() != null ? localEntry.getWhat()
                : serverEntry.getWhat();
    }

    /**
     * Records the amount after the update was applied, which is the only place that knows a key's new amount without
     * scanning the entries.
     */
    @Inject(method = "handleUpdate(Lappeng/menu/me/common/GridInventoryEntry;)V", at = @At("RETURN"))
    private void pae2t$afterEntryUpdate(GridInventoryEntry serverEntry, CallbackInfo ci) {
        var key = pae2t$updatingKey;
        pae2t$updatingKey = null;
        if (key == null || pae2t$amountsStale) {
            return;
        }

        var current = this.entries.get(serverEntry.getSerial());
        if (current == null || current.getWhat() == null) {
            pae2t$amounts.remove(key);
        } else {
            pae2t$amounts.put(current.getWhat(), current.getStoredAmount());
        }
    }

    @Unique
    private void pae2t$markChanged(@Nullable AEKey what) {
        if (what != null) {
            pae2t$changedKeys.add(what);
        }
    }

    @Inject(method = "clear()V", at = @At("HEAD"))
    private void pae2t$onClear(CallbackInfo ci) {
        pae2t$craftableKeys = null;
        pae2t$amountsStale = true;
        pae2t$changedKeys.clear();
        pae2t$changedKeysUnknown = true;
        pae2t$revision++;
    }

    /**
     * Replaces the scan over every entry.
     *
     * @author Phantom
     * @reason the craftable keys are cached until the contents change
     */
    @Overwrite
    public boolean isCraftable(AEKey what) {
        return pae2t$getCraftableKeys().contains(what);
    }

    @Inject(method = "getKeyComparator", at = @At("HEAD"), cancellable = true)
    private void pae2t$useCreativeOrder(SortOrder order, SortDir dir, CallbackInfoReturnable<Comparator<AEKey>> cir) {
        if (order != SortOrder.MOD || !ClientConfig.creativeSort()) {
            return;
        }

        var comparator = CreativeOrder.comparator(dir);
        if (comparator != null) {
            cir.setReturnValue(comparator);
        }
    }

    @Override
    public long getRevision() {
        return pae2t$revision;
    }

    @Override
    public @Nullable Set<AEKey> drainChangedKeys() {
        if (pae2t$changedKeysUnknown) {
            pae2t$changedKeysUnknown = false;
            pae2t$changedKeys.clear();
            return null;
        }
        if (pae2t$changedKeys.isEmpty()) {
            return Set.of();
        }
        var result = Set.copyOf(pae2t$changedKeys);
        pae2t$changedKeys.clear();
        return result;
    }

    @Override
    public @Nullable Set<AEKey> pendingChangedKeys() {
        if (pae2t$changedKeysUnknown) {
            return null;
        }
        return pae2t$changedKeys.isEmpty() ? Set.of() : Set.copyOf(pae2t$changedKeys);
    }

    @Override
    public long amountOf(AEKey what) {
        if (pae2t$amountsStale) {
            pae2t$amounts.clear();
            for (var entry : this.entries.values()) {
                if (entry.getWhat() != null) {
                    pae2t$amounts.put(entry.getWhat(), entry.getStoredAmount());
                }
            }
            pae2t$amountsStale = false;
        }
        return pae2t$amounts.getOrDefault(what, 0L);
    }

    @Override
    public Set<AEKey> getCraftableKeys() {
        return pae2t$getCraftableKeys();
    }

    @Unique
    private Set<AEKey> pae2t$getCraftableKeys() {
        var keys = pae2t$craftableKeys;
        if (keys == null) {
            var result = new HashSet<AEKey>();
            for (var entry : this.entries.values()) {
                if (entry.isCraftable()) {
                    result.add(entry.getWhat());
                }
            }
            keys = Collections.unmodifiableSet(result);
            pae2t$craftableKeys = keys;
        }
        return keys;
    }
}
