package net.krypton.utility;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.krypton.utility.core.ConfigManager;
import net.krypton.utility.core.ModuleManager;
import net.krypton.utility.hud.HudRenderer;
import net.krypton.utility.inventory.InventoryActions;
import net.krypton.utility.inventory.InventoryUi;
import net.krypton.utility.module.FullbrightModule;
import net.krypton.utility.render.EspRenderer;
import net.krypton.utility.render.Projector;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. The mod is 100% client side: it registers no network channel, opens no connection and reads/writes a
 * single file (config/krypton-utility.json).
 */
public final class KryptonUtility implements ClientModInitializer {
    public static final String MOD_ID = "krypton_utility";
    public static final Logger LOGGER = LoggerFactory.getLogger("Krypton Utility");

    @Override
    public void onInitializeClient() {
        ModuleManager.init();
        ConfigManager.captureDefaults();
        ConfigManager.load();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        Keybinds.register(category);

        ClientTickEvents.END_CLIENT_TICK.register(KryptonUtility::tick);

        // HUD + screen-space ESP, drawn after the vanilla subtitles element
        HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES,
                Identifier.fromNamespaceAndPath(MOD_ID, "overlay"), HudRenderer::render);

        // camera matrices for the projected ESP + depth-tested "World 3D" boxes
        WorldRenderEvents.END_EXTRACTION.register(Projector::capture);
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(EspRenderer::render3D);

        InventoryUi.register();

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ModuleManager.disconnect();
            Projector.invalidate();
            InventoryActions.cancel();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> ConfigManager.save());

        SafeLog.info("Krypton Utility loaded (client-only, no network access)");
    }

    private static void tick(Minecraft mc) {
        Keybinds.tick(mc);
        FullbrightModule.tickFade(mc);
        ModuleManager.tick(mc);
        InventoryActions.tick(mc);
    }
}
