package org.example;

import java.util.Random;

public class ZipfLoadGenerator {

    static final int SIZE = 1 << 20; // ~1 млн
    static final int MAX_K = 1023;
    static final double S = 1.15;
    static final long SEED = 42L;

    static int[] generateValues() {
        Random rand = new Random(SEED);

        double[] cdf = new double[MAX_K];
        double sum = 0.0;

        for (int k = 1; k <= MAX_K; k++) {
            sum += 1.0 / Math.pow(k, S);
        }

        double cumulative = 0.0;
        for (int k = 1; k <= MAX_K; k++) {
            cumulative += (1.0 / Math.pow(k, S)) / sum;
            cdf[k - 1] = cumulative;
        }

        int[] values = new int[SIZE];

        for (int i = 0; i < SIZE; i++) {
            if (rand.nextDouble() < 1.0 / 3.0) {
                values[i] = 0;
                continue;
            }

            double r = rand.nextDouble();

            int lo = 0;
            int hi = MAX_K - 1;

            while (lo < hi) {
                int mid = (lo + hi) >>> 1;

                if (cdf[mid] >= r) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }

            values[i] = lo + 1;
        }

        return values;
    }
}
