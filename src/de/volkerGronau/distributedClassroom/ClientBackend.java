package de.volkerGronau.distributedClassroom;

import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig.Builder;
import org.asynchttpclient.Response;
import org.asynchttpclient.proxy.ProxyServer;

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
	protected Rectangle screenBounds;
	protected String urlBaseString;
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
	protected int requestCount = 0;
	protected AsyncHttpClient client;
	protected boolean useGIF;
	protected AnimatedGIFWriter animatedGIFWriter = new AnimatedGIFWriter(false);
	protected Thread interactionThread;
	protected volatile long lastCycle;
	protected String userName;

	public ClientBackend(Screen screen, String userName, String serverAddress) throws Exception {
		super();

		this.userName = userName;
		this.screen = screen;
		this.urlBaseString = serverAddress;//+ "?userName=" + URLEncoder.encode(name, "UTF-8") + "&version=" + DistributedClassroom.VERSION;
		this.screenBounds = screen.getBounds();

		createInteractionThread();

		//		Thread watcherThread = new Thread("Watchdog Thread") {
		//			@Override
		//			public void run() {
		//				try {
		//					while (!isInterrupted()) {
		//						Thread.sleep(1000);
		//						if (lastCycle < System.currentTimeMillis() - 60000) {
		//							interactionThread.interrupt();
		//							System.out.println("Watchdog - resetting.");
		//							resetServerConnection();
		//							createInteractionThread();
		//						}
		//					}
		//				} catch (Exception e) {
		//					e.printStackTrace();
		//				}
		//			}
		//		};
		//		watcherThread.setDaemon(false);
		//		watcherThread.start();
	}

	protected void createInteractionThread() {
		lastCycle = System.currentTimeMillis();
		interactionThread = new Thread("Server Interactionthread") {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						long startTime = System.currentTimeMillis();
						if (client == null) {
							createHttpClient();
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

	protected void createHttpClient() {
		Proxy proxy = ProxyHelper.getProxy(urlBaseString, ProxyType.os);
		System.out.println("Using Proxy: " + proxy);
		ProxyServer proxyServer = null;
		if (Type.HTTP.equals(proxy.type())) {
			String addr = proxy.address().toString();
			int index = addr.indexOf(':');
			if (index != -1) {
				proxyServer = (new ProxyServer.Builder(addr.substring(0, index), Integer.parseInt(addr.substring(index + 1)))).build();
			}
		}

		AsyncHttpClientConfig config = (new Builder()).setMaxConnections(1).setMaxConnectionsPerHost(1).setConnectTimeout(5000).setConnectionTtl(30000).setProxyServer(proxyServer).setReadTimeout(30000).setMaxRequestRetry(1).setKeepAlive(false).build();
		client = new DefaultAsyncHttpClient(config);
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
		}
	}

	protected void resetServerConnection() {
		oldImage = null;
		oldCursorPosition = null;
		oldUserStatus = null;
		if (client != null) {
			try {
				client.close();
			} catch (IOException e) {
			}
		}
		client = null;
	}

	protected void contactServer() {
		nextForcedContact = 0;
	}

	protected String getURL() throws MalformedURLException {
		//		StringBuilder result = new StringBuilder(urlBaseString);//.append("&requestCount=").append(requestCount++);
		//		if (!userStatus.equals(oldUserStatus)) {
		//			oldUserStatus = userStatus;
		//			result.append("&userStatus=").append(userStatus);
		//		}
		//		if (imageToSendToServer != null) {
		//			result.append("&imageIsUpdate=").append(imageToSendToServer == differenceImage);
		//			//			result.append("&changedPixels=").append(changedPixels);
		//		}
		//		if (cursorPosition != null && !cursorPosition.equals(oldCursorPosition) && screenBounds.contains(cursorPosition)) {
		//			oldCursorPosition = cursorPosition;
		//			result.append("&cursorX=").append(oldCursorPosition.x - screenBounds.x);
		//			result.append("&cursorY=").append(oldCursorPosition.y - screenBounds.y);
		//		}
		//		return result.toString();
		return urlBaseString;
	}

	protected void addParameters(BoundRequestBuilder b) {
		b.addHeader("userName", userName);
		if (!userStatus.equals(oldUserStatus)) {
			oldUserStatus = userStatus;
			b.addHeader("userStatus", String.valueOf(userStatus));
		}
		if (imageToSendToServer != null) {
			b.addHeader("imageIsUpdate", String.valueOf(imageToSendToServer == differenceImage));
		}
		if (cursorPosition != null && !cursorPosition.equals(oldCursorPosition) && screenBounds.contains(cursorPosition)) {
			oldCursorPosition = cursorPosition;
			b.addHeader("cursorX", String.valueOf(oldCursorPosition.x - screenBounds.x));
			b.addHeader("cursorY", String.valueOf(oldCursorPosition.y - screenBounds.y));
		}

		b.addHeader("requestCount", String.valueOf(++requestCount));
	}

	protected void sendDataToServer() throws Exception {
		System.out.println("preparing");
		Future<Response> f;
		if (imageToSendToServer == null) {
			System.out.println("using GET");
			BoundRequestBuilder b = client.preparePost(getURL()).setRequestTimeout(30000);
			addParameters(b);
			f = b.execute();
		} else {
			System.out.println("using POST");
			ByteArrayOutputStream bos = new ByteArrayOutputStream();

			if (useGIF) {
				animatedGIFWriter.write(imageToSendToServer, bos);
			} else {
				ImageIO.write(imageToSendToServer, "PNG", bos);
			}
			BoundRequestBuilder b = client.preparePost(getURL()).setRequestTimeout(30000).setBody(bos.toByteArray());
			addParameters(b);
			f = b.execute();
		}
		System.out.println("sending " + requestCount);

		long start = System.currentTimeMillis();
		Response response = f.get();
		long took = System.currentTimeMillis() - start;
		System.out.println("Done, request took: " + took);
		if (imageToSendToServer != null && took > 3000) {
			useGIF = true;
		}

		if (response.getStatusCode() != 200) {
			response.getResponseBody();
			throw new Exception("Got status line: " + response.getStatusText());
		}

		try (ObjectInputStream ois = new ObjectInputStream(response.getResponseBodyAsStream());) {
			processServerResponse(ois);
		}
	}

	protected String readNextToken(ObjectInputStream ois) throws IOException {
		return ois.readLine();
	}

	protected void processServerResponse(ObjectInputStream ois) throws NumberFormatException, IOException {
		isInputControlledByServer = ois.readBoolean();
		int pictureInterval = ois.readInt();
		if (imageToSendToServer != null) {
			takeNextPictureAt = System.currentTimeMillis() + pictureInterval;
		}
		long lastUserStatusReset = ois.readLong();
		if (lastUserStatusReset != oldLastUserStatusReset) {
			oldLastUserStatusReset = lastUserStatusReset;
			if (onResetUserStatus != null) {
				Platform.runLater(onResetUserStatus); // faster but could run into a race condition, in this project it does not matter
			}
		}

		//TODO: empty the connection stream first and then process the event -> much cleaner
		int count = ois.readInt();
		for (int index = 0; index < count; index++) {
			String command = readNextToken(ois);
			switch (command) {
				case "kp" :
					robot.keyPress(getCodeForKey(readNextToken(ois)));
					break;
				case "kr" :
					robot.keyRelease(getCodeForKey(readNextToken(ois)));
					break;
				case "mm" :
					robot.mouseMove(screenBounds.x + Integer.parseInt(readNextToken(ois)), screenBounds.y + Integer.parseInt(readNextToken(ois)));
					break;
				case "mp" :
					robot.mousePress(InputEvent.getMaskForButton(Integer.parseInt(readNextToken(ois))));
					break;
				case "mr" :
					robot.mouseRelease(InputEvent.getMaskForButton(Integer.parseInt(readNextToken(ois))));
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
