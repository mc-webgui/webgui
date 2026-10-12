package space.webgui.wmc.core;

import java.io.Serial;

/** The browser could not be installed, with a reason a player can act on. */
public class InstallException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InstallException(String message) {
        super(message);
    }

    public InstallException(String message, Throwable cause) {
        super(message, cause);
    }
}
