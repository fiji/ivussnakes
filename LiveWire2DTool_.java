


import ij.gui.Toolbar;
import ij.plugin.PlugIn;

public class LiveWire2DTool_ extends LiveWireTool implements PlugIn {

	public LiveWire2DTool_() {
		toolID = Toolbar.getInstance().getToolId("LiveWire 2d Tool");
	}
	
	public String kindSpecifier() {
		return "area";
	}
	
	public boolean isArea() {
		return true;
	}
}
