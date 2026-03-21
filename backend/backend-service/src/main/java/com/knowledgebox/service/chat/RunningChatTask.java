package com.knowledgebox.service.chat;

import java.util.concurrent.atomic.AtomicBoolean;

final class RunningChatTask {

    private final Long userId;
    private final String sessionId;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean cancelledByUser = new AtomicBoolean(false);
    private volatile Thread worker;

    RunningChatTask(Long userId, String sessionId) {
        this.userId = userId;
        this.sessionId = sessionId;
    }

    void bind(Thread worker) {
        this.worker = worker;
    }

    void cancel() {
        this.cancelled.set(true);
    }

    void cancelByUser() {
        this.cancelled.set(true);
        this.cancelledByUser.set(true);
    }

    boolean isCancelled() {
        return cancelled.get();
    }

    boolean cancelledByUser() {
        return cancelledByUser.get();
    }

    Long userId() {
        return userId;
    }

    String sessionId() {
        return sessionId;
    }

    Thread worker() {
        return worker;
    }
}
