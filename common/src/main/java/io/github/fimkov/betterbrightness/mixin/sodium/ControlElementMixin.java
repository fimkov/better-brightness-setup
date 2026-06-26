package io.github.fimkov.betterbrightness.mixin.sodium;

import io.github.fimkov.betterbrightness.Brightness;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.control.ControlElement;
import me.jellysquid.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ControlElement.class, remap = false)
public abstract class ControlElementMixin {
    @Shadow
    public abstract Option<?> getOption();

    @Shadow
    public abstract Dim2i getDimensions();

    private record Icon(int lightLevel, String labelKey, ResourceLocation texture,
                        int texW, int texH, float u, float v, int srcW, int srcH) {
    }

    private static final Icon[] BETTERBRIGHTNESS_ICONS = {
            new Icon(1, "betterbrightness.short.hidden",
                    new ResourceLocation("minecraft", "textures/entity/creeper/creeper.png"),
                    64, 32, 8.0f, 8.0f, 8, 8),
            new Icon(4, "betterbrightness.short.barely",
                    new ResourceLocation("minecraft", "textures/block/deepslate.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new Icon(7, "betterbrightness.short.clear",
                    new ResourceLocation("minecraft", "textures/block/coal_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
            new Icon(10, "betterbrightness.short.bright",
                    new ResourceLocation("minecraft", "textures/block/diamond_ore.png"),
                    16, 16, 0.0f, 0.0f, 16, 16),
    };

    private boolean betterbrightness$isBrightness() {
        try {
            return Component.translatable("options.gamma").equals(getOption().getName());
        } catch (Throwable t) {
            return false;
        }
    }

    @Inject(method = "method_25394", at = @At("TAIL"))
    private void betterbrightness$drawCalibrationIcons(
            GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!betterbrightness$isBrightness()) {
            return;
        }

        try {
            Font font = Minecraft.getInstance().font;

            double gamma;
            try {
                gamma = ((Number) getOption().getValue()).doubleValue() / 100.0;
            } catch (Throwable valueError) {
                gamma = Minecraft.getInstance().options.gamma().get();
            }

            Dim2i dim = getDimensions();
            int rowLeft = dim.x() + 6;
            int rowRight = dim.getLimitX() - 6;

            final int n = BETTERBRIGHTNESS_ICONS.length;
            final int iconSize = 16;
            int iconsTop = dim.getLimitY() + 3;
            float colWidth = (rowRight - rowLeft) / (float) n;

            for (int i = 0; i < n; i++) {
                Icon icon = BETTERBRIGHTNESS_ICONS[i];
                int colCenterX = rowLeft + (int) ((i + 0.5f) * colWidth);
                int ix = colCenterX - iconSize / 2;

                final int white = 0xFFFFFFFF;
                guiGraphics.fill(ix - 1, iconsTop - 1, ix + iconSize + 1, iconsTop, white);
                guiGraphics.fill(ix - 1, iconsTop + iconSize, ix + iconSize + 1, iconsTop + iconSize + 1, white);
                guiGraphics.fill(ix - 1, iconsTop - 1, ix, iconsTop + iconSize + 1, white);
                guiGraphics.fill(ix + iconSize, iconsTop - 1, ix + iconSize + 1, iconsTop + iconSize + 1, white);

                float bright = (float) Math.max(0.0, Math.min(1.0,
                        Brightness.displayedBrightness(gamma, icon.lightLevel() / 15.0)));
                try {
                    guiGraphics.setColor(bright, bright, bright, 1.0f);
                    guiGraphics.blit(icon.texture(), ix, iconsTop, iconSize, iconSize,
                            icon.u(), icon.v(), icon.srcW(), icon.srcH(), icon.texW(), icon.texH());
                    guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                } catch (Throwable blitError) {
                    guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                    guiGraphics.fill(ix, iconsTop, ix + iconSize, iconsTop + iconSize, 0xFF402020);
                }

                String label = Component.translatable(icon.labelKey()).getString();
                int maxLabelW = (int) colWidth - 2;
                while (label.length() > 1 && font.width(label) > maxLabelW) {
                    label = label.substring(0, label.length() - 1);
                }
                guiGraphics.drawCenteredString(font, Component.literal(label),
                        colCenterX, iconsTop + iconSize + 3, 0xFFFFFFFF);
            }
        } catch (Throwable ignored) {
        }
    }
}
