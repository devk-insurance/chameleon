package de.devk.chameleon.jmx.client;

public interface ClientTcpConnectionMetricsMBean {

    String getRemoteAddress();
    long getIncomingLines();
}
