package ru.deelter.realcombat.net;

import io.netty.buffer.ByteBuf;

final class Payloads {
	private static final int MAX = Protocol.MAX_RULES * 3 + 8;

	private Payloads() {
	}

	/**
	 * what is left of a message, for {@link Protocol} to read
	 */
	static byte[] rest(ByteBuf buf) {
		int length = buf.readableBytes();
		if (length > MAX) throw new IllegalArgumentException("realcombat message too big: " + length);
		byte[] data = new byte[length];
		buf.readBytes(data);
		return data;
	}
}
