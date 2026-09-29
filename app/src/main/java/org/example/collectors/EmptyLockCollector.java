package org.example.collectors;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EmptyLockCollector implements MetricsCollector {
    private static final int NUMBER_OF_BUCKETS = 255;

    private final long[] buckets;
    private long count, min, max, sum;
    private final Lock lock;

    public EmptyLockCollector() {
        buckets = new long[NUMBER_OF_BUCKETS];
        count = 0;
        min = Long.MAX_VALUE;
        max = Long.MIN_VALUE;
        lock = new ReentrantLock();
    }

    @Override
    public void record(long value) {
        lock.lock();
        lock.unlock();
    }

    @Override
    public Snapshot snapshot() {
        return new Snapshot(buckets, count, sum, min, max, 0, 0);
    }
}
