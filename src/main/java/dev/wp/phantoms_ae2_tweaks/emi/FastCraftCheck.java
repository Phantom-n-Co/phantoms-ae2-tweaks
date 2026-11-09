package dev.wp.phantoms_ae2_tweaks.emi;

import java.util.Arrays;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;

/**
 * Checks craftability against an inventory the way {@link EmiPlayerInventory#canCraft(EmiRecipe)} does, without the
 * per-recipe map and list allocations.
 * <p>
 * EMI asks every candidate recipe of a craftables rebuild, so at pack scale this runs for most of the recipe index
 * (hundreds of thousands of recipes); the allocations EMI makes there dominate the profile otherwise.
 */
public final class FastCraftCheck {
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private FastCraftCheck() {
    }

    public static boolean canCraftWith(EmiPlayerInventory inventory, EmiRecipe recipe) {
        var scratch = SCRATCH.get();
        scratch.reset();

        for (var ingredient : recipe.getInputs()) {
            if (ingredient.isEmpty()) {
                continue;
            }

            var satisfied = false;
            if (ingredient instanceof EmiStack single) {
                // getEmiStacks() allocates a list, so fast-path the common case
                satisfied = scratch.consume(inventory, single);
            } else {
                for (var stack : ingredient.getEmiStacks()) {
                    if (scratch.consume(inventory, stack)) {
                        satisfied = true;
                        break;
                    }
                }
            }

            if (!satisfied) {
                return false;
            }
        }
        return true;
    }

    /**
     * What the ingredients of one recipe consumed so far, so a recipe needing four of an item isn't craftable from one.
     */
    private static final class Scratch {
        private EmiStack[] stacks = new EmiStack[16];
        private long[] amounts = new long[16];
        private int count;

        void reset() {
            count = 0;
        }

        boolean consume(EmiPlayerInventory inventory, EmiStack stack) {
            // Key and value are the same instance, so identity comparison is enough
            var identity = inventory.inventory.get(stack);
            if (identity == null) {
                return false;
            }

            var index = -1;
            var alreadyUsed = 0L;
            for (var i = 0; i < count; i++) {
                if (stacks[i] == identity) {
                    index = i;
                    alreadyUsed = amounts[i];
                    break;
                }
            }

            if (identity.getAmount() - alreadyUsed < stack.getAmount()) {
                return false;
            }

            if (index >= 0) {
                amounts[index] = alreadyUsed + stack.getAmount();
            } else {
                if (count == stacks.length) {
                    stacks = Arrays.copyOf(stacks, count * 2);
                    amounts = Arrays.copyOf(amounts, count * 2);
                }
                stacks[count] = identity;
                amounts[count] = stack.getAmount();
                count++;
            }
            return true;
        }
    }
}
