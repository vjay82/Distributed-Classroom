package de.volkerGronau.distributedClassroom.settings;

public class Settings {
	protected String name;
	protected int screen;
	protected String serverAddress;
	protected int serverPort;

	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getScreen() {
		return screen;
	}
	public void setScreen(int screen) {
		this.screen = screen;
	}
	public String getServerAddress() {
		return serverAddress;
	}
	public void setServerAddress(String serverAddress) {
		this.serverAddress = serverAddress;
	}
	public int getServerPort() {
		return serverPort;
	}
	public void setServerPort(int serverPort) {
		this.serverPort = serverPort;
	}

}
