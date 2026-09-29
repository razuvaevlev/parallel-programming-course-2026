package org.example.collectors;

public interface MetricsCollector {
    void record(long value);

    Snapshot snapshot();
}
