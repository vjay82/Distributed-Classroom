package de.volkerGronau.distributedClassroom;

import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.imageio.ImageIO;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;

public class ClientBackend {

	public static enum UserStatus {
		OK, NEUTRAL, NOT_OK
	}

	protected final Robot robot = new Robot();
	protected AnimatedGIFWriter animatedGIFWriter = new AnimatedGIFWriter(false);
	protected UserStatus userStatus = UserStatus.NEUTRAL;
	protected int pictureInterval = 5000;

	protected BufferedImage oldImage;
	protected BufferedImage differenceImage;
	protected BufferedImage imageToSendToServer;

	protected Screen screen;
	protected Rectangle screenBounds;
	protected String serverAddress;
	protected long takeNextPictureAt = 0;
	protected java.awt.Point cursorPosition;
	protected java.awt.Point oldCursorPosition;
	protected int changedPixels;
	protected boolean isInputControlledByServer;
	protected long nextForcedContact = 0;
	protected Runnable onResetUserStatus;
	protected UserStatus oldUserStatus;
	protected long oldLastUserStatusReset;
	protected int hotPixels;
	protected int hotPixelHitCount;
	protected int requestCount = 0;
	protected boolean useGIF;
	protected Thread interactionThread;
	protected volatile long lastCycle;
	protected String userName;
	protected Socket clientSocket;
	protected Thread readThread;

	public ClientBackend(Screen screen, String userName, String serverAddress) throws Exception {
		super();

		this.userName = userName;
		this.screen = screen;
		this.serverAddress = serverAddress;
		this.screenBounds = screen.getBounds();

		createInteractionThread();
	}

	protected void createInteractionThread() {
		lastCycle = System.currentTimeMillis();
		interactionThread = new Thread("Server Interactionthread") {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						long startTime = System.currentTimeMillis();
						if (clientSocket == null) {
							lastCycle = System.currentTimeMillis();
						}
						updateData();
						//						if (!isInputControlledByServer) {
						long waitingTime = 100l - System.currentTimeMillis() + startTime;
						if (waitingTime > 0) {
							Thread.sleep(waitingTime);
						}
						//						}
						lastCycle = System.currentTimeMillis();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		interactionThread.setDaemon(false);
		interactionThread.start();
	}

	public void updateData() {
		try {
			long currentTimeMillis = System.currentTimeMillis();

			if (takeNextPictureAt < currentTimeMillis) {
				BufferedImage newBufferedImage = robot.createScreenCapture(screenBounds);
				if (differenceImage == null) {
					differenceImage = new BufferedImage(newBufferedImage.getWidth(), newBufferedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
				}

				if (oldImage == null) {
					imageToSendToServer = newBufferedImage;
					oldImage = newBufferedImage;
					changedPixels = screenBounds.width * screenBounds.height;
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
			if (!newCursorPosition.equals(cursorPosition) && screenBounds.contains(newCursorPosition)) {
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
			e.printStackTrace();
			resetServerConnection();
			try {
				Thread.sleep(1000);
			} catch (Exception e2) {
			}
		}
	}

	protected void resetServerConnection() {
		oldImage = null;
		oldCursorPosition = null;
		oldUserStatus = null;
		if (readThread != null) {
			readThread.interrupt();
		}
		if (clientSocket != null) {
			try {
				clientSocket.close();
			} catch (IOException e) {
			}
		}
		clientSocket = null;
	}

	protected void contactServer() {
		nextForcedContact = 0;
	}

	NetworkOutputStream networkOutputStream;
	protected void sendDataToServer() throws Exception {

		if (clientSocket == null) {
			createClientSocket();
		}

		System.out.println("sending data");
		if (cursorPosition != null && !cursorPosition.equals(oldCursorPosition) && screenBounds.contains(cursorPosition)) {
			oldCursorPosition = cursorPosition;
			networkOutputStream.writeChar('c');
			networkOutputStream.writeInt(oldCursorPosition.x - screenBounds.x);
			networkOutputStream.writeInt(oldCursorPosition.y - screenBounds.y);
		}
		if (imageToSendToServer != null) {
			networkOutputStream.writeChar('p');
			networkOutputStream.writeBoolean(imageToSendToServer == differenceImage);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			if (useGIF) {
				animatedGIFWriter.write(imageToSendToServer, bos);
			} else {
				ImageIO.write(imageToSendToServer, "PNG", bos);
			}
			takeNextPictureAt = System.currentTimeMillis() + pictureInterval;

			long startTime = System.currentTimeMillis();
			networkOutputStream.writeInt(bos.size());
			networkOutputStream.write(bos.toByteArray());
			long timeTaken = System.currentTimeMillis() - startTime;
			System.out.println("Sending image took: " + timeTaken);
			if (timeTaken > 2000) {
				useGIF = true;
			}
		}

		if (!userStatus.equals(oldUserStatus)) {
			oldUserStatus = userStatus;
			networkOutputStream.writeChar('u');
			networkOutputStream.writeString(userStatus.name());
		}
		networkOutputStream.writeChar('i'); // idle command = keep alive
		networkOutputStream.flush();
		System.out.println("finished");
	}

	protected void createClientSocket() throws UnknownHostException, IOException {
		System.out.println("Creating new client socket");

		int index = serverAddress.indexOf(':');
		String host = index > -1 ? serverAddress.substring(0, index) : serverAddress;
		int port = index > -1 ? Integer.parseInt(serverAddress.substring(index + 1)) : 9876;

		clientSocket = new Socket(host, port);
		clientSocket.setSoTimeout(60000);
		networkOutputStream = new NetworkOutputStream(clientSocket.getOutputStream());
		networkOutputStream.writeString(userName);

		readThread = new Thread() {

			@Override
			public void run() {
				try (NetworkInputStream networkInputStream = new NetworkInputStream(clientSocket.getInputStream());) {
					while (!isInterrupted()) {
						char command = networkInputStream.readChar();
						switch (command) {
							case 'i' :
								int oldPictureInterval = pictureInterval;
								pictureInterval = networkInputStream.readInt();
								takeNextPictureAt = System.currentTimeMillis() - oldPictureInterval + pictureInterval;
								System.out.println("Setting picture interval to " + pictureInterval);
								break;
							case 'c' :
								isInputControlledByServer = networkInputStream.readBoolean();
								break;
							case 'r' :
								if (onResetUserStatus != null) {
									Platform.runLater(onResetUserStatus); // faster but could run into a race condition, in this project it does not matter
								}
								break;
							case 'm' :
								controlInputDevices(networkInputStream);
								break;
							default :
								break;
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
					resetServerConnection();
				}
			}
		};
		readThread.setDaemon(false);
		readThread.start();
	}

	protected void controlInputDevices(NetworkInputStream networkInputStream) throws NumberFormatException, IOException {
		//TODO: empty the connection stream first and then process the event -> much cleaner
		int count = networkInputStream.readInt();
		for (int index = 0; index < count; index++) {
			char command = networkInputStream.readChar();
			switch (command) {
				case 'a' :
					robot.keyPress(getCodeForKey(networkInputStream.readString()));
					break;
				case 'b' :
					robot.keyRelease(getCodeForKey(networkInputStream.readString()));
					break;
				case 'c' :
					robot.mouseMove(screenBounds.x + networkInputStream.readInt(), screenBounds.y + networkInputStream.readInt());
					break;
				case 'd' :
					robot.mousePress(InputEvent.getMaskForButton(networkInputStream.readInt()));
					break;
				case 'e' :
					robot.mouseRelease(InputEvent.getMaskForButton(networkInputStream.readInt()));
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

	public void resetUserStatus() {
		oldUserStatus = UserStatus.NEUTRAL;
		userStatus = UserStatus.NEUTRAL;
	}

}
