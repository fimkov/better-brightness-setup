package io.github.fimkov.betterbrightness.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(targets = "net.minecraft.client.gui.components.OptionsList$Entry")
public interface OptionsListEntryAccessor {
    @Accessor("options")
    Map<OptionInstance<?>, AbstractWidget> betterbrightness$getOptions();

    @Accessor("options")
    @Mutable
    void betterbrightness$setOptions(Map<OptionInstance<?>, AbstractWidget> options);

    @Accessor("children")
    List<AbstractWidget> betterbrightness$getChildren();

    @Accessor("children")
    @Mutable
    void betterbrightness$setChildren(List<AbstractWidget> children);
}
