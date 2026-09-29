package org.example.collectors;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class LockStripingCollector implements MetricsCollector {

    private static final int NUMBER_OF_BUCKETS = 256;
    private static final int BUCKET_WIDTH = 4;

    private static final int STRIPES = 16;
    private static final int STRIPE_MASK = STRIPES - 1;

    private final long[] buckets = new long[NUMBER_OF_BUCKETS];
    private final ReentrantLock[] locks = new ReentrantLock[STRIPES];

    private final AtomicLong count = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);

    public LockStripingCollector() {
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    @Override
    public void record(long value) {
        int bucket = (int) Math.min(value / BUCKET_WIDTH, NUMBER_OF_BUCKETS - 1);
        int stripe = bucket & STRIPE_MASK;

        locks[stripe].lock();
        try {
            buckets[bucket]++;
        } finally {
            locks[stripe].unlock();
        }

        count.incrementAndGet();
        sum.addAndGet(value);
        updateMin(value);
        updateMax(value);
    }

    private void updateMin(long value) {
        long cur;
        while ((cur = min.get()) > value) {
            if (min.compareAndSet(cur, value))
                return;
        }
    }

    private void updateMax(long value) {
        long cur;
        while ((cur = max.get()) < value) {
            if (max.compareAndSet(cur, value))
                return;
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] copy = new long[NUMBER_OF_BUCKETS];

        for (int s = 0; s < STRIPES; s++) {
            locks[s].lock();
            try {
                for (int b = s; b < NUMBER_OF_BUCKETS; b += STRIPES) {
                    copy[b] = buckets[b];
                }
            } finally {
                locks[s].unlock();
            }
        }

        long c = count.get();
        long mn = min.get();
        long mx = max.get();
        long sm = sum.get();

        if (c == 0) {
            mn = 0;
            mx = 0;
        }

        long p50 = percentile(copy, c, 0.50);
        long p99 = percentile(copy, c, 0.99);

        return new Snapshot(copy, c, sm, mn, mx, p50, p99);
    }

    private static long percentile(long[] buckets, long count, double percentile) {
        if (count == 0)
            return 0;

        long threshold = (long) Math.ceil(count * percentile);
        long cumulative = 0;

        for (int i = 0; i < buckets.length; i++) {
            cumulative += buckets[i];
            if (cumulative >= threshold) {
                return (i + 1) * (long) BUCKET_WIDTH;
            }
        }
        return (long) buckets.length * BUCKET_WIDTH;
    }
}
