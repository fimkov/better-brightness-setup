package io.github.fimkov.betterbrightness.mixin.sodium;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read-only accessor for Sodium's package-private {@code Option.id} field.
 *
 * <p>Both S3 mixins must scope their behaviour to the single brightness option
 * ({@code sodium:general.gamma}) and leave every other option untouched. The only
 * stable, per-option identity Sodium exposes on the row's bound {@link Option} is its
 * {@link Identifier} {@code id}, but that field is package-private with no getter.
 * This {@code @Accessor} mixin (no-op when Sodium is absent — the whole config is
 * {@code required:false}) lets us read it so both mixins can early-return for anything
 * that isn't the gamma option.
 */
@Mixin(Option.class)
public interface OptionIdAccessor {

    @Accessor("id")
    Identifier betterbrightness$getId();
}
