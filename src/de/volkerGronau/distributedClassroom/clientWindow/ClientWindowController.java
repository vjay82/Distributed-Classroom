package de.volkerGronau.distributedClassroom.clientWindow;

import java.util.ResourceBundle;

import de.volkerGronau.distributedClassroom.ClientBackend;
import de.volkerGronau.distributedClassroom.ClientBackend.UserStatus;
import de.volkerGronau.distributedClassroom.DistributedClassroom;
import de.volkerGronau.distributedClassroom.Screen;
import de.volkerGronau.distributedClassroom.settings.Settings;
import javafx.fxml.FXML;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

public class ClientWindowController {

	//	@FXML
	//	protected Button buttonStopSharing;

	@FXML
	protected TabPane statusTabPane;

	protected ClientBackend clientBackend;
	protected boolean eventIsFromUser = true;

	public void init(Stage stage, ResourceBundle resources, Settings settings) throws Exception {
		stage.setTitle(String.format(resources.getString("title"), DistributedClassroom.VERSION));
		try {
			stage.setAlwaysOnTop(true);
		} catch (Throwable t) {
			// Old JavaFX does not support this function
		}

		//		buttonStopSharing.setOnAction(e -> {
		//			Platform.exit();
		//		});

		stage.setOnCloseRequest(e -> {
			System.out.println("Exiting");
			//Platform.exit();
			System.exit(0);
		});

		clientBackend = new ClientBackend(Screen.getScreen(String.valueOf(settings.getScreen())), settings.getName(), settings.getServerAddress());
		clientBackend.setOnResetUserStatus(() -> {
			eventIsFromUser = false;
			statusTabPane.getSelectionModel().select(1);
			clientBackend.resetUserStatus();
			eventIsFromUser = true;
		});
		statusTabPane.getSelectionModel().selectedIndexProperty().addListener((obs, old, index) -> {
			if (eventIsFromUser) {
				switch (index.intValue()) {
					case 0 :
						clientBackend.setUserStatus(UserStatus.OK);
						break;
					case 1 :
						clientBackend.setUserStatus(UserStatus.NEUTRAL);
						break;
					case 2 :
						clientBackend.setUserStatus(UserStatus.NOT_OK);
						break;
				}
			}
		});
		statusTabPane.getSelectionModel().select(1);
	}

}
