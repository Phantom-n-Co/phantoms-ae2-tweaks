package dev.wp.phantoms_ae2_tweaks;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side addon for Applied Energistics 2, currently focused on its EMI integration.
 */
@Mod(PAE2T.MOD_ID)
public final class PAE2T {
    public static final String MOD_ID = "phantoms_ae2_tweaks";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public PAE2T(IEventBus modBus, ModContainer container) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        modBus.addListener(ClientConfig::onConfigEvent);
    }
}
