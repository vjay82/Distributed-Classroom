package de.volkerGronau.distributedClassroom;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import de.volkerGronau.ApplicationHelper;
import de.volkerGronau.distributedClassroom.clientWindow.ClientWindowController;
import de.volkerGronau.distributedClassroom.javaFX.MyFXMLLoader;
import de.volkerGronau.distributedClassroom.serverWindow.ServerWindowController;
import de.volkerGronau.distributedClassroom.settings.Settings;
import de.volkerGronau.distributedClassroom.startWindow.RunAfterStartWindowClosed;
import de.volkerGronau.distributedClassroom.startWindow.StartWindowController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class DistributedClassroom extends Application {

	public static final String VERSION = "1.02";

	protected ApplicationHelper applicationHelper = new ApplicationHelper("Volker Gronau", "Distributed Classroom");

	public static void main(String[] args) {
		launch(args);
	}

	protected Path getSettingsJsonpath() {
		return applicationHelper.getApplicationHome().resolve("Settings.json");
	}

	@Override
	public void start(Stage primaryStage) {
		try {
			Platform.setImplicitExit(false);

			applicationHelper.initApplicationHome();

			Path settingsJsonPath = getSettingsJsonpath();

			Settings settings;
			if (Files.isReadable(settingsJsonPath)) {
				try (Reader reader = Files.newBufferedReader(settingsJsonPath)) {
					settings = new Gson().fromJson(new JsonReader(reader), Settings.class);
				}
				if (Strings.isNullOrEmpty(settings.getServerAddress()) || settings.getServerAddress().startsWith("http")) {
					settings.setServerAddress("vjay.duckdns.org:9876");
				}
			} else {
				settings = new Settings();
				settings.setName(Strings.nullToEmpty(System.getenv("username")));
				settings.setServerAddress("vjay.duckdns.org:9876");
				settings.setServerPort(9876);
			}

			MyFXMLLoader fxmlLoader = new MyFXMLLoader(StartWindowController.class, "StartWindow.fxml");
			primaryStage.setScene(new Scene((Parent) fxmlLoader.load()));
			StartWindowController windowController = (StartWindowController) fxmlLoader.getController();
			windowController.init(primaryStage, fxmlLoader.getResources(), settings, afterStartWindowController());
			primaryStage.show();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public RunAfterStartWindowClosed afterStartWindowController() {
		return (settings, startServer, startClient) -> {
			try {
				try (Writer writer = Files.newBufferedWriter(getSettingsJsonpath())) {
					writer.write(new Gson().toJson(settings));
				}

				if (startServer) {
					MyFXMLLoader fxmlLoader = new MyFXMLLoader(ServerWindowController.class, "ServerWindow.fxml");
					Stage stage = new Stage();
					stage.setScene(new Scene((Parent) fxmlLoader.load()));
					ServerWindowController windowController = (ServerWindowController) fxmlLoader.getController();
					windowController.init(stage, fxmlLoader.getResources(), settings);
					stage.show();
				} else if (startClient) {
					MyFXMLLoader fxmlLoader = new MyFXMLLoader(ClientWindowController.class, "ClientWindow.fxml");
					Stage stage = new Stage(StageStyle.UTILITY);

					stage.setScene(new Scene((Parent) fxmlLoader.load()));
					ClientWindowController windowController = (ClientWindowController) fxmlLoader.getController();
					windowController.init(stage, fxmlLoader.getResources(), settings);
					stage.show();
				} else {
					Platform.exit();
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		};
	}

}
