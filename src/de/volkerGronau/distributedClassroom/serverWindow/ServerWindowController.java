package de.volkerGronau.distributedClassroom.serverWindow;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import de.volkerGronau.ApplicationHelper;
import de.volkerGronau.distributedClassroom.settings.Settings;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class ServerWindowController implements HttpHandler {

	@FXML
	protected ScrollPane scrollPane;

	@FXML
	protected FlowPane flowPane;

	protected static class Client {
		String userName;
		BufferedImage bufferedImage;
		WritableImage fxImage;
		java.awt.Point cursorPos;
		BorderPane borderPane; // root element for user
		ImageView imageView; // his/her image
		List<String> commands = Lists.newArrayList();
	}

	protected Map<String, Client> clientCache = Maps.newHashMap();
	protected int[] cursorImageData;

	protected String openedUserName;
	protected boolean isControlling;

	public void init(Stage stage, ResourceBundle resources, Settings settings) throws Exception {
		stage.setTitle(resources.getString("title"));

		scrollPane.viewportBoundsProperty().addListener((obs, old, bounds) -> {
			flowPane.setPrefWidth(bounds.getMaxX() - bounds.getMinX());
		});

		BufferedImage cursorBufferedImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics graphics = cursorBufferedImage.getGraphics();
		graphics.drawImage(ApplicationHelper.Resources.getImage("cursorRed.png", false).getImage(), 0, 0, null);
		graphics.dispose();
		cursorImageData = ((DataBufferInt) cursorBufferedImage.getRaster().getDataBuffer()).getData();

		HttpServer server = HttpServer.create(new InetSocketAddress(settings.getServerPort()), 0);
		server.createContext("/DistributedClassroom", this);
		server.setExecutor(null); // creates a default executor
		server.start();

		stage.setOnCloseRequest(e -> {
			try {
				server.stop(0);
				Platform.exit();
			} catch (Exception e2) {
				e2.printStackTrace();
				System.exit(1);
			}

		});
	}

	@Override
	public void handle(HttpExchange httpExchange) throws IOException {
		String result = "OK";

		Map<String, String> parameters = Maps.newHashMap();
		for (String param : Splitter.on("&").split(httpExchange.getRequestURI().getQuery())) {
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

		if (parameters.containsKey("cursorX")) {
			client.cursorPos = new Point(Integer.parseInt(parameters.get("cursorX")), Integer.parseInt(parameters.get("cursorY")));
		} else {
			client.cursorPos = null;
		}
		System.out.println(httpExchange.getRequestURI().getQuery());
		if (parameters.containsKey("imageIsUpdate")) {
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
		//		System.out.println("result:" + result);
		if ("OK".equals(result)) {
			notifyOfNewImage(userName, client);
		}

		boolean thisUserOpened = userName.equals(openedUserName);
		StringBuilder response = new StringBuilder(result).append('\n').append(thisUserOpened && isControlling).append('\n');
		response.append(thisUserOpened ? "0" : "5000").append('\n'); // if opened user update the picture fast, otherwise slowly
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

	protected void notifyOfNewImage(String userName, Client client) {
		client.fxImage = SwingFXUtils.toFXImage(client.bufferedImage, null);
		if (client.cursorPos != null) {
			try {
				int maxHeight = client.cursorPos.y + Math.min(32, client.bufferedImage.getHeight() - client.cursorPos.y);
				int maxWidth = client.cursorPos.x + Math.min(32, client.bufferedImage.getWidth() - client.cursorPos.x);
				//
				//				client.fxImage.getPixelWriter().setPixels(client.cursorPos.x, client.cursorPos.y, maxWidth, maxHeight, PixelFormat.getIntArgbInstance(), cursorImageData, cursorImageOffset, cursorImageScan);

				int index = 0;
				int cursorColor;
				PixelWriter pixelWriter = client.fxImage.getPixelWriter();
				for (int x = client.cursorPos.x; x < maxWidth; x++) {
					for (int y = client.cursorPos.y; y < maxHeight; y++) {
						cursorColor = cursorImageData[index++];
						if (cursorColor >> 24 != 0) {
							pixelWriter.setArgb(x, y, cursorColor);
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		Platform.runLater(() -> {
			updateGUI(client);
		});
	}

	protected void updateGUI(Client client) {
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
			client.borderPane.setTop(new Label(client.userName));
			flowPane.getChildren().add(client.borderPane);
		}

		if (openedUserName == null) {
			client.imageView.setImage(client.fxImage);
		} else if (client.userName.equals(openedUserName)) {
			((ImageView) ((StackPane) scrollPane.getContent()).getChildren().get(0)).setImage(client.fxImage);
		}
	}

	private void switchToSingleUserView(Client client) {

		Button backButton = new Button("Back");
		backButton.setOnAction(e -> {
			switchToMultiUserView();
		});
		CheckBox takeControlCheckBox = new CheckBox("Take Control");
		takeControlCheckBox.selectedProperty().addListener((obs, old, selected) -> {
			isControlling = selected;
		});

		ImageView imageView = new ImageView();
		imageView.setImage(client.fxImage);
		imageView.setFocusTraversable(true);

		HBox hBox = new HBox(backButton, takeControlCheckBox);

		StackPane stackPane = new StackPane(imageView, hBox);
		stackPane.setAlignment(Pos.TOP_LEFT);
		stackPane.setFocusTraversable(true);

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
		stackPane.setOnMouseMoved(e -> {
			if (takeControlCheckBox.isSelected()) {
				synchronized (client.commands) {
					client.commands.add("mm\n" + ((int) e.getX()) + "\n" + ((int) e.getY()) + "\n");
				}
			}
		});
		stackPane.setOnMouseDragged(e -> {
			if (takeControlCheckBox.isSelected()) {
				synchronized (client.commands) {
					client.commands.add("mm\n" + ((int) e.getX()) + "\n" + ((int) e.getY()) + "\n");
				}
			}
		});
		stackPane.setOnMousePressed(e -> {
			if (takeControlCheckBox.isSelected()) {
				synchronized (client.commands) {
					client.commands.add("mp\n" + e.getButton().ordinal() + "\n");
				}
			}
		});
		stackPane.setOnMouseReleased(e -> {
			if (takeControlCheckBox.isSelected()) {
				synchronized (client.commands) {
					client.commands.add("mr\n" + e.getButton().ordinal() + "\n");
				}
			}
		});
		imageView.focusedProperty().addListener((obs, old, focused) -> {
			imageView.requestFocus();
		});

		scrollPane.setContent(stackPane);
		openedUserName = client.userName;
		imageView.requestFocus();

	}

	protected void switchToMultiUserView() {
		for (Client client : clientCache.values()) {
			client.imageView.setImage(client.fxImage);
		}
		scrollPane.setContent(flowPane);
		openedUserName = null;
		isControlling = false;
	}

}
