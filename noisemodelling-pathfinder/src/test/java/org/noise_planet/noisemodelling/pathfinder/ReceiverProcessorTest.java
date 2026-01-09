package org.noise_planet.noisemodelling.pathfinder;

import org.h2gis.api.ProgressVisitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ProfilerThread;

import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ReceiverProcessorTest {

    private TestScene testScene;
    private TestProfilerThread testProfilerThread;
    private TestCutPlaneVisitor testCutPlaneVisitor;
    private TestProgressVisitor testProgressVisitor;
    private ReceiverProcessor receiverProcessor;

    @BeforeEach
    void setUp() {
        testScene = new TestScene();
        testProfilerThread = new TestProfilerThread();
        testCutPlaneVisitor = new TestCutPlaneVisitor();
        testProgressVisitor = new TestProgressVisitor();
        
        // Setup default scene values
        testScene.setReflexionOrder(1);
        testScene.setMaxSrcDist(1000.0);
        testScene.setMaxRefDist(100.0);
        testScene.computeVerticalDiffraction = true;
        testScene.computeHorizontalDiffraction = true;
        
        receiverProcessor = new ReceiverProcessor(testScene, testProfilerThread);
    }

    @Test
    @DisplayName("ReceiverProcessor should be created with valid parameters")
    void testConstructor() {
        // Act
        ReceiverProcessor processor = new ReceiverProcessor(testScene, testProfilerThread);

        // Assert
        assertNotNull(processor, "ReceiverProcessor should be created successfully");
    }

    @Test
    @DisplayName("ReceiverProcessor should handle null profiler thread")
    void testConstructorWithNullProfiler() {
        // Act
        ReceiverProcessor processor = new ReceiverProcessor(testScene, null);

        // Assert
        assertNotNull(processor, "ReceiverProcessor should handle null profiler thread");
    }

    @Test
    @DisplayName("ReceiverProcessor should process receiver with basic configuration")
    void testProcessReceiverBasic() {
        // Arrange
        TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo(0, new Coordinate(0, 0, 0));

        // Act
        assertDoesNotThrow(() -> {
            receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, testProgressVisitor);
        }, "Basic receiver processing should not throw exceptions");

        // Assert
        assertTrue(testCutPlaneVisitor.startReceiverCalled, "startReceiver should be called");
        assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, "finalizeReceiver should be called");
    }

    @Test
    @DisplayName("ReceiverProcessor should handle receiver with reflections disabled")
    void testProcessReceiverNoReflections() {
        // Arrange
        testScene.setReflexionOrder(0); // Disable reflections
        TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo(1, new Coordinate(10, 10, 0));

        // Act
        assertDoesNotThrow(() -> {
            receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, testProgressVisitor);
        }, "Processing with disabled reflections should not throw exceptions");

        // Assert
        assertTrue(testCutPlaneVisitor.startReceiverCalled, "startReceiver should be called");
        assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, "finalizeReceiver should be called");
    }

    @Test
    @DisplayName("ReceiverProcessor should handle receiver with reflections enabled")
    void testProcessReceiverWithReflections() {
        // Arrange
        testScene.setReflexionOrder(2); // Enable reflections
        TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo(2, new Coordinate(20, 20, 0));

        // Act
        assertDoesNotThrow(() -> {
            receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, testProgressVisitor);
        }, "Processing with reflections should not throw exceptions");

        // Assert
        assertTrue(testCutPlaneVisitor.startReceiverCalled, "startReceiver should be called");
        assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, "finalizeReceiver should be called");
    }

    @Test
    @DisplayName("ReceiverProcessor should handle null progress visitor")
    void testProcessReceiverNullProgressVisitor() {
        // Arrange
        TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo(3, new Coordinate(30, 30, 0));

        // Act & Assert
        assertDoesNotThrow(() -> {
            receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, null);
        }, "Processing with null progress visitor should not throw exceptions");

        assertTrue(testCutPlaneVisitor.startReceiverCalled, "startReceiver should be called");
        assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, "finalizeReceiver should be called");
    }

    @Test
    @DisplayName("ReceiverProcessor should handle cancelled progress visitor")
    void testProcessReceiverCancelledProgress() {
        // Arrange
        testProgressVisitor.setCanceled(true);
        TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo(4, new Coordinate(40, 40, 0));

        // Act & Assert
        assertDoesNotThrow(() -> {
            receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, testProgressVisitor);
        }, "Processing with cancelled progress should not throw exceptions");

        assertTrue(testCutPlaneVisitor.startReceiverCalled, "startReceiver should be called");
        assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, "finalizeReceiver should be called");
    }

    @Test
    @DisplayName("ReceiverProcessor should handle different coordinates")
    void testProcessReceiverDifferentCoordinates() {
        Coordinate[] testCoordinates = {
            new Coordinate(0, 0, 0),
            new Coordinate(100, 100, 50),
            new Coordinate(-50, -50, 10),
            new Coordinate(500, 250, 100)
        };

        for (int i = 0; i < testCoordinates.length; i++) {
            // Arrange
            TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo(i, testCoordinates[i]);
            testCutPlaneVisitor = new TestCutPlaneVisitor(); // Reset for each iteration

            // Act & Assert
            assertDoesNotThrow(() -> {
                receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, testProgressVisitor);
            }, "Should handle coordinate " + testCoordinates[i] + " without exceptions");

            assertTrue(testCutPlaneVisitor.startReceiverCalled, 
                "startReceiver should be called for coordinate " + testCoordinates[i]);
            assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, 
                "finalizeReceiver should be called for coordinate " + testCoordinates[i]);
        }
    }

    @Test
    @DisplayName("ReceiverProcessor should handle different reflection orders")
    void testProcessReceiverDifferentReflectionOrders() {
        int[] reflectionOrders = {0, 1, 2, 3, 5};

        for (int order : reflectionOrders) {
            // Arrange
            testScene.setReflexionOrder(order);
            TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo(order, new Coordinate(order * 10, order * 10, 0));
            testCutPlaneVisitor = new TestCutPlaneVisitor(); // Reset for each iteration

            // Act & Assert
            assertDoesNotThrow(() -> {
                receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, testProgressVisitor);
            }, "Should handle reflection order " + order + " without exceptions");

            assertTrue(testCutPlaneVisitor.startReceiverCalled, 
                "startReceiver should be called for reflection order " + order);
            assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, 
                "finalizeReceiver should be called for reflection order " + order);
        }
    }

    @Test
    @DisplayName("ReceiverProcessor should handle different maximum distances")
    void testProcessReceiverDifferentMaxDistances() {
        double[] maxDistances = {10.0, 100.0, 500.0, 1000.0, 2000.0};

        for (double maxDist : maxDistances) {
            // Arrange
            testScene.setMaxSrcDist(maxDist);
            testScene.maxRefDist = maxDist / 10;
            TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo((int)maxDist, 
                new Coordinate(maxDist/100, maxDist/100, 0));
            testCutPlaneVisitor = new TestCutPlaneVisitor(); // Reset for each iteration

            // Act & Assert
            assertDoesNotThrow(() -> {
                receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, testProgressVisitor);
            }, "Should handle max distance " + maxDist + " without exceptions");

            assertTrue(testCutPlaneVisitor.startReceiverCalled, 
                "startReceiver should be called for max distance " + maxDist);
            assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, 
                "finalizeReceiver should be called for max distance " + maxDist);
        }
    }

    @Test
    @DisplayName("ReceiverProcessor should handle diffraction settings")
    void testProcessReceiverDiffractionSettings() {
        boolean[][] diffractionSettings = {
            {false, false},
            {true, false},
            {false, true},
            {true, true}
        };

        for (int i = 0; i < diffractionSettings.length; i++) {
            // Arrange
            testScene.computeVerticalDiffraction = diffractionSettings[i][0];
            testScene.computeHorizontalDiffraction = diffractionSettings[i][1];
            TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo(i, new Coordinate(i * 10, i * 10, 0));
            testCutPlaneVisitor = new TestCutPlaneVisitor(); // Reset for each iteration

            // Act & Assert
            assertDoesNotThrow(() -> {
                receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, testProgressVisitor);
            }, "Should handle diffraction settings [" + diffractionSettings[i][0] + "," + 
               diffractionSettings[i][1] + "] without exceptions");

            assertTrue(testCutPlaneVisitor.startReceiverCalled, 
                "startReceiver should be called for diffraction settings");
            assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, 
                "finalizeReceiver should be called for diffraction settings");
        }
    }

    @Test
    @DisplayName("ReceiverProcessor should work with profiler enabled")
    void testProcessReceiverWithProfiler() {
        // Arrange
        TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo(10, new Coordinate(100, 100, 0));

        // Act & Assert
        assertDoesNotThrow(() -> {
            receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, testProgressVisitor);
        }, "Processing with profiler should not throw exceptions");

        assertTrue(testCutPlaneVisitor.startReceiverCalled, "startReceiver should be called");
        assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, "finalizeReceiver should be called");
    }

    @Test
    @DisplayName("ReceiverProcessor should work with profiler disabled")
    void testProcessReceiverWithoutProfiler() {
        // Arrange
        receiverProcessor = new ReceiverProcessor(testScene, null);
        TestReceiverPointInfo receiverInfo = new TestReceiverPointInfo(11, new Coordinate(110, 110, 0));

        // Act & Assert
        assertDoesNotThrow(() -> {
            receiverProcessor.processReceiver(receiverInfo, testCutPlaneVisitor, testProgressVisitor);
        }, "Processing without profiler should not throw exceptions");

        assertTrue(testCutPlaneVisitor.startReceiverCalled, "startReceiver should be called");
        assertTrue(testCutPlaneVisitor.finalizeReceiverCalled, "finalizeReceiver should be called");
    }

    @Test
    @DisplayName("ReceiverProcessor should handle edge cases")
    void testProcessReceiverEdgeCases() {
        // Test with extreme coordinates
        TestReceiverPointInfo extremeReceiver = new TestReceiverPointInfo(999, 
            new Coordinate(Double.MAX_VALUE/2, Double.MAX_VALUE/2, 0));

        assertDoesNotThrow(() -> {
            receiverProcessor.processReceiver(extremeReceiver, testCutPlaneVisitor, testProgressVisitor);
        }, "Should handle extreme coordinates");

        // Test with zero reflection order and zero max distance
        testScene.setReflexionOrder(0);
        testScene.setMaxSrcDist(0.0);
        TestReceiverPointInfo zeroConfigReceiver = new TestReceiverPointInfo(0, new Coordinate(0, 0, 0));

        assertDoesNotThrow(() -> {
            receiverProcessor.processReceiver(zeroConfigReceiver, testCutPlaneVisitor, testProgressVisitor);
        }, "Should handle zero configuration");
    }

    // Test implementations
    private static class TestScene extends Scene {
        // Scene configuration methods will be accessed through the base class
    }

    private static class TestProfilerThread extends ProfilerThread {
        public TestProfilerThread() {
            super(new java.io.File("test_profiler.csv")); // Minimal constructor
        }
    }

    private static class TestCutPlaneVisitor implements CutPlaneVisitor {
        boolean startReceiverCalled = false;
        boolean finalizeReceiverCalled = false;

        @Override
        public void startReceiver(ReceiverPointInfo receiver, Collection<SourcePointInfo> sourceList, 
                                AtomicInteger cutProfileCount) {
            startReceiverCalled = true;
        }

        @Override
        public void finalizeReceiver(ReceiverPointInfo receiver) {
            finalizeReceiverCalled = true;
        }

        @Override
        public PathSearchStrategy onNewCutPlane(CutProfile cutProfile) {
            return PathSearchStrategy.CONTINUE;
        }
    }

    private static class TestProgressVisitor implements ProgressVisitor {
        private boolean canceled = false;

        public void setCanceled(boolean canceled) {
            this.canceled = canceled;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        @Override
        public ProgressVisitor subProcess(int i) {
            return this;
        }

        @Override
        public void endOfProgress() {
            // Empty implementation
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener listener) {
            // Empty implementation
        }

        @Override
        public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
            // Empty implementation
        }

        @Override
        public int getStepCount() {
            return 0;
        }

        @Override
        public void cancel() {
            this.canceled = true;
        }

        @Override
        public double getProgression() {
            return 0.0;
        }

        @Override
        public void setStep(int i) {
            // Empty implementation
        }

        @Override
        public void endStep() {
            // Empty implementation
        }
    }

    private static class TestReceiverPointInfo extends ReceiverPointInfo {
        public TestReceiverPointInfo(int receiverIndex, Coordinate coordinates) {
            super(receiverIndex, 0L, coordinates); // Use long for receiverPk
        }
    }
}
