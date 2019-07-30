package de.devk.chameleon.jmx.client;

import java.util.concurrent.atomic.AtomicLong;

public class ClientConnectionEventFilterMetrics implements ClientConnectionEventFilterMetricsMBean {

    private final String remoteAddress;

    private final AtomicLong validLines = new AtomicLong(0);
    private final AtomicLong badLines = new AtomicLong(0);

    public ClientConnectionEventFilterMetrics(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    @Override
    public String getRemoteAddress() {
        return remoteAddress;
    }

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
