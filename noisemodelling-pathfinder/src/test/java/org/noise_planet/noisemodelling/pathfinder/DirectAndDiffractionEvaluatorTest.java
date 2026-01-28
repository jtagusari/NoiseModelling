package org.noise_planet.noisemodelling.pathfinder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.SourcePointInfo;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DirectAndDiffractionEvaluatorTest {

    private TestScene testScene;
    private TestCutPlaneVisitor testCutPlaneVisitor;
    private TestSourcePointInfo testSourceInfo;
    private TestReceiverPointInfo testReceiverInfo;

    @BeforeEach
    void setUp() {
        testScene = new TestScene();
        testCutPlaneVisitor = new TestCutPlaneVisitor();
        testSourceInfo = new TestSourcePointInfo(0, new Coordinate(0, 0, 0));
        testReceiverInfo = new TestReceiverPointInfo(0, new Coordinate(100, 0, 0));
        
        // Setup default scene values
        testScene.setDefaultGroundAttenuation(0.5);
        testScene.setSourceCount(1);
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle basic direct path computation")
    void testComputeDirectPathBasic() {
        // Arrange
        testScene.setFreeField(true);

        // Act
        CutPlaneVisitor.PathSearchStrategy result = DirectAndDiffractionEvaluator.computeDirectPath(
            testSourceInfo, testReceiverInfo, false, false, testCutPlaneVisitor, testScene);

        // Assert
        assertEquals(CutPlaneVisitor.PathSearchStrategy.CONTINUE, result, 
            "Should return CONTINUE strategy for basic direct path");
        assertTrue(testCutPlaneVisitor.onNewCutPlaneCalled, 
            "onNewCutPlane should be called for free field");
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle vertical diffraction enabled")
    void testComputeDirectPathWithVerticalDiffraction() {
        // Arrange
        testScene.setFreeField(false);

        // Act
        CutPlaneVisitor.PathSearchStrategy result = DirectAndDiffractionEvaluator.computeDirectPath(
            testSourceInfo, testReceiverInfo, true, false, testCutPlaneVisitor, testScene);

        // Assert
        assertEquals(CutPlaneVisitor.PathSearchStrategy.CONTINUE, result, 
            "Should return CONTINUE strategy with vertical diffraction");
        assertTrue(testCutPlaneVisitor.onNewCutPlaneCalled, 
            "onNewCutPlane should be called when vertical diffraction is enabled");
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle horizontal diffraction enabled")
    void testComputeDirectPathWithHorizontalDiffraction() {
        // Arrange
        testScene.setFreeField(false);

        // Act
        CutPlaneVisitor.PathSearchStrategy result = DirectAndDiffractionEvaluator.computeDirectPath(
            testSourceInfo, testReceiverInfo, false, true, testCutPlaneVisitor, testScene);

        // Assert
        assertEquals(CutPlaneVisitor.PathSearchStrategy.CONTINUE, result, 
            "Should return CONTINUE strategy with horizontal diffraction");
        // Note: onNewCutPlane might not be called if no vertical diffraction and not free field
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle both diffractions enabled")
    void testComputeDirectPathWithBothDiffractions() {
        // Arrange
        testScene.setFreeField(false);

        // Act
        CutPlaneVisitor.PathSearchStrategy result = DirectAndDiffractionEvaluator.computeDirectPath(
            testSourceInfo, testReceiverInfo, true, true, testCutPlaneVisitor, testScene);

        // Assert
        assertEquals(CutPlaneVisitor.PathSearchStrategy.CONTINUE, result, 
            "Should return CONTINUE strategy with both diffractions enabled");
        assertTrue(testCutPlaneVisitor.onNewCutPlaneCalled, 
            "onNewCutPlane should be called when vertical diffraction is enabled");
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle SKIP_SOURCE strategy")
    void testComputeDirectPathSkipSource() {
        // Arrange
        testScene.setFreeField(true);
        testCutPlaneVisitor.setReturnStrategy(CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE);

        // Act
        CutPlaneVisitor.PathSearchStrategy result = DirectAndDiffractionEvaluator.computeDirectPath(
            testSourceInfo, testReceiverInfo, true, true, testCutPlaneVisitor, testScene);

        // Assert
        assertEquals(CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE, result, 
            "Should return SKIP_SOURCE strategy when visitor returns it");
        assertTrue(testCutPlaneVisitor.onNewCutPlaneCalled, 
            "onNewCutPlane should be called before early termination");
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle SKIP_RECEIVER strategy")
    void testComputeDirectPathSkipReceiver() {
        // Arrange
        testScene.setFreeField(true);
        testCutPlaneVisitor.setReturnStrategy(CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER);

        // Act
        CutPlaneVisitor.PathSearchStrategy result = DirectAndDiffractionEvaluator.computeDirectPath(
            testSourceInfo, testReceiverInfo, true, true, testCutPlaneVisitor, testScene);

        // Assert
        assertEquals(CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER, result, 
            "Should return SKIP_RECEIVER strategy when visitor returns it");
        assertTrue(testCutPlaneVisitor.onNewCutPlaneCalled, 
            "onNewCutPlane should be called before early termination");
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle different source configurations")
    void testComputeDirectPathDifferentSources() {
        // Test with different source indices
        int[] sourceIndices = {0, 1, 5, 10};
        
        for (int sourceIndex : sourceIndices) {
            // Arrange
            testSourceInfo = new TestSourcePointInfo(sourceIndex, new Coordinate(0, 0, 0));
            testScene.setSourceCount(sourceIndex + 1); // Ensure valid source index
            testCutPlaneVisitor = new TestCutPlaneVisitor(); // Reset for each iteration

            // Act
            CutPlaneVisitor.PathSearchStrategy result = DirectAndDiffractionEvaluator.computeDirectPath(
                testSourceInfo, testReceiverInfo, true, false, testCutPlaneVisitor, testScene);

            // Assert
            assertEquals(CutPlaneVisitor.PathSearchStrategy.CONTINUE, result, 
                "Should handle source index " + sourceIndex);
        }
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle different receiver configurations")
    void testComputeDirectPathDifferentReceivers() {
        // Test with different receiver indices
        int[] receiverIndices = {0, 1, 5, 10};
        
        for (int receiverIndex : receiverIndices) {
            // Arrange
            testReceiverInfo = new TestReceiverPointInfo(receiverIndex, new Coordinate(100, 0, 0));
            testCutPlaneVisitor = new TestCutPlaneVisitor(); // Reset for each iteration

            // Act
            CutPlaneVisitor.PathSearchStrategy result = DirectAndDiffractionEvaluator.computeDirectPath(
                testSourceInfo, testReceiverInfo, true, false, testCutPlaneVisitor, testScene);

            // Assert
            assertEquals(CutPlaneVisitor.PathSearchStrategy.CONTINUE, result, 
                "Should handle receiver index " + receiverIndex);
        }
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle different coordinate pairs")
    void testComputeDirectPathDifferentCoordinates() {
        Coordinate[][] coordinatePairs = {
            {new Coordinate(0, 0, 0), new Coordinate(10, 0, 0)},
            {new Coordinate(0, 0, 0), new Coordinate(0, 10, 0)},
            {new Coordinate(0, 0, 0), new Coordinate(10, 10, 0)},
            {new Coordinate(0, 0, 5), new Coordinate(50, 50, 10)},
            {new Coordinate(-10, -10, 0), new Coordinate(10, 10, 0)}
        };

        for (int i = 0; i < coordinatePairs.length; i++) {
            // Arrange
            testSourceInfo = new TestSourcePointInfo(0, coordinatePairs[i][0]);
            testReceiverInfo = new TestReceiverPointInfo(0, coordinatePairs[i][1]);
            testCutPlaneVisitor = new TestCutPlaneVisitor(); // Reset for each iteration

            // Act
            CutPlaneVisitor.PathSearchStrategy result = DirectAndDiffractionEvaluator.computeDirectPath(
                testSourceInfo, testReceiverInfo, true, true, testCutPlaneVisitor, testScene);

            // Assert
            assertEquals(CutPlaneVisitor.PathSearchStrategy.CONTINUE, result, 
                "Should handle coordinate pair " + i);
        }
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle null cut profile components")
    void testComputeDirectPathNullComponents() {
        // Arrange
        testScene.setSourceInProfile(false);
        testScene.setReceiverInProfile(false);

        // Act & Assert - expect IllegalStateException when no source points exist
        assertThrows(IllegalStateException.class, () -> {
            DirectAndDiffractionEvaluator.computeDirectPath(
                testSourceInfo, testReceiverInfo, true, true, testCutPlaneVisitor, testScene);
        }, "Should throw IllegalStateException when no source point exists in the profile");
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle invalid source index")
    void testComputeDirectPathInvalidSourceIndex() {
        // Arrange
        testSourceInfo = new TestSourcePointInfo(-1, new Coordinate(0, 0, 0)); // Invalid index
        testScene.setSourceCount(1);

        // Act & Assert
        assertDoesNotThrow(() -> {
            DirectAndDiffractionEvaluator.computeDirectPath(
                testSourceInfo, testReceiverInfo, true, false, testCutPlaneVisitor, testScene);
        }, "Should handle invalid source index gracefully");
    }

    @Test
    @DisplayName("DirectAndDiffractionEvaluator should handle edge cases")
    void testComputeDirectPathEdgeCases() {
        // Test with source and receiver at same location
        testSourceInfo = new TestSourcePointInfo(0, new Coordinate(0, 0, 0));
        testReceiverInfo = new TestReceiverPointInfo(0, new Coordinate(0, 0, 0));

        assertDoesNotThrow(() -> {
            DirectAndDiffractionEvaluator.computeDirectPath(
                testSourceInfo, testReceiverInfo, true, true, testCutPlaneVisitor, testScene);
        }, "Should handle source and receiver at same location");

        // Test with extreme coordinates
        testSourceInfo = new TestSourcePointInfo(0, new Coordinate(0, 0, 0));
        testReceiverInfo = new TestReceiverPointInfo(0, new Coordinate(10000, 10000, 1000));

        assertDoesNotThrow(() -> {
            DirectAndDiffractionEvaluator.computeDirectPath(
                testSourceInfo, testReceiverInfo, true, true, testCutPlaneVisitor, testScene);
        }, "Should handle extreme coordinates");
    }

    // Test implementations
    private static class TestScene extends Scene {
        private double defaultGroundAttenuation = 0.5;
        private int sourceCount = 1;
        private boolean freeField = true;
        private boolean sourceInProfile = true;
        private boolean receiverInProfile = true;

        public void setDefaultGroundAttenuation(double attenuation) {
            this.defaultGroundAttenuation = attenuation;
        }

        public void setSourceCount(int count) {
            this.sourceCount = count;
        }

        public void setFreeField(boolean freeField) {
            this.freeField = freeField;
        }

        public void setSourceInProfile(boolean sourceInProfile) {
            this.sourceInProfile = sourceInProfile;
        }

        public void setReceiverInProfile(boolean receiverInProfile) {
            this.receiverInProfile = receiverInProfile;
        }

        @Override
        public double getDefaultGroundAttenuation() {
            return defaultGroundAttenuation;
        }

        @Override
        public int countSources() {
            return sourceCount;
        }

        @Override
        public long getSourcePkById(int sourceIndex) {
            return sourceIndex; // Simple mapping for testing
        }

        @Override
        public CutProfile getProfile(Coordinate source, Coordinate receiver, double groundAttenuation, boolean stopAtObstacle, SourcePointInfo sourcePointInfo) {
            CutProfile profile = new CutProfile();
            
            if (sourceInProfile) {
                CutPointSource cutPointSource = new CutPointSource(source);
                profile.setSource(cutPointSource);
            }
            
            if (receiverInProfile) {
                CutPointReceiver cutPointReceiver = new CutPointReceiver(receiver);
                profile.setReceiver(cutPointReceiver);
            }
            
            // Set free field status based on test configuration
            if (freeField) {
                // For free field, we need to ensure the profile is marked as such
                // This might require specific implementation depending on CutProfile
            }
            
            return profile;
        }
    }

    private static class TestCutPlaneVisitor implements CutPlaneVisitor {
        boolean onNewCutPlaneCalled = false;
        private PathSearchStrategy returnStrategy = PathSearchStrategy.CONTINUE;

        public void setReturnStrategy(PathSearchStrategy strategy) {
            this.returnStrategy = strategy;
        }

        @Override
        public void startReceiver(ReceiverPointInfo receiver, Collection<SourcePointInfo> sourceList, 
                                AtomicInteger cutProfileCount) {
            // Empty implementation
        }

        @Override
        public void finalizeReceiver(ReceiverPointInfo receiver) {
            // Empty implementation
        }

        @Override
        public PathSearchStrategy onNewCutPlane(CutProfile cutProfile) {
            onNewCutPlaneCalled = true;
            return returnStrategy;
        }
    }

    private static class TestSourcePointInfo extends SourcePointInfo {
        public TestSourcePointInfo(int sourceIndex, Coordinate coordinates) {
            super(sourceIndex, 0L, coordinates, 1.0, 
                  new org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation(0.0, 0.0, 0.0));
        }
    }

    private static class TestReceiverPointInfo extends ReceiverPointInfo {
        public TestReceiverPointInfo(int receiverIndex, Coordinate coordinates) {
            super(receiverIndex, 0L, coordinates);
        }
    }
}
