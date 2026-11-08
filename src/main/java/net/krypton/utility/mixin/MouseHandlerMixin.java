package net.krypton.utility.mixin;

import net.krypton.utility.module.FreecamModule;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the mouse wheel change the Freecam speed instead of scrolling the hotbar. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void krypton$freecamScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (FreecamModule.handleScroll(yOffset)) {
            ci.cancel();
        }
    }
}
