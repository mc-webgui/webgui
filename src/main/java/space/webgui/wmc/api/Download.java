package space.webgui.wmc.api;

import java.nio.file.Path;

/**
 * A file the page is fetching, as it goes.
 *
 * <p>Reported while it runs and once more when it stops, so a mod can draw progress and say
 * where the file landed. A download that the person cancelled, or that failed, arrives with
 * {@link #canceled()} set: from the outside those are the same thing — a file that is not
 * there — and telling them apart would mean promising more than Chromium reports.
 *
 * @param id       Chromium's number for this download, stable for its lifetime, so that
 *                 several at once can be told apart
 * @param url      where it is coming from
 * @param name     what it will be called, which is not necessarily the last part of the url
 * @param path     where it is being written, or {@code null} until that has been decided
 * @param received bytes so far
 * @param total    bytes expected, or {@code -1} when the server did not say — which is common
 *                 enough that a progress bar has to cope with it
 */
public record Download(int id, String url, String name, Path path,
        long received, long total, boolean done, boolean canceled) {

    /** Whether it is still running. */
    public boolean inProgress() {
        return !done && !canceled;
    }

    /**
     * How far along, from zero to one, or {@code -1} when the size is not known.
     *
     * <p>Worth having here rather than in every mod: the "not known" case is the one people
     * forget, and a bar that shows a download as finished because it divided by minus one is
     * worse than one that shows nothing.
     */
    public double progress() {
        if (done) {
            return 1;
        }
        return total > 0 ? Math.min(1, (double) received / total) : -1;
    }
}
