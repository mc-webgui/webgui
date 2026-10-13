package space.webgui.wmc.api;

/** Where the runtime is in its life. */
public enum RuntimeState {

    /** No implementation is installed. Views cannot be opened and never will be. */
    UNAVAILABLE,

    /** Installed, not asked to start yet. */
    IDLE,

    /** Fetching the Chromium build for this platform; progress arrives via {@link RuntimeListener}. */
    DOWNLOADING,

    /** Binaries are in place and Chromium is coming up. */
    STARTING,

    /** Views can be opened. */
    READY,

    /** Start-up failed; the reason is on {@link WebRuntime#whenReady()}. */
    FAILED
}
