package dev.wp.phantoms_ae2_tweaks;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config. The sort order is read on every view update, so the value is cached.
 */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue CREATIVE_SORT;

    private static volatile boolean creativeSort = true;

    static {
        var builder = new ModConfigSpec.Builder();
        CREATIVE_SORT = builder
                .comment("Sort the terminal's 'Mod' order the way the creative inventory lists items, instead of by mod.")
                .define("creativeSort", true);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    public static boolean creativeSort() {
        return creativeSort;
    }

    public static void onConfigEvent(ModConfigEvent event) {
        creativeSort = CREATIVE_SORT.get();
    }
}
