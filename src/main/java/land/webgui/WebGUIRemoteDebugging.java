package land.webgui;

/**
 * Whether the real Chrome DevTools can reach this client.
 *
 * The full inspector needs Chromium's remote debugging port. Nothing in the browser
 * library sets it and it cannot be turned on once the browser has started — but on
 * Windows CEF builds its command line from the process command line, and the game passes
 * arguments it does not recognise straight through. So a single launch argument,
 * {@code --remote-debugging-port=9222}, is enough, and this class reports whether someone
 * used it.
 *
 * Reported rather than set, because by the time any mod runs the command line is fixed.
 */
public final class WebGUIRemoteDebugging {

    private static final String SWITCH = "--remote-debugging-port=";
    /** What the wiki tells people to use, and what the screen offers when it is off. */
    public static final int SUGGESTED_PORT = 9222;

    private static Integer cached;

    private WebGUIRemoteDebugging() {}

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
        int port = port();
        return port > 0 ? "http://127.0.0.1:" + port : "";
    }

    private static int findPort() {
        // Two sources because neither is reliable alone: the launcher's arguments are in
        // sun.java.command on every JVM that matters, while ProcessHandle can return
        // nothing at all on Windows.
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
