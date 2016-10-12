package de.volkerGronau.distributedClassroom.startWindow;

import java.util.ResourceBundle;

import de.volkerGronau.distributedClassroom.Screenshot;
import de.volkerGronau.distributedClassroom.settings.Settings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class StartWindowController {

	@FXML
	protected TextField tfName;

	@FXML
	protected TextField tfServerAddress;

	@FXML
	protected TextField tfServerPort;

	@FXML
	protected Button buttonClose;

	@FXML
	protected Button buttonStart;

	@FXML
	protected TabPane tpClientServer;

	@FXML
	protected ScreenChoiceBox screenChoiceBox;

	public void init(Stage stage, ResourceBundle resources, Settings settings, RunAfterStartWindowClosed runAfterStartWindowClosed) {
		stage.setTitle(resources.getString("title"));

		tfName.setText(settings.getName());
		tfServerAddress.setText(settings.getServerAddress());
		tfServerPort.setText(String.valueOf(settings.getServerPort()));
		screenChoiceBox.setScreenshots(Screenshot.getScreenShots());
		screenChoiceBox.setScreenIndex(settings.getScreen());

		buttonStart.setOnAction(e -> {
			saveSettings(settings);
			stage.close();
			runAfterStartWindowClosed.run(settings, tpClientServer.getSelectionModel().getSelectedIndex() == 1, tpClientServer.getSelectionModel().getSelectedIndex() == 0);
		});

		buttonClose.setOnAction(e -> {
			saveSettings(settings);
			stage.close();
			runAfterStartWindowClosed.run(settings, false, false);
		});

		stage.setOnCloseRequest(e -> {
			saveSettings(settings);
			runAfterStartWindowClosed.run(settings, false, false);
		});

	}

	private void saveSettings(Settings settings) {
		settings.setName(tfName.getText());
		settings.setServerAddress(tfServerAddress.getText());
		settings.setServerPort(Integer.parseInt(tfServerPort.getText()));

		settings.setScreen(screenChoiceBox.getSelectionModel().getSelectedItem().getIndex());
	}

}
