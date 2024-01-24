package ir.ac.kntu.SynchronousTransmission.applications;

import ir.ac.kntu.SynchronousTransmission.NullApplication;
import ir.ac.kntu.SynchronousTransmission.StApplication;

import java.util.Objects;

/**
 * This class is the default implementation of {@link StApplication} interface
 * and does nothing.
 */
public abstract class BaseApplication implements StApplication {

    protected StApplication next = new NullApplication();

    public StApplication next() {
        return next;
    }

    @Override
    public void addNextApplication(StApplication application) {
        Objects.requireNonNull(application);

        next = application;
    }
}

