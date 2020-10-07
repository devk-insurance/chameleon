package de.devk.chameleon.jmx.hostTags;

public interface DatabaseMetricsMBean {

    long getInsertedOrUpdatedHostTags();
    long getQueriedHostTags();
    long getRemovedHostTags();
}
