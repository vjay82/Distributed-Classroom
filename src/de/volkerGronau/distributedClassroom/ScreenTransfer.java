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
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.mutable.MutableBoolean;

import de.volkerGronau.distributedClassroom.ProxyHelper.ProxyType;
import javafx.scene.input.KeyCode;

public class ScreenTransfer {

	protected final Robot robot = new Robot();

	protected BufferedImage bufferedImage;
	protected BufferedImage differenceImage;
	protected BufferedImage imageToSendToServer;
	protected Screen screen;
	protected String urlBaseString;
	protected Proxy proxy;
	protected long takeNextPictureAt = 0;
	protected java.awt.Point cursorPosition;
	protected int changedPixels;
	protected boolean isInputControlledByServer;
	protected long nextForcedContact = 0;

	public ScreenTransfer(Screen screen, String name, String serverAddress) throws Exception {
		super();

		this.screen = screen;
		this.urlBaseString = serverAddress + "?userName=" + name;

		Thread thread = new Thread("Server Interactionthread") {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						//						long startTime = System.currentTimeMillis();
						if (proxy == null) {
							proxy = ProxyHelper.getProxy(urlBaseString, ProxyType.os);
						}
						updateData();
						//						long waitingTime = 100l - (System.currentTimeMillis() - startTime);
						//						if (waitingTime > 0) {
						//							Thread.sleep(waitingTime);
						//						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
	}

	public void updateData() {
		try {
			long currentTimeMillis = System.currentTimeMillis();
			boolean callSendDataToServer = false;

			if (takeNextPictureAt < currentTimeMillis) {
				BufferedImage newBufferedImage = robot.createScreenCapture(screen.getBounds());
				if (differenceImage == null) {
					differenceImage = new BufferedImage(newBufferedImage.getWidth(), newBufferedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
				}

				if (bufferedImage == null) {
					imageToSendToServer = newBufferedImage;
					bufferedImage = newBufferedImage;
					callSendDataToServer = true;
					changedPixels = 0;
				} else {
					changedPixels = getChangedPixels(bufferedImage, newBufferedImage, differenceImage);
					if (changedPixels > 20) {
						imageToSendToServer = differenceImage;
						bufferedImage = newBufferedImage;
						callSendDataToServer = true;
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
				callSendDataToServer = true;
			}

			if (callSendDataToServer || isInputControlledByServer || nextForcedContact < currentTimeMillis) {
				sendDataToServer();
				nextForcedContact = currentTimeMillis + 3000;
			}

		} catch (Exception e) {
			bufferedImage = null;
			e.printStackTrace();
		}
	}

	protected void sendDataToServer() throws Exception {
		MutableBoolean result = new MutableBoolean(false);

		Thread sendThread = new Thread() {

			@Override
			public void run() {
				try {
					StringBuilder serverURL = new StringBuilder(urlBaseString);
					if (imageToSendToServer != null) {
						serverURL.append("&imageIsUpdate=").append(imageToSendToServer == differenceImage);
						serverURL.append("&changedPixels=").append(changedPixels);
					}
					if (screen.getBounds().contains(cursorPosition)) {
						serverURL.append("&cursorX=").append(cursorPosition.x - screen.getBounds().x);
						serverURL.append("&cursorY=").append(cursorPosition.y - screen.getBounds().y);
					}
					if (proxy != null) {
						serverURL.append("&r=").append(Math.random()); // Lots of proxies cache anyway, we trick it
					}

					URL url = new URL(serverURL.toString());
					URLConnection connection = url.openConnection(proxy);
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
								isInputControlledByServer = Boolean.parseBoolean(reader.readLine());
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
		//		StringBuilder result = new StringBuilder();
		//		char[] c = new char[1];
		//		while (reader.read(c) != -1) {
		//			if (c[0] == '\0') {
		//				return result.toString();
		//			}
		//			result.append(c[0]);
		//		}
		//		return null;
		return reader.readLine();
	}

	protected void processServerResponse(BufferedReader reader) throws NumberFormatException, IOException {
		int pictureInterval = Integer.parseInt(reader.readLine());
		if (imageToSendToServer != null) {
			takeNextPictureAt = System.currentTimeMillis() + pictureInterval;
		}
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

}
