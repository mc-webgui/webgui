package space.webgui.wmc.core;

import java.io.IOException;
import java.io.InputStream;

/**
 * A source was asked to resume and answered with the whole archive anyway.
 *
 * <p>Servers are allowed to ignore a range request, and some proxies do it without saying so.
 * The caller has to be told, because appending a whole file onto the bytes it already has
 * produces an archive with its first megabytes twice — which then fails a checksum for a
 * reason that looks nothing like the cause.
 *
 * <p>The stream is carried along so the caller can start over from it rather than ask again.
 */
public final class RangeIgnoredException extends IOException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final transient InputStream whole;

    public RangeIgnoredException(InputStream whole) {
        super("the source sent the whole archive when asked for a range");
        this.whole = whole;
    }

    /** The archive from the start. */
    public InputStream whole() {
        return whole;
    }
}
