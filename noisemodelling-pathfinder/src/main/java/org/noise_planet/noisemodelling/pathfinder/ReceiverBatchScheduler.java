package org.noise_planet.noisemodelling.pathfinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper to compute receiver processing batches used by PathFinder.run.
 * Extracting this logic eases testing and separates scheduling concerns
 * from orchestration logic.
 */
public final class ReceiverBatchScheduler {

    private ReceiverBatchScheduler() {
        // utility
    }

    public static final class Range {
        public final int start;
        public final int end; // exclusive

        public Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Compute contiguous ranges that partition [0, total) into at most threadCount
     * balanced batches. If threadCount <= 1 a single full-range is returned.
     */
    public static List<Range> computeBatches(int total, int threadCount) {
        if (total <= 0) {
            return Collections.emptyList();
        }
        if (threadCount <= 1) {
            return Collections.singletonList(new Range(0, total));
        }
        int maxBatch = (int) Math.ceil(total / (double) threadCount);
        List<Range> batches = new ArrayList<>();
        int end = 0;
        while (end < total) {
            int newEnd = Math.min(end + maxBatch, total);
            batches.add(new Range(end, newEnd));
            end = newEnd;
        }
        return batches;
    }
}
