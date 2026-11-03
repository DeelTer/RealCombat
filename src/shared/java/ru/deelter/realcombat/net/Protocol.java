package ru.deelter.realcombat.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The messages between the client and the server, as the bytes of a plugin channel. The Fabric server and the Paper
 * plugin write and read them with this class, so a client talks to either the same way. Numbers are big-endian, as
 * Minecraft's own buffers; a varint is Minecraft's.
 */
public final class Protocol {
	public static final String ACTION = "realcombat:action";
	public static final String STATE = "realcombat:state";
	public static final String RULES = "realcombat:rules";
	public static final String BLADE = "realcombat:blade";
	public static final int MAX_RULES = 1 << 15;

	private Protocol() {
	}

	/**
	 * Client to server: the player pressed something.
	 */
	public enum Action {
		/**
		 * the client has the mod: its player fights by these rules from now on
		 */
		HELLO,
		ATTACK,
		FEINT,
		PARRY,
		KICK;

		private static final Action[] VALUES = values();

		public static Action of(int ordinal) {
			return VALUES[Math.clamp(ordinal, 0, VALUES.length - 1)];
		}
	}

	public record ActionMessage(Action action, int dir) {
	}

	/**
	 * Server to client: where a fighter is in his swing.
	 *
	 * @param forced  the change was not the fighter's own doing: his own client takes it over its prediction
	 * @param combo   his last blow landed: a short recovery, the next swing may start at once
	 * @param ripostes a parry of his caught a blow: his next swing is a riposte
	 */
	public record StateMessage(int entity, int phase, int dir, int ticks, int length, float stamina, boolean riposte, boolean forced,
							   boolean combo, boolean ripostes) {
		public StateMessage(int entity, int phase, int dir, int ticks, int length, float stamina, boolean riposte, boolean forced) {
			this(entity, phase, dir, ticks, length, stamina, riposte, forced, false, false);
		}
	}

	public static byte[] action(ActionMessage message) {
		return write(out -> {
			varint(out, message.action().ordinal());
			varint(out, message.dir());
		});
	}

	public static ActionMessage action(byte[] data) {
		return read(data, in -> new ActionMessage(Action.of(varint(in)), varint(in)));
	}

	public static byte[] state(StateMessage m) {
		return write(out -> {
			out.writeInt(m.entity());
			out.writeByte(m.phase());
			out.writeByte(m.dir());
			out.writeShort(m.ticks());
			out.writeShort(m.length());
			out.writeFloat(m.stamina());
			out.writeByte((m.riposte() ? 1 : 0) | (m.forced() ? 2 : 0) | (m.combo() ? 4 : 0) | (m.ripostes() ? 8 : 0));
		});
	}

	public static StateMessage state(byte[] data) {
		return read(data, in -> {
			int entity = in.readInt();
			int phase = in.readByte();
			int dir = in.readByte();
			int ticks = in.readShort();
			int length = in.readShort();
			float stamina = in.readFloat();
			int flags = in.readByte();
			return new StateMessage(entity, phase, dir, ticks, length, stamina, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
		});
	}

	/**
	 * Client to server, every tick while in VR with a weapon: the way the sword in the hand points, as drawn. The
	 * server gets the controller's aim from Vivecraft, but the sword is held at an angle to it.
	 */
	public static byte[] blade(float x, float y, float z) {
		return write(out -> {
			out.writeFloat(x);
			out.writeFloat(y);
			out.writeFloat(z);
		});
	}

	public static float[] blade(byte[] data) {
		return read(data, in -> new float[]{in.readFloat(), in.readFloat(), in.readFloat()});
	}

	/**
	 * the server's rules, as JSON in a Minecraft string (a varint length in bytes, then UTF-8)
	 */
	public static byte[] rules(String json) {
		return write(out -> {
			byte[] text = json.getBytes(StandardCharsets.UTF_8);
			varint(out, text.length);
			out.write(text);
		});
	}

	public static String rules(byte[] data) {
		return read(data, in -> {
			int length = varint(in);
			if (length < 0 || length > MAX_RULES * 3) throw new IOException("rules too long: " + length);
			return new String(in.readNBytes(length), StandardCharsets.UTF_8);
		});
	}

	private interface Writer {
		void write(DataOutputStream out) throws IOException;
	}

	private interface Reader<T> {
		T read(DataInputStream in) throws IOException;
	}

	private static byte[] write(Writer writer) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			writer.write(out);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return bytes.toByteArray();
	}

	private static <T> T read(byte[] data, Reader<T> reader) {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			return reader.read(in);
		} catch (IOException e) {
			throw new IllegalArgumentException("bad realcombat message", e);
		}
	}

	private static void varint(DataOutputStream out, int value) throws IOException {
		while ((value & ~0x7F) != 0) {
			out.writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.writeByte(value);
	}

	private static int varint(DataInputStream in) throws IOException {
		int value = 0;
		for (int shift = 0; shift < 35; shift += 7) {
			int b = in.readUnsignedByte();
			value |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) return value;
		}
		throw new IOException("varint too long");
	}
}
