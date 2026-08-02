package com.casper.goetyarkham.mixin.client;

import com.casper.goetyarkham.client.SanityHud;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    @ModifyVariable(
            method = "screenToChatY",
            at = @At(value = "LOAD", ordinal = 0),
            ordinal = 0,
            argsOnly = true
    )
    private double goetyarkham$moveMouseWithSanityChat(double mouseY) {
        return mouseY + SanityHud.chatOffsetY();
    }
}
