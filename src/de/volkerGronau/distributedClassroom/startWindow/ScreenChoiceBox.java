package de.volkerGronau.distributedClassroom.startWindow;

import java.awt.Insets;
import java.awt.Rectangle;
import java.util.EventListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

import com.google.common.collect.Lists;

import de.volkerGronau.distributedClassroom.Screen;
import de.volkerGronau.distributedClassroom.Screenshot;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.PopupControl;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;

public class ScreenChoiceBox extends ChoiceBox<Screen> implements EventListener {
	protected class MyCanvas extends Canvas {
		private List<Rectangle2D> rectangles = Lists.newArrayList();
		protected Screenshot mouseOver;
		protected Screenshot oldMouseOver;
		private Glow glow = new Glow();
		Map<Double, Font> fontCache = new HashMap<>();

		public MyCanvas() {
			super();

			InvalidationListener listener = new InvalidationListener() {
				@Override
				public void invalidated(Observable o) {
					redraw();
				}

			};
			widthProperty().addListener(listener);
			heightProperty().addListener(listener);
			setOnMouseMoved(event -> {
				mouseOver = null;
				for (Rectangle2D rectangle2D : rectangles) {
					if (rectangle2D.contains(event.getX(), event.getY())) {
						mouseOver = screenshots.get(rectangles.indexOf(rectangle2D));
						break;
					}
				}

				if (mouseOver != oldMouseOver) {
					oldMouseOver = mouseOver;
					redraw();
				}
			});
			setOnMousePressed(event -> {
				if (mouseOver != null) {
					for (Screen screen : getItems()) {
						if (screen.getBounds().equals(mouseOver == null ? null : mouseOver.getBounds())) {
							getSelectionModel().select(screen);
							hide();
							break;
						}
					}
				}
			});
		}
		protected Insets getRange() {
			Insets range = new Insets(0, 0, 0, 0);
			for (Screenshot screen : screenshots) {
				Rectangle r = screen.getBounds();
				if (r.x < range.left) {
					range.left = r.x;
				}
				if (r.x + r.width > range.right) {
					range.right = r.x + r.width;
				}
				if (r.y < range.top) {
					range.top = r.y;
				}
				if (r.y + r.height > range.bottom) {
					range.bottom = r.y + r.height;
				}
			}
			range.left *= 1.1d;
			range.top *= 1.1d;
			range.right *= 1.1d;
			range.bottom *= 1.1d;
			return range;
		}

		private void redraw() {
			Insets range = getRange();
			double width = getWidth();
			double height = getHeight();
			GraphicsContext graphicsContext = getGraphicsContext2D();
			graphicsContext.setFill(Color.BLACK);
			graphicsContext.clearRect(0, 0, width, height);

			width -= 10;
			height -= 10;

			int widthNeeded = range.right - range.left;
			int heightNeeded = range.bottom - range.top;
			double scale = Math.min(width / widthNeeded, height / heightNeeded);

			double toY = 10 + (height - (scale * heightNeeded)) / 2;
			double toX = 10 + (width - (scale * widthNeeded)) / 2;

			graphicsContext.setTextAlign(TextAlignment.CENTER);
			graphicsContext.setTextBaseline(VPos.CENTER);
			rectangles.clear();
			int index = 0;
			for (Screenshot screen : screenshots) {
				Rectangle r = screen.getBounds();

				double x = toX + scale * (r.x * 1.05d - range.left);
				double y = toY + scale * (r.y * 1.05d - range.top);
				width = scale * r.width;
				height = scale * r.height;
				if (width > 0 && height > 0) {
					rectangles.add(new Rectangle2D(x, y, width, height));
					if (screen == mouseOver) {
						graphicsContext.setEffect(glow);
					} else {
						graphicsContext.setEffect(null);
					}
					graphicsContext.drawImage(screen.getFXImage(), x, y, width, height);
					graphicsContext.setFont(fontCache.computeIfAbsent(Math.min(width, height), key -> new Font(key)));
					graphicsContext.fillText(String.valueOf(++index), x + width / 2, y + height / 2);
				}
			}

		}

		@Override
		public boolean isResizable() {
			return false;
		}

	}

	protected PopupControl popupControl;
	protected List<Screenshot> screenshots;
	protected MyCanvas myCanvas;
	protected boolean armed;
	protected ResourceBundle resources = ResourceBundle.getBundle(getClass().getName());
	protected SimpleObjectProperty<Integer> screenIndex = new SimpleObjectProperty<Integer>(0);

	{
		setOnMousePressed(e -> {
			if (!getPopupControl().isShowing()) {
				armed = true;
			}
		});
		setConverter(new StringConverter<Screen>() {

			@Override
			public String toString(Screen screen) {
				Rectangle bounds = screen.getBounds();
				return resources.getString("Screen") + " " + (screen.getIndex() + 1) + " (" + bounds.width + "x" + bounds.height + ")";
			}

			@Override
			public Screen fromString(String string) {
				return null;
			}
		});
		getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Screen>() {

			@Override
			public void changed(ObservableValue<? extends Screen> observable, Screen oldValue, Screen newValue) {
				if (newValue != null && !Objects.equals(screenIndex.getValue(), newValue.getIndex())) {
					screenIndex.setValue(newValue.getIndex());
				}
			}
		});
		//		EventBus.get().addEventListener(SettingsScreenshotsCreatedEvent.class, ExecutionContext.IMMEDIATE, new WeakEventListenerWrapper(this));
		//		EventBus.get().fireEvent(new SettingsRequestScreenshotsCreatedEvent());
	}

	public SimpleObjectProperty<Integer> screenIndexProperty() {
		return screenIndex;
	}

	public void setScreenshots(List<Screenshot> screenshots) {
		getItems().setAll(screenshots);
		this.screenshots = screenshots;
		updateSelection();
	}

	public void setScreenIndex(int screenIndex) {
		this.screenIndex.setValue(screenIndex);
		updateSelection();
	}

	private void updateSelection() {
		if (getSelectionModel().getSelectedIndex() != screenIndex.getValue()) {
			getSelectionModel().select(screenIndex.getValue());
		}
	}

	@Override
	public void show() {
		if (getPopupControl().isShowing()) {
			popupControl.hide();
		} else if (armed) {
			armed = false;
			Point2D p = localToScreen(0, 0);
			getPopupControl().show(getScene().getWindow(), p.getX() + getWidth() - myCanvas.getWidth(), p.getY() + getHeight() + 3);
		}
	}

	private PopupControl getPopupControl() {
		if (popupControl == null) {
			myCanvas = new MyCanvas();
			popupControl = new PopupControl();
			BorderPane anchorPane = new BorderPane(myCanvas);
			//	AnchorPane.setTopAnchor(myCanvas, 8d);
			anchorPane.setStyle("-fx-background-color: -fx-background;");
			anchorPane.setEffect(new DropShadow());
			popupControl.getScene().setRoot(anchorPane);
			Point2D p = localToScreen(0, 0);
			de.volkerGronau.distributedClassroom.Screen screen = de.volkerGronau.distributedClassroom.Screen.getScreenOf(p);

			Insets range = myCanvas.getRange();
			int widthNeeded = range.right - range.left;
			int heightNeeded = range.bottom - range.top;
			double width = screen.getBounds().width / 5;
			double height = screen.getBounds().height / 5;
			double scale = Math.min(width / widthNeeded, height / heightNeeded);
			width = widthNeeded * scale + 20;
			height = heightNeeded * scale + 20;

			myCanvas.setWidth(width);
			myCanvas.setHeight(height);

			popupControl.setAutoHide(true);
		}
		return popupControl;
	}

	@Override
	public void hide() {
		getPopupControl().hide();
	}

	//	@Override
	//	public void onThickClientEvent(Event event) {
	//		if (event instanceof SettingsScreenshotsCreatedEvent) {
	//			setScreenshots(((SettingsScreenshotsCreatedEvent) event).getScreenshots());
	//		}
	//	}

}
