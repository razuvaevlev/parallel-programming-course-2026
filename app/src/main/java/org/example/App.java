package org.example;

import org.example.collectors.DoubleBufferCollector;
import org.example.collectors.LockStripingCollector;
import org.example.collectors.MetricsCollector;
import org.example.collectors.SharedLockCollector;
import org.example.collectors.SimpleMetricsCollector;
import org.example.collectors.ThreadLocalCollector;

public class App {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: App <stage 1..4> <number of threads>");
            System.exit(1);
        }

        int stage;
        int numberOfThreads;

        try {
            stage = Integer.parseInt(args[0]);
            numberOfThreads = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Arguments must be integers: <stage 0..4> <number of threads>");
            System.exit(1);
            return;
        }

        if (stage < 0 || stage > 4) {
            System.err.println("Stage must be in range 0..4");
            System.exit(1);
        }

        if (numberOfThreads <= 0) {
            System.err.println("Number of threads must be positive");
            System.exit(1);
        }

        var values = ZipfLoadGenerator.generateValues();
        var collector = getCollector(stage);
        var runner = new Runner();

        try {
            System.out.println("Result: " + runner.measurePoint(collector, values, numberOfThreads));
        } catch (InterruptedException e) {
            System.out.println("Threads were interrupted while measurement");
            Thread.currentThread().interrupt();
        }
    }

    private static MetricsCollector getCollector(int stage) {
        switch (stage) {
            case 0:
                return new SimpleMetricsCollector();
            case 1:
                // return new EmptyLockCollector();
                return new SharedLockCollector();
            case 2:
                return new LockStripingCollector();
            case 3:
                return new ThreadLocalCollector();
            case 4:
                return new DoubleBufferCollector();
            default:
                throw new IllegalArgumentException("Unsupported stage: " + stage);
        }
    }
}
