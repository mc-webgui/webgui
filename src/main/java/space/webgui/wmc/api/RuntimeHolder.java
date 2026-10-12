package space.webgui.wmc.api;

import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

/**
 * Finds the runtime implementation, and stands in for it when there is none.
 *
 * <p>A mod compiled against this module alone still runs: it sees a runtime reporting
 * {@link RuntimeState#UNAVAILABLE}. That beats a {@code NoClassDefFoundError} at an
 * arbitrary later moment, and it lets a mod treat "the browser is not installed" as an
 * ordinary state rather than a crash.
 */
final class RuntimeHolder {

    private static volatile WebRuntime current;

    private RuntimeHolder() {
    }

    static WebRuntime current() {
        WebRuntime found = current;
        if (found != null) {
            return found;
        }
        synchronized (RuntimeHolder.class) {
            if (current == null) {
                // Looked up through this class's own loader rather than the thread's. Under a
                // mod loader they are not the same one, and the implementation beside this
                // class is then rejected as "not a subtype" of the interface beside it —
                // which reads as no runtime being installed at all.
                current = ServiceLoader.load(WebRuntime.class, RuntimeHolder.class.getClassLoader())
                        .findFirst().orElseGet(Absent::new);
            }
            return current;
        }
    }

    private static final class Absent implements WebRuntime {

        private static final String WHY = "no wmc runtime is installed";

        @Override
        public RuntimeState state() {
            return RuntimeState.UNAVAILABLE;
        }

        @Override
        public CompletableFuture<Void> whenReady() {
            return CompletableFuture.failedFuture(new IllegalStateException(WHY));
        }

        @Override
        public CompletableFuture<WebView> open(WebViewSpec spec) {
            return CompletableFuture.failedFuture(new IllegalStateException(WHY));
        }

        @Override
        public void registerScheme(String scheme, SchemeHandler handler) {
            // Accepted and dropped: a mod registering schemes at start-up should not have to
            // branch on whether the runtime happens to be installed.
        }

        @Override
        public RuntimeInfo info() {
            return RuntimeInfo.absent();
        }

        @Override
        public void addListener(RuntimeListener listener) {
        }

        @Override
        public void removeListener(RuntimeListener listener) {
        }
    }
}
