package de.volkerGronau.distributedClassroom;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class NetworkOutputStream extends OutputStream {
	protected OutputStream out;

	public NetworkOutputStream(OutputStream out) {
		super();
		this.out = out;
	}

	@Override
	public void write(int arg0) throws IOException {
		out.write(arg0);
	}

	public void writeInt(int i) throws IOException {
		byte[] bytes = new byte[4];
		bytes[3] = (byte) (i & 0xFF);
		bytes[2] = (byte) ((i >> 8) & 0xFF);
		bytes[1] = (byte) ((i >> 16) & 0xFF);
		bytes[0] = (byte) ((i >> 24) & 0xFF);
		out.write(bytes);
	}

	public void writeBoolean(boolean b) throws IOException {
		byte[] bytes = new byte[1];
		bytes[0] = (byte) (b ? 1 : 0);
		out.write(bytes);
	}

	public void writeChar(char c) throws IOException {
		byte[] bytes = new byte[1];
		bytes[0] = (byte) c;
		out.write(bytes);
	}

	public void writeString(String s) throws IOException {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		writeInt(bytes.length);
		write(bytes);
	}
}
