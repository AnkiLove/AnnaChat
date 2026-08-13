package dev.annachat.service;

import dev.annachat.api.MessageProcessor;
import dev.annachat.api.context.ChatContext;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ProcessorService {
    private final List<MessageProcessor> processors = new CopyOnWriteArrayList<>();

    public void register(MessageProcessor processor) {
        processors.add(processor);
    }

    public void unregister(MessageProcessor processor) {
        processors.remove(processor);
    }

    public void process(ChatContext context) {
        List<MessageProcessor> ordered = processors.stream()
                .sorted(Comparator.comparingInt(MessageProcessor::priority))
                .toList();
        String message = context.message();
        for (MessageProcessor processor : ordered) {
            message = processor.process(context, message);
            if (message == null) {
                context.cancelled(true);
                return;
            }
        }
        context.message(message);
    }
}
