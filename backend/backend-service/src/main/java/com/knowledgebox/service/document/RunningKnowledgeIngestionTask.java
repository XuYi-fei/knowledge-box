package com.knowledgebox.service.document;

import java.util.concurrent.atomic.AtomicBoolean;

final class RunningKnowledgeIngestionTask {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile Thread worker;

    void bind(Thread worker) {
        this.worker = worker;
    }

    void cancel() {
        cancelled.set(true);
        Thread boundWorker = worker;
        if (boundWorker != null) {
            boundWorker.interrupt();
        }
    }

    boolean isCancelled() {
        return cancelled.get();
    }
}
