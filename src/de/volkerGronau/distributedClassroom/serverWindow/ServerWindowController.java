package de.volkerGronau.distributedClassroom.serverWindow;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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

public class ServerWindowController implements Handler {

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
		volatile WritableImage fxImage;
		java.awt.Point cursorPos;
		BorderPane borderPane; // root element for user
		ImageView imageView; // his/her image
		List<String> commands = Lists.newArrayList(); // cached commands to send with next client's request
		UserStatus userStatus;
		long lastContact;
		int requestCount;
		volatile boolean removed;
		public void updateImageView() {
			if (imageView != null) {
				imageView.setImage(fxImage);
			}
		}
		public void resetUserStatus() {
			if (borderPane != null) {
				borderPane.setStyle("");
			}
			userStatus = UserStatus.NEUTRAL;
		}
	}

	protected Map<String, Client> clientCache = Maps.newHashMap();
	protected int[] cursorImageData;
	protected volatile Client openedClient;
	protected volatile boolean isControlling;
	protected long lastUserStatusReset;
	protected Stage stage;
	protected ResourceBundle resources;
	protected volatile boolean closing;
	protected volatile boolean frozen;

	protected void updateTitle() {
		stage.setTitle(resources.getString("title") + (openedClient == null ? "" : " - " + openedClient.userName + " - " + resources.getString("status") + " " + openedClient.userStatus));
	}

	public void init(Stage stage, ResourceBundle resources, Settings settings) throws Exception {
		this.stage = stage;
		this.resources = resources;
		updateTitle();
		scrollPane.viewportBoundsProperty().addListener((obs, old, bounds) -> {
			flowPane.setPrefWidth(bounds.getMaxX() - bounds.getMinX());
		});
		cursorImageData = loadCursorImageData();

		ContextHandler contextHandler = new ContextHandler();
		contextHandler.setContextPath("/");
		contextHandler.setMaxFormContentSize(10 * 1024 * 1024);
		contextHandler.setHandler(this);

		Server server = new Server(settings.getServerPort());

		server.setHandler(contextHandler);
		server.setAttribute("maxFormContentSize", -1);

		server.start();

		stage.setOnCloseRequest(e -> {
			Thread closeThread = new Thread("CloseThread") {

				@Override
				public void run() {
					System.out.println("Stopping HttpServer");
					closing = true;
					try {
						server.stop();
						server.join();
						server.destroy();
					} catch (Exception e) {
						e.printStackTrace();
					}
					System.out.println("Closing Application");
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
					client.resetUserStatus();
				}
			}
			updateTitle();
		});

		Thread removeOldClientsThread = new Thread("RemoveOldClientsThread") {

			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						Thread.sleep(10000);
						synchronized (clientCache) {
							long removeOlderThan = System.currentTimeMillis() - 60000;
							Iterator<Entry<String, Client>> iterator = clientCache.entrySet().iterator();
							while (iterator.hasNext()) {
								Client client = iterator.next().getValue();
								if (client.lastContact < removeOlderThan) {
									client.removed = true;
									iterator.remove();
									Platform.runLater(() -> {
										if (client.borderPane != null) {
											flowPane.getChildren().remove(client.borderPane);
										}
									});
								}
							}
						}
					}
				} catch (InterruptedException e) {
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
	public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		try {
			System.out.println("Request: " + baseRequest.getQueryString());
			if (closing) {
				throw new ServletException("Killing client because closing.");
			}

			String userName = request.getParameter("userName");//URLDecoder.decode(parameters.get("userName"), "UTF-8");

			Client client;
			synchronized (clientCache) {
				client = clientCache.get(userName);
				if (client == null) {
					client = new Client();
					client.userName = userName;
					clientCache.put(userName, client);
				}
			}

			//		String requestCountStr = request.getParameter("requestCount");
			//		if (requestCountStr == null) {
			//			throw new ServletException("Killing client because requestCount missing.");
			//		}
			//
			//		int requestCount = Integer.parseInt(requestCountStr);
			//		if (requestCount > 0 && client.requestCount > requestCount) {
			//			throw new ServletException("Killing client because requestCount to small.");
			//		}

			//		client.requestCount = requestCount;
			client.lastContact = System.currentTimeMillis();

			boolean imageOrCursorPositionChanged = false;
			if (request.getParameter("cursorX") != null) {
				imageOrCursorPositionChanged = true;
				client.cursorPos = new Point(Integer.parseInt(request.getParameter("cursorX")), Integer.parseInt(request.getParameter("cursorY")));
			}

			if (request.getParameter("imageIsUpdate") != null) {
				imageOrCursorPositionChanged = true;

				BufferedImage transferedImage = ImageIO.read(request.getInputStream());
				if (transferedImage == null) {
					throw new ServletException("Image is null");
				}

				if (client.bufferedImage != null) {
					Graphics g = client.bufferedImage.getGraphics();
					g.drawImage(transferedImage, 0, 0, null);
					g.dispose();
				} else {
					if (Boolean.parseBoolean(request.getParameter("imageIsUpdate"))) { // we got an update but we have nothing to update
						throw new ServletException("NOK, got an update-image but have no base");
					} else {
						client.bufferedImage = transferedImage;
					}
				}
			}

			boolean userStatusChanged = false;
			if (request.getParameter("userStatus") != null) {
				UserStatus userStatus = UserStatus.valueOf(request.getParameter("userStatus"));
				if (!userStatus.equals(client.userStatus)) {
					client.userStatus = userStatus;
					userStatusChanged = true;
				}
			}

			if (!client.removed && client.bufferedImage != null) {
				updateUI(userName, client, imageOrCursorPositionChanged, userStatusChanged);
			}

			boolean thisUserOpened = client == openedClient;
			ServletOutputStream os = response.getOutputStream();
			os.println(String.valueOf(thisUserOpened && isControlling));
			os.println(thisUserOpened ? "0" : "5000"); // if opened user update the picture fast, otherwise slowly
			os.println(String.valueOf(lastUserStatusReset));

			synchronized (client.commands) {
				for (String line : client.commands) {
					os.print(line);
				}
				client.commands.clear();
			}

			os.flush();
			baseRequest.setHandled(true);
		} catch (Exception e) {
			e.printStackTrace();
			try {
				response.getOutputStream().close();
			} catch (Exception e2) {
			}
			throw e;
		}
	}
	protected void updateUI(String userName, Client client, boolean imageOrCursorPositionChanged, boolean userStatusChanged) {
		if (imageOrCursorPositionChanged) {
			WritableImage newFxImage = SwingFXUtils.toFXImage(client.bufferedImage, null);
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
					PixelWriter pixelWriter = newFxImage.getPixelWriter();
					for (int x = client.cursorPos.x; x < maxWidthTotal; x++) {
						for (int y = client.cursorPos.y; y < maxHeightTotal; y++) {
							cursorColor = cursorImageData[index++];
							if (cursorColor >> 24 != 0) {
								pixelWriter.setArgb(x, y, cursorColor);
							}
						}
						index += (32 - maxHeight);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			client.fxImage = newFxImage;
		}
		Platform.runLater(() -> { // Runs in JavaFX Thread
			if (!client.removed) {
				if (client.borderPane == null) {
					ImageView imageView = new ImageView();
					imageView.setFitWidth(400);
					imageView.setFitHeight(250);
					imageView.setPreserveRatio(true);
					imageView.setOnMouseClicked(e -> {
						switchToSingleUserView(client);
					});
					client.imageView = imageView;
					client.borderPane = new BorderPane(imageView);
					Label label = new Label(client.userName);
					label.setStyle("-fx-font-size:30px");
					label.setMaxHeight(10);
					client.borderPane.setTop(label);
					int index = 0;
					int insertIndex = 0;
					List<Node> children = flowPane.getChildren();
					for (Node bp : children) {
						String text = ((Label) ((BorderPane) bp).getTop()).getText();
						if (text.compareToIgnoreCase(userName) < 0) {
							insertIndex = index++ + 1;
						} else {
							break;
						}
					}
					children.add(insertIndex, client.borderPane);
				}
				if (imageOrCursorPositionChanged) {
					if (openedClient == null) {
						client.imageView.setImage(client.fxImage);
					} else if (client == openedClient) {
						if (!frozen) {
							((ImageView) scrollPane.getContent()).setImage(client.fxImage);
						}
					}
				}
				if (userStatusChanged) {
					if (client == openedClient) {
						updateTitle();
					}
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
		CheckBox freezeCheckBox = new CheckBox("Freeze");
		freezeCheckBox.setStyle("-fx-background-color: lightgray");
		freezeCheckBox.selectedProperty().addListener((obs, old, selected) -> {
			frozen = selected;
			if (!frozen) {
				((ImageView) scrollPane.getContent()).setImage(client.fxImage); // When unfrozen update image immediately
			}
		});
		menuPane.getChildren().add(backButton);
		menuPane.getChildren().add(takeControlCheckBox);
		menuPane.getChildren().add(freezeCheckBox);

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
		isControlling = false;
		frozen = false;
		openedClient = client;
		Platform.runLater(imageView::requestFocus);
		updateTitle();
	}

	protected void switchToMultiUserView() {
		while (menuPane.getChildren().size() > 1) {
			menuPane.getChildren().remove(1);
		}
		synchronized (clientCache) {
			for (Client client : clientCache.values()) {
				client.updateImageView();
			}
		}
		scrollPane.setContent(flowPane);
		openedClient = null;
		isControlling = false;
		updateTitle();
	}

	@Override
	public void addLifeCycleListener(Listener arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isFailed() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isRunning() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isStarted() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isStarting() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isStopped() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isStopping() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void removeLifeCycleListener(Listener arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void start() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void stop() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub

	}

	@Override
	public Server getServer() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setServer(Server arg0) {
		// TODO Auto-generated method stub

	}

}
