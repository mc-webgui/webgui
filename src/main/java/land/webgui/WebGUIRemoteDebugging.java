package land.webgui;

/**
 * Whether the real Chrome DevTools can reach this client.
 *
 * CEF builds its command line from the process command line, and the game passes
 * arguments it does not recognise straight through, so {@code --remote-debugging-port}
 * on the game's own command line is enough to get the full inspector.
 *
 * The engine is ours, so the mod can also choose the port itself when the settings screen
 * asks for the tools; {@link #inUse()} is the one Chromium actually got, whichever way.
 */
public final class WebGUIRemoteDebugging {

    private static final String SWITCH = "--remote-debugging-port=";
    /** What the wiki tells people to use, and what the screen offers when it is off. */
    public static final int SUGGESTED_PORT = 9222;

    private static Integer cached;

    /** The port Chromium was started with, which is not known until it starts. */
    private static volatile int inUse;

    private WebGUIRemoteDebugging() {}

    /** Told by the boot once Chromium has been configured. */
    public static void started(int port) {
        inUse = Math.max(0, port);
    }

    /** The port the inspector is actually on, or 0 while there is none. */
    public static int inUse() {
        return inUse;
    }

    public static boolean reachable() {
        return inUse > 0;
    }

    /** The port the inspector is listening on, or 0 when the argument was not given. */
    public static synchronized int port() {
        if (cached == null) {
            cached = findPort();
        }
        return cached;
    }

    public static boolean enabled() {
        return port() > 0;
    }

    /** The address to open in a browser, or an empty string when it is off. */
    public static String address() {
        return inUse > 0 ? "http://127.0.0.1:" + inUse : "";
    }

    private static int findPort() {
        // Both sources: ProcessHandle returns nothing at all on some platforms.
        int fromCommand = parse(System.getProperty("sun.java.command", ""));
        if (fromCommand > 0) {
            return fromCommand;
        }
        try {
            return ProcessHandle.current().info().arguments()
                    .map(args -> parse(String.join(" ", args)))
                    .orElse(0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int parse(String commandLine) {
        int at = commandLine.indexOf(SWITCH);
        if (at < 0) {
            return 0;
        }
        int from = at + SWITCH.length();
        int to = from;
        while (to < commandLine.length() && Character.isDigit(commandLine.charAt(to))) {
            to++;
        }
        if (to == from) {
            return 0;
        }
        try {
            int port = Integer.parseInt(commandLine.substring(from, to));
            return port > 0 && port <= 65535 ? port : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
