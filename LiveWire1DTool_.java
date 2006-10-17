import ij.gui.Toolbar;
import ij.plugin.PlugIn;

public class LiveWire1DTool_ extends LiveWireTool implements PlugIn {

	public LiveWire1DTool_() {
		toolID = Toolbar.getInstance().getToolId("LiveWire 1d Tool");
	}
}
