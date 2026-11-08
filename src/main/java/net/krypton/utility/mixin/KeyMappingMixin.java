package net.krypton.utility.mixin;

import net.krypton.utility.module.FreecamModule;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides movement/interaction keys from the vanilla input pipeline while Freecam is active. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void krypton$blockIsDown(CallbackInfoReturnable<Boolean> cir) {
        if (FreecamModule.shouldBlock((KeyMapping) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void krypton$blockClick(CallbackInfoReturnable<Boolean> cir) {
        if (FreecamModule.shouldBlock((KeyMapping) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
