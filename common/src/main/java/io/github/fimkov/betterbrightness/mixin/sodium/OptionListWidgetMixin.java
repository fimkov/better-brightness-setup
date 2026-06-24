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

/**
 * Gives ONLY the Sodium brightness option's row ({@code sodium:general.gamma}) extra vertical
 * height — room for the 4 inline calibration icons + captions that {@link SliderControlElementMixin}
 * draws below the track. Every other row keeps Sodium's uniform {@code Layout.entryHeight(font)}.
 *
 * <p><b>How Sodium lays out rows (0.9.0, verified from bytecode).</b> {@code OptionListWidget}
 * computes one fixed {@code this.entryHeight = Layout.entryHeight(this.font)} (= {@code font.lineHeight*2})
 * and, for each option, builds {@code new Dim2i(x, y + listHeight, width, this.entryHeight)}, hands it to
 * {@code control.createElement(...)}, then advances {@code listHeight += this.entryHeight}. The control
 * does not get to declare its own height. So to make one row taller we must change BOTH the {@code Dim2i}
 * height handed to that row's element AND the matching {@code listHeight} advance, or the list geometry
 * (scroll extent, the rows below) desyncs.
 *
 * <p><b>Mechanism (two coordinated redirects, scoped to the gamma row).</b>
 * <ol>
 *   <li>{@code @Redirect} the single {@code Control.createElement(...)} call in each row loop
 *       ({@code renderAllPages} / {@code renderFilteredOptions}). The handler reads the bound option's id
 *       via {@link OptionIdAccessor}; for {@code sodium:general.gamma} it rebuilds the {@code Dim2i} taller
 *       by {@link #betterbrightness$extraHeight()} and records that delta in {@link #betterbrightness$extra};
 *       for every other option it sets the delta to {@code 0} and passes the dimensions through unchanged.</li>
 *   <li>{@code @Redirect} the {@code this.entryHeight} field read that feeds {@code listHeight += entryHeight}
 *       <em>immediately after</em> {@code createElement} (isolated with a {@code @Slice} starting at the
 *       {@code createElement} invoke, so only that one read is affected), returning
 *       {@code entryHeight + betterbrightness$extra}. Because the redirect handler in step 1 runs right
 *       before this read in the same loop iteration, the advance always matches the row that was just built.</li>
 * </ol>
 * Both redirects target the SAME extra-height value, so the taller gamma row and the list advance stay
 * consistent; all non-gamma rows are byte-for-byte unchanged (delta {@code 0}).
 *
 * <p>Fragile + version-locked: these are Sodium non-API internals (the row-build loop and the
 * {@code entryHeight} field). Pinned to Sodium {@code 0.9.0} / MC 26.2. The whole {@code betterbrightness.sodium}
 * mixin config is {@code required:false}, so when Sodium is absent none of this applies and the game boots
 * normally.
 */
@Mixin(value = OptionListWidget.class, remap = false)
public abstract class OptionListWidgetMixin {

    /** The one option whose row we make taller. */
    private static final Identifier BRIGHTNESS_OPTION_ID = Identifier.parse("sodium:general.gamma");

    /** Sodium's per-rebuild row height (= {@code Layout.entryHeight(font)}). */
    @Shadow
    private int entryHeight;

    /**
     * Extra height (px) for the row most recently built by {@link #betterbrightness$tallerGammaRow}.
     * Non-zero only for the gamma row; reset to 0 for every other row. Read by the
     * {@code entryHeight}-advance redirect in the same loop iteration.
     */
    private int betterbrightness$extra;

    /** Extra vertical room reserved under the brightness slider for the icon row + captions. */
    private int betterbrightness$extraHeight() {
        return Minecraft.getInstance().font.lineHeight * 4;
    }

    /**
     * Redirect the per-option {@code control.createElement(...)} call. For the brightness option only,
     * hand the element a taller {@link Dim2i} and remember the delta so the list advance below matches.
     */
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
        Dim2i useDim = dim;
        this.betterbrightness$extra = 0;

        try {
            Option option = control.getOption();
            Identifier id = ((OptionIdAccessor) (Object) option).betterbrightness$getId();
            if (BRIGHTNESS_OPTION_ID.equals(id)) {
                int extra = this.betterbrightness$extraHeight();
                this.betterbrightness$extra = extra;
                useDim = new Dim2i(dim.x(), dim.y(), dim.width(), dim.height() + extra);
            }
        } catch (Throwable ignored) {
            // If anything about Sodium's option model shifts, fall back to the unmodified row.
            this.betterbrightness$extra = 0;
            useDim = dim;
        }

        return control.createElement(screen, list, useDim, theme);
    }

    /**
     * Redirect the {@code this.entryHeight} read that drives {@code listHeight += entryHeight} right after
     * {@code createElement}, adding the gamma row's extra height so the list geometry stays consistent.
     * The {@code @Slice} pins this to reads at or after the {@code createElement} invoke — the
     * row-{@code Dim2i}'s own {@code entryHeight} read (before {@code createElement}) and all header reads
     * are left untouched.
     *
     * <p><b>Consume-once.</b> The slice has no upper bound, so in {@code renderAllPages} it also matches the
     * {@code listHeight += entryHeight} advance inside the later {@code ExternalPage} else-branch. To stop a
     * non-zero delta from the gamma row bleeding into a subsequent {@code ExternalPage} advance in the same
     * rebuild (which a third-party Sodium-API mod could add — Sodium's own pages are all {@code OptionPage}),
     * this handler reads the delta and RESETS {@link #betterbrightness$extra} to {@code 0} immediately. The
     * gamma row's {@code createElement} redirect sets the delta right before this advance, so it is consumed
     * exactly once — by the gamma row's own advance — and can never apply to any later row, order-independent.
     */
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
        this.betterbrightness$extra = 0; // consume once; never leak into a later ExternalPage advance
        return this.entryHeight + delta;
    }
}
