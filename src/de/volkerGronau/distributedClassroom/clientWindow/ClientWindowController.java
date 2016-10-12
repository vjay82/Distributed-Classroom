package de.volkerGronau.distributedClassroom.clientWindow;

import java.util.ResourceBundle;

import de.volkerGronau.distributedClassroom.Screen;
import de.volkerGronau.distributedClassroom.ScreenCapture;
import de.volkerGronau.distributedClassroom.settings.Settings;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

public class ClientWindowController {

	@FXML
	protected Button buttonStopSharing;

	public void init(Stage stage, ResourceBundle resources, Settings settings) throws Exception {
		stage.setTitle(resources.getString("title"));

		buttonStopSharing.setOnAction(e -> {
			Platform.exit();
		});

		stage.setOnCloseRequest(e -> {
			Platform.exit();
		});

		new ScreenCapture(Screen.getScreen(String.valueOf(settings.getScreen())), settings.getName(), settings.getServerAddress());

	}

}
