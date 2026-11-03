package ru.deelter.realcombat.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client, in answer to the hello: the server's rules as JSON, so the client predicts with the same timings.
 */
public record RulesPayload(String json) implements CustomPacketPayload {
	public static final Type<RulesPayload> TYPE = new Type<>(Identifier.parse(Protocol.RULES));
	public static final StreamCodec<RegistryFriendlyByteBuf, RulesPayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeBytes(Protocol.rules(p.json)),
			buf -> new RulesPayload(Protocol.rules(Payloads.rest(buf))));

	@Override
	public Type<RulesPayload> type() {
		return TYPE;
	}
}
