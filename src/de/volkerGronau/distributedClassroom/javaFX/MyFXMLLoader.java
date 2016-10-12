package de.volkerGronau.distributedClassroom.javaFX;

import java.util.ResourceBundle;

import javafx.fxml.FXMLLoader;

public class MyFXMLLoader extends FXMLLoader {
	String fxmlName;

	public MyFXMLLoader(Class<?> controllerClass, String fxmlName) throws InstantiationException, IllegalAccessException {
		super(controllerClass.getResource(fxmlName));
		setResources(ResourceBundle.getBundle(controllerClass.getName()));
		setController(controllerClass.newInstance());
		this.fxmlName = fxmlName;
	}

	public String getFxmlName() {
		return fxmlName;
	}

}
