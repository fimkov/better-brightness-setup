package io.github.fimkov.betterbrightness.mixin;

import io.github.fimkov.betterbrightness.client.BrightnessSetupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Arrays;

/**
 * Replaces the vanilla brightness (gamma) slider in Video Settings with a "Setup Brightness" button
 * that opens our calibration screen — same half-width slot the slider occupied.
 *
 * <p>26.2: {@code VideoSettingsScreen.addOptions()} adds the Display section via
 * {@code this.list.addSmall(displayOptions(options))}; in that array {@code gamma} is paired with
 * {@code preferredGraphicsBackend} as the last small (half-width) row. We {@code @Redirect} that
 * {@code addSmall(OptionInstance[])} call (the redirect hands us the {@code OptionsList} receiver
 * directly — no {@code @Shadow} of the superclass {@code OptionsSubScreen.list}, which Mixin can't
 * resolve against this subclass target) and rebuild the section: every display option except
 * {@code gamma} and {@code preferredGraphicsBackend}, then a half-width row of
 * [Setup Brightness button | graphics-backend], so the button sits exactly where the slider was.
 * The button opens {@link BrightnessSetupScreen} with this screen as the parent (Done returns here).
 */
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
        OptionInstance<?> backend = opts.preferredGraphicsBackend();

        // Display options minus gamma and the graphics-backend (re-added beside the button below).
        list.addSmall(Arrays.stream(displayOptions)
                .filter(o -> o != gamma && o != backend)
                .toArray(OptionInstance[]::new));

        // gamma's old half-width slot: "Setup Brightness" button (left) + graphics-backend option (right).
        Button button = Button.builder(
                        Component.translatable("betterbrightness.setup_button"),
                        b -> Minecraft.getInstance().gui.setScreen(
                                new BrightnessSetupScreen((Screen) (Object) this)))
                .build();
        AbstractWidget backendWidget = backend.createButton(opts);
        list.addSmall(button, backendWidget);
    }
}
