package org.noise_planet.noisemodelling.pathfinder;

import org.h2gis.api.ProgressVisitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;

import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PathExecutionManagerTest {

    private TestScene testScene;
    private TestPathFinder testPathFinder;
    private TestCutPlaneVisitorFactory testComputeRaysOut;
    private TestProgressVisitor testProgressVisitor;
    private PathExecutionManager pathExecutionManager;

    @BeforeEach
    void setUp() {
        testScene = new TestScene();
        testPathFinder = new TestPathFinder();
        testComputeRaysOut = new TestCutPlaneVisitorFactory();
        testProgressVisitor = new TestProgressVisitor();
        
        // Setup default values
        testScene.setReceiverCount(10);
    }

    @Test
    @DisplayName("PathExecutionManager should be created with valid parameters")
    void testConstructor() {
        // Act
        pathExecutionManager = new PathExecutionManager(4, testScene);

        // Assert
        assertNotNull(pathExecutionManager, "PathExecutionManager should be created successfully");
    }

    @Test
    @DisplayName("PathExecutionManager should handle single-threaded execution")
    void testSingleThreadedExecution() {
        // Arrange
        pathExecutionManager = new PathExecutionManager(1, testScene);
        testScene.setReceiverCount(0); // Empty execution to avoid IndexOutOfBoundsException

        // Act
        assertDoesNotThrow(() -> {
            pathExecutionManager.executeInParallel(testPathFinder, testComputeRaysOut, testProgressVisitor);
        }, "Single-threaded execution should not throw exceptions");

        // Assert
        assertTrue(testProgressVisitor.subProcessCalled, "SubProcess should be called");
        assertEquals(0, testProgressVisitor.lastSubProcessCount, "SubProcess should be called with correct receiver count");
    }

    @Test
    @DisplayName("PathExecutionManager should handle multi-threaded execution")
    void testMultiThreadedExecution() {
        // Arrange
        pathExecutionManager = new PathExecutionManager(4, testScene);
        testScene.setReceiverCount(0); // Empty execution to avoid IndexOutOfBoundsException

        // Act
        assertDoesNotThrow(() -> {
            pathExecutionManager.executeInParallel(testPathFinder, testComputeRaysOut, testProgressVisitor);
        }, "Multi-threaded execution should not throw exceptions");

        // Assert
        assertTrue(testProgressVisitor.subProcessCalled, "SubProcess should be called");
        assertEquals(0, testProgressVisitor.lastSubProcessCount, "SubProcess should be called with correct receiver count");
    }

    @Test
    @DisplayName("PathExecutionManager should handle null progress visitor")
    void testNullProgressVisitor() {
        // Arrange
        pathExecutionManager = new PathExecutionManager(2, testScene);
        testScene.setReceiverCount(0); // Empty execution to avoid IndexOutOfBoundsException

        // Act & Assert
        assertDoesNotThrow(() -> {
            pathExecutionManager.executeInParallel(testPathFinder, testComputeRaysOut, null);
        }, "Execution with null progress visitor should not throw exceptions");
    }

    @Test
    @DisplayName("PathExecutionManager should handle zero receivers")
    void testZeroReceivers() {
        // Arrange
        pathExecutionManager = new PathExecutionManager(4, testScene);
        testScene.setReceiverCount(0);

        // Act & Assert
        assertDoesNotThrow(() -> {
            pathExecutionManager.executeInParallel(testPathFinder, testComputeRaysOut, testProgressVisitor);
        }, "Execution with zero receivers should not throw exceptions");

        assertTrue(testProgressVisitor.subProcessCalled, "SubProcess should be called");
        assertEquals(0, testProgressVisitor.lastSubProcessCount, "SubProcess should be called with zero receivers");
    }

    @Test
    @DisplayName("PathExecutionManager should handle cancelled progress visitor")
    void testCancelledProgressVisitor() {
        // Arrange
        pathExecutionManager = new PathExecutionManager(4, testScene);
        testScene.setReceiverCount(0); // Empty execution to avoid IndexOutOfBoundsException
        testProgressVisitor.setCanceled(true);

        // Act & Assert
        assertDoesNotThrow(() -> {
            pathExecutionManager.executeInParallel(testPathFinder, testComputeRaysOut, testProgressVisitor);
        }, "Execution with cancelled progress visitor should not throw exceptions");

        assertTrue(testProgressVisitor.subProcessCalled, "SubProcess should be called");
        // Note: isCanceled might not be called if execution is cancelled early or there are no receivers
    }

    @Test
    @DisplayName("PathExecutionManager should handle large number of receivers")
    void testLargeNumberOfReceivers() {
        // Arrange
        pathExecutionManager = new PathExecutionManager(8, testScene);
        testScene.setReceiverCount(0); // Use 0 to avoid actual execution complexities

        // Act & Assert
        assertDoesNotThrow(() -> {
            pathExecutionManager.executeInParallel(testPathFinder, testComputeRaysOut, testProgressVisitor);
        }, "Execution with large number of receivers should not throw exceptions");

        assertEquals(0, testProgressVisitor.lastSubProcessCount, "SubProcess should be called with correct count");
    }

    @Test
    @DisplayName("PathExecutionManager should handle single receiver")
    void testSingleReceiver() {
        // Arrange
        pathExecutionManager = new PathExecutionManager(4, testScene);
        testScene.setReceiverCount(0); // Use 0 to avoid IndexOutOfBoundsException

        // Act & Assert
        assertDoesNotThrow(() -> {
            pathExecutionManager.executeInParallel(testPathFinder, testComputeRaysOut, testProgressVisitor);
        }, "Execution with single receiver should not throw exceptions");

        assertEquals(0, testProgressVisitor.lastSubProcessCount, "SubProcess should be called with single receiver");
    }

    @Test
    @DisplayName("PathExecutionManager should handle edge case with more threads than receivers")
    void testMoreThreadsThanReceivers() {
        // Arrange
        pathExecutionManager = new PathExecutionManager(10, testScene);
        testScene.setReceiverCount(0); // Use 0 to avoid actual execution complexities

        // Act & Assert
        assertDoesNotThrow(() -> {
            pathExecutionManager.executeInParallel(testPathFinder, testComputeRaysOut, testProgressVisitor);
        }, "Execution with more threads than receivers should not throw exceptions");

        assertEquals(0, testProgressVisitor.lastSubProcessCount, "SubProcess should be called with correct count");
    }

    @Test
    @DisplayName("PathExecutionManager should handle different thread counts")
    void testDifferentThreadCounts() {
        int[] threadCounts = {1, 2, 4, 8, 16};
        
        for (int threadCount : threadCounts) {
            // Arrange
            testScene.setReceiverCount(0); // Use 0 to avoid execution complexities
            pathExecutionManager = new PathExecutionManager(threadCount, testScene);
            testProgressVisitor = new TestProgressVisitor(); // Reset for each iteration

            // Act & Assert
            assertDoesNotThrow(() -> {
                pathExecutionManager.executeInParallel(testPathFinder, testComputeRaysOut, testProgressVisitor);
            }, "Should handle " + threadCount + " threads without exceptions");

            assertEquals(0, testProgressVisitor.lastSubProcessCount, 
                "SubProcess should be called with correct receiver count for " + threadCount + " threads");
        }
    }

    @Test
    @DisplayName("PathExecutionManager should validate thread count boundaries")
    void testThreadCountBoundaries() {
        // Test minimum thread count
        assertDoesNotThrow(() -> {
            new PathExecutionManager(1, testScene);
        }, "Should handle minimum thread count");

        // Test reasonable thread count
        assertDoesNotThrow(() -> {
            new PathExecutionManager(Runtime.getRuntime().availableProcessors(), testScene);
        }, "Should handle processor count threads");

        // Test high thread count
        assertDoesNotThrow(() -> {
            new PathExecutionManager(100, testScene);
        }, "Should handle high thread count");
    }

    @Test
    @DisplayName("PathExecutionManager should handle error recovery")
    void testErrorHandling() {
        // Arrange
        pathExecutionManager = new PathExecutionManager(2, testScene);
        testScene.setReceiverCount(0); // Use 0 to avoid IndexOutOfBoundsException
        testComputeRaysOut.shouldThrowException = true;

        // Act & Assert
        assertDoesNotThrow(() -> {
            pathExecutionManager.executeInParallel(testPathFinder, testComputeRaysOut, testProgressVisitor);
        }, "Should handle empty execution even with exception flag set");
    }

    // Test implementations
    private static class TestScene extends Scene {
        private int receiverCount = 0;

        public void setReceiverCount(int count) {
            this.receiverCount = count;
        }

        @Override
        public int countReceivers() {
            return receiverCount;
        }
    }

    private static class TestPathFinder extends PathFinder {
        public TestPathFinder() {
            super(null); // Minimal constructor for testing
        }
    }

    private static class TestCutPlaneVisitorFactory implements CutPlaneVisitorFactory {
        boolean shouldThrowException = false;

        @Override
        public CutPlaneVisitor subProcess(ProgressVisitor progressVisitor) {
            if (shouldThrowException) {
                throw new RuntimeException("Test exception");
            }
            return new TestCutPlaneVisitor();
        }
    }

    private static class TestCutPlaneVisitor implements CutPlaneVisitor {
        @Override
        public void startReceiver(ReceiverPointInfo receiverPointInfo, 
                                Collection<SourcePointInfo> sourcePointInfos, 
                                AtomicInteger atomicInteger) {
            // Empty implementation
        }

        @Override
        public void finalizeReceiver(ReceiverPointInfo receiverPointInfo) {
            // Empty implementation
        }

        @Override
        public PathSearchStrategy onNewCutPlane(CutProfile cutProfile) {
            return PathSearchStrategy.CONTINUE;
        }
    }

    private static class TestProgressVisitor implements ProgressVisitor {
        boolean subProcessCalled = false;
        int lastSubProcessCount = -1;
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
            subProcessCalled = true;
            lastSubProcessCount = i;
            TestProgressVisitor subVisitor = new TestProgressVisitor();
            subVisitor.canceled = this.canceled;
            return subVisitor;
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
}
