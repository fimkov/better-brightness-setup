package io.github.fimkov.betterbrightness.mixin.sodium;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.options.control.AbstractOptionList;
import net.caffeinemc.mods.sodium.client.gui.options.control.Control;
import net.caffeinemc.mods.sodium.client.gui.options.control.ControlElement;
import net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(value = OptionListWidget.class, remap = false)
public abstract class OptionListWidgetMixin {
    private static final Identifier BRIGHTNESS_OPTION_ID = Identifier.parse("sodium:general.gamma");

    @Shadow
    private int entryHeight;

    private int betterbrightness$extra;

    private int betterbrightness$extraHeight() {
        return Minecraft.getInstance().font.lineHeight * 4;
    }

    @Redirect(
            method = {"renderAllPages", "renderFilteredOptions"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/gui/options/control/Control;"
                            + "createElement(Lnet/minecraft/client/gui/screens/Screen;"
                            + "Lnet/caffeinemc/mods/sodium/client/gui/options/control/AbstractOptionList;"
                            + "Lnet/caffeinemc/mods/sodium/client/util/Dim2i;"
                            + "Lnet/caffeinemc/mods/sodium/client/gui/ColorTheme;)"
                            + "Lnet/caffeinemc/mods/sodium/client/gui/options/control/ControlElement;"
            ))
    private ControlElement betterbrightness$tallerGammaRow(
            Control control, Screen screen, AbstractOptionList list, Dim2i dim, ColorTheme theme) {
        this.betterbrightness$extra = 0;

        try {
            Option option = control.getOption();
            Identifier id = ((OptionIdAccessor) (Object) option).betterbrightness$getId();
            if (BRIGHTNESS_OPTION_ID.equals(id)) {
                this.betterbrightness$extra = this.betterbrightness$extraHeight();
            }
        } catch (Throwable ignored) {
            this.betterbrightness$extra = 0;
        }

        return control.createElement(screen, list, dim, theme);
    }

    @Redirect(
            method = {"renderAllPages", "renderFilteredOptions"},
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/caffeinemc/mods/sodium/client/gui/widgets/OptionListWidget;entryHeight:I"),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/caffeinemc/mods/sodium/client/gui/options/control/Control;"
                                    + "createElement(Lnet/minecraft/client/gui/screens/Screen;"
                                    + "Lnet/caffeinemc/mods/sodium/client/gui/options/control/AbstractOptionList;"
                                    + "Lnet/caffeinemc/mods/sodium/client/util/Dim2i;"
                                    + "Lnet/caffeinemc/mods/sodium/client/gui/ColorTheme;)"
                                    + "Lnet/caffeinemc/mods/sodium/client/gui/options/control/ControlElement;"
                    )
            ))
    private int betterbrightness$advanceForGammaRow(OptionListWidget self) {
        int delta = this.betterbrightness$extra;
        this.betterbrightness$extra = 0;
        return this.entryHeight + delta;
    }
}
