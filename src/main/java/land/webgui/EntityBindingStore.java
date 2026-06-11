package land.webgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import land.webgui.server.EntityBinding;
//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
//? } else {
/*import net.neoforged.fml.loading.FMLPaths;*/
//? }

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EntityBindingStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<UUID, EntityBinding> bindings = new ConcurrentHashMap<>();

    private EntityBindingStore() {}

    // DTO for JSON serialization
    private static class BindingsFile {
        List<BindingEntry> bindings = new ArrayList<>();
    }

    private static class BindingEntry {
        String entityUuid;
        String urlTemplate;
        boolean cancelInteraction;
    }

    private static Path storePath() {
        //? if fabric {
        return FabricLoader.getInstance().getConfigDir().resolve("webgui").resolve("entity_bindings.json");
        //? } else {
        /*return FMLPaths.CONFIGDIR.get().resolve("webgui").resolve("entity_bindings.json");*/
        //? }
    }

    public static void bind(UUID entityUuid, EntityBinding binding) {
        bindings.put(entityUuid, binding);
        save();
    }

    public static boolean unbind(UUID entityUuid) {
        boolean removed = bindings.remove(entityUuid) != null;
        if (removed) save();
        return removed;
    }

    public static Optional<EntityBinding> get(UUID entityUuid) {
        return Optional.ofNullable(bindings.get(entityUuid));
    }

    public static int size() {
        return bindings.size();
    }

    public static void load() {
        Path path = storePath();
        bindings.clear();
        if (!Files.isRegularFile(path)) return;
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            BindingsFile file = GSON.fromJson(json, BindingsFile.class);
            if (file == null || file.bindings == null) return;
            for (BindingEntry e : file.bindings) {
                if (e.entityUuid == null || e.urlTemplate == null) continue;
                try {
                    bindings.put(UUID.fromString(e.entityUuid), new EntityBinding(e.urlTemplate, e.cancelInteraction));
                } catch (IllegalArgumentException ignored) {
                    WebGUIMod.LOGGER.warn("webgui: skipping invalid entity UUID in entity_bindings.json: {}", e.entityUuid);
                }
            }
            WebGUIMod.LOGGER.info("webgui: loaded {} entity binding(s) from {}", bindings.size(), path.getFileName());
        } catch (IOException ex) {
            WebGUIMod.LOGGER.error("webgui: failed to load entity_bindings.json", ex);
        }
    }

    private static void save() {
        Path path = storePath();
        BindingsFile file = new BindingsFile();
        for (Map.Entry<UUID, EntityBinding> entry : bindings.entrySet()) {
            BindingEntry e = new BindingEntry();
            e.entityUuid = entry.getKey().toString();
            e.urlTemplate = entry.getValue().urlTemplate();
            e.cancelInteraction = entry.getValue().cancelInteraction();
            file.bindings.add(e);
        }
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(file), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            WebGUIMod.LOGGER.error("webgui: failed to save entity_bindings.json", ex);
        }
    }
}
