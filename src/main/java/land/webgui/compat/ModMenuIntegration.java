//? if fabric {
package land.webgui.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import land.webgui.WebGUISettingsScreen;

/**
 * Puts a Config button on WebGUI's entry in Mod Menu's list.
 *
 * Fabric has no mod list of its own, so this is where a Fabric player expects to find a
 * mod's settings. Mod Menu is compiled against but never required: without it nothing
 * reads this entrypoint and the class is simply never loaded.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WebGUISettingsScreen::new;
    }
}
//? }
