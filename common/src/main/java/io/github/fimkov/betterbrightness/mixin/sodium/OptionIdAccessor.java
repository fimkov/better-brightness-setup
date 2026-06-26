package io.github.fimkov.betterbrightness.mixin.sodium;

import net.caffeinemc.mods.sodium.client.config.structure.Option;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Option.class)
public interface OptionIdAccessor {
    @Accessor("id")
    Identifier betterbrightness$getId();
}
