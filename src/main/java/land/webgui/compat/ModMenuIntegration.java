//? if fabric {
package land.webgui.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import land.webgui.WebGUISettingsScreen;

/**
 * Puts a Config button on WebGUI's entry in Mod Menu's list, which is where a Fabric
 * player looks for a mod's settings.
 *
 * Compiled against Mod Menu, never requiring it: without it nothing reads this
 * entrypoint and the class is never loaded.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WebGUISettingsScreen::new;
    }
}
//? }
