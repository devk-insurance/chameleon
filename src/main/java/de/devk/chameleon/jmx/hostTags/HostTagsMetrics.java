package de.devk.chameleon.jmx.hostTags;

import java.util.concurrent.atomic.AtomicLong;

public class HostTagsMetrics implements HostTagsMetricsMBean {
    private final AtomicLong updatedHosts = new AtomicLong(0);
    private final AtomicLong updatesTags = new AtomicLong(0);

    public void addUpdatedHostTags(int numberOfHosts, int numberOfTags) {
        updatedHosts.addAndGet(numberOfHosts);
        updatesTags.addAndGet(numberOfTags);
    }

    @Override
    public long getUpdatedHosts() {
        return updatedHosts.get();
    }

    @Override
    public long getUpdatedTags() {
        return updatesTags.get();
    }
}
