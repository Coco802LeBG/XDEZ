package net.krypton.utility.mixin;

import net.krypton.utility.module.FreecamModule;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Redirects mouse-look to the free camera while Freecam is active so the player body stays put. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void krypton$freecamTurn(double yRot, double xRot, CallbackInfo ci) {
        if (FreecamModule.handleTurn((Entity) (Object) this, yRot, xRot)) {
            ci.cancel();
        }
    }
}
