package de.volkerGronau.distributedClassroom.serverWindow;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import de.volkerGronau.ApplicationHelper;
import de.volkerGronau.distributedClassroom.ClientBackend.UserStatus;
import de.volkerGronau.distributedClassroom.settings.Settings;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class ServerWindowController implements HttpHandler {

	@FXML
	protected ScrollPane scrollPane;

	@FXML
	protected FlowPane flowPane;

	@FXML
	protected HBox menuPane;

	@FXML
	protected AnchorPane rootPane;

	@FXML
	protected Button resetUserStatusButton;

	protected static class Client {
		String userName;
		BufferedImage bufferedImage;
		WritableImage fxImage;
		java.awt.Point cursorPos;
		BorderPane borderPane; // root element for user
		ImageView imageView; // his/her image
		List<String> commands = Lists.newArrayList(); // cached commands to send with next client's request
		UserStatus userStatus;
		long lastContact;
	}

	protected Map<String, Client> clientCache = Maps.newHashMap();
	protected int[] cursorImageData;
	protected String openedUserName;
	protected boolean isControlling;
	protected long lastUserStatusReset;

	public void init(Stage stage, ResourceBundle resources, Settings settings) throws Exception {
		stage.setTitle(resources.getString("title"));
		scrollPane.viewportBoundsProperty().addListener((obs, old, bounds) -> {
			flowPane.setPrefWidth(bounds.getMaxX() - bounds.getMinX());
		});
		cursorImageData = loadCursorImageData();

		HttpServer server = HttpServer.create(new InetSocketAddress(settings.getServerPort()), 0);
		server.createContext("/DistributedClassroom", this);
		server.setExecutor(null); // creates a default executor
		server.start();

		stage.setOnCloseRequest(e -> {
			Thread closeThread = new Thread("CloseThread") {

				@Override
				public void run() {
					System.out.println("Stopping HttpServer");
					try {
						server.stop(2);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					System.out.println("Closing Application");
					//					Platform.runLater(() -> {
					//						Platform.exit();
					//					});
					System.exit(0);
				}
			};
			closeThread.setDaemon(true);
			closeThread.start();

		});

		rootPane.setOnMouseMoved(e -> {
			menuPane.setVisible(e.getY() <= menuPane.getHeight());
		});

		resetUserStatusButton.setOnAction(e -> {
			lastUserStatusReset = System.currentTimeMillis();
			synchronized (clientCache) {
				for (Client client : clientCache.values()) {
					client.borderPane.setStyle("");
					client.userStatus = UserStatus.NEUTRAL;
				}
			}
		});

		Thread removeOldClientsThread = new Thread("RemoveOldClientsThread") {

			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						Thread.sleep(20000);
						synchronized (clientCache) {
							long removeOlderThan = System.currentTimeMillis() - 20000;
							Iterator<Entry<String, Client>> iterator = clientCache.entrySet().iterator();
							while (iterator.hasNext()) {
								Client client = iterator.next().getValue();
								if (client.lastContact < removeOlderThan) {
									iterator.remove();
									Platform.runLater(() -> {
										flowPane.getChildren().remove(client.borderPane);
									});
								}
							}
						}
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		removeOldClientsThread.setDaemon(false);
		removeOldClientsThread.start();
	}

	protected int[] loadCursorImageData() throws Exception {
		BufferedImage cursorBufferedImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics graphics = cursorBufferedImage.getGraphics();
		graphics.drawImage(ApplicationHelper.Resources.getImage("cursorRed.png", false).getImage(), 0, 0, null);
		graphics.dispose();
		return ((DataBufferInt) cursorBufferedImage.getRaster().getDataBuffer()).getData();
	}

	@Override
	public void handle(HttpExchange httpExchange) throws IOException {
		String result = "OK";
		String query = httpExchange.getRequestURI().getQuery();

		System.out.println(query);

		Map<String, String> parameters = Maps.newHashMap();
		for (String param : Splitter.on("&").split(query)) {
			int index = param.indexOf('=');
			if (index != -1) {
				parameters.put(param.substring(0, index), param.substring(index + 1));
			}
		}

		String userName = parameters.get("userName");

		Client client;
		synchronized (clientCache) {
			client = clientCache.get(userName);
			if (client == null) {
				client = new Client();
				client.userName = userName;
				clientCache.put(userName, client);
			}
		}

		client.lastContact = System.currentTimeMillis();

		boolean imageOrCursorPositionChanged = false;
		if (parameters.containsKey("cursorX")) {
			imageOrCursorPositionChanged = true;
			client.cursorPos = new Point(Integer.parseInt(parameters.get("cursorX")), Integer.parseInt(parameters.get("cursorY")));
		}

		if (parameters.containsKey("imageIsUpdate")) {
			imageOrCursorPositionChanged = true;
			BufferedImage transferedImage = ImageIO.read(httpExchange.getRequestBody());
			if (client.bufferedImage != null) {
				Graphics g = client.bufferedImage.getGraphics();
				g.drawImage(transferedImage, 0, 0, null);
				g.dispose();
			} else {
				if (Boolean.parseBoolean(parameters.get("imageIsUpdate"))) { // we got an update but we have nothing to update
					result = "NOK, got an update-image but have no base";
				} else {
					client.bufferedImage = transferedImage;
				}
			}
		}

		boolean userStatusChanged = false;
		if (parameters.containsKey("userStatus")) {
			UserStatus userStatus = UserStatus.valueOf(parameters.get("userStatus"));
			if (!userStatus.equals(client.userStatus)) {
				client.userStatus = userStatus;
				userStatusChanged = true;
			}
		}

		//		System.out.println("result:" + result);
		if ("OK".equals(result)) {
			updateUI(userName, client, imageOrCursorPositionChanged, userStatusChanged);
		}

		boolean thisUserOpened = userName.equals(openedUserName);
		StringBuilder response = new StringBuilder(result).append('\n').append(thisUserOpened && isControlling).append('\n');
		response.append(thisUserOpened ? "0" : "5000").append('\n'); // if opened user update the picture fast, otherwise slowly
		response.append(lastUserStatusReset).append('\n');
		synchronized (client.commands) {
			for (String line : client.commands) {
				response.append(line);
			}
			client.commands.clear();
		}

		httpExchange.sendResponseHeaders(200, response.length());
		OutputStream os = httpExchange.getResponseBody();
		os.write(response.toString().getBytes(StandardCharsets.UTF_8));
		os.close();
	}

	protected void updateUI(String userName, Client client, boolean imageOrCursorPositionChanged, boolean userStatusChanged) {
		if (imageOrCursorPositionChanged) {
			client.fxImage = SwingFXUtils.toFXImage(client.bufferedImage, null);
			if (client.cursorPos != null) {
				try {
					int maxHeight = Math.min(32, client.bufferedImage.getHeight() - client.cursorPos.y);
					int maxHeightTotal = client.cursorPos.y + maxHeight;
					int maxWidth = Math.min(32, client.bufferedImage.getWidth() - client.cursorPos.x);
					int maxWidthTotal = client.cursorPos.x + maxWidth;
					//
					//				client.fxImage.getPixelWriter().setPixels(client.cursorPos.x, client.cursorPos.y, maxWidth, maxHeight, PixelFormat.getIntArgbInstance(), cursorImageData, cursorImageOffset, cursorImageScan);

					int index = 0;
					int cursorColor;
					PixelWriter pixelWriter = client.fxImage.getPixelWriter();
					for (int x = client.cursorPos.x; x < maxWidthTotal; x++) {
						for (int y = client.cursorPos.y; y < maxHeightTotal; y++) {
							cursorColor = cursorImageData[index++];
							if (cursorColor >> 24 != 0) {
								pixelWriter.setArgb(x, y, cursorColor);
							}
						}
						index += (32 - maxWidth) * 32;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		Platform.runLater(() -> { // Runs in JavaFX Thread
			if (client.borderPane == null) {
				ImageView imageView = new ImageView();
				imageView.setFitWidth(400);
				imageView.setFitHeight(400);
				imageView.setPreserveRatio(true);
				imageView.setOnMouseClicked(e -> {
					switchToSingleUserView(client);
				});
				client.imageView = imageView;
				client.borderPane = new BorderPane(imageView);
				Label label = new Label(client.userName);
				label.setStyle("-fx-font-size:30px");
				client.borderPane.setTop(label);
				int index = 0;
				int insertIndex = 0;
				List<Node> children = flowPane.getChildren();
				for (Node bp : children) {
					String text = ((Label) ((BorderPane) bp).getTop()).getText();
					if (text.compareToIgnoreCase(userName) < 0) {
						insertIndex = index + 1;
					} else {
						break;
					}
				}
				children.add(insertIndex, client.borderPane);
			}
			if (imageOrCursorPositionChanged) {
				if (openedUserName == null) {
					client.imageView.setImage(client.fxImage);
				} else if (client.userName.equals(openedUserName)) {
					((ImageView) scrollPane.getContent()).setImage(client.fxImage);
				}
			}
			if (userStatusChanged) {
				switch (client.userStatus) {
					case OK :
						client.borderPane.setStyle("-fx-background-color:green");
						break;
					case NOT_OK :
						client.borderPane.setStyle("-fx-background-color:red");
						break;
					default :
						client.borderPane.setStyle("");
				}

			}
		});
	}

	protected void switchToSingleUserView(Client client) {
		Button backButton = new Button("Back");
		backButton.setOnAction(e -> {
			switchToMultiUserView();
		});
		CheckBox takeControlCheckBox = new CheckBox("Take Control");
		takeControlCheckBox.setStyle("-fx-background-color: lightgray");
		takeControlCheckBox.selectedProperty().addListener((obs, old, selected) -> {
			isControlling = selected;
		});
		menuPane.getChildren().add(backButton);
		menuPane.getChildren().add(takeControlCheckBox);

		ImageView imageView = new ImageView();
		imageView.setImage(client.fxImage);
		imageView.setFocusTraversable(true);

		imageView.setOnKeyPressed(e -> {
			if (takeControlCheckBox.isSelected()) {
				synchronized (client.commands) {
					client.commands.add("kp\n" + e.getCode().name() + "\n");
				}
			}
		});
		imageView.setOnKeyReleased(e -> {
			if (takeControlCheckBox.isSelected()) {
				synchronized (client.commands) {
					client.commands.add("kr\n" + e.getCode().name() + "\n");
				}
			}
		});
		imageView.setOnMouseMoved(e -> {
			if (takeControlCheckBox.isSelected()) {
				synchronized (client.commands) {
					client.commands.add("mm\n" + ((int) e.getX()) + "\n" + ((int) e.getY()) + "\n");
				}
			}
		});
		imageView.setOnMouseDragged(e -> {
			if (takeControlCheckBox.isSelected()) {
				synchronized (client.commands) {
					client.commands.add("mm\n" + ((int) e.getX()) + "\n" + ((int) e.getY()) + "\n");
				}
			}
		});
		imageView.setOnMousePressed(e -> {
			if (takeControlCheckBox.isSelected()) {
				synchronized (client.commands) {
					client.commands.add("mp\n" + e.getButton().ordinal() + "\n");
				}
			}
		});
		imageView.setOnMouseReleased(e -> {
			if (takeControlCheckBox.isSelected()) {
				synchronized (client.commands) {
					client.commands.add("mr\n" + e.getButton().ordinal() + "\n");
				}
			}
		});
		imageView.focusedProperty().addListener((obs, old, focused) -> {
			imageView.requestFocus();
		});

		scrollPane.setContent(imageView);
		openedUserName = client.userName;
		imageView.requestFocus();
	}

	protected void switchToMultiUserView() {
		while (menuPane.getChildren().size() > 1) {
			menuPane.getChildren().remove(1);
		}
		for (Client client : clientCache.values()) {
			client.imageView.setImage(client.fxImage);
		}
		scrollPane.setContent(flowPane);
		openedUserName = null;
		isControlling = false;
	}

}
