package de.volkerGronau.distributedClassroom;

import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

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
	//	protected int requestCount = 0;
	protected CloseableHttpClient httpClient;
	protected HttpClientContext httpClientContext;
	protected boolean useGIF;
	protected AnimatedGIFWriter animatedGIFWriter = new AnimatedGIFWriter(false);
	protected Thread interactionThread;
	protected volatile long lastCycle;

	public ClientBackend(Screen screen, String name, String serverAddress) throws Exception {
		super();

		this.screen = screen;
		this.urlBaseString = serverAddress + "?userName=" + URLEncoder.encode(name, "UTF-8") + "&version=" + DistributedClassroom.VERSION;
		this.screenBounds = screen.getBounds();

		createInteractionThread();

		Thread watcherThread = new Thread("Watchdog Thread") {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						Thread.sleep(1000);
						if (lastCycle < System.currentTimeMillis() - 60000) {
							interactionThread.interrupt();
							System.out.println("Watchdog - resetting.");
							resetServerConnection();
							createInteractionThread();
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		watcherThread.setDaemon(false);
		watcherThread.start();
	}

	protected void createInteractionThread() {
		lastCycle = System.currentTimeMillis();
		interactionThread = new Thread("Server Interactionthread") {
			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						long startTime = System.currentTimeMillis();
						if (httpClient == null) {
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
		HttpHost httpHost = null;
		httpClientContext = null;

		Proxy proxy = ProxyHelper.getProxy(urlBaseString, ProxyType.os);
		System.out.println("Using Proxy: " + proxy);

		if (Type.HTTP.equals(proxy.type())) {
			String addr = proxy.address().toString();
			int index = addr.indexOf(':');
			if (index != -1) {
				httpHost = new HttpHost(addr.substring(0, index), Integer.parseInt(addr.substring(index + 1)), "http");
			} else {
				httpHost = new HttpHost(addr);
			}
		} else if (Type.SOCKS.equals(proxy.type())) {
			String addr = proxy.address().toString();
			int index = addr.indexOf(':');
			if (index != -1) {
				InetSocketAddress socksaddr = new InetSocketAddress(addr.substring(0, index), Integer.parseInt(addr.substring(index + 1)));
				httpClientContext = HttpClientContext.create();
				httpClientContext.setAttribute("socks.address", socksaddr);
			} else {
				InetSocketAddress socksaddr = new InetSocketAddress(addr, 1080);
				httpClientContext = HttpClientContext.create();
				httpClientContext.setAttribute("socks.address", socksaddr);
			}
		}

		RequestConfig config = RequestConfig.custom().setConnectTimeout(20000).setProxy(httpHost).setSocketTimeout(20000).setConnectionRequestTimeout(20000).setContentCompressionEnabled(false).build();
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setDefaultRequestConfig(config);
		builder.setConnectionTimeToLive(20, TimeUnit.SECONDS);
		httpClient = builder.disableAutomaticRetries().disableContentCompression().build();
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
		//		requestCount = 0;
		httpClient = null;
	}

	protected void contactServer() {
		nextForcedContact = 0;
	}

	protected String getURL() throws MalformedURLException {
		StringBuilder result = new StringBuilder(urlBaseString);//.append("&requestCount=").append(requestCount++);
		if (!userStatus.equals(oldUserStatus)) {
			oldUserStatus = userStatus;
			result.append("&userStatus=").append(userStatus);
		}
		if (imageToSendToServer != null) {
			result.append("&imageIsUpdate=").append(imageToSendToServer == differenceImage);
			//			result.append("&changedPixels=").append(changedPixels);
		}
		if (cursorPosition != null && !cursorPosition.equals(oldCursorPosition) && screenBounds.contains(cursorPosition)) {
			oldCursorPosition = cursorPosition;
			result.append("&cursorX=").append(oldCursorPosition.x - screenBounds.x);
			result.append("&cursorY=").append(oldCursorPosition.y - screenBounds.y);
		}

		//		if (!Proxy.NO_PROXY.equals(proxy)) {
		//			result.append("&r=").append(Math.random()); // Lots of proxies cache anyway, we trick them
		//		}
		return result.toString();
	}

	protected void sendDataToServer() throws Exception {
		System.out.println("sending...");
		HttpUriRequest httpUriRequest;
		if (imageToSendToServer == null) {
			httpUriRequest = new HttpGet(getURL());
		} else {
			httpUriRequest = new HttpPost(getURL());
			ByteArrayOutputStream bos = new ByteArrayOutputStream();

			if (useGIF) {
				animatedGIFWriter.write(imageToSendToServer, bos);
			} else {
				ImageIO.write(imageToSendToServer, "PNG", bos);
			}

			System.out.println("Image size is: " + bos.size());
			((HttpPost) httpUriRequest).setEntity(new InputStreamEntity(new ByteArrayInputStream(bos.toByteArray()), bos.size()));
		}
		long start = System.currentTimeMillis();
		CloseableHttpResponse response = httpClient.execute(httpUriRequest, httpClientContext);
		long took = System.currentTimeMillis() - start;
		System.out.println("Done, request took: " + took);

		if (imageToSendToServer != null && took > 3000) {
			useGIF = true;
		}

		HttpEntity entity = response.getEntity();
		try {
			if (response.getStatusLine().getStatusCode() != 200) {
				throw new Exception("Got status line: " + response.getStatusLine());
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8))) {
				processServerResponse(reader);
			}
		} finally {
			try {
				EntityUtils.consume(entity);
			} catch (Exception e) {
			}
			try {
				response.close();
			} catch (Exception e) {
			}
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
					robot.mouseMove(screenBounds.x + Integer.parseInt(readNextToken(reader)), screenBounds.y + Integer.parseInt(readNextToken(reader)));
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

	public void resetUserStatus() {
		oldUserStatus = UserStatus.NEUTRAL;
		userStatus = UserStatus.NEUTRAL;
	}

}
