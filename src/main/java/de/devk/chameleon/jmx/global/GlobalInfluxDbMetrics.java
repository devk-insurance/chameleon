package de.devk.chameleon.jmx.global;

import java.util.concurrent.atomic.AtomicLong;

public class GlobalInfluxDbMetrics implements GlobalInfluxDbMetricsMBean {

    private final AtomicLong httpRequests = new AtomicLong(0);
    private final AtomicLong successfulHttpResponses = new AtomicLong(0);
    private final AtomicLong errorHttpResponses = new AtomicLong(0);

    @Override
    public long getHttpRequests() {
        return httpRequests.get();
    }

    public void incrementHttpRequests() {
        httpRequests.incrementAndGet();
    }

    @Override
    public long getSuccessfulHttpResponses() {
        return successfulHttpResponses.get();
    }

    public void incrementSuccessfulHttpResponses() {
        successfulHttpResponses.incrementAndGet();
    }

    @Override
    public long getErrorHttpResponses() {
        return errorHttpResponses.get();
    }

    public void incrementErrorHttpResponses() {
        errorHttpResponses.incrementAndGet();
    }


}
