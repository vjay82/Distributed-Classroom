package de.volkerGronau.distributedClassroom;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.List;

import com.google.common.collect.Lists;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;

public class Screenshot extends Screen {
	public static List<Screenshot> getScreenShots() {
		List<Screenshot> result = Lists.newArrayList();
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screenDevices = ge.getScreenDevices();
		for (int index = 0; index < screenDevices.length; index++) {
			result.add(new Screenshot(index, screenDevices[index]));
		}
		return result;
	}

	protected BufferedImage image;
	protected WritableImage fxImage;

	public BufferedImage getSwingImage() {
		return image;
	}

	public void setSwingImage(BufferedImage image) {
		this.image = image;
	}

	public WritableImage getFXImage() {
		if (fxImage == null) {
			fxImage = SwingFXUtils.toFXImage(image, null);
		}
		return fxImage;
	}

	public Screenshot(int index, GraphicsDevice graphicsDevice) {
		super(index, graphicsDevice);
		try {
			image = new Robot().createScreenCapture(bounds);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public int getIndex() {
		return index;
	}

}
