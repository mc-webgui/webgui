package land.webgui;

import de.keksuccino.rinku.Rinku;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefBeforeDownloadCallback;
import org.cef.callback.CefDownloadItem;
import org.cef.callback.CefDownloadItemCallback;
import org.cef.handler.CefDownloadHandler;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lets a page hand the player a file.
 *
 * With no handler CEF asks the host where to put a download and, hearing nothing, throws
 * it away — so an invoice, a log, a schematic or a CSV export was simply unclickable.
 *
 * Everything lands in one folder under the game directory, named by the page's suggestion
 * with the path stripped out of it. The page never chooses where its bytes go: it is web
 * content, and a suggested name is the one part of a download an attacker fully controls.
 */
public final class WebviewDownloadHandler {

    private static final String FOLDER = "webgui-downloads";

    private WebviewDownloadHandler() {}

    public static void register() {
        // Rinku forwards this one itself, through a relay that lets several mods decide.
        Rinku.getClient().addDownloadHandler(new CefDownloadHandler() {

            @Override
            public boolean canDownload(CefBrowser browser, String url, String requestMethod) {
                return true;
            }

            @Override
            public void onBeforeDownload(CefBrowser browser, CefDownloadItem item,
                                         String suggestedName, CefBeforeDownloadCallback callback) {
                start(item, suggestedName, callback);
            }

            /**
             * Rinku's own variant, which also asks whether this handler took the decision.
             * Answering true stops it falling through to another mod's handler and saving
             * the same file twice.
             */
            @Override
            public boolean onBeforeDownloadWithDecision(CefBrowser browser, CefDownloadItem item,
                                                        String suggestedName, CefBeforeDownloadCallback callback) {
                start(item, suggestedName, callback);
                return true;
            }

            @Override
            public void onDownloadUpdated(CefBrowser browser, CefDownloadItem item,
                                          CefDownloadItemCallback callback) {
                if (item != null && item.isComplete()) {
                    // Told, not silent. A file that arrives with no trace is a file the
                    // player will never find, and the folder is not one they know about.
                    WebGUIMod.LOGGER.info("webgui: downloaded {}", item.getFullPath());
                    tell("Saved to " + FOLDER + "/" + fileName(item.getFullPath()));
                } else if (item != null && item.isCanceled()) {
                    WebGUIMod.LOGGER.info("webgui: download cancelled ({})", item.getURL());
                }
            }
        });
    }

    private static void start(CefDownloadItem item, String suggestedName, CefBeforeDownloadCallback callback) {
        if (callback == null) {
            return;
        }
        try {
            Path target = WebviewDownloadNames.free(directory(), WebviewDownloadNames.safeName(suggestedName));
            Files.createDirectories(target.getParent());
            // false: no "where do you want to save this" dialog. There is no window to
            // put one in, and the answer is always the same folder.
            callback.Continue(target.toAbsolutePath().toString(), false);
            WebGUIMod.LOGGER.info("webgui: saving a download to {}", target);
        } catch (Exception e) {
            WebGUIMod.LOGGER.warn("webgui: could not start a download of {}: {}",
                    item == null ? "?" : item.getURL(), e.toString());
            callback.Continue("", false);
        }
    }

    private static Path directory() {
        //? if fabric {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(FOLDER);
        //? } else {
        /*return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve(FOLDER);*/
        //? }
    }

    private static String fileName(String fullPath) {
        if (fullPath == null) {
            return "?";
        }
        int slash = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
        return slash >= 0 ? fullPath.substring(slash + 1) : fullPath;
    }

    private static void tell(String message) {
        //? if fabric {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(net.minecraft.text.Text.literal("[WebGUI] " + message), false);
            }
        });
        //? } else {
        /*net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                //? if >=26 {
                client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[WebGUI] " + message));
                //? } else {
                client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("[WebGUI] " + message), false);
                //? }
            }
        });*/
        //? }
    }
}
