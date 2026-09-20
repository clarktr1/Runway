package com.runway.api.executions;

import java.util.concurrent.atomic.AtomicBoolean;

public class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Runnable target;

    public synchronized void bind(Runnable cancellable) {
        this.target = cancellable;
        if (cancelled.get()) {
            cancellable.run();
        }
    }

    public synchronized void cancel() {
        if (cancelled.compareAndSet(false, true) && target != null) {
            target.run();
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
