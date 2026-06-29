package io.github.fimkov.betterbrightness.mixin;

import net.minecraft.client.gui.components.OptionsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(targets = "net.minecraft.client.gui.components.OptionsList$Entry")
public interface OptionsListEntryAccessor {
    @Accessor("children")
    List<OptionsList.OptionInstanceWidget> betterbrightness$getChildren();

    @Accessor("children")
    @Mutable
    void betterbrightness$setChildren(List<OptionsList.OptionInstanceWidget> children);
}
