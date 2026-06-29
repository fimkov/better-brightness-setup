package io.github.fimkov.betterbrightness.mixin;

import io.github.fimkov.betterbrightness.client.BrightnessSetupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin {
    @Shadow
    protected OptionsList list;

    @Inject(method = "init", at = @At("TAIL"))
    private void betterbrightness$replaceGammaWithButton(CallbackInfo ci) {
        if (this.list == null) {
            return;
        }
        OptionInstance<Double> gamma = Minecraft.getInstance().options.gamma();
        AbstractWidget gammaWidget = this.list.findOption(gamma);
        if (gammaWidget == null) {
            return;
        }

        Screen self = (Screen) (Object) this;
        Button button = Button.builder(
                        Component.translatable("betterbrightness.setup_button"),
                        b -> Minecraft.getInstance().setScreen(new BrightnessSetupScreen(self)))
                .bounds(gammaWidget.getX(), gammaWidget.getY(), gammaWidget.getWidth(), gammaWidget.getHeight())
                .build();

        for (Object entryObject : this.list.children()) {
            if (!(entryObject instanceof OptionsListEntryAccessor entry)) {
                continue;
            }
            List<OptionsList.OptionInstanceWidget> children = entry.betterbrightness$getChildren();
            if (children == null) {
                continue;
            }

            boolean found = false;
            List<OptionsList.OptionInstanceWidget> newChildren = new ArrayList<>();
            for (OptionsList.OptionInstanceWidget child : children) {
                if (child.widget() == gammaWidget) {
                    newChildren.add(new OptionsList.OptionInstanceWidget(button, gamma));
                    found = true;
                } else {
                    newChildren.add(child);
                }
            }

            if (found) {
                entry.betterbrightness$setChildren(newChildren);
                break;
            }
        }
    }
}
