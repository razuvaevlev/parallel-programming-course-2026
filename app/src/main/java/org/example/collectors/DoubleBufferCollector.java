package org.example.collectors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleBufferCollector implements MetricsCollector {
    private static final int NUMBER_OF_BUCKETS = 255;
    private static final int BUCKET_WIDTH = 4;
    private long globalCount;
    private long globalSum;
    private long globalMin;
    private long globalMax;
    private long[] globalBuckets;

    private volatile int active = 0;

    private final Object snapLock = new Object();

    public DoubleBufferCollector() {
        active = 0;
        globalCount = 0;
        globalMax = 0;
        globalMin = Long.MAX_VALUE;
        globalBuckets = new long[NUMBER_OF_BUCKETS];
    }

    private static final class ThreadBuffers {
        final long[][] buckets = new long[2][NUMBER_OF_BUCKETS];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = { Long.MAX_VALUE, Long.MAX_VALUE };
        final long[] max = { 0, 0 };

        final AtomicInteger inside = new AtomicInteger(-1);
    }

    private final List<ThreadBuffers> allBuffers = new ArrayList<>();

    private final ThreadLocal<ThreadBuffers> myBuffers = ThreadLocal.withInitial(() -> {
        final ThreadBuffers s = new ThreadBuffers();
        synchronized (snapLock) {
            allBuffers.add(s);
        }
        return s;
    });

    @Override
    public void record(long value) {
        var my = myBuffers.get();
        int b;
        // int b = active;
        // my.inside.set(b);
        while (true) {
            b = active;
            my.inside.set(b);
            if (active == b)
                break;
            my.inside.setRelease(-1);
        }
        var bucket = (int) Math.min(value / 4, NUMBER_OF_BUCKETS - 1);
        my.buckets[b][bucket]++;
        my.count[b]++;
        my.sum[b] += value;
        my.min[b] = Long.min(my.min[b], value);
        my.max[b] = Long.max(my.max[b], value);

        my.inside.setRelease(-1);
    }

    private static long percentile(long[] buckets, long count, double percentile) {
        if (count == 0)
            return 0;

        long threshold = (long) Math.ceil(count * percentile);
        long cumulative = 0;

        for (int i = 0; i < NUMBER_OF_BUCKETS; i++) {
            cumulative += buckets[i];
            if (cumulative >= threshold) {
                return (i + 1) * BUCKET_WIDTH;
            }
        }
        return NUMBER_OF_BUCKETS * BUCKET_WIDTH;
    }

    @Override
    public Snapshot snapshot() {
        long[] bucketsCopy;
        long count, sum, min, max;
        synchronized (snapLock) {
            int old = active;
            active = 1 - old;
            for (ThreadBuffers threadBuffers : allBuffers) {
                while (threadBuffers.inside.get() == old) {
                    Thread.onSpinWait();
                }

                globalCount += threadBuffers.count[old];
                globalSum += threadBuffers.sum[old];
                for (int i = 0; i < NUMBER_OF_BUCKETS; i++) {
                    globalBuckets[i] += threadBuffers.buckets[old][i];
                }

                globalMin = Long.min(globalMin, threadBuffers.min[old]);
                globalMax = Long.max(globalMax, threadBuffers.max[old]);

                threadBuffers.count[old] = 0;
                threadBuffers.sum[old] = 0;
                Arrays.fill(threadBuffers.buckets[old], 0);
            }
            count = globalCount;
            sum = globalSum;
            min = globalMin;
            max = globalMax;
            bucketsCopy = globalBuckets.clone();
        }

        return new Snapshot(bucketsCopy, count, sum, min, max,
                percentile(bucketsCopy, count, 0.5),
                percentile(bucketsCopy, count, 0.99));
    }
}
