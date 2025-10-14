package ir.ac.kntu.error;

import java.util.logging.Logger;

public class InvalidPhaseException extends RuntimeException {
    private static final Logger logger = Logger.getLogger(InvalidPhaseException.class.getName());

    public InvalidPhaseException(String message) {
        super(message);
        logger.severe(message);
    }

    public InvalidPhaseException(String message, Throwable cause) {
        super(message, cause);
        logger.severe(message + " | Cause: " + cause.getMessage());
    }
}
