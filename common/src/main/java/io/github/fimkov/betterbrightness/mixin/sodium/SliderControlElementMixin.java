package io.github.fimkov.betterbrightness.mixin.sodium;

import io.github.fimkov.betterbrightness.Brightness;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.gui.Colors;
import net.caffeinemc.mods.sodium.client.gui.Dimensioned;
import net.caffeinemc.mods.sodium.client.gui.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.options.control.SliderControl$SliderControlElement", remap = false)
public abstract class SliderControlElementMixin {
    private static final Identifier BRIGHTNESS_OPTION_ID = Identifier.parse("sodium:general.gamma");

    @Shadow
    public abstract IntegerOption getOption();

    private record Icon(int lightLevel, String labelKey, Identifier texture,
                        int texW, int texH, float u, float v, int srcW, int srcH) {
    }

    private static final Icon[] ICONS = {
            new Icon(1, "betterbrightness.short.hidden",
                    Identifier.withDefaultNamespace("textures/entity/creeper/creeper.png"),
                    64, 32, 8.0f, 8.0f, 8, 8),
            new Icon(4, "betterbrightness.short.barely",
                    Identifier.withDefaultNamespace("textures/block/deepslate.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new Icon(7, "betterbrightness.short.clear",
                    Identifier.withDefaultNamespace("textures/block/coal_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new Icon(10, "betterbrightness.short.bright",
                    Identifier.withDefaultNamespace("textures/block/diamond_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
    };

    @Inject(method = "render", at = @At("TAIL"))
    private void betterbrightness$drawCalibrationIcons(
            GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        final IntegerOption option;
        final Identifier id;
        try {
            option = this.getOption();
            id = ((OptionIdAccessor) (Object) option).betterbrightness$getId();
        } catch (Throwable t) {
            return;
        }
        if (!BRIGHTNESS_OPTION_ID.equals(id)) {
            return;
        }

        try {
            Font font = Minecraft.getInstance().font;

            double gamma;
            try {
                gamma = ((Number) option.getValidatedValue()).doubleValue() / 100.0;
            } catch (Throwable valueError) {
                gamma = Minecraft.getInstance().options.gamma().get();
            }

            Dimensioned dim = (Dimensioned) (Object) this;
            int rowLeft = dim.getX() + Layout.OPTION_TEXT_SIDE_PADDING;
            int rowRight = dim.getLimitX() - Layout.OPTION_TEXT_SIDE_PADDING;

            final int n = ICONS.length;
            final int iconSize = 16;
            int iconsTop = dim.getLimitY() + 3;
            float colWidth = (rowRight - rowLeft) / (float) n;

            for (int i = 0; i < n; i++) {
                Icon icon = ICONS[i];
                int colCenterX = rowLeft + (int) ((i + 0.5f) * colWidth);
                int ix = colCenterX - iconSize / 2;

                final int white = 0xFFFFFFFF;
                graphics.fill(ix - 1, iconsTop - 1, ix + iconSize + 1, iconsTop, white);
                graphics.fill(ix - 1, iconsTop + iconSize, ix + iconSize + 1, iconsTop + iconSize + 1, white);
                graphics.fill(ix - 1, iconsTop - 1, ix, iconsTop + iconSize + 1, white);
                graphics.fill(ix + iconSize, iconsTop - 1, ix + iconSize + 1, iconsTop + iconSize + 1, white);

                double bright = Brightness.displayedBrightness(gamma, icon.lightLevel() / 15.0);
                int gray = (int) Math.round(bright * 255.0);
                int argb = 0xFF000000 | (gray << 16) | (gray << 8) | gray;
                try {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, icon.texture(), ix, iconsTop,
                            icon.u(), icon.v(), iconSize, iconSize, icon.srcW(), icon.srcH(),
                            icon.texW(), icon.texH(), argb);
                } catch (Throwable blitError) {
                    graphics.fill(ix, iconsTop, ix + iconSize, iconsTop + iconSize, 0xFF402020);
                }

                String label = Component.translatable(icon.labelKey()).getString();
                int maxLabelW = (int) colWidth - 2;
                while (label.length() > 1 && font.width(label) > maxLabelW) {
                    label = label.substring(0, label.length() - 1);
                }
                graphics.drawCenteredString(font, Component.literal(label),
                        colCenterX, iconsTop + iconSize + 3, Colors.FOREGROUND);
            }
        } catch (Throwable ignored) {
        }
    }
}
