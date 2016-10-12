package de.volkerGronau.distributedClassroom.serverWindow;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import de.volkerGronau.distributedClassroom.settings.Settings;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;

public class ServerWindowController implements HttpHandler {

	@FXML
	protected ScrollPane scrollPane;

	@FXML
	protected FlowPane flowPane;

	protected Map<String, BufferedImage> imageCache = Maps.newHashMap();
	protected Map<String, BorderPane> guiElementCache = Maps.newHashMap();

	protected String openedUserName;

	public void init(Stage stage, ResourceBundle resources, Settings settings) throws Exception {
		stage.setTitle(resources.getString("title"));

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
		Map<String, String> parameters = Maps.newHashMap();
		for (String param : Splitter.on("&").split(httpExchange.getRequestURI().getQuery())) {
			int index = param.indexOf('=');
			if (index != -1) {
				parameters.put(param.substring(0, index), param.substring(index + 1));
			}
		}

		String userName = parameters.get("userName");
		BufferedImage bufferedImage = ImageIO.read(httpExchange.getRequestBody());

		synchronized (imageCache) {
			BufferedImage previousImage = imageCache.get(userName);
			if (previousImage == null) {
				synchronized (bufferedImage) {
					imageCache.put(userName, bufferedImage);
					notifyOfNewImage(userName, bufferedImage);
				}

			} else {
				synchronized (previousImage) {
					Graphics g = previousImage.getGraphics();
					g.drawImage(bufferedImage, 0, 0, null);
					g.dispose();
					notifyOfNewImage(userName, previousImage);
				}
			}
		}

		String response = "OK";
		httpExchange.sendResponseHeaders(200, response.length());
		OutputStream os = httpExchange.getResponseBody();
		os.write(response.getBytes());
		os.close();
	}

	protected void notifyOfNewImage(String userName, BufferedImage bufferedImage) {
		Image image = SwingFXUtils.toFXImage(bufferedImage, null);
		Platform.runLater(() -> {
			updateGUI(userName, image);
		});
	}

	protected void updateGUI(String userName, Image image) {
		BorderPane borderPane = guiElementCache.get(userName);
		if (borderPane == null) {
			ImageView imageView = new ImageView();
			imageView.setFitWidth(400);
			imageView.setFitHeight(400);
			imageView.setPreserveRatio(true);
			imageView.setOnMouseClicked(e -> {
				ImageView iv = new ImageView();
				iv.setImage(((ImageView) guiElementCache.get(userName).getCenter()).getImage());
				scrollPane.setContent(iv);
				openedUserName = userName;
				iv.setOnMouseClicked(e2 -> {
					scrollPane.setContent(flowPane);
					openedUserName = null;
				});
			});
			borderPane = new BorderPane(imageView);
			borderPane.setTop(new Label(userName));
			flowPane.getChildren().add(borderPane);
			guiElementCache.put(userName, borderPane);
		}

		((ImageView) borderPane.getCenter()).setImage(image);

		if (userName.equals(openedUserName)) {
			((ImageView) scrollPane.getContent()).setImage(image);
		}
	}

}
