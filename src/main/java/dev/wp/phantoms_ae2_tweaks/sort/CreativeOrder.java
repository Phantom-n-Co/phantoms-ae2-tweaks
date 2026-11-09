package dev.wp.phantoms_ae2_tweaks.sort;

import java.util.Collection;
import java.util.Comparator;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

import appeng.api.config.SortDir;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

/**
 * The order the creative inventory lists things in, i.e. the order of its search tab.
 * <p>
 * Only the relative order matters: items the creative tabs do not list, and keys that are not items at all, sort last.
 * The positions are read once per creative tab rebuild, and each key is resolved at most once per snapshot, so sorting
 * does not pay for a stack lookup per comparison.
 */
public final class CreativeOrder {
    private static final int UNKNOWN = Integer.MAX_VALUE;
    private static final int UNRESOLVED = Integer.MIN_VALUE;

    private static volatile Snapshot snapshot;
    private static volatile boolean dirty = true;

    private CreativeOrder() {
    }

    /**
     * @return the creative order as a comparator, or null while the creative tabs have nothing to read (before the
     *         first EMI reload), in which case the caller should keep AE2's own sorting.
     */
    public static Comparator<AEKey> comparator(SortDir dir) {
        var current = snapshot();
        if (current == null) {
            return null;
        }
        return dir == SortDir.ASCENDING ? current.ascending : current.descending;
    }

    /**
     * Makes the next sort rebuild the order. Creative tabs are rebuilt during an EMI reload, which is also when items
     * can appear, disappear or move.
     */
    public static void invalidate() {
        dirty = true;
    }

    private static Snapshot snapshot() {
        var current = snapshot;
        if (current != null && !dirty) {
            return current;
        }

        var rebuilt = Snapshot.build();
        if (rebuilt == null) {
            return current;
        }

        snapshot = rebuilt;
        dirty = false;
        return rebuilt;
    }

    private static final class Snapshot {
        /** First creative position of each item, regardless of components. */
        private final Object2IntOpenHashMap<Item> items;
        /** Position of stacks that are more than their item, such as potions or enchanted books. */
        private final Object2IntOpenHashMap<AEItemKey> variants;
        /** Positions for fluids, which follow the items, ordered by registry id. */
        private final int fluidOffset;

        /**
         * Keyed by identity: an {@link AEKey}'s own hash and equality compare the whole component map of the stack, which
         * in a large pack is far more expensive than the sort it is part of. Keys are interned, so identity is enough.
         */
        private final Reference2IntOpenHashMap<AEKey> resolved = new Reference2IntOpenHashMap<>();
        /** Lowercased display names, resolved once per key: the tie break otherwise translates and builds a string per comparison. */
        private final Reference2ObjectOpenHashMap<AEKey, String> names = new Reference2ObjectOpenHashMap<>();
        private final Comparator<AEKey> ascending;
        private final Comparator<AEKey> descending;

        private Snapshot(Object2IntOpenHashMap<Item> items, Object2IntOpenHashMap<AEItemKey> variants, int fluidOffset) {
            this.items = items;
            this.variants = variants;
            this.fluidOffset = fluidOffset;
            this.resolved.defaultReturnValue(UNRESOLVED);
            this.ascending = Comparator.<AEKey>comparingInt(this::position).thenComparing(this::name);
            this.descending = this.ascending.reversed();
        }

        private static Snapshot build() {
            Collection<ItemStack> displayed = CreativeModeTabs.searchTab().getDisplayItems();
            if (displayed.isEmpty()) {
                return null;
            }

            var items = new Object2IntOpenHashMap<Item>(displayed.size());
            items.defaultReturnValue(UNKNOWN);
            var variants = new Object2IntOpenHashMap<AEItemKey>();
            variants.defaultReturnValue(UNKNOWN);
            // Only stacks that differ from the first one seen for their item are variants, which keeps the components of
            // the plain item stacks out of the map; they are the vast majority and hashing them is the bulk of the cost.
            var firstSeen = new Object2ObjectOpenHashMap<Item, DataComponentMap>();

            var position = 0;
            for (var stack : displayed) {
                if (stack.isEmpty()) {
                    continue;
                }

                var item = stack.getItem();
                var components = stack.getComponents();
                if (firstSeen.putIfAbsent(item, components) == null) {
                    items.put(item, position);
                } else if (!components.equals(firstSeen.get(item))) {
                    var key = AEItemKey.of(stack);
                    if (key != null) {
                        variants.putIfAbsent(key, position);
                    }
                }
                position++;
            }

            return new Snapshot(items, variants, position);
        }

        private int position(AEKey key) {
            var cached = this.resolved.getInt(key);
            if (cached != UNRESOLVED) {
                return cached;
            }

            var resolved = resolve(key);
            this.resolved.put(key, resolved);
            return resolved;
        }

        private String name(AEKey key) {
            var name = this.names.get(key);
            if (name == null) {
                name = key.getDisplayName().getString().toLowerCase(java.util.Locale.ROOT);
                this.names.put(key, name);
            }
            return name;
        }

        private int resolve(AEKey key) {
            if (key instanceof AEItemKey itemKey) {
                var variant = this.variants.getInt(itemKey);
                return variant != UNKNOWN ? variant : this.items.getInt(itemKey.getItem());
            }

            if (key instanceof AEFluidKey fluidKey) {
                return this.fluidOffset + BuiltInRegistries.FLUID.getId(fluidKey.getFluid());
            }

            return UNKNOWN;
        }
    }
}
