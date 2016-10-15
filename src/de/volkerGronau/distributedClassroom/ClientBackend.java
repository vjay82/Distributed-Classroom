package de.volkerGronau.distributedClassroom;

import java.awt.MouseInfo;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.mutable.MutableBoolean;

import de.volkerGronau.distributedClassroom.ProxyHelper.ProxyType;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;

public class ClientBackend {

	public static enum UserStatus {
		OK, NEUTRAL, NOT_OK
	}

	protected final Robot robot = new Robot();

	protected BufferedImage oldImage;
	protected BufferedImage differenceImage;
	protected BufferedImage imageToSendToServer;

	protected Screen screen;
	protected String urlBaseString;
	protected Proxy proxy;
	protected long takeNextPictureAt = 0;
	protected java.awt.Point cursorPosition;
	protected java.awt.Point oldCursorPosition;
	protected int changedPixels;
	protected boolean isInputControlledByServer;
	protected long nextForcedContact = 0;
	protected Runnable onResetUserStatus;
	protected UserStatus userStatus = UserStatus.NEUTRAL;
	protected UserStatus oldUserStatus;
	protected long oldLastUserStatusReset;
	protected int hotPixels;
	protected int hotPixelHitCount;

	public ClientBackend(Screen screen, String name, String serverAddress) throws Exception {
		super();

		this.screen = screen;
		this.urlBaseString = serverAddress + "?userName=" + name;

		Thread thread = new Thread("Server Interactionthread") {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						long startTime = System.currentTimeMillis();
						if (proxy == null) {
							proxy = ProxyHelper.getProxy(urlBaseString, ProxyType.os);
						}
						updateData();
						//						if (!isInputControlledByServer) {
						long waitingTime = 100l - System.currentTimeMillis() + startTime;
						if (waitingTime > 0) {
							Thread.sleep(waitingTime);
						}
						//						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		thread.setDaemon(true);
		//		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	public void updateData() {
		try {
			long currentTimeMillis = System.currentTimeMillis();

			if (takeNextPictureAt < currentTimeMillis) {
				BufferedImage newBufferedImage = robot.createScreenCapture(screen.getBounds());
				if (differenceImage == null) {
					differenceImage = new BufferedImage(newBufferedImage.getWidth(), newBufferedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
				}

				if (oldImage == null) {
					imageToSendToServer = newBufferedImage;
					oldImage = newBufferedImage;
					changedPixels = 0;
					contactServer();
				} else {
					changedPixels = getChangedPixels(oldImage, newBufferedImage, differenceImage);
					if (changedPixels > 0) {
						if (changedPixels != hotPixels || hotPixelHitCount == 0) { // mac OS non retina = 30 pixels blinking cursor, retina = ?
							if (hotPixels == changedPixels) {
								hotPixelHitCount = 1;//++;
							} else {
								hotPixels = changedPixels;
								hotPixelHitCount = 0;
							}
							imageToSendToServer = differenceImage;
							oldImage = newBufferedImage;
							contactServer();
						} else {
							imageToSendToServer = null;
						}
					} else {
						imageToSendToServer = null;
					}
				}
			} else {
				imageToSendToServer = null;
			}

			java.awt.Point newCursorPosition = MouseInfo.getPointerInfo().getLocation();
			if (!newCursorPosition.equals(cursorPosition)) {
				cursorPosition = newCursorPosition;
				contactServer();
			}

			if (nextForcedContact < currentTimeMillis) {
				sendDataToServer();
				if (isInputControlledByServer) {
					nextForcedContact = 0;
				} else {
					nextForcedContact = currentTimeMillis + 3000;
				}
			}

		} catch (Exception e) {
			resetServerConnection();
			e.printStackTrace();
		}
	}

	protected void resetServerConnection() {
		oldImage = null;
		oldCursorPosition = null;
		oldUserStatus = null;
	}

	protected void contactServer() {
		nextForcedContact = 0;
	}

	protected URL getURL() throws MalformedURLException {
		StringBuilder result = new StringBuilder(urlBaseString);
		if (!userStatus.equals(oldUserStatus)) {
			oldUserStatus = userStatus;
			result.append("&userStatus=").append(userStatus);
		}
		if (imageToSendToServer != null) {
			result.append("&imageIsUpdate=").append(imageToSendToServer == differenceImage);
			//			result.append("&changedPixels=").append(changedPixels);
		}
		if (!cursorPosition.equals(oldCursorPosition) && screen.getBounds().contains(cursorPosition)) {
			oldCursorPosition = cursorPosition;
			result.append("&cursorX=").append(cursorPosition.x - screen.getBounds().x);
			result.append("&cursorY=").append(cursorPosition.y - screen.getBounds().y);
		}
		if (proxy != null) {
			result.append("&r=").append(Math.random()); // Lots of proxies cache anyway, we trick it
		}
		return new URL(result.toString());
	}

	protected void sendDataToServer() throws Exception {

		MutableBoolean result = new MutableBoolean(false);
		Thread sendThread = new Thread() {

			@Override
			public void run() {
				try {

					URLConnection connection = getURL().openConnection(proxy);
					connection.setUseCaches(false);
					connection.setConnectTimeout(2000);
					connection.setReadTimeout(5000);

					if (imageToSendToServer != null) {
						connection.setDoOutput(true);
						((HttpURLConnection) connection).setRequestMethod("POST");
						try (OutputStream os = connection.getOutputStream()) {
							ImageIO.write(imageToSendToServer, "PNG", os);
							os.flush();
						}
					}

					if (((HttpURLConnection) connection).getResponseCode() == 200) {
						try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
							try {
								result.setValue("OK".equals(reader.readLine()));
								if (result.isTrue()) {
									processServerResponse(reader);
								}
							} catch (Exception e) {
								while (reader.read() != -1) {
								}
								((HttpURLConnection) connection).disconnect();
							}
						}
					}
				} catch (Exception e) {
					proxy = null; // Reset detected proxy on communication error
					e.printStackTrace();
					result.setFalse();
				}
			}

		};
		sendThread.setDaemon(false);
		sendThread.start();
		sendThread.join(10000);
		if (sendThread.isAlive()) {
			sendThread.interrupt();
			throw new Exception("Timeout occured.");
		}

		if (result.isFalse()) {
			throw new Exception("Did not get expected answer from server.");
		}

	}

	protected String readNextToken(BufferedReader reader) throws IOException {
		return reader.readLine();
	}

	protected void processServerResponse(BufferedReader reader) throws NumberFormatException, IOException {
		isInputControlledByServer = Boolean.parseBoolean(reader.readLine());
		int pictureInterval = Integer.parseInt(reader.readLine());
		if (imageToSendToServer != null) {
			takeNextPictureAt = System.currentTimeMillis() + pictureInterval;
		}
		long lastUserStatusReset = Long.parseLong(reader.readLine());
		if (lastUserStatusReset != oldLastUserStatusReset) {
			oldLastUserStatusReset = lastUserStatusReset;
			if (onResetUserStatus != null) {
				Platform.runLater(onResetUserStatus); // faster but could run into a race condition, in this project it does not matter
			}
		}

		//TODO: empty the connection stream first and then process the event -> much cleaner
		String command;
		while ((command = readNextToken(reader)) != null) {
			switch (command) {
				case "kp" :
					robot.keyPress(getCodeForKey(readNextToken(reader)));
					break;
				case "kr" :
					robot.keyRelease(getCodeForKey(readNextToken(reader)));
					break;
				case "mm" :
					robot.mouseMove(screen.getBounds().x + Integer.parseInt(readNextToken(reader)), screen.getBounds().y + Integer.parseInt(readNextToken(reader)));
					break;
				case "mp" :
					robot.mousePress(InputEvent.getMaskForButton(Integer.parseInt(readNextToken(reader))));
					break;
				case "mr" :
					robot.mouseRelease(InputEvent.getMaskForButton(Integer.parseInt(readNextToken(reader))));
					break;
			}

		}
	}

	protected int getCodeForKey(String key) {
		return KeyCode.valueOf(key).impl_getCode();
		//return KeyEvent.getKeyCodeForChar(key.charAt(0));
	}

	protected int getChangedPixels(BufferedImage oldImage, BufferedImage newImage, BufferedImage differenceImage) {
		int[] pixelsOld = ((DataBufferInt) oldImage.getRaster().getDataBuffer()).getData();
		int[] pixelsNew = ((DataBufferInt) newImage.getRaster().getDataBuffer()).getData();
		int[] pixelsDifference = ((DataBufferInt) differenceImage.getRaster().getDataBuffer()).getData();

		int result = 0;
		for (int index = pixelsOld.length - 1; index >= 0; index--) {
			if (pixelsOld[index] == pixelsNew[index]) {
				// equal, we set the difference to transparent
				pixelsDifference[index] = 0;
			} else {
				// not equal, we set the difference to the difference
				pixelsDifference[index] = pixelsNew[index] + 0xFF000000;
				result++;
			}
		}

		return result;
	}

	public Runnable getOnResetUserStatus() {
		return onResetUserStatus;
	}

	public void setOnResetUserStatus(Runnable onResetUserStatus) {
		this.onResetUserStatus = onResetUserStatus;
	}

	public UserStatus getUserStatus() {
		return userStatus;
	}

	public void setUserStatus(UserStatus userStatus) {
		this.userStatus = userStatus;
		contactServer();
	}

}