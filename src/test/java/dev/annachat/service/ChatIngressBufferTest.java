package dev.annachat.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class ChatIngressBufferTest {
    @Test
    void paperEventClaimsMessageOnlyOnce() {
        ChatIngressBuffer buffer = new ChatIngressBuffer();
        ChatIngressBuffer.PendingMessage message = pending("!hello", "global", "hello", "!");

        assertTrue(buffer.add(message));
        assertSame(message, buffer.claimForPaper("!hello"));
        assertFalse(buffer.claim(message));
    }

    @Test
    void paperEventMatchesAlreadyStrippedPrefix() {
        ChatIngressBuffer buffer = new ChatIngressBuffer();
        ChatIngressBuffer.PendingMessage message = pending("#hello", "friends", "hello", "#");

        assertTrue(buffer.add(message));
        assertSame(message, buffer.claimForPaper("hello"));
    }

    @Test
    void rapidSecondMessageDoesNotStealCancelledFirstMessage() {
        ChatIngressBuffer buffer = new ChatIngressBuffer();
        ChatIngressBuffer.PendingMessage first = pending("first", null, "first", null);
        ChatIngressBuffer.PendingMessage second = pending("second", null, "second", null);

        assertTrue(buffer.add(first));
        assertTrue(buffer.add(second));
        assertSame(second, buffer.claimForPaper("second"));
        assertTrue(buffer.claim(first));
    }

    @Test
    void identicalRapidMessageClaimsNewestCapture() {
        ChatIngressBuffer buffer = new ChatIngressBuffer();
        ChatIngressBuffer.PendingMessage first = pending("same", null, "same", null);
        ChatIngressBuffer.PendingMessage second = pending("same", null, "same", null);

        assertTrue(buffer.add(first));
        assertTrue(buffer.add(second));
        assertSame(second, buffer.claimForPaper("same"));
        assertTrue(buffer.claim(first));
    }

    @Test
    void aSinglePendingMessageAcceptsDownstreamTextChanges() {
        ChatIngressBuffer buffer = new ChatIngressBuffer();
        ChatIngressBuffer.PendingMessage message = pending("hello", null, "hello", null);

        assertTrue(buffer.add(message));
        assertSame(message, buffer.claimForPaper("HELLO"));
    }

    @Test
    void closingBufferRejectsLateFallback() {
        ChatIngressBuffer buffer = new ChatIngressBuffer();
        ChatIngressBuffer.PendingMessage message = pending("hello", null, "hello", null);

        assertTrue(buffer.add(message));
        buffer.close();
        assertFalse(buffer.claim(message));
        assertFalse(buffer.add(message));
    }

    @Test
    void paperAndFoliaFallbackCannotClaimTheSameMessage() throws Exception {
        ChatIngressBuffer buffer = new ChatIngressBuffer();
        ChatIngressBuffer.PendingMessage message = pending("hello", null, "hello", null);
        CountDownLatch start = new CountDownLatch(1);
        assertTrue(buffer.add(message));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> paper = executor.submit(() -> {
                start.await();
                return buffer.claimForPaper("hello") != null;
            });
            Future<Boolean> fallback = executor.submit(() -> {
                start.await();
                return buffer.claim(message);
            });
            start.countDown();

            assertNotEquals(paper.get(), fallback.get());
        }
    }

    private static ChatIngressBuffer.PendingMessage pending(String original, String channel,
                                                             String message, String prefix) {
        return new ChatIngressBuffer.PendingMessage(1L, "Anna", original, channel, message, prefix);
    }
}
