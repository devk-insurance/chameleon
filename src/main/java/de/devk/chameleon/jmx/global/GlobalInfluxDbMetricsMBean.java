package de.devk.chameleon.jmx.global;

public interface GlobalInfluxDbMetricsMBean {

    long getHttpRequests();
    long getSuccessfulHttpResponses();
    long getErrorHttpResponses();
}
