package de.devk.chameleon.jmx.client;

import java.util.concurrent.atomic.AtomicLong;

public class ClientTcpConnectionMetrics implements ClientTcpConnectionMetricsMBean {

    private final String remoteAddress;

    private final AtomicLong incomingLines = new AtomicLong(0);

    public ClientTcpConnectionMetrics(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    @Override
    public String getRemoteAddress() {
        return remoteAddress;
    }

    @Override
    public long getIncomingLines() {
        return incomingLines.get();
    }

    public void incrementIncomingLines() {
        incomingLines.incrementAndGet();
    }


}
