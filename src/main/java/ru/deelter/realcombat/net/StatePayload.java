package ru.deelter.realcombat.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import ru.deelter.realcombat.combat.Fighter;

/**
 * Server to client: where a fighter is in his swing, for drawing it and for reading it. One message per change of
 * phase; the client runs the phase on by itself. The bytes are {@link Protocol}'s.
 */
public record StatePayload(Protocol.StateMessage message) implements CustomPacketPayload {
	public static final Type<StatePayload> TYPE = new Type<>(Identifier.parse(Protocol.STATE));
	public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeBytes(Protocol.state(p.message)),
			buf -> new StatePayload(Protocol.state(Payloads.rest(buf))));

	public static StatePayload of(int entity, Fighter f) {
		return new StatePayload(new Protocol.StateMessage(entity, f.getPhase().ordinal(), f.getDir().ordinal(), f.getTicks(), f.getLength(),
				f.getStamina(), f.isRiposte(), f.isForced(), f.isCombo(), f.getRiposteWindow() > 0, f.isOffhand()));
	}

	public int entity() {
		return message.entity();
	}

	public int phase() {
		return message.phase();
	}

	public int dir() {
		return message.dir();
	}

	public int ticks() {
		return message.ticks();
	}

	public int length() {
		return message.length();
	}

	public float stamina() {
		return message.stamina();
	}

	public boolean riposte() {
		return message.riposte();
	}

	public boolean forced() {
		return message.forced();
	}

	public boolean combo() {
		return message.combo();
	}

	public boolean ripostes() {
		return message.ripostes();
	}

	public boolean offhand() {
		return message.offhand();
	}

	@Override
	public Type<StatePayload> type() {
		return TYPE;
	}
}
