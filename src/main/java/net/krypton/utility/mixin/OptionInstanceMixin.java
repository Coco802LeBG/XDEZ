package net.krypton.utility.mixin;

import net.krypton.utility.module.FreecamModule;
import net.krypton.utility.module.FullbrightModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds the renderer a different gamma (Fullbright) or FOV (Freecam) without ever touching the stored option value.
 * Both checks start with a cheap static test because this getter is called very often.
 */
@Mixin(OptionInstance.class)
public abstract class OptionInstanceMixin<T> {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("unchecked")
    private void krypton$override(CallbackInfoReturnable<T> cir) {
        boolean gamma = FullbrightModule.isActive();
        boolean fov = FreecamModule.isActive();
        if (!gamma && !fov) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Options options = mc == null ? null : mc.options;
        if (options == null) {
            return;
        }
        if (gamma && (Object) this == options.gamma()) {
            cir.setReturnValue((T) (Object) Double.valueOf(FullbrightModule.gamma()));
        } else if (fov && (Object) this == options.fov()) {
            int override = FreecamModule.fovOverride();
            if (override > 0) {
                cir.setReturnValue((T) (Object) Integer.valueOf(override));
            }
        }
    }
}
