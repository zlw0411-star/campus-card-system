package com.campuscard.ui.components;

import com.campuscard.dto.SessionContext;

import javax.swing.SwingWorker;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Runs application-service work on a background thread and drops stale UI results. */
public final class SessionWorker {
    private SessionWorker() { }

    @FunctionalInterface public interface Work<T> { T call(SessionContext session) throws Exception; }

    public static <T> void execute(SessionContext captured, BooleanSupplier stillCurrent, Work<T> work,
                                   Consumer<T> success, Consumer<Exception> failure, Runnable cleanup) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception {
                if (!stillCurrent.getAsBoolean() || captured == null) throw new CancellationException();
                return work.call(captured);
            }

            @Override protected void done() {
                cleanup.run();
                if (!stillCurrent.getAsBoolean()) return;
                try { success.accept(get()); }
                catch (Exception ex) { failure.accept(ex); }
            }
        }.execute();
    }
}
