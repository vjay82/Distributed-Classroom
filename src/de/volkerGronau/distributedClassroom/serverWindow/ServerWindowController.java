package de.volkerGronau.distributedClassroom.serverWindow;

import java.awt.Graphics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import com.google.common.collect.Maps;

import de.volkerGronau.ApplicationHelper;
import de.volkerGronau.distributedClassroom.ClientBackend.UserStatus;
import de.volkerGronau.distributedClassroom.DistributedClassroom;
import de.volkerGronau.distributedClassroom.NetworkInputStream;
import de.volkerGronau.distributedClassroom.NetworkOutputStream;
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

public class ServerWindowController {

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

	protected class Client {
		String userName;
		BufferedImage bufferedImage;
		volatile WritableImage fxImage;
		java.awt.Point cursorPos;
		BorderPane borderPane; // root element for user
		ImageView imageView; // his/her image
		UserStatus userStatus;
		long lastContact;
		int requestCount;
		volatile boolean removed;
		Socket socket;
		NetworkOutputStream networkOutputStream;
		protected ExecutorService connectionWorker = Executors.newFixedThreadPool(1);
		public void updateImageView() {
			if (imageView != null) {
				imageView.setImage(fxImage);
			}
		}
		public void readBufferedImagePicture(NetworkInputStream networkInputStream) throws Exception {
			boolean imageIsUpdate = networkInputStream.readBoolean();

			byte[] imageBytes = new byte[networkInputStream.readInt()];
			if (networkInputStream.read(imageBytes) < imageBytes.length) {
				throw new Exception("Could not read image");
			}

			BufferedImage transferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
			if (transferedImage == null) {
				throw new Exception("Image is null");
			}

			if (bufferedImage != null) {
				Graphics g = bufferedImage.getGraphics();
				g.drawImage(transferedImage, 0, 0, null);
				g.dispose();
			} else {
				if (imageIsUpdate) { // we got an update but we have nothing to update
					throw new Exception("NOK, got an update-image but have no base");
				} else {
					bufferedImage = transferedImage;
				}
			}

		}
		public void readCursorPosition(NetworkInputStream ois) throws IOException {
			cursorPos = new Point(ois.readInt(), ois.readInt());
		}
		public void readUserStatus(NetworkInputStream ois) throws ClassNotFoundException, IOException {
			userStatus = UserStatus.valueOf(ois.readString());
		}
		public void setPictureInterval(int i) {
			connectionWorker.execute(() -> {
				try {
					synchronized (networkOutputStream) {
						networkOutputStream.writeChar('i');
						networkOutputStream.writeInt(i);
						networkOutputStream.flush();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
		public void setIsInputControlledByServer(boolean b) {
			connectionWorker.execute(() -> {
				try {
					synchronized (networkOutputStream) {
						networkOutputStream.writeChar('c');
						networkOutputStream.writeBoolean(b);
						networkOutputStream.flush();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
		public void resetUserStatus() throws IOException {
			if (borderPane != null) {
				borderPane.setStyle("");
			}
			userStatus = UserStatus.NEUTRAL;
			connectionWorker.execute(() -> {
				try {
					synchronized (networkOutputStream) {
						networkOutputStream.writeChar('r');
						networkOutputStream.flush();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
		public void inputControl_keyPress(String key) {
			connectionWorker.execute(() -> {
				try {
					synchronized (networkOutputStream) {
						networkOutputStream.writeChar('m');
						networkOutputStream.writeChar('a');
						networkOutputStream.writeString(key);
						networkOutputStream.flush();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}

		public void inputControl_keyRelease(String key) {
			connectionWorker.execute(() -> {
				try {
					synchronized (networkOutputStream) {
						networkOutputStream.writeChar('m');
						networkOutputStream.writeChar('b');
						networkOutputStream.writeString(key);
						networkOutputStream.flush();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}

		public void inputControl_mouseMove(int x, int y) {
			connectionWorker.execute(() -> {
				try {
					synchronized (networkOutputStream) {
						networkOutputStream.writeChar('m');
						networkOutputStream.writeChar('c');
						networkOutputStream.writeInt(x);
						networkOutputStream.writeInt(y);
						networkOutputStream.flush();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}

		public void inputControl_mousePress(int mask) {
			connectionWorker.execute(() -> {
				try {
					synchronized (networkOutputStream) {
						networkOutputStream.writeChar('m');
						networkOutputStream.writeChar('d');
						networkOutputStream.writeInt(mask);
						networkOutputStream.flush();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}

		public void inputControl_mouseRelease(int mask) {
			connectionWorker.execute(() -> {
				try {
					synchronized (networkOutputStream) {
						networkOutputStream.writeChar('m');
						networkOutputStream.writeChar('e');
						networkOutputStream.writeInt(mask);
						networkOutputStream.flush();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
		}
		public void close() {
			removed = true;
			connectionWorker.execute(() -> {
				try {
					if (networkOutputStream != null) {
						networkOutputStream.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				networkOutputStream = null;
				try {
					if (socket != null) {
						socket.close();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				socket = null;
			});
		}
		public void sendAlive() {
			connectionWorker.execute(() -> {
				try {
					synchronized (networkOutputStream) {
						networkOutputStream.writeChar('a');
						networkOutputStream.flush();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
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
		stage.setTitle(String.format(resources.getString("title"), DistributedClassroom.VERSION) + (openedClient == null ? "" : " - " + openedClient.userName + " - " + resources.getString("status") + " " + openedClient.userStatus));
	}

	public void init(Stage stage, ResourceBundle resources, Settings settings) throws Exception {
		this.stage = stage;
		this.resources = resources;
		updateTitle();
		scrollPane.viewportBoundsProperty().addListener((obs, old, bounds) -> {
			flowPane.setPrefWidth(bounds.getMaxX() - bounds.getMinX());
		});
		cursorImageData = loadCursorImageData();

		ServerSocket welcomeSocket = new ServerSocket(settings.getServerPort());

		Thread welcomeThread = new Thread() {
			@Override
			public void run() {
				while (true) {
					try {
						addClient(welcomeSocket.accept());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		};
		welcomeThread.setDaemon(true);
		welcomeThread.start();

		stage.setOnCloseRequest(e -> {
			Thread closeThread = new Thread("CloseThread") {

				@Override
				public void run() {
					System.out.println("Stopping Server");
					closing = true;
					try {
						welcomeSocket.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
					System.out.println("Closing Application");
					System.exit(0);
				}
			};
			closeThread.setDaemon(false);
			closeThread.start();
		});

		rootPane.setOnMouseMoved(e -> {
			menuPane.setVisible(e.getY() <= menuPane.getHeight());
		});

		resetUserStatusButton.setOnAction(e -> {
			lastUserStatusReset = System.currentTimeMillis();
			synchronized (clientCache) {
				for (Client client : clientCache.values()) {
					try {
						client.resetUserStatus();
					} catch (IOException e1) {
					}
				}
			}
			updateTitle();
		});

		Thread removeOldClientsThread = new Thread("RemoveOldClientsThread") {

			@Override
			public void run() {
				try {
					while (!isInterrupted()) {
						Thread.sleep(19000);
						synchronized (clientCache) {
							long removeOlderThan = System.currentTimeMillis() - 60000;
							Iterator<Entry<String, Client>> iterator = clientCache.entrySet().iterator();
							while (iterator.hasNext()) {
								Client client = iterator.next().getValue();
								if (client.lastContact < removeOlderThan) {
									iterator.remove();
									client.close();
									Platform.runLater(() -> {
										if (client.borderPane != null) {
											flowPane.getChildren().remove(client.borderPane);
										}
									});
								} else {
									client.sendAlive();
								}
							}
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};
		removeOldClientsThread.setDaemon(true);
		removeOldClientsThread.start();
	}

	protected int[] loadCursorImageData() throws Exception {
		BufferedImage cursorBufferedImage = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics graphics = cursorBufferedImage.getGraphics();
		graphics.drawImage(ApplicationHelper.Resources.getImage("cursorRed.png", false).getImage(), 0, 0, null);
		graphics.dispose();
		return ((DataBufferInt) cursorBufferedImage.getRaster().getDataBuffer()).getData();
	}

	protected void addClient(Socket clientSocket) {
		System.out.println("Adding Client, starting thread");
		Thread clientThread = new Thread() {
			@Override
			public void run() {

				try {
					clientSocket.setSoTimeout(60000);
					NetworkInputStream ois = new NetworkInputStream(clientSocket.getInputStream());
					String userName = ois.readString();
					System.out.println("Adding Client, userName: " + userName);
					Client client;
					synchronized (clientCache) {
						client = clientCache.get(userName);
						if (client == null) {
							client = new Client();
							client.userName = userName;
							client.lastContact = System.currentTimeMillis();
							clientCache.put(userName, client);
						}
					}
					client.socket = clientSocket;
					client.networkOutputStream = new NetworkOutputStream(clientSocket.getOutputStream());
					client.setPictureInterval(client == openedClient ? 0 : 5000);
					client.setIsInputControlledByServer(client == openedClient && isControlling);

					while (!isInterrupted()) {
						//System.out.println("UserName: " + userName + " waiting for command, available is: " + ois.available());
						char command = ois.readChar();
						System.out.println("UserName: " + userName + " got command: " + command);
						switch (command) {
							case 'p' :
								client.readBufferedImagePicture(ois);
								if (!client.removed && client.bufferedImage != null) {
									updateUI_ClientImage(userName, client);
								}
								break;
							case 'c' :
								client.readCursorPosition(ois);
								updateUI_ClientImage(userName, client);
								break;
							case 'u' :
								client.readUserStatus(ois);
								updateUI_UserStatus(userName, client);
								break;
							case 'i' : // idle
								break;

						}

						client.lastContact = System.currentTimeMillis();
					}

				} catch (Exception e) {
					e.printStackTrace();
					try {
						clientSocket.close();
					} catch (IOException e1) {
					}
				}

			}
		};
		clientThread.setDaemon(true);
		clientThread.start();
	}

	protected void updateUI_UserStatus(String userName, Client client) {
		Platform.runLater(() -> { // Runs in JavaFX Thread
			if (client == openedClient) {
				updateTitle();
			}
			if (client.borderPane != null) {
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

	protected void updateUI_ClientImage(String userName, Client client) {
		if (client.bufferedImage != null) {
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
					if (openedClient == null) {
						client.imageView.setImage(client.fxImage);
					} else if (client == openedClient) {
						if (!frozen) {
							((ImageView) scrollPane.getContent()).setImage(client.fxImage);
						}
					}
				}
			});
		}
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
			openedClient.setIsInputControlledByServer(isControlling);
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
				client.inputControl_keyPress(e.getCode().name());
			}
		});
		imageView.setOnKeyReleased(e -> {
			if (takeControlCheckBox.isSelected()) {
				client.inputControl_keyRelease(e.getCode().name());
			}
		});
		imageView.setOnMouseMoved(e -> {
			if (takeControlCheckBox.isSelected()) {
				client.inputControl_mouseMove((int) e.getX(), (int) e.getY());

			}
		});
		imageView.setOnMouseDragged(e -> {
			if (takeControlCheckBox.isSelected()) {
				client.inputControl_mouseMove((int) e.getX(), (int) e.getY());
			}
		});
		imageView.setOnMousePressed(e -> {
			if (takeControlCheckBox.isSelected()) {
				client.inputControl_mousePress(e.getButton().ordinal());
			}
		});
		imageView.setOnMouseReleased(e -> {
			if (takeControlCheckBox.isSelected()) {
				client.inputControl_mouseRelease(e.getButton().ordinal());
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
		client.setPictureInterval(0);
	}

	protected void switchToMultiUserView() {
		while (menuPane.getChildren().size() > 1) {
			menuPane.getChildren().remove(1);
		}
		synchronized (clientCache) {
			for (Client client : clientCache.values()) {
				client.updateImageView();
				client.setPictureInterval(5000);
				client.setIsInputControlledByServer(false);
			}
		}
		scrollPane.setContent(flowPane);
		openedClient = null;
		isControlling = false;
		updateTitle();
	}

}
