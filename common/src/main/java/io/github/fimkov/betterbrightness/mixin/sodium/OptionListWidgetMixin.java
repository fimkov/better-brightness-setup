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
 * Reserves a blank vertical band <em>below</em> ONLY the Sodium brightness option's row
 * ({@code sodium:general.gamma}) — room for the 4 inline calibration icons + captions that
 * {@link SliderControlElementMixin} draws there. Every other row keeps Sodium's uniform
 * {@code Layout.entryHeight(font)}.
 *
 * <p><b>How Sodium lays out rows (0.9.0, verified from bytecode).</b> {@code OptionListWidget}
 * computes one fixed {@code this.entryHeight = Layout.entryHeight(this.font)} (= {@code font.lineHeight*2})
 * and, for each option, builds {@code new Dim2i(x, y + listHeight, width, this.entryHeight)}, hands it to
 * {@code control.createElement(...)}, then advances {@code listHeight += this.entryHeight}. The control
 * does not get to declare its own height.
 *
 * <p>We deliberately leave the gamma row's {@code Dim2i} <em>unchanged</em>: enlarging it would push the
 * element's {@code getCenterY()} (= {@code getY()+getHeight()/2}) down and float Sodium's slider + value
 * label in the middle of an oversized row (the "widget too big" look). Instead we inflate ONLY the
 * {@code listHeight} advance for that one row — the element renders at normal height with its slider + label
 * pinned to the top, and the inflated advance pushes the NEXT option down, leaving a blank band directly
 * under the row that {@link SliderControlElementMixin} draws the icons into. (Drawing into that band is safe:
 * {@code OptionListWidget} scissors only the whole list viewport, never per-row.)
 *
 * <p><b>Mechanism (two coordinated redirects, scoped to the gamma row).</b>
 * <ol>
 *   <li>{@code @Redirect} the single {@code Control.createElement(...)} call in each row loop
 *       ({@code renderAllPages} / {@code renderFilteredOptions}). The handler reads the bound option's id
 *       via {@link OptionIdAccessor}; for {@code sodium:general.gamma} it records the reserve height
 *       ({@link #betterbrightness$extraHeight()}) in {@link #betterbrightness$extra} but passes the
 *       {@code Dim2i} through UNCHANGED; for every other option it sets the delta to {@code 0}.</li>
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

    /**
     * Blank vertical room reserved <em>below</em> the brightness row for the icon row + per-column labels.
     *
     * <p>We do NOT enlarge the row element's own {@code Dim2i} (that would move Sodium's
     * {@code getCenterY()} down and float the slider + value label in the middle of an oversized row — the
     * "widget too big" look). The element keeps its normal height, so Sodium draws the slider + label pinned
     * to the top; we only inflate the {@code listHeight} advance by this much, which pushes the NEXT option
     * down and leaves a blank band right under this row. {@link SliderControlElementMixin} draws the 16px
     * icons + a label line into that band (which is safe to draw into: {@code OptionListWidget} scissors only
     * the whole list viewport, never per-row). {@code lineHeight*4} ≈ 36px fits a 16px icon + label snugly
     * while staying compact.
     */
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
        this.betterbrightness$extra = 0;

        try {
            Option option = control.getOption();
            Identifier id = ((OptionIdAccessor) (Object) option).betterbrightness$getId();
            if (BRIGHTNESS_OPTION_ID.equals(id)) {
                // Reserve blank space below this row via the listHeight advance only; the element keeps its
                // normal Dim2i so Sodium's slider + label render pinned to the top (not floated in the centre).
                this.betterbrightness$extra = this.betterbrightness$extraHeight();
            }
        } catch (Throwable ignored) {
            // If anything about Sodium's option model shifts, fall back to the unmodified row.
            this.betterbrightness$extra = 0;
        }

        return control.createElement(screen, list, dim, theme);
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
