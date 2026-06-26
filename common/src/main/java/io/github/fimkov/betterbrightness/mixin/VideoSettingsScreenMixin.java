package io.github.fimkov.betterbrightness.mixin;

import io.github.fimkov.betterbrightness.client.BrightnessSetupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Arrays;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin {
    @Redirect(
            method = "addOptions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/OptionsList;addSmall([Lnet/minecraft/client/OptionInstance;)V",
                    ordinal = 0))
    private void betterbrightness$replaceGammaWithButton(OptionsList list, OptionInstance<?>[] displayOptions) {
        Options opts = Minecraft.getInstance().options;
        OptionInstance<Double> gamma = opts.gamma();

        list.addSmall(Arrays.stream(displayOptions)
                .filter(o -> o != gamma)
                .toArray(OptionInstance[]::new));

        Button button = Button.builder(
                        Component.translatable("betterbrightness.setup_button"),
                        b -> Minecraft.getInstance().setScreen(
                                new BrightnessSetupScreen((Screen) (Object) this)))
                .build();
        list.addSmall(button, null);
    }
}
