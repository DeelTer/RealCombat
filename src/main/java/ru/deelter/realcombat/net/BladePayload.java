package ru.deelter.realcombat.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server: the way a VR player's sword points, as drawn ({@link Protocol#blade}).
 */
public record BladePayload(float x, float y, float z) implements CustomPacketPayload {
	public static final Type<BladePayload> TYPE = new Type<>(Identifier.parse(Protocol.BLADE));
	public static final StreamCodec<RegistryFriendlyByteBuf, BladePayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeBytes(Protocol.blade(p.x, p.y, p.z)),
			buf -> {
				float[] d = Protocol.blade(Payloads.rest(buf));
				return new BladePayload(d[0], d[1], d[2]);
			});

	@Override
	public Type<BladePayload> type() {
		return TYPE;
	}
}
