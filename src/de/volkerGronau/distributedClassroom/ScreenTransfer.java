package de.volkerGronau.distributedClassroom;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;

import javax.imageio.ImageIO;

import de.volkerGronau.distributedClassroom.ProxyHelper.ProxyType;

public class ScreenTransfer {

	protected final Robot robot = new Robot();
	protected BufferedImage bufferedImage;
	protected BufferedImage differenceImage;
	protected Screen screen;
	protected String urlString;
	protected Proxy proxy;

	public ScreenTransfer(Screen screen, String name, String serverAddress) throws AWTException {
		super();

		this.screen = screen;
		this.urlString = serverAddress + "?userName=" + name;

		Thread thread = new Thread("Screen Grab Thread") {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						if (proxy == null) {
							proxy = ProxyHelper.getProxy(urlString, ProxyType.os);
						}
						grabScreen();
						Thread.sleep(1000);
					}
				} catch (InterruptedException e) {
				}
			}
		};
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	public void grabScreen() {
		try {
			BufferedImage newBufferedImage = robot.createScreenCapture(screen.getBounds());
			if (differenceImage == null) {
				differenceImage = new BufferedImage(newBufferedImage.getWidth(), newBufferedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
			}

			if (bufferedImage == null || isImageDifferent(bufferedImage, newBufferedImage, differenceImage)) {
				//				ImageIO.write(bufferedImage, "PNG", new File("c:\\temp\\test.png"));
				//				ImageIO.write(differenceImage, "PNG", new File("c:\\temp\\differenceImage.png"));

				sendImage(bufferedImage == null ? newBufferedImage : differenceImage);
				bufferedImage = newBufferedImage;

			}
		} catch (Exception e) {
			bufferedImage = null;
			e.printStackTrace();
		}
	}

	protected void sendImage(BufferedImage bufferedImage) throws Exception {
		URL url = new URL(urlString);
		URLConnection conn = url.openConnection(proxy);
		conn.setDoOutput(true);
		((HttpURLConnection) conn).setRequestMethod("POST");

		boolean ok = false;
		try (OutputStream os = conn.getOutputStream()) {
			ImageIO.write(bufferedImage, "PNG", os);
			os.flush();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if ("OK".equals(line)) {
						ok = true;
					}
				}
			}
		}

		if (!ok) {
			throw new Exception("Did not get expected answer from server.");
		}

	}

	protected boolean isImageDifferent(BufferedImage oldImage, BufferedImage newImage, BufferedImage differenceImage) {
		int[] pixelsOld = ((DataBufferInt) oldImage.getRaster().getDataBuffer()).getData();
		int[] pixelsNew = ((DataBufferInt) newImage.getRaster().getDataBuffer()).getData();
		int[] pixelsDifference = ((DataBufferInt) differenceImage.getRaster().getDataBuffer()).getData();

		boolean diff = false;
		for (int index = pixelsOld.length - 1; index >= 0; index--) {
			if (pixelsOld[index] == pixelsNew[index]) {
				// equal, we can set the difference to transparent
				pixelsDifference[index] = 0;
			} else {
				// all equal, we can set the difference to the difference
				pixelsDifference[index] = pixelsNew[index] + 0xFF000000;
				diff = true;
			}
		}

		return diff;
	}

}
