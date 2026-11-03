package ru.deelter.realcombat.mixin.client;

import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.deelter.realcombat.client.Hud;

/**
 * A parry and a broken guard are shown as icons at the crosshair instead of words over the hotbar. The server (Fabric
 * or Paper) and the sparring partner say them as translatable action bar messages; those are caught here.
 */
@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
	@Inject(method = "handleOverlay", at = @At("HEAD"), cancellable = true)
	private void realcombat$icon(Component message, CallbackInfo ci) {
		if (Hud.outcomeIcon(message)) ci.cancel();
	}

	@Inject(method = "handleSystemMessage", at = @At("HEAD"), cancellable = true)
	private void realcombat$iconSystem(Component message, boolean overlay, CallbackInfo ci) {
		if (overlay && Hud.outcomeIcon(message)) ci.cancel();
	}
}
