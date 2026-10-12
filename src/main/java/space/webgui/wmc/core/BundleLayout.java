package space.webgui.wmc.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Where the pieces are inside an installed bundle.
 *
 * <p>They are found by looking rather than by a table of per-platform paths. The three
 * platforms genuinely disagree — Windows and Linux are flat, macOS puts everything inside a
 * framework and an app bundle — and a table has to be right about all of them at once, in a
 * file that is edited on whichever machine the author happens to use. Worse, it is silently
 * wrong when upstream moves something: the browser starts, shows nothing, and says nothing.
 *
 * <p>Looking for the landmarks instead — the library, the helper process, the file Chromium
 * reads its resources from — is a handful of lines, works on a platform this was never run
 * on, and fails loudly with the directory it searched.
 */
public final class BundleLayout {

    private final Path root;
    private final Path library;
    private final Path helper;
    private final Path resourcesDirectory;
    private final Path localesDirectory;

    private BundleLayout(Path root, Path library, Path helper, Path resources, Path locales) {
        this.root = root;
        this.library = library;
        this.helper = helper;
        this.resourcesDirectory = resources;
        this.localesDirectory = locales;
    }

    /**
     * Works out the layout of the bundle installed at {@code root}.
     *
     * @throws InstallException if a piece is missing, naming what and where it looked
     */
    public static BundleLayout discover(Path root) {
        Objects.requireNonNull(root, "root");
        if (!Files.isDirectory(root)) {
            throw new InstallException("no browser runtime at " + root);
        }
        List<Path> files = listFiles(root);

        Path library = require(find(files, "libcef.dll", "libcef.so", "Chromium Embedded Framework"),
                "the Chromium library", root);
        Path helper = require(findHelper(files), "the helper process", root);
        Path resources = require(find(files, "icudtl.dat").map(Path::getParent),
                "the resources directory (the one holding icudtl.dat)", root);
        Path locales = findDirectory(files, "locales").orElse(null);

        return new BundleLayout(root, library, helper, resources, locales);
    }

    private static List<Path> listFiles(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.toList();
        } catch (IOException e) {
            throw new InstallException("could not read the browser runtime at " + root, e);
        }
    }

    private static Optional<Path> find(List<Path> files, String... names) {
        List<String> wanted = List.of(names);
        return files.stream()
                .filter(Files::isRegularFile)
                .filter(path -> wanted.contains(path.getFileName().toString()))
                .findFirst();
    }

    private static Optional<Path> findDirectory(List<Path> files, String name) {
        return files.stream()
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().equals(name))
                .findFirst();
    }

    /**
     * The subprocess Chromium spawns for renderers and GPU work.
     *
     * <p>On macOS it lives inside an app bundle and is named with a space in it, so it is
     * matched by prefix rather than by an exact name.
     */
    private static Optional<Path> findHelper(List<Path> files) {
        return files.stream()
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.equals("jcef_helper") || name.equals("jcef_helper.exe")
                            || (name.startsWith("jcef helper") && path.getParent() != null
                                && path.getParent().getFileName().toString().equals("MacOS"));
                })
                // A macOS bundle ships four helpers: the main one and three named for what
                // they do, "jcef Helper (GPU)" and friends. Chromium is given the main one and
                // derives the rest itself, so the plain name wins over any parenthesised one.
                .min((a, b) -> {
                    int byName = Boolean.compare(hasSuffix(a), hasSuffix(b));
                    return byName != 0 ? byName : Integer.compare(a.getNameCount(), b.getNameCount());
                });
    }

    private static boolean hasSuffix(Path helper) {
        return helper.getFileName().toString().contains("(");
    }

    private static Path require(Optional<Path> found, String what, Path root) {
        return found.orElseThrow(() -> new InstallException(
                "the browser runtime at " + root + " has no " + what + "; it is incomplete"));
    }

    public Path root() {
        return root;
    }

    /** The Chromium library, which has to be loaded before anything else in the bundle. */
    public Path library() {
        return library;
    }

    /**
     * The {@code .framework} directory, on the platform that has one.
     *
     * <p>macOS only. CEF has to be told where it is: left to work it out, java-cef builds the
     * path out of the library path property, which is a list of directories rather than the
     * one directory it assumes, and the result is a dlopen failure quoting a path with colons
     * in it.
     */
    public Optional<Path> frameworkDirectory() {
        for (Path part = library().getParent(); part != null; part = part.getParent()) {
            if (part.getFileName() != null && part.getFileName().toString().endsWith(".framework")) {
                return Optional.of(part);
            }
        }
        return Optional.empty();
    }

    /** What goes into {@code browser_subprocess_path}. */
    public Path helper() {
        return helper;
    }

    /**
     * The application bundle the helper lives in, on the platform that has one.
     *
     * <p>macOS only, and it is what Chromium is given as the main bundle. That reads oddly —
     * the main bundle of a game is not a browser helper — but it is how a bundle laid out flat
     * is made to work: Chromium derives where the framework and its libraries are from the
     * main bundle, and pointing it at a plain directory leaves the subprocesses looking for
     * the graphics libraries beside their own executable, where they are not.
     */
    public Optional<Path> helperBundle() {
        for (Path part = helper(); part != null; part = part.getParent()) {
            if (part.getFileName() != null && part.getFileName().toString().endsWith(".app")) {
                return Optional.of(part);
            }
        }
        return Optional.empty();
    }

    /** What goes into {@code resources_dir_path}. */
    public Path resourcesDirectory() {
        return resourcesDirectory;
    }

    /**
     * What goes into {@code locales_dir_path}, if the platform has one.
     *
     * <p>Empty on macOS, where the locale files live inside the framework's resources and CEF
     * finds them itself.
     */
    public Optional<Path> localesDirectory() {
        return Optional.ofNullable(localesDirectory);
    }

    /** A native library in the bundle, by its plain name, for example {@code jcef}. */
    public Optional<Path> nativeLibrary(String name) {
        return Stream.of(System.mapLibraryName(name), "lib" + name + ".so", name + ".dll")
                .map(root::resolve)
                .filter(Files::isRegularFile)
                .findFirst();
    }

    @Override
    public String toString() {
        return "BundleLayout[" + root + "]";
    }
}
