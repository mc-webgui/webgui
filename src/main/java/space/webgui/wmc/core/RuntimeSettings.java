package space.webgui.wmc.core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * What can be varied about the runtime, in plain types.
 *
 * <p>Named here rather than left as arguments scattered through the start-up path because
 * these are the things a person changes: a port to attach a debugger to, how loud Chromium is
 * in the log, whether a page may start playing sound on its own. The Minecraft side turns a
 * config file into one of these and hands it over; nothing else needs to know a config file
 * exists.
 *
 * <p>Deliberately free of Chromium types, so that the module holding the config never has to
 * name one.
 */
public record RuntimeSettings(
        Path gameDirectory,
        int remoteDebuggingPort,
        boolean openDevtoolsForEveryView,
        boolean allowAutoplay,
        LogLevel logLevel,
        List<String> extraArguments,
        Consumer<String> urlOpener,
        Downloads downloads) {

    /**
     * What happens when a page downloads a file.
     *
     * @param directory     where files are put, or {@code null} for the one beside the browser
     *                      cache — inside the game directory, so that two instances do not
     *                      write over each other and nothing lands in the player's Downloads
     *                      folder unasked
     * @param askWhereToPut whether the person chooses the place each time. Chromium shows the
     *                      system's own save dialog for this, the same one it opens for a file
     *                      input, so there is no window for this project to build
     */
    public record Downloads(Path directory, boolean askWhereToPut) {

        /** Into the game's own folder, without asking. */
        public static Downloads defaults() {
            return new Downloads(null, false);
        }

        public Downloads into(Path where) {
            return new Downloads(where, askWhereToPut);
        }

        public Downloads asking(boolean ask) {
            return new Downloads(directory, ask);
        }

        /** Where a file should go, given where the game is. */
        Path directoryIn(Path gameDirectory) {
            return directory != null ? directory
                    : gameDirectory.resolve(".wmc").resolve("downloads");
        }
    }

    /** How much Chromium writes to the log. */
    public enum LogLevel {
        VERBOSE,
        INFO,
        WARNING,
        ERROR,
        OFF
    }

    public RuntimeSettings {
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        Objects.requireNonNull(logLevel, "logLevel");
        Objects.requireNonNull(urlOpener, "urlOpener");
        Objects.requireNonNull(downloads, "downloads");
        if (remoteDebuggingPort < 0 || remoteDebuggingPort > 65535) {
            throw new IllegalArgumentException("the debugging port must be 0..65535, got " + remoteDebuggingPort);
        }
        extraArguments = List.copyOf(extraArguments);
    }

    /** The working directory, nothing exposed, warnings only. */
    public static RuntimeSettings defaults() {
        return new RuntimeSettings(Paths.get("."), 0, false, false, LogLevel.WARNING,
                List.of(), RuntimeSettings::openInSystemBrowser, Downloads.defaults());
    }

    public RuntimeSettings gameDirectory(Path directory) {
        return new RuntimeSettings(directory, remoteDebuggingPort, openDevtoolsForEveryView,
                allowAutoplay, logLevel, extraArguments, urlOpener, downloads);
    }

    /**
     * The port Chromium listens on for a debugger, or zero for none.
     *
     * <p>It is a port on the loopback interface with no authentication, which is how the
     * protocol works and why it is off unless asked for: anything able to reach it can read
     * and drive every page the game has open.
     */
    public RuntimeSettings remoteDebuggingPort(int port) {
        return new RuntimeSettings(gameDirectory, port, openDevtoolsForEveryView,
                allowAutoplay, logLevel, extraArguments, urlOpener, downloads);
    }

    /** Whether opening a view also opens the developer tools for it. */
    public RuntimeSettings openDevtoolsForEveryView(boolean open) {
        return new RuntimeSettings(gameDirectory, remoteDebuggingPort, open,
                allowAutoplay, logLevel, extraArguments, urlOpener, downloads);
    }

    public RuntimeSettings allowAutoplay(boolean allow) {
        return new RuntimeSettings(gameDirectory, remoteDebuggingPort, openDevtoolsForEveryView,
                allow, logLevel, extraArguments, urlOpener, downloads);
    }

    public RuntimeSettings logLevel(LogLevel level) {
        return new RuntimeSettings(gameDirectory, remoteDebuggingPort, openDevtoolsForEveryView,
                allowAutoplay, level, extraArguments, urlOpener, downloads);
    }

    public RuntimeSettings extraArguments(List<String> arguments) {
        return new RuntimeSettings(gameDirectory, remoteDebuggingPort, openDevtoolsForEveryView,
                allowAutoplay, logLevel, arguments, urlOpener, downloads);
    }

    /**
     * How a link is opened outside the game, for the developer tools window.
     *
     * <p>Replaceable because a game knows how to do this better than a plain library does:
     * Minecraft has its own opener that behaves on every platform it supports.
     */
    public RuntimeSettings urlOpener(Consumer<String> opener) {
        return new RuntimeSettings(gameDirectory, remoteDebuggingPort, openDevtoolsForEveryView,
                allowAutoplay, logLevel, extraArguments, opener, downloads);
    }

    /** Where files a page downloads are put, and whether the person is asked each time. */
    public RuntimeSettings downloads(Downloads policy) {
        return new RuntimeSettings(gameDirectory, remoteDebuggingPort, openDevtoolsForEveryView,
                allowAutoplay, logLevel, extraArguments, urlOpener, policy);
    }

    /** Where a download should be written, with the default worked out. */
    public Path downloadDirectory() {
        return downloads.directoryIn(gameDirectory);
    }

    /**
     * Opens an address in whatever browser the person uses.
     *
     * <p>Not through AWT's desktop, which was tried and is the wrong tool here: a game sets
     * {@code java.awt.headless} on the way up, and a headless toolkit reports that opening a
     * browser is unsupported. The check then failed quietly and the method did nothing at all,
     * which is how the developer tools came to be "turned on" and never appear.
     *
     * <p>So the command the platform already has, which needs no toolkit. It is asked for
     * without waiting: opening a browser can take seconds, and nothing here should hold up
     * whoever pressed the key.
     */
    private static void openInSystemBrowser(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        List<String> command;
        if (os.contains("win")) {
            // Through the shell's own opener rather than "start", which is a builtin and needs
            // a shell; the empty argument is the window title start would otherwise eat.
            command = List.of("rundll32", "url.dll,FileProtocolHandler", url);
        } else if (os.contains("mac")) {
            command = List.of("open", url);
        } else {
            command = List.of("xdg-open", url);
        }
        try {
            new ProcessBuilder(command).start();
        } catch (Exception opening) {
            // Not being able to open a browser is not a reason to fail whatever asked for it,
            // but it is a reason to say so: the address is the only way in, and someone who
            // was told nothing would look for the fault in the port.
            System.err.println("wmc: could not open " + url + " (" + opening + "). Open it by hand.");
        }
    }
}
