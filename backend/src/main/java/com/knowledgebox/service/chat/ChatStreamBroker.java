package com.knowledgebox.service.chat;

import com.knowledgebox.api.ChatStreamEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class ChatStreamBroker {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String messageCode) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(messageCode, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(messageCode, emitter));
        emitter.onTimeout(() -> remove(messageCode, emitter));
        emitter.onError(exception -> remove(messageCode, emitter));
        return emitter;
    }

    public void publish(String messageCode, String eventName, ChatStreamEvent event) {
        List<SseEmitter> subscribers = emitters.get(messageCode);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        subscribers.removeIf(emitter -> !send(emitter, eventName, event));
        if (subscribers.isEmpty()) {
            emitters.remove(messageCode);
        }
    }

    public void complete(String messageCode) {
        List<SseEmitter> subscribers = emitters.remove(messageCode);
        if (subscribers == null) {
            return;
        }
        subscribers.forEach(SseEmitter::complete);
    }

    private boolean send(SseEmitter emitter, String eventName, ChatStreamEvent event) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(event));
            return true;
        } catch (IOException exception) {
            emitter.completeWithError(exception);
            return false;
        }
    }

    private void remove(String messageCode, SseEmitter emitter) {
        List<SseEmitter> subscribers = emitters.get(messageCode);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(emitter);
        if (subscribers.isEmpty()) {
            emitters.remove(messageCode);
        }
    }
}
