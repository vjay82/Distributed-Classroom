package de.volkerGronau.distributedClassroom.startWindow;

import de.volkerGronau.distributedClassroom.settings.Settings;

public interface RunAfterStartWindowClosed {
	public void run(Settings settings, boolean startServer, boolean startClient);
}
