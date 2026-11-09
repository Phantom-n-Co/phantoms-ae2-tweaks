package dev.wp.phantoms_ae2_tweaks;

/**
 * Marks a crafting-terminal action as "craft one batch into the player inventory", which AE2 has no action for: its
 * shift action crafts as many as fit in a stack.
 * <p>
 * The marker rides in the {@code extraId} of AE2's own inventory action packet, which AE2 ignores for crafting actions,
 * so nothing new has to be negotiated. A server without this mod simply treats it as the craft-to-cursor action.
 */
public final class CraftOneToInventory {
    public static final long ACTION_ID = 0x7061653274L;

    private CraftOneToInventory() {
    }
}
