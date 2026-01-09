package org.noise_planet.noisemodelling.pathfinder.utils.profiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfilerThreadTest {

    private ProfilerThread profilerThread;
    private File outputFile;
    private Thread thread;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        outputFile = tempDir.resolve("profiler_test.csv").toFile();
        profilerThread = new ProfilerThread(outputFile);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (profilerThread != null) {
            profilerThread.stop();
        }
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            thread.join(1000); // Wait up to 1 second for thread to finish
        }
    }

    @Test
    @DisplayName("ProfilerThread should create output file and write header")
    void testProfilerThreadCreatesFileAndWritesHeader() throws IOException, InterruptedException {
        // Arrange
        profilerThread.setWriteInterval(1); // 1 second for faster testing
        profilerThread.setFlushInterval(1); // 1 second for faster testing

        // Act
        thread = new Thread(profilerThread);
        thread.start();
        Thread.sleep(500); // Let it start
        profilerThread.stop();
        thread.join(2000); // Wait for thread to finish

        // Assert
        assertTrue(outputFile.exists(), "Output file should be created");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");

        List<String> lines = readFileLines(outputFile);
        assertFalse(lines.isEmpty(), "File should have content");
        
        // Check header line
        String header = lines.get(0);
        assertTrue(header.contains("time"), "Header should contain 'time' column");
    }

    @Test
    @DisplayName("ProfilerThread should write metric data to file")
    void testProfilerThreadWritesMetricData() throws IOException, InterruptedException {
        // Arrange
        profilerThread.setWriteInterval(1); // 1 second for faster testing
        profilerThread.setFlushInterval(1); // 1 second for faster testing

        // Act
        thread = new Thread(profilerThread);
        thread.start();
        Thread.sleep(1500); // Let it run and write at least one data line
        profilerThread.stop();
        thread.join(2000); // Wait for thread to finish

        // Assert
        List<String> lines = readFileLines(outputFile);
        assertTrue(lines.size() >= 2, "File should have header and at least one data line");
        
        // Check that data lines contain numeric values
        for (int i = 1; i < lines.size(); i++) {
            String dataLine = lines.get(i);
            assertFalse(dataLine.trim().isEmpty(), "Data line should not be empty");
            
            // Time should be a numeric value
            String[] values = dataLine.split(",");
            assertTrue(values.length >= 1, "Data line should have at least one value (time)");
            assertDoesNotThrow(() -> Double.parseDouble(values[0]), 
                "Time value should be parseable as double");
        }
    }

    @Test
    @DisplayName("ProfilerThread should handle custom metrics")
    void testProfilerThreadWithCustomMetrics() throws IOException, InterruptedException {
        // Arrange
        TestMetric customMetric = new TestMetric();
        profilerThread.addMetric(customMetric);
        profilerThread.setWriteInterval(1);
        profilerThread.setFlushInterval(1);

        // Act
        thread = new Thread(profilerThread);
        thread.start();
        Thread.sleep(1500);
        profilerThread.stop();
        thread.join(2000);

        // Assert
        List<String> lines = readFileLines(outputFile);
        assertTrue(lines.size() >= 2, "File should have header and data");
        
        // Check header includes custom metric columns
        String header = lines.get(0);
        assertTrue(header.contains("test_metric"), "Header should contain custom metric column");
        
        // Check data includes custom metric values
        String dataLine = lines.get(1);
        String[] values = dataLine.split(",");
        assertTrue(values.length >= 2, "Data should include time and custom metric");
    }

    @Test
    @DisplayName("ProfilerThread should retrieve metrics by class")
    void testGetMetricByClass() {
        // Arrange
        TestMetric customMetric = new TestMetric();
        profilerThread.addMetric(customMetric);

        // Act
        TestMetric retrievedMetric = profilerThread.getMetric(TestMetric.class);

        // Assert
        assertNotNull(retrievedMetric, "Should be able to retrieve metric by class");
        assertSame(customMetric, retrievedMetric, "Retrieved metric should be the same instance");
    }

    @Test
    @DisplayName("ProfilerThread should return null for non-existent metric class")
    void testGetNonExistentMetric() {
        // Act
        TestMetric retrievedMetric = profilerThread.getMetric(TestMetric.class);

        // Assert
        assertNull(retrievedMetric, "Should return null for non-existent metric class");
    }

    @Test
    @DisplayName("ProfilerThread should handle stop gracefully")
    void testStopGracefully() throws InterruptedException {
        // Arrange
        thread = new Thread(profilerThread);
        thread.start();
        Thread.sleep(100); // Let it start

        // Act
        profilerThread.stop();
        thread.join(2000); // Wait for thread to finish

        // Assert
        assertFalse(thread.isAlive(), "Thread should be stopped");
    }

    @Test
    @DisplayName("ProfilerThread should handle write interval configuration")
    void testWriteIntervalConfiguration() {
        // Act
        profilerThread.setWriteInterval(30);
        profilerThread.setFlushInterval(120);

        // Assert
        // No direct way to verify internal state, but method should not throw
        assertDoesNotThrow(() -> {
            thread = new Thread(profilerThread);
            thread.start();
            Thread.sleep(100);
            profilerThread.stop();
            thread.join(1000);
        });
    }

    @Test
    @DisplayName("ProfilerThread should handle metrics with multiple columns")
    void testMetricsWithMultipleColumns() throws IOException, InterruptedException {
        // Arrange
        MultiColumnMetric multiMetric = new MultiColumnMetric();
        profilerThread.addMetric(multiMetric);
        profilerThread.setWriteInterval(1);
        profilerThread.setFlushInterval(1);

        // Act
        thread = new Thread(profilerThread);
        thread.start();
        Thread.sleep(1500);
        profilerThread.stop();
        thread.join(2000);

        // Assert
        List<String> lines = readFileLines(outputFile);
        String header = lines.get(0);
        assertTrue(header.contains("column1"), "Header should contain column1");
        assertTrue(header.contains("column2"), "Header should contain column2");
        
        String dataLine = lines.get(1);
        String[] values = dataLine.split(",");
        assertTrue(values.length >= 3, "Data should include time and two custom columns");
    }

    @Test
    @DisplayName("ProfilerThread should handle thread interruption gracefully")
    void testThreadInterruption() throws InterruptedException {
        // Arrange
        thread = new Thread(profilerThread);
        thread.start();
        Thread.sleep(100); // Let it start

        // Act
        thread.interrupt();
        thread.join(2000); // Wait for thread to finish

        // Assert
        assertFalse(thread.isAlive(), "Thread should handle interruption gracefully");
    }

    // Helper method to read file lines
    private List<String> readFileLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    // Test metric implementation
    private static class TestMetric implements ProfilerThread.Metric {
        private int counter = 0;

        @Override
        public String[] getColumnNames() {
            return new String[]{"test_metric"};
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

    // Test metric with multiple columns
    private static class MultiColumnMetric implements ProfilerThread.Metric {
        private int value1 = 10;
        private int value2 = 20;

        @Override
        public String[] getColumnNames() {
            return new String[]{"column1", "column2"};
        }

        @Override
        public String[] getCurrentValues() {
            return new String[]{String.valueOf(value1), String.valueOf(value2)};
        }

        @Override
        public void tick(long currentMillis) {
            value1++;
            value2 += 2;
        }
    }
}
