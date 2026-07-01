package sql4ibatis;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import sql4ibatis.preferences.PreferenceConstants;

/**
 * The activator class controls the plug-in life cycle and accesses shared resources like PreferenceStore.
 */
public class Activator extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "sql4ibatis";

	private static Activator plugin;
	
	public Activator() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance.
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * Initializes the default preferences for this plug-in.
	 */
	@Override
	protected void initializeDefaultPreferences(IPreferenceStore store) {
		// Set default max rows limit to 1000.
		store.setDefault(PreferenceConstants.DB_MAX_ROWS, 1000);
	}
}
