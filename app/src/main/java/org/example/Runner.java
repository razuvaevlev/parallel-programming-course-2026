package org.example;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;

import org.example.collectors.MetricsCollector;

public class Runner {
    public double run(MetricsCollector collector, int[] values, int numberOfThreads, long seconds)
            throws InterruptedException {
        var start = new CountDownLatch(1);
        var stop = new AtomicBoolean(false);
        var ops = new long[numberOfThreads];

        Thread[] threads = new Thread[numberOfThreads];
        for (int k = 0; k < numberOfThreads; k++) {
            final int tid = k;
            threads[k] = new Thread(() -> {
                long localCount = 0;
                int i = tid * 1000;
                if (values.length > 0) {
                    i = i % values.length;
                }
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                int len = values.length;
                while (!stop.get()) {
                    collector.record(values[i]);
                    localCount++;
                    i++;
                    if (i == len)
                        i = 0;
                }
                ops[tid] = localCount;
            }, "bench-" + k);
            threads[k].start();
        }

        var t0 = System.currentTimeMillis();
        start.countDown();
        Thread.sleep(Duration.ofSeconds(seconds));
        stop.set(true);
        var t1 = System.currentTimeMillis();
        for (Thread t : threads)
            t.join();

        var sum = LongStream.of(ops).sum();

        return sum / ((t1 - t0) / 1000);
    }

    public double measurePoint(MetricsCollector collector, int[] values, int numberOfThreads)
            throws InterruptedException {
        run(collector, values, numberOfThreads, 5);
        var results = new ArrayList<Double>();
        for (int i = 0; i < 5; i++) {
            results.add(run(collector, values, numberOfThreads, 5));
        }
        System.out.println("Count: " + collector.snapshot().count());
        Collections.sort(results);
        int middle = results.size() / 2;
        middle = middle > 0 && middle % 2 == 0 ? middle - 1 : middle;
        return results.get(middle);
    }
}
