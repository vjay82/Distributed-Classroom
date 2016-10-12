package de.volkerGronau.distributedClassroom;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;

import javafx.geometry.Point2D;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Class to query information about displays.
 *
 * @author volker.gronau
 *
 */
public class Screen {
	protected static org.apache.logging.log4j.Logger logger = LogManager.getLogger();

	public static Screen getScreen(String screenDescription) {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screenDevices = ge.getScreenDevices();

		if (screenDescription != null) {
			if (screenDescription.contains("x")) {
				String[] res = screenDescription.split("x");

				int x = Integer.parseInt(res[0]);
				int y = Integer.parseInt(res[1]);

				for (int index = 0; index < screenDevices.length; index++) {
					GraphicsDevice g = screenDevices[index];
					Rectangle bounds = g.getDefaultConfiguration().getBounds();
					if (bounds.width == x && bounds.height == y) {
						logger.info("Found screen for description {}", screenDescription);
						return new Screen(index, g);
					}
				}

				logger.error("Screen with resolution " + x + "x" + y + " not found.");
			} else {
				int index = Integer.parseInt(screenDescription);
				if (index < 0) {
					logger.error("Screen index " + index + " < 0.");
				} else if (index >= screenDevices.length) {
					logger.error("Screen index " + index + " > screens.size (" + screenDevices.length + ").");
				} else {
					return new Screen(index, screenDevices[index]);
				}
			}
		}

		if (screenDevices.length > 0) {
			GraphicsDevice defaultDevice = ge.getDefaultScreenDevice();
			for (int index = 0; index < screenDevices.length; index++) {
				if (screenDevices[index] == defaultDevice) {
					return new Screen(index, ge.getDefaultScreenDevice());
				}
			}
		} else {
			logger.error("No screendevices found at all.");
		}
		return new Screen(-1, ge.getDefaultScreenDevice());
	}

	public static Screen getScreenOf(Window window) {
		return getScreenOf(new Point((int) (window.getX() + window.getWidth() / 2), (int) (window.getY() + window.getHeight() / 2)));
	}

	public static Screen getScreenOf(Stage stage) {
		return getScreenOf(new Point((int) (stage.getX() + stage.getWidth() / 2), (int) (stage.getY() + stage.getHeight() / 2)));
	}

	public static Screen getScreenOf(Point2D p) {
		return getScreenOf(new Point((int) p.getX(), (int) p.getY()));
	}

	public static Screen getScreenOf(Point p) {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screenDevices = ge.getScreenDevices();
		for (int index = 0; index < screenDevices.length; index++) {
			GraphicsDevice g = screenDevices[index];
			Rectangle bounds = g.getDefaultConfiguration().getBounds();
			if (bounds.contains(p)) {
				return new Screen(index, g);
			}
		}
		return new Screen(-1, ge.getDefaultScreenDevice());
	}

	public static List<String> getScreenDescriptions() {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] screenDevices = ge.getScreenDevices();
		List<String> result = new ArrayList<>(screenDevices.length);
		for (int index = 0; index < screenDevices.length; index++) {
			result.add(String.valueOf(index));
		}
		return result;
	}

	protected int index;
	protected Rectangle bounds;
	protected Rectangle boundsWithInsets;
	protected GraphicsConfiguration graphicsConfiguration;

	public Screen(int index, GraphicsDevice graphicsDevice) {
		graphicsConfiguration = graphicsDevice.getDefaultConfiguration();
		bounds = graphicsConfiguration.getBounds();
		this.index = index;
		Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(graphicsConfiguration);
		boundsWithInsets = new Rectangle(bounds.x + screenInsets.left, bounds.y + screenInsets.top, bounds.width - screenInsets.left - screenInsets.right, bounds.height - screenInsets.top - screenInsets.bottom);
	}

	public int getIndex() {
		return index;
	}

	public Rectangle getBounds() {
		return bounds;
	}

	public Rectangle getBoundsWithInsets() {
		return boundsWithInsets;
	}

	public GraphicsConfiguration getGraphicsConfiguration() {
		return graphicsConfiguration;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((bounds == null) ? 0 : bounds.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Screen other = (Screen) obj;
		if (bounds == null) {
			if (other.bounds != null) {
				return false;
			}
		} else if (!bounds.equals(other.bounds)) {
			return false;
		}
		return true;
	}

}
