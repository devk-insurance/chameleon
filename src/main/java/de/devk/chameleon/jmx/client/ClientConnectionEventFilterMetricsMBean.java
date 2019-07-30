package de.devk.chameleon.jmx.client;

public interface ClientConnectionEventFilterMetricsMBean {

    String getRemoteAddress();

    long getValidLines();
    long getBadLines();
}
