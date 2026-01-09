package org.noise_planet.noisemodelling.pathfinder.utils.profiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProfilerThread.Metric interface and its implementations.
 */
class ProfilerThreadMetricTest {

    private ProfilerThread profilerThread;
    private File outputFile;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        outputFile = tempDir.resolve("metric_test.csv").toFile();
        profilerThread = new ProfilerThread(outputFile);
    }

    @Test
    @DisplayName("TimeMetric should provide correct column names")
    void testTimeMetricColumnNames() {
        // Arrange - Create a TestTimeMetric instance
        AtomicLong timeTracker = new AtomicLong(System.currentTimeMillis());
        TestTimeMetric testTimeMetric = new TestTimeMetric(timeTracker, System.currentTimeMillis());

        // Act
        String[] columnNames = testTimeMetric.getColumnNames();

        // Assert
        assertNotNull(columnNames, "Column names should not be null");
        assertEquals(1, columnNames.length, "TimeMetric should have exactly one column");
        assertEquals("time", columnNames[0], "Column name should be 'time'");
    }

    @Test
    @DisplayName("TimeMetric should provide current values in correct format")
    void testTimeMetricCurrentValues() {
        // Arrange
        long startTime = System.currentTimeMillis();
        AtomicLong timeTracker = new AtomicLong(startTime + 5000); // 5 seconds later
        TestTimeMetric timeMetric = new TestTimeMetric(timeTracker, startTime);

        // Act
        String[] currentValues = timeMetric.getCurrentValues();

        // Assert
        assertNotNull(currentValues, "Current values should not be null");
        assertEquals(1, currentValues.length, "TimeMetric should have exactly one value");
        
        // Should be approximately 5.00 seconds
        double timeValue = Double.parseDouble(currentValues[0]);
        assertTrue(timeValue >= 4.99 && timeValue <= 5.01, 
            "Time value should be approximately 5.00 seconds, but was: " + timeValue);
    }

    @Test
    @DisplayName("TimeMetric tick method should not throw exceptions")
    void testTimeMetricTick() {
        // Arrange
        AtomicLong timeTracker = new AtomicLong(System.currentTimeMillis());
        TestTimeMetric timeMetric = new TestTimeMetric(timeTracker, System.currentTimeMillis());

        // Act & Assert
        assertDoesNotThrow(() -> {
            timeMetric.tick(System.currentTimeMillis());
            timeMetric.tick(System.currentTimeMillis() + 1000);
        }, "TimeMetric tick should not throw exceptions");
    }

    @Test
    @DisplayName("TimeMetric should handle large time differences")
    void testTimeMetricLargeTimeDifferences() {
        // Arrange
        long startTime = System.currentTimeMillis();
        AtomicLong timeTracker = new AtomicLong(startTime + 3600000); // 1 hour later
        TestTimeMetric timeMetric = new TestTimeMetric(timeTracker, startTime);

        // Act
        String[] currentValues = timeMetric.getCurrentValues();

        // Assert
        double timeValue = Double.parseDouble(currentValues[0]);
        assertTrue(timeValue >= 3599.0 && timeValue <= 3601.0, 
            "Time value should be approximately 3600 seconds (1 hour)");
    }

    @Test
    @DisplayName("TimeMetric should handle zero time difference")
    void testTimeMetricZeroTimeDifference() {
        // Arrange
        long currentTime = System.currentTimeMillis();
        AtomicLong timeTracker = new AtomicLong(currentTime);
        TestTimeMetric timeMetric = new TestTimeMetric(timeTracker, currentTime);

        // Act
        String[] currentValues = timeMetric.getCurrentValues();

        // Assert
        double timeValue = Double.parseDouble(currentValues[0]);
        assertTrue(timeValue >= -0.01 && timeValue <= 0.01, 
            "Time value should be approximately 0.00 seconds");
    }

    @Test
    @DisplayName("Custom metric should work with ProfilerThread")
    void testCustomMetricIntegration() {
        // Arrange
        CounterMetric counterMetric = new CounterMetric();
        profilerThread.addMetric(counterMetric);

        // Act
        CounterMetric retrievedMetric = profilerThread.getMetric(CounterMetric.class);

        // Assert
        assertNotNull(retrievedMetric, "Should be able to retrieve custom metric");
        assertEquals(counterMetric, retrievedMetric, "Retrieved metric should be the same instance");
        
        // Test metric functionality
        String[] columns = retrievedMetric.getColumnNames();
        assertEquals(1, columns.length);
        assertEquals("counter", columns[0]);
        
        String[] values = retrievedMetric.getCurrentValues();
        assertEquals("0", values[0]);
        
        retrievedMetric.tick(System.currentTimeMillis());
        values = retrievedMetric.getCurrentValues();
        assertEquals("1", values[0]);
    }

    @Test
    @DisplayName("Multiple metrics should work together")
    void testMultipleMetrics() {
        // Arrange
        CounterMetric counterMetric = new CounterMetric();
        TimestampMetric timestampMetric = new TimestampMetric();
        
        profilerThread.addMetric(counterMetric);
        profilerThread.addMetric(timestampMetric);

        // Act
        CounterMetric retrievedCounter = profilerThread.getMetric(CounterMetric.class);
        TimestampMetric retrievedTimestamp = profilerThread.getMetric(TimestampMetric.class);

        // Assert
        assertNotNull(retrievedCounter, "Counter metric should be retrievable");
        assertNotNull(retrievedTimestamp, "Timestamp metric should be retrievable");
        assertNotSame(retrievedCounter, retrievedTimestamp, "Metrics should be different instances");
    }

    // Test implementation of TimeMetric for testing purposes
    private static class TestTimeMetric implements ProfilerThread.Metric {
        private final AtomicLong timeTracker;
        private final long startTime;

        public TestTimeMetric(AtomicLong timeTracker, long startTime) {
            this.timeTracker = timeTracker;
            this.startTime = startTime;
        }

        @Override
        public String[] getColumnNames() {
            return new String[]{"time"};
        }

        @Override
        public String[] getCurrentValues() {
            return new String[]{String.format(java.util.Locale.ROOT, "%.2f", 
                (timeTracker.get() - startTime) / 1e3)};
        }

        @Override
        public void tick(long currentMillis) {
            // TimeMetric doesn't need to do anything on tick
        }
    }

    // Test metric implementations
    private static class CounterMetric implements ProfilerThread.Metric {
        private int counter = 0;

        @Override
        public String[] getColumnNames() {
            return new String[]{"counter"};
        }

        @Override
        public String[] getCurrentValues() {
            return new String[]{String.valueOf(counter)};
        }

        @Override
        public void tick(long currentMillis) {
            counter++;
        }
    }

    private static class TimestampMetric implements ProfilerThread.Metric {
        private long lastTick = 0;

        @Override
        public String[] getColumnNames() {
            return new String[]{"last_tick"};
        }

        @Override
        public String[] getCurrentValues() {
            return new String[]{String.valueOf(lastTick)};
        }

        @Override
        public void tick(long currentMillis) {
            lastTick = currentMillis;
        }
    }
}
