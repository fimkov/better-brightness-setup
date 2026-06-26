package io.github.fimkov.betterbrightness.mixin;

import io.github.fimkov.betterbrightness.client.BrightnessSetupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends Screen {
    protected VideoSettingsScreenMixin(Component component) {
        super(component);
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/OptionsList;addSmall([Lnet/minecraft/client/OptionInstance;)V"))
    private void betterbrightness$dropGammaRow(OptionsList list, OptionInstance<?>[] displayOptions) {
        OptionInstance<Double> gamma = Minecraft.getInstance().options.gamma();
        list.addSmall(Arrays.stream(displayOptions)
                .filter(o -> o != gamma)
                .toArray(OptionInstance[]::new));
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void betterbrightness$addSetupButton(CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        addRenderableWidget(Button.builder(
                        Component.translatable("betterbrightness.setup_button"),
                        b -> Minecraft.getInstance().setScreen(new BrightnessSetupScreen(self)))
                .bounds(6, 6, 120, 20)
                .build());
    }
}
