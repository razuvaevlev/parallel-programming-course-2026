package org.example.collectors;

public class SharedLockCollector implements MetricsCollector {
    private static final int NUMBER_OF_BUCKETS = 255;
    private static final int BUCKET_WIDTH = 4;

    private final long[] buckets;
    private long count, min, max, sum;

    public SharedLockCollector() {
        buckets = new long[NUMBER_OF_BUCKETS];
        count = 0;
        min = Long.MAX_VALUE;
        max = Long.MIN_VALUE;
    }

    @Override
    synchronized public void record(long value) {
        var bucket = (int) Math.min(value / BUCKET_WIDTH, NUMBER_OF_BUCKETS - 1);
        buckets[bucket] += 1;
        min = Long.min(min, value);
        max = Long.max(max, value);
        count++;
        sum += value;
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
    synchronized public Snapshot snapshot() {
        var bucketsCopy = buckets.clone();
        var minCopy = min;
        var maxCopy = max;
        var countCopy = count;
        var sumCopy = sum;

        var p99 = percentile(bucketsCopy, count, 0.99);
        var p50 = percentile(bucketsCopy, count, 0.50);
        return new Snapshot(bucketsCopy, countCopy, sumCopy, minCopy, maxCopy, p50, p99);
    }
}
