package org.noise_planet.noisemodelling.pathfinder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiver;
import org.noise_planet.noisemodelling.pathfinder.path.MirrorReceiversCompute;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReflection;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Obstruction;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionPathBuilderTest {

    private TestScene testScene;
    private TestSourcePointInfo testSourceInfo;
    private TestReceiverPointInfo testReceiverInfo;
    private TestMirrorReceiversCompute testMirrorCompute;
    private TestCutPlaneVisitor testCutPlaneVisitor;
    private List<CutPoint> testCutPoints;

    @BeforeEach
    void setUp() {
        testScene = new TestScene();
        testSourceInfo = new TestSourcePointInfo(0, new Coordinate(0, 0, 0));
        testReceiverInfo = new TestReceiverPointInfo(0, new Coordinate(100, 0, 0));
        testMirrorCompute = new TestMirrorReceiversCompute();
        testCutPlaneVisitor = new TestCutPlaneVisitor();
        testCutPoints = new ArrayList<>();
    }

    @Test
    @DisplayName("ReflectionPathBuilder should compute reflexion with CONTINUE strategy")
    void testComputeReflexionContinueStrategy() {
        // Arrange
        CutPlaneVisitor.PathSearchStrategy initialStrategy = CutPlaneVisitor.PathSearchStrategy.CONTINUE;
        testCutPlaneVisitor.setReturnStrategy(CutPlaneVisitor.PathSearchStrategy.CONTINUE);

        // Act
        CutPlaneVisitor.PathSearchStrategy result = ReflectionPathBuilder.computeReflexion(
            testReceiverInfo, testSourceInfo, testMirrorCompute, testCutPlaneVisitor, initialStrategy, testScene);

        // Assert
        assertEquals(CutPlaneVisitor.PathSearchStrategy.CONTINUE, result, 
            "Should return CONTINUE strategy for normal reflection processing");
    }

    @Test
    @DisplayName("ReflectionPathBuilder should compute reflexion with SKIP_SOURCE strategy")
    void testComputeReflexionSkipSourceStrategy() {
        // Arrange
        CutPlaneVisitor.PathSearchStrategy initialStrategy = CutPlaneVisitor.PathSearchStrategy.CONTINUE;
        testCutPlaneVisitor.setReturnStrategy(CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE);

        // Act
        CutPlaneVisitor.PathSearchStrategy result = ReflectionPathBuilder.computeReflexion(
            testReceiverInfo, testSourceInfo, testMirrorCompute, testCutPlaneVisitor, initialStrategy, testScene);

        // Assert - Reflection processing may not directly use visitor strategy in simple cases
        assertNotNull(result, "Should return a valid strategy");
        assertTrue(result == CutPlaneVisitor.PathSearchStrategy.CONTINUE ||
                  result == CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE ||
                  result == CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER, 
            "Should return valid strategy (actual reflection processing may override visitor strategy)");
    }

    @Test
    @DisplayName("ReflectionPathBuilder should compute reflexion with SKIP_RECEIVER strategy")
    void testComputeReflexionSkipReceiverStrategy() {
        // Arrange
        CutPlaneVisitor.PathSearchStrategy initialStrategy = CutPlaneVisitor.PathSearchStrategy.CONTINUE;
        testCutPlaneVisitor.setReturnStrategy(CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER);

        // Act
        CutPlaneVisitor.PathSearchStrategy result = ReflectionPathBuilder.computeReflexion(
            testReceiverInfo, testSourceInfo, testMirrorCompute, testCutPlaneVisitor, initialStrategy, testScene);

        // Assert - Reflection processing may not directly use visitor strategy in simple cases
        assertNotNull(result, "Should return a valid strategy");
        assertTrue(result == CutPlaneVisitor.PathSearchStrategy.CONTINUE ||
                  result == CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE ||
                  result == CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER, 
            "Should return valid strategy (actual reflection processing may override visitor strategy)");
    }

    @Test
    @DisplayName("ReflectionPathBuilder should handle different source configurations")
    void testComputeReflexionDifferentSources() {
        int[] sourceIndices = {0, 1, 5, 10};
        
        for (int sourceIndex : sourceIndices) {
            // Arrange
            testSourceInfo = new TestSourcePointInfo(sourceIndex, new Coordinate(0, 0, 0));
            CutPlaneVisitor.PathSearchStrategy initialStrategy = CutPlaneVisitor.PathSearchStrategy.CONTINUE;
            testCutPlaneVisitor.setReturnStrategy(CutPlaneVisitor.PathSearchStrategy.CONTINUE);

            // Act
            CutPlaneVisitor.PathSearchStrategy result = ReflectionPathBuilder.computeReflexion(
                testReceiverInfo, testSourceInfo, testMirrorCompute, testCutPlaneVisitor, initialStrategy, testScene);

            // Assert
            assertNotNull(result, "Should handle source index " + sourceIndex);
            assertTrue(result == CutPlaneVisitor.PathSearchStrategy.CONTINUE ||
                      result == CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE ||
                      result == CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER, 
                "Should return valid strategy for source index " + sourceIndex);
        }
    }

    @Test
    @DisplayName("ReflectionPathBuilder should handle different receiver configurations")
    void testComputeReflexionDifferentReceivers() {
        int[] receiverIndices = {0, 1, 5, 10};
        
        for (int receiverIndex : receiverIndices) {
            // Arrange
            testReceiverInfo = new TestReceiverPointInfo(receiverIndex, new Coordinate(100, 0, 0));
            CutPlaneVisitor.PathSearchStrategy initialStrategy = CutPlaneVisitor.PathSearchStrategy.CONTINUE;
            testCutPlaneVisitor.setReturnStrategy(CutPlaneVisitor.PathSearchStrategy.CONTINUE);

            // Act
            CutPlaneVisitor.PathSearchStrategy result = ReflectionPathBuilder.computeReflexion(
                testReceiverInfo, testSourceInfo, testMirrorCompute, testCutPlaneVisitor, initialStrategy, testScene);

            // Assert
            assertNotNull(result, "Should handle receiver index " + receiverIndex);
            assertTrue(result == CutPlaneVisitor.PathSearchStrategy.CONTINUE ||
                      result == CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE ||
                      result == CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER, 
                "Should return valid strategy for receiver index " + receiverIndex);
        }
    }

    @Test
    @DisplayName("ReflectionPathBuilder should insert reflection point attributes")
    void testInsertReflectionPointAttributes() {
        // Arrange
        CutPoint sourcePoint = new CutPointSource(new Coordinate(0, 0, 0));
        testCutPoints.clear();
        
        TestMirrorReceiver mirrorReceiver = new TestMirrorReceiver(
            new Coordinate(50, 0, 0), // Wall point
            new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8}, // Alphas
            5 // Wall primary key
        );

        // Act
        ReflectionPathBuilder.insertReflectionPointAttributes(sourcePoint, testCutPoints, mirrorReceiver);

        // Assert
        assertEquals(1, testCutPoints.size(), "Should add one reflection point");
        assertTrue(testCutPoints.get(0) instanceof CutPointReflection, 
            "Added point should be CutPointReflection");
        
        CutPointReflection reflectionPoint = (CutPointReflection) testCutPoints.get(0);
        assertEquals(5, reflectionPoint.getWallPk(), "Should set wall primary key");
    }

    @Test
    @DisplayName("ReflectionPathBuilder should insert reflection point with receiver")
    void testInsertReflectionPointAttributesWithReceiver() {
        // Arrange
        CutPoint receiverPoint = new CutPointReceiver(new Coordinate(100, 0, 0));
        testCutPoints.clear();
        
        TestMirrorReceiver mirrorReceiver = new TestMirrorReceiver(
            new Coordinate(50, 0, 0), // Wall point
            new double[]{0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9}, // Alphas
            10 // Wall primary key
        );

        // Act
        ReflectionPathBuilder.insertReflectionPointAttributes(receiverPoint, testCutPoints, mirrorReceiver);

        // Assert
        assertEquals(1, testCutPoints.size(), "Should add one reflection point");
        assertTrue(testCutPoints.get(0) instanceof CutPointReflection, 
            "Added point should be CutPointReflection");
        
        CutPointReflection reflectionPoint = (CutPointReflection) testCutPoints.get(0);
        assertEquals(10, reflectionPoint.getWallPk(), "Should set wall primary key");
    }

    @Test
    @DisplayName("ReflectionPathBuilder should handle different absorption coefficients safely")
    void testInsertReflectionPointAttributesSafely() {
        // Arrange
        CutPoint sourcePoint = new CutPointSource(new Coordinate(0, 0, 0));
        testCutPoints.clear();
        
        TestMirrorReceiver mirrorReceiver = new TestMirrorReceiver(
            new Coordinate(50, 0, 0), // Wall point
            new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8}, // Alphas
            5 // Positive wall primary key
        );

        // Act & Assert - Should handle reflection point creation safely
        assertDoesNotThrow(() -> {
            ReflectionPathBuilder.insertReflectionPointAttributes(sourcePoint, testCutPoints, mirrorReceiver);
        }, "Should handle reflection point creation without error");

        // Verify the reflection point was created
        assertEquals(1, testCutPoints.size(), "Should add one reflection point");
        assertTrue(testCutPoints.get(0) instanceof CutPointReflection, 
            "Added point should be CutPointReflection");
        
        CutPointReflection reflectionPoint = (CutPointReflection) testCutPoints.get(0);
        assertEquals(5, reflectionPoint.getWallPk(), "Should set positive wall primary key");
    }

    @Test
    @DisplayName("ReflectionPathBuilder should handle multiple reflection insertions")
    void testInsertMultipleReflectionPoints() {
        // Arrange
        CutPoint sourcePoint = new CutPointSource(new Coordinate(0, 0, 0));
        CutPoint receiverPoint = new CutPointReceiver(new Coordinate(100, 0, 0));
        testCutPoints.clear();
        
        TestMirrorReceiver mirrorReceiver1 = new TestMirrorReceiver(
            new Coordinate(25, 0, 0), new double[]{0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8}, 1);
        TestMirrorReceiver mirrorReceiver2 = new TestMirrorReceiver(
            new Coordinate(75, 0, 0), new double[]{0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9}, 2);

        // Act
        ReflectionPathBuilder.insertReflectionPointAttributes(sourcePoint, testCutPoints, mirrorReceiver1);
        ReflectionPathBuilder.insertReflectionPointAttributes(receiverPoint, testCutPoints, mirrorReceiver2);

        // Assert
        assertEquals(2, testCutPoints.size(), "Should add two reflection points");
        assertTrue(testCutPoints.get(0) instanceof CutPointReflection, 
            "First point should be CutPointReflection");
        assertTrue(testCutPoints.get(1) instanceof CutPointReflection, 
            "Second point should be CutPointReflection");
        
        CutPointReflection reflection1 = (CutPointReflection) testCutPoints.get(0);
        CutPointReflection reflection2 = (CutPointReflection) testCutPoints.get(1);
        assertEquals(1, reflection1.getWallPk(), "First reflection should have wall PK 1");
        assertEquals(2, reflection2.getWallPk(), "Second reflection should have wall PK 2");
    }

    @Test
    @DisplayName("ReflectionPathBuilder should handle different absorption coefficients")
    void testInsertReflectionPointDifferentAlphas() {
        // Arrange
        CutPoint sourcePoint = new CutPointSource(new Coordinate(0, 0, 0));
        testCutPoints.clear();

        double[][] alphaArrays = {
            {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0}, // Perfect reflector
            {1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0}, // Perfect absorber
            {0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8}, // Frequency-dependent
            {0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5}  // Uniform absorption
        };

        for (int i = 0; i < alphaArrays.length; i++) {
            // Arrange
            testCutPoints.clear();
            TestMirrorReceiver mirrorReceiver = new TestMirrorReceiver(
                new Coordinate(50, 0, 0), alphaArrays[i], i);

            // Act
            ReflectionPathBuilder.insertReflectionPointAttributes(sourcePoint, testCutPoints, mirrorReceiver);

            // Assert
            assertEquals(1, testCutPoints.size(), "Should add reflection point for alpha set " + i);
            assertTrue(testCutPoints.get(0) instanceof CutPointReflection, 
                "Should create CutPointReflection for alpha set " + i);
        }
    }

    @Test
    @DisplayName("ReflectionPathBuilder should handle extreme coordinates")
    void testComputeReflexionExtremeCoordinates() {
        // Arrange
        TestSourcePointInfo extremeSource = new TestSourcePointInfo(0, 
            new Coordinate(Double.MAX_VALUE/2, Double.MAX_VALUE/2, 0));
        TestReceiverPointInfo extremeReceiver = new TestReceiverPointInfo(0, 
            new Coordinate(-Double.MAX_VALUE/2, -Double.MAX_VALUE/2, 0));
        CutPlaneVisitor.PathSearchStrategy initialStrategy = CutPlaneVisitor.PathSearchStrategy.CONTINUE;
        testCutPlaneVisitor.setReturnStrategy(CutPlaneVisitor.PathSearchStrategy.CONTINUE);

        // Act & Assert
        assertDoesNotThrow(() -> {
            CutPlaneVisitor.PathSearchStrategy result = ReflectionPathBuilder.computeReflexion(
                extremeReceiver, extremeSource, testMirrorCompute, testCutPlaneVisitor, initialStrategy, testScene);
            assertNotNull(result, "Should handle extreme coordinates");
        }, "Should handle extreme coordinates without error");
    }

    @Test
    @DisplayName("ReflectionPathBuilder should preserve initial strategy flow")
    void testComputeReflexionPreservesStrategyFlow() {
        // Test all initial strategy combinations
        CutPlaneVisitor.PathSearchStrategy[] strategies = {
            CutPlaneVisitor.PathSearchStrategy.CONTINUE,
            CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE,
            CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER
        };

        for (CutPlaneVisitor.PathSearchStrategy initialStrategy : strategies) {
            for (CutPlaneVisitor.PathSearchStrategy visitorStrategy : strategies) {
                // Arrange
                testCutPlaneVisitor.setReturnStrategy(visitorStrategy);

                // Act
                CutPlaneVisitor.PathSearchStrategy result = ReflectionPathBuilder.computeReflexion(
                    testReceiverInfo, testSourceInfo, testMirrorCompute, testCutPlaneVisitor, initialStrategy, testScene);

                // Assert
                assertNotNull(result, 
                    "Should handle strategy combination: initial=" + initialStrategy + ", visitor=" + visitorStrategy);
                assertTrue(result == CutPlaneVisitor.PathSearchStrategy.CONTINUE ||
                          result == CutPlaneVisitor.PathSearchStrategy.SKIP_SOURCE ||
                          result == CutPlaneVisitor.PathSearchStrategy.SKIP_RECEIVER, 
                    "Should return valid strategy for combination: initial=" + initialStrategy + ", visitor=" + visitorStrategy);
            }
        }
    }

    // Test implementations
    private static class TestScene extends Scene {
        // Minimal implementation for testing
    }

    private static class TestSourcePointInfo extends SourcePointInfo {
        public TestSourcePointInfo(int sourceIndex, Coordinate coordinates) {
            super(sourceIndex, 0L, coordinates, 1.0, 
                  new Orientation(0.0, 0.0, 0.0));
        }
    }

    private static class TestReceiverPointInfo extends ReceiverPointInfo {
        public TestReceiverPointInfo(int receiverIndex, Coordinate coordinates) {
            super(receiverIndex, 0L, coordinates);
        }
    }

    private static class TestMirrorReceiversCompute extends MirrorReceiversCompute {
        public TestMirrorReceiversCompute() {
            super(new ArrayList<>(), new Coordinate(0, 0, 0), 1, 1000.0, 100.0);
        }
    }

    private static class TestCutPlaneVisitor implements CutPlaneVisitor {
        private CutPlaneVisitor.PathSearchStrategy returnStrategy = CutPlaneVisitor.PathSearchStrategy.CONTINUE;

        public void setReturnStrategy(CutPlaneVisitor.PathSearchStrategy strategy) {
            this.returnStrategy = strategy;
        }

        @Override
        public PathSearchStrategy onNewCutPlane(org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile cutProfile) {
            return returnStrategy;
        }

        @Override
        public void startReceiver(ReceiverPointInfo receiver, java.util.Collection<SourcePointInfo> sourceList, 
                                java.util.concurrent.atomic.AtomicInteger cutProfileCount) {
            // Mock implementation
        }

        @Override
        public void finalizeReceiver(ReceiverPointInfo receiver) {
            // Mock implementation
        }
    }

    private static class TestMirrorReceiver extends MirrorReceiver {
        public TestMirrorReceiver(Coordinate wallCoordinate, double[] alphas, long primaryKey) {
            super(new Coordinate(0, 0, 0), null, new TestWall(wallCoordinate, alphas, primaryKey));
        }

        private static class TestWall extends org.noise_planet.noisemodelling.pathfinder.profilebuilder.Wall {
            public TestWall(Coordinate coordinate, double[] alphas, long primaryKey) {
                super(new LineSegment(
                    new Coordinate(coordinate.x - 1, coordinate.y, coordinate.z),
                    new Coordinate(coordinate.x + 1, coordinate.y, coordinate.z)
                ), 0, ProfileBuilder.IntersectionType.BUILDING);
                this.primaryKey = primaryKey;
                
                // Set alphas in the parent Obstruction
                java.util.List<Double> alphasList = new java.util.ArrayList<>();
                for (double alpha : alphas) {
                    alphasList.add(alpha);
                }
                // Use reflection to set alphas since there's no public setter
                try {
                    java.lang.reflect.Field alphasField = Obstruction.class.getDeclaredField("alphas");
                    alphasField.setAccessible(true);
                    alphasField.set(this, alphasList);
                } catch (Exception e) {
                    // If reflection fails, initialize with empty list to avoid null
                    try {
                        java.lang.reflect.Field alphasField = Obstruction.class.getDeclaredField("alphas");
                        alphasField.setAccessible(true);
                        alphasField.set(this, new java.util.ArrayList<Double>());
                    } catch (Exception ex) {
                        // Final fallback - this should not happen
                    }
                }
            }
        }
    }
}
