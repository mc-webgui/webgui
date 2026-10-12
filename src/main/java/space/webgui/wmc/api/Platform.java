package space.webgui.wmc.api;

import java.util.Locale;

/** Normalises what the JVM reports into the names the binaries are published under. */
public final class Platform {

    private Platform() {
    }

    /** For example {@code windows-amd64}, {@code linux-arm64}, {@code macos-arm64}. */
    public static String current() {
        return operatingSystem() + "-" + architecture();
    }

    static String operatingSystem() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return "windows";
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return "macos";
        }
        return "linux";
    }

    static String architecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64" -> "amd64";
            case "aarch64", "arm64" -> "arm64";
            case "x86", "i386", "i486", "i586", "i686" -> "i386";
            default -> arch;
        };
    }
}
