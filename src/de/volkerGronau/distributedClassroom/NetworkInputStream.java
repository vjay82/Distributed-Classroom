package de.volkerGronau.distributedClassroom;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class NetworkInputStream extends InputStream {
	protected InputStream in;

	public NetworkInputStream(InputStream arg0) {
		this.in = arg0;
	}

	@Override
	public int read() throws IOException {
		return in.read();
	}

	public byte[] readBytes(int count) throws IOException {
		byte[] result = new byte[count];
		if (read(result) < count) {
			throw new EOFException();
		}
		return result;
	}

	public char readChar() throws IOException {
		byte[] bytes = readBytes(1);
		return (char) bytes[0];
	}

	public boolean readBoolean() throws IOException {
		byte[] bytes = readBytes(1);
		return bytes[0] != 0;
	}

	public int readInt() throws IOException {
		byte[] bytes = readBytes(4);
		int result = 0;
		for (int i = 0; i < 4; i++) {
			int shift = (4 - 1 - i) * 8;
			result += (bytes[i] & 0x000000FF) << shift;
		}
		return result;
	}

	public String readString() throws IOException {
		return new String(readBytes(readInt()), StandardCharsets.UTF_8);
	}

}
