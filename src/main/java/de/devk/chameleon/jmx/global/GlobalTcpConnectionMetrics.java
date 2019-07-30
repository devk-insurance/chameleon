package de.devk.chameleon.jmx.global;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class GlobalTcpConnectionMetrics implements GlobalTcpConnectionMetricsMBean {

    private final AtomicLong establishedConnections = new AtomicLong(0);
    private final AtomicLong closedConnections = new AtomicLong(0);
    private final AtomicLong incomingLines = new AtomicLong(0);
    
    private final AtomicInteger currentlyEstablishedConnections = new AtomicInteger(0);


    @Override
    public long getEstablishedConnections() {
        return establishedConnections.get();
    }

    public void incrementEstablishedConnections() {
        establishedConnections.incrementAndGet();
    }

    @Override
    public long getClosedConnections() {
        return closedConnections.get();
    }

    public void incrementClosedConnections() {
        closedConnections.incrementAndGet();
    }

    @Override
    public long getIncomingLines() {
        return incomingLines.get();
    }

    public void incrementIncomingLines() {
        incomingLines.incrementAndGet();
    }

    public int getCurrentlyEstablishedConnections() {
        return currentlyEstablishedConnections.get();
    }
    
    public void incrementCurrentlyEstablishedConnections() {
        currentlyEstablishedConnections.incrementAndGet();
    }

    public void decrementCurrentlyEstablishedConnections() {
        currentlyEstablishedConnections.decrementAndGet();
    }

}
