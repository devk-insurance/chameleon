package de.devk.chameleon.jmx.hostTags;

import java.util.concurrent.atomic.AtomicLong;

public class DatabaseMetrics implements DatabaseMetricsMBean {
    private final AtomicLong insertedOrUpdatedHostTags = new AtomicLong(0);
    private final AtomicLong queriedHostTags = new AtomicLong(0);
    private final AtomicLong removedHostTags = new AtomicLong(0);

    public void incrementInsertedOrUpdatedHostTags() {
        insertedOrUpdatedHostTags.incrementAndGet();
    }

    public void incrementQueriedHostTags() {
        queriedHostTags.incrementAndGet();
    }

    public void addRemovedHostTags(int numberOfRemovedHostTags) {
        removedHostTags.addAndGet(numberOfRemovedHostTags);
    }

    @Override
    public long getInsertedOrUpdatedHostTags() {
        return insertedOrUpdatedHostTags.get();
    }

    @Override
    public long getQueriedHostTags() {
        return queriedHostTags.get();
    }

    @Override
    public long getRemovedHostTags() {
        return removedHostTags.get();
    }
}
