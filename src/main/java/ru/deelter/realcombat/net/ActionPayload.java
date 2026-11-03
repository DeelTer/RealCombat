package ru.deelter.realcombat.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server: the player pressed something. The server runs the same rules and has the last word. The bytes
 * are {@link Protocol}'s, what a Paper plugin reads too.
 */
public record ActionPayload(Protocol.Action action, int dir) implements CustomPacketPayload {
	public static final Type<ActionPayload> TYPE = new Type<>(Identifier.parse(Protocol.ACTION));
	public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeBytes(Protocol.action(new Protocol.ActionMessage(p.action, p.dir))),
			buf -> {
				Protocol.ActionMessage m = Protocol.action(Payloads.rest(buf));
				return new ActionPayload(m.action(), m.dir());
			});

	@Override
	public Type<ActionPayload> type() {
		return TYPE;
	}
}
