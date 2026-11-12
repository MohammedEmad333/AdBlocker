package com.example.adblocker;

import org.junit.Test;
import static org.junit.Assert.*;

public class DnsQueryStatisticsTest {
    @Test public void requestsAreNotCountedUntilAnAllowedAnswerIsRecorded() {
        DnsQueryStatistics stats = new DnsQueryStatistics();
        assertEquals(0, stats.successfulAllowedResponses());
        stats.recordAllowedResponse();
        assertEquals(1, stats.successfulAllowedResponses());
    }

    @Test public void newSessionClearsPriorSuccessfulAnswers() {
        DnsQueryStatistics stats = new DnsQueryStatistics();
        stats.recordAllowedResponse();
        stats.recordAllowedResponse();
        stats.reset();
        assertEquals(0, stats.successfulAllowedResponses());
    }

    @Test public void concurrentResponsesDoNotLoseUpdates() throws Exception {
        DnsQueryStatistics stats = new DnsQueryStatistics();
        Thread[] workers = new Thread[8];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) stats.recordAllowedResponse();
            });
            workers[i].start();
        }
        for (Thread worker : workers) worker.join();
        assertEquals(8000, stats.successfulAllowedResponses());
    }
}
