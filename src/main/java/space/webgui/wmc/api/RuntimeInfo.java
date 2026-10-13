package space.webgui.wmc.api;

/**
 * What is actually running, for logs and bug reports.
 *
 * @param cefVersion       the Chromium Embedded Framework version, or {@code "none"}
 * @param chromiumVersion  the Chromium version, or {@code "none"}
 * @param platform         normalised as {@code os-arch}, such as {@code windows-amd64}
 * @param runtimeDirectory where the binaries live, or empty when there are none
 */
public record RuntimeInfo(String cefVersion, String chromiumVersion, String platform, String runtimeDirectory) {

    static RuntimeInfo absent() {
        return new RuntimeInfo("none", "none", Platform.current(), "");
    }
}
