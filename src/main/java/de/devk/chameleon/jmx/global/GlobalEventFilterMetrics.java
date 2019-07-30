package de.devk.chameleon.jmx.global;

import java.util.concurrent.atomic.AtomicLong;

public class GlobalEventFilterMetrics implements GlobalEventFilterMetricsMBean {

    private final AtomicLong validLines = new AtomicLong(0);
    private final AtomicLong badLines = new AtomicLong(0);

    @Override
    public long getValidLines() {
        return validLines.get();
    }

    public void incrementValidIncomingLines() {
        validLines.incrementAndGet();
    }

    @Override
    public long getBadLines() {
        return badLines.get();
    }

    public void incrementBadIncomingLines() {
        badLines.incrementAndGet();
    }

}
