package dev.wp.phantoms_ae2_tweaks.api;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;

/**
 * The members AE2's branch adds to {@code IClientRepo}, mirrored here so this mod can call them: an addon compiles
 * against AE2's unmodified interface, so the calls need a type that declares them. Implemented on AE2's client
 * repository by {@link dev.wp.phantoms_ae2_tweaks.mixin.RepoMixin}.
 */
public interface RevisionedRepo {
    /**
     * Incremented whenever the contents change, for caching derived data.
     */
    long getRevision();

    /**
     * @return The keys that changed since the last call, or {@code null} if every key has to be assumed changed, which
     *         is the case after a full update replaced the contents.
     */
    @Nullable
    Set<AEKey> drainChangedKeys();

    /**
     * The changed keys without consuming them, for a caller that runs before the one that drains them.
     *
     * @return The keys that changed since the last drain, or {@code null} if every key has to be assumed changed.
     */
    @Nullable
    Set<AEKey> pendingChangedKeys();

    /**
     * @return How much the repository currently stores of a key, or 0 if it does not store it. AE2's own lookup of an
     *         entry scans every entry.
     */
    long amountOf(AEKey what);

    /**
     * Checks if the repository knows that the given key can be crafted.
     */
    boolean isCraftable(AEKey what);

    /**
     * @return The craftable keys, as an unmodifiable snapshot until the contents change.
     */
    Set<AEKey> getCraftableKeys();
}
