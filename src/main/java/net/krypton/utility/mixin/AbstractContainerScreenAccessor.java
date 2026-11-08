package net.krypton.utility.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int krypton$leftPos();

    @Accessor("topPos")
    int krypton$topPos();

    @Accessor("imageWidth")
    int krypton$imageWidth();

    @Accessor("imageHeight")
    int krypton$imageHeight();
}
