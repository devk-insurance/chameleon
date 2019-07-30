package de.devk.chameleon.jmx.global;

public interface GlobalTcpConnectionMetricsMBean {

    long getEstablishedConnections();
    long getClosedConnections();
    long getIncomingLines();

    int getCurrentlyEstablishedConnections();
}
