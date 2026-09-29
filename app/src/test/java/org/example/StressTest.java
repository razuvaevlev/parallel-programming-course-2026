package org.example;

import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.collectors.DoubleBufferCollector;
import org.example.collectors.LockStripingCollector;
import org.example.collectors.MetricsCollector;
import org.example.collectors.Snapshot;
import org.example.collectors.ThreadLocalCollector;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StressTest {
	private static final Logger LOGGER = Logger.getLogger(StressTest.class.getName());

	static Stream<MetricsCollector> collectorsProvider() {
		return Stream.of(
				new LockStripingCollector(),
				new ThreadLocalCollector(),
				new DoubleBufferCollector());
	}

	@ParameterizedTest
	@MethodSource("collectorsProvider")
	void stressTest(MetricsCollector collector) {
		final int writerCount = 4;
		final int snapshotAttempts = 10_000;
		CountDownLatch ready = new CountDownLatch(writerCount);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch firstRecord = new CountDownLatch(writerCount);
		AtomicBoolean running = new AtomicBoolean(true);
		long[] callsByThread = new long[writerCount];
		Thread[] writers = new Thread[writerCount];

		for (int i = 0; i < writerCount; i++) {
			final int writerIndex = i;
			writers[i] = new Thread(() -> {
				long calls = 0;
				ready.countDown();
				try {
					start.await();
					while (running.get()) {
						collector.record(0);
						calls++;
						if (calls == 1) {
							firstRecord.countDown();
						}
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					callsByThread[writerIndex] = calls;
				}
			}, "bench-" + i);
			writers[i].start();
		}

		try {
			ready.await();
			start.countDown();
			firstRecord.await();

			long brokenSnapshots = 0;
			long sumLessThanCount = 0;
			long sumGreaterThanCount = 0;
			for (int i = 0; i < snapshotAttempts; i++) {
				Snapshot snapshot = collector.snapshot();
				long bucketTotal = 0;
				for (long bucket : snapshot.buckets()) {
					bucketTotal += bucket;
				}

				if (bucketTotal != snapshot.count()) {
					brokenSnapshots++;
					if (bucketTotal < snapshot.count()) {
						sumLessThanCount++;
					} else {
						sumGreaterThanCount++;
					}
				}
			}

			running.set(false);
			for (Thread writer : writers) {
				writer.join();
			}

			long totalCalls = 0;
			for (long calls : callsByThread) {
				totalCalls += calls;
			}
			long finalCount = collector.snapshot().count();
			double brokenPercentage = brokenSnapshots * 100.0 / snapshotAttempts;
			LOGGER.info(String.format(
					"%s: broken snapshots=%d/%d (%.2f%%), sum < count=%d, sum > count=%d, final count - writer calls=%d",
					collector.getClass().getSimpleName(), brokenSnapshots, snapshotAttempts,
					brokenPercentage, sumLessThanCount, sumGreaterThanCount,
					finalCount - totalCalls));
		} catch (InterruptedException e) {
			running.set(false);
			for (Thread writer : writers) {
				writer.interrupt();
			}
			Thread.currentThread().interrupt();
			throw new AssertionError("Inconsistency test interrupted", e);
		}
	}
}
