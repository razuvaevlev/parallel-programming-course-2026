package org.example.collectors;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ThreadLocalCollector implements MetricsCollector {
    private static final int NUMBER_OF_BUCKETS = 255;
    private static final int BUCKET_WIDTH = 4;

    public static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(NUMBER_OF_BUCKETS);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        final ThreadState s = new ThreadState();
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });

    @Override
    public void record(final long value) {
        final ThreadState s = myState.get();
        final int b = (int) Math.min(value / 4, NUMBER_OF_BUCKETS - 1);

        s.buckets.setRelease(b, s.buckets.getPlain(b) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        if (value < s.min.getPlain())
            s.min.setRelease(value);
        if (value > s.max.getPlain())
            s.max.setRelease(value);
    }

    private static long percentile(final long[] buckets, final long count, final double percentile) {
        if (count == 0)
            return 0;

        final long threshold = (long) Math.ceil(count * percentile);
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
        synchronized (listLock) {
            var copyOfStates = new ArrayList<>(allStates);
            final long[] out = new long[NUMBER_OF_BUCKETS];
            long count = 0, sum = 0, min = Long.MAX_VALUE, max = 0;

            for (final ThreadState s : copyOfStates) {
                for (int i = 0; i < NUMBER_OF_BUCKETS; i++)
                    out[i] += s.buckets.get(i); // C++: .load(relaxed)
                count += s.count.get();

                sum += s.sum.get();
                min = Math.min(min, s.min.get());
                max = Math.max(max, s.max.get());
            }
            final long p50 = percentile(out, count, 0.50);
            final long p99 = percentile(out, count, 0.99);
            return new Snapshot(out, count, sum, min, max, p50, p99);
        }
    }
}
