package space.webgui.wmc.api;

/**
 * Start-up as it happens.
 *
 * <p>Worth listening to for one reason: the first run downloads a few hundred megabytes, and
 * a game that appears frozen for two minutes is a bug report. Everything here is optional.
 */
public interface RuntimeListener {

    /** The runtime moved between states. */
    default void onState(RuntimeState state) {
    }

    /**
     * Download progress.
     *
     * @param received bytes so far
     * @param total    bytes expected, or {@code -1} when the server did not say
     */
    default void onDownload(long received, long total) {
    }

    /** Start-up failed. The runtime stays in {@link RuntimeState#FAILED}. */
    default void onFailure(Throwable cause) {
    }
}
