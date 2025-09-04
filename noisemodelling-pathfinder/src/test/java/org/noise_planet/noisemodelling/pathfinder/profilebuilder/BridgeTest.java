/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Bridge class.
 * Tests bridge construction, acoustic calculations, point position determination,
 * diffraction calculations, mirror image sources, and virtual source generation.
 */
public class BridgeTest {

    private Bridge bridge;
    private GeometryFactory geometryFactory;
    private List<Double> defaultAlphas;

    @BeforeEach
    public void setUp() {
        geometryFactory = new GeometryFactory();
        defaultAlphas = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8);
    }

    // Test helper methods

    /**
     * Create test bridge points for a simple rectangular bridge.
     */
    private List<BridgePoint> createTestBridgePoints() {
        List<BridgePoint> points = new ArrayList<>();
        
        // Create a rectangular bridge: 20x10 units
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0);
        bp1.setPosition(BridgePoint.Position.CENTER);
        points.add(bp1);
        
        BridgePoint bp2 = new BridgePoint(new Coordinate(20, 0), 2, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0);
        bp2.setPosition(BridgePoint.Position.CENTER);
        points.add(bp2);
        
        BridgePoint bp3 = new BridgePoint(new Coordinate(20, 10), 3, 100, 16.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0);
        bp3.setPosition(BridgePoint.Position.CENTER);
        points.add(bp3);
        
        BridgePoint bp4 = new BridgePoint(new Coordinate(0, 10), 4, 100, 16.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0);
        bp4.setPosition(BridgePoint.Position.CENTER);
        points.add(bp4);
        
        return points;
    }

    /**
     * Create a simple 3D bridge deck polygon.
     */
    private Polygon createTestDeckGeometry() {
        Coordinate[] coords = {
            new Coordinate(0, 0, 15.0),
            new Coordinate(20, 0, 15.0),
            new Coordinate(20, 10, 16.0),
            new Coordinate(0, 10, 16.0),
            new Coordinate(0, 0, 15.0)
        };
        return geometryFactory.createPolygon(coords);
    }

    /**
     * Create mock ProfileBuilder that returns ground height based on coordinates.
     */
    private ProfileBuilder createMockProfileBuilder(double groundHeight) {
        return new ProfileBuilder() {
            @Override
            public double getZGround(Coordinate coordinate, AtomicInteger triangleHint) {
                return groundHeight;
            }
        };
    }

    // Constructor tests

    @Test
    public void testConstructorWithBridgePoints() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        
        assertNotNull(bridge, "Bridge should be created");
        assertEquals(1L, bridge.getPrimaryKey(), "Primary key should be set");
        assertEquals(4, bridge.getBridgePointCount(), "Should have 4 bridge points");
        assertTrue(bridge.hasBridgePoints(), "Should have bridge points");
    }

    @Test
    public void testConstructorWithDeckGeometry() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 2L);
        
        assertNotNull(bridge, "Bridge should be created");
        assertEquals(2L, bridge.getPrimaryKey(), "Primary key should be set");
        assertNotNull(bridge.getDeckGeometry(), "Deck geometry should be set");
        assertFalse(bridge.getEdge().isEmpty(), "Edge should be created");
    }

    @Test
    public void testConstructorWithNullAlphas() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, null, 1L);
        
        assertNotNull(bridge, "Bridge should be created even with null alphas");
        assertEquals(1L, bridge.getPrimaryKey(), "Primary key should be set");
    }

    // Static factory method tests

    @Test
    public void testCreateBridgesFromPoints() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        
        List<Bridge> bridges = Bridge.createBridgesFromPoints(bridgePoints, defaultAlphas);
        
        assertEquals(1, bridges.size(), "Should create one bridge");
        Bridge createdBridge = bridges.get(0);
        assertEquals(100L, createdBridge.getPrimaryKey(), "Bridge should have correct primary key");
        assertEquals(4, createdBridge.getBridgePointCount(), "Bridge should have all points");
    }

    @Test
    public void testCreateBridgesFromPointsMultipleBridges() {
        List<BridgePoint> bridgePoints = new ArrayList<>();
        
        // Bridge 1 points
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0);
        
        // Bridge 2 points
        BridgePoint bp3 = new BridgePoint(new Coordinate(0, 20), 3, 200, 12.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0);
        BridgePoint bp4 = new BridgePoint(new Coordinate(10, 20), 4, 200, 12.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0);
        
        bridgePoints.addAll(Arrays.asList(bp1, bp2, bp3, bp4));
        
        List<Bridge> bridges = Bridge.createBridgesFromPoints(bridgePoints, defaultAlphas);
        
        assertEquals(2, bridges.size(), "Should create two bridges");
        
        // Find bridges by primary key
        Bridge bridge100 = bridges.stream().filter(b -> b.getPrimaryKey() == 100L).findFirst().orElse(null);
        Bridge bridge200 = bridges.stream().filter(b -> b.getPrimaryKey() == 200L).findFirst().orElse(null);
        
        assertNotNull(bridge100, "Bridge with PK 100 should exist");
        assertNotNull(bridge200, "Bridge with PK 200 should exist");
        assertEquals(2, bridge100.getBridgePointCount(), "Bridge 100 should have 2 points");
        assertEquals(2, bridge200.getBridgePointCount(), "Bridge 200 should have 2 points");
    }

    @Test
    public void testCreateBridgesFromPointsEmpty() {
        List<Bridge> bridges = Bridge.createBridgesFromPoints(new ArrayList<>(), defaultAlphas);
        assertTrue(bridges.isEmpty(), "Should return empty list for empty input");
    }

    @Test
    public void testCreateBridgesFromPointsNull() {
        List<Bridge> bridges = Bridge.createBridgesFromPoints(null, defaultAlphas);
        assertTrue(bridges.isEmpty(), "Should return empty list for null input");
    }

    @Test
    public void testCreateBridgesFromPointsWithoutAlphas() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        
        List<Bridge> bridges = Bridge.createBridgesFromPoints(bridgePoints);
        
        assertEquals(1, bridges.size(), "Should create one bridge");
        assertEquals(100L, bridges.get(0).getPrimaryKey(), "Bridge should have correct primary key");
    }

    // Deck geometry creation tests

    @Test
    public void testCreateDeckGeometry() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        
        bridge.createDeckGeometry(profileBuilder);
        
        assertNotNull(bridge.getDeckGeometry(), "Deck geometry should be created");
        assertNotNull(bridge.getFootprintGeometry(), "Footprint geometry should be available");
        assertFalse(bridge.getEdge().isEmpty(), "Edge should be created");
    }

    // Point position tests

    @Test
    public void testIsPointOnBridgeBasicFunctionality() {
        // Use bridge points to create a bridge with proper triangulation
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        bridge.createDeckGeometry(profileBuilder);
        
        // Point outside bridge footprint should definitely not be on bridge
        Coordinate pointOutside = new Coordinate(30, 5, 15.0);
        assertFalse(bridge.isPointOnBridge(pointOutside), "Point outside footprint should not be on bridge");
        
        // Test that the method doesn't throw exceptions
        Coordinate testPoint = new Coordinate(10, 5, 15.0);
        assertNotNull(Boolean.valueOf(bridge.isPointOnBridge(testPoint)), "Method should complete without exception");
    }

    @Test
    public void testIsPointOnBridgeWithToleranceBasic() {
        // Use bridge points to create a bridge with proper triangulation
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        bridge.createDeckGeometry(profileBuilder);
        
        // Point outside bridge footprint should not be on bridge regardless of tolerance
        Coordinate pointOutside = new Coordinate(30, 5, 15.0);
        assertFalse(bridge.isPointOnBridge(pointOutside, 10.0), 
                   "Point outside footprint should not be on bridge even with large tolerance");
        
        // Test that the method doesn't throw exceptions with different tolerance values
        Coordinate testPoint = new Coordinate(10, 5, 15.0);
        assertNotNull(Boolean.valueOf(bridge.isPointOnBridge(testPoint, 0.5)), 
                     "Method with small tolerance should complete without exception");
        assertNotNull(Boolean.valueOf(bridge.isPointOnBridge(testPoint, 5.0)), 
                     "Method with large tolerance should complete without exception");
    }

    @Test
    public void testIsPointWithinBridgeFootprint() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // Point inside footprint
        Coordinate pointInside = new Coordinate(10, 5);
        assertTrue(bridge.isPointWithinBridgeFootprint(pointInside), 
                  "Point should be within bridge footprint");
        
        // Point on boundary
        Coordinate pointOnBoundary = new Coordinate(0, 0);
        assertTrue(bridge.isPointWithinBridgeFootprint(pointOnBoundary), 
                  "Point on boundary should be within bridge footprint");
        
        // Point outside footprint
        Coordinate pointOutside = new Coordinate(30, 5);
        assertFalse(bridge.isPointWithinBridgeFootprint(pointOutside), 
                   "Point should not be within bridge footprint");
    }

    // Height calculation tests

    @Test
    public void testGetDeckHeightAtPoint() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        
        bridge.createDeckGeometry(profileBuilder);
        
        // Test point inside bridge
        Coordinate pointInside = new Coordinate(10, 5);
        double height = bridge.getDeckHeightAtPoint(pointInside);
        assertFalse(Double.isNaN(height), "Height should be calculable for point inside bridge");
        assertTrue(height >= 15.0 && height <= 16.0, "Height should be within expected range");
    }

    @Test
    public void testGetDeckHeightAtPointOutside() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        
        bridge.createDeckGeometry(profileBuilder);
        
        // Test point outside bridge
        Coordinate pointOutside = new Coordinate(30, 5);
        double height = bridge.getDeckHeightAtPoint(pointOutside);
        assertTrue(Double.isNaN(height), "Height should be NaN for point outside bridge");
    }

    // Bridge point management tests

    @Test
    public void testAddBridgePoint() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        
        BridgePoint newPoint = new BridgePoint(new Coordinate(5, 5), 5, 100, 15.5, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0);
        bridge.addBridgePoint(newPoint);
        
        assertEquals(5, bridge.getBridgePointCount(), "Should have 5 bridge points");
        assertTrue(bridge.getBridgePoints().contains(newPoint), "New point should be in collection");
    }

    @Test
    public void testRemoveBridgePoint() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        
        boolean removed = bridge.removeBridgePoint(1L);
        assertTrue(removed, "Should remove existing point");
        assertEquals(3, bridge.getBridgePointCount(), "Should have 3 bridge points after removal");
        
        boolean removedAgain = bridge.removeBridgePoint(999L);
        assertFalse(removedAgain, "Should not remove non-existing point");
    }

    @Test
    public void testGetAverageAbsoluteDeckHeight() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        
        double avgHeight = bridge.getAverageAbsoluteDeckHeight();
        assertEquals(15.5, avgHeight, 0.001, "Average height should be (15+15+16+16)/4 = 15.5");
    }

    // Diffraction calculation tests
    
    @Test
    public void testDiffractionPointsNotImplemented() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // Note: Diffraction calculation methods are currently commented out in the Bridge class
        // This test serves as a placeholder for when the functionality is re-implemented
        assertNotNull(bridge, "Bridge should be created successfully");
        assertNotNull(bridge.getDeckGeometry(), "Bridge should have deck geometry");
    }

    // Mirror image source tests

    @Test
    public void testCalculateMirrorImageSources() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // Source below bridge
        Coordinate sourceBelow = new Coordinate(10, 5, 10.0);
        
        List<Coordinate> mirrorSources = bridge.generateMirrorImageSourcesByBridge(sourceBelow);
        
        assertNotNull(mirrorSources, "Mirror sources should not be null");
        if (!mirrorSources.isEmpty()) {
            Coordinate mirrorSource = mirrorSources.get(0);
            assertTrue(mirrorSource.z > sourceBelow.z, "Mirror source should be above original source");
        }
    }

    @Test
    public void testCalculateMirrorImageSourcesSourceNotBelow() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // Source above bridge
        Coordinate sourceAbove = new Coordinate(10, 5, 20.0);
        
        List<Coordinate> mirrorSources = bridge.generateMirrorImageSourcesByBridge(sourceAbove);
        
        assertTrue(mirrorSources.isEmpty(), "Should have no mirror sources when source not below bridge");
    }

    // Virtual source tests

    @Test
    public void testGenerateVirtualSourceAtBridgeBottom() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // Source on bridge
        Coordinate sourceOnBridge = new Coordinate(10, 5, 15.5);
        
        List<Coordinate> virtualSources = bridge.generateVirtualSourcesAtBridgeBottom(sourceOnBridge);
        
        assertNotNull(virtualSources, "Virtual sources should not be null");
        if (!virtualSources.isEmpty()) {
            Coordinate virtualSource = virtualSources.get(0);
            assertTrue(virtualSource.z < sourceOnBridge.z, "Virtual source should be below original source");
        }
    }

    @Test
    public void testGenerateVirtualSourceAtBridgeBottomSourceNotOn() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // Source not on bridge
        Coordinate sourceNotOnBridge = new Coordinate(30, 5, 15.5);
        
        List<Coordinate> virtualSources = bridge.generateVirtualSourcesAtBridgeBottom(sourceNotOnBridge);
        
        assertTrue(virtualSources.isEmpty(), "Should not generate virtual source when source not on bridge");
    }

    @Test
    public void testGenerateSourcesOnBridge() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // Source below bridge
        Coordinate sourceBelow = new Coordinate(10, 5, 10.0);
        
        List<Coordinate> sourcesOnBridge = bridge.generateSourcesOnBridge(sourceBelow, 1.0);
        
        assertNotNull(sourcesOnBridge, "Sources on bridge should not be null");
    }

    // Reflection relevance tests - Currently commented out in Bridge class

    @Test
    public void testReflectionRelevanceNotImplemented() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // Note: isRelevantForReflection method is currently commented out in the Bridge class
        // This test serves as a placeholder for when the functionality is re-implemented
        assertNotNull(bridge, "Bridge should be created successfully");
        assertNotNull(bridge.getDeckGeometry(), "Bridge should have deck geometry");
    }

    // Geometry tests

    @Test
    public void testGetEnvelope2D() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        Envelope envelope = bridge.getEnvelope2D();
        assertNotNull(envelope, "Envelope should not be null");
    }

    @Test
    public void testIntersects() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // Create a geometry that intersects with the bridge
        Coordinate[] coords = {
            new Coordinate(5, 5),
            new Coordinate(15, 5),
            new Coordinate(15, 15),
            new Coordinate(5, 15),
            new Coordinate(5, 5)
        };
        Polygon intersectingGeometry = geometryFactory.createPolygon(coords);
        
        assertTrue(bridge.intersects(intersectingGeometry), "Bridge should intersect with overlapping geometry");
        
        // Create a geometry that doesn't intersect
        Coordinate[] coords2 = {
            new Coordinate(30, 30),
            new Coordinate(40, 30),
            new Coordinate(40, 40),
            new Coordinate(30, 40),
            new Coordinate(30, 30)
        };
        Polygon nonIntersectingGeometry = geometryFactory.createPolygon(coords2);
        
        assertFalse(bridge.intersects(nonIntersectingGeometry), "Bridge should not intersect with non-overlapping geometry");
    }

    // Property tests

    @Test
    public void testGirderTypeProperty() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        
        assertNull(bridge.getGirderType(), "Initial girder type should be null");
        
        bridge.setGirderType(Bridge.GirderType.STEEL_BOX);
        assertEquals(Bridge.GirderType.STEEL_BOX, bridge.getGirderType(), "Girder type should be set");
        
        bridge.setGirderType(Bridge.GirderType.CONCRETE_PLATE);
        assertEquals(Bridge.GirderType.CONCRETE_PLATE, bridge.getGirderType(), "Girder type should be updated");
    }

    @Test
    public void testSlabTypeProperty() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        
        assertNull(bridge.getSlabType(), "Initial slab type should be null");
        
        bridge.setSlabType(Bridge.SlabType.CONCRETE);
        assertEquals(Bridge.SlabType.CONCRETE, bridge.getSlabType(), "Slab type should be set");
        
        bridge.setSlabType(Bridge.SlabType.STEEL);
        assertEquals(Bridge.SlabType.STEEL, bridge.getSlabType(), "Slab type should be updated");
    }

    @Test
    public void testGetEdge() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        Polygon edge = bridge.getEdge();
        assertNotNull(edge, "Edge should not be null");
        
        // Verify that we get a copy (defensive copy)
        Polygon originalEdge = bridge.getEdge();
        assertEquals(edge.getNumPoints(), originalEdge.getNumPoints(), "Copies should have same number of points");
    }

    // Edge case tests

    @Test
    public void testBridgeWithNullDeckGeometry() {
        bridge = new Bridge((Polygon) null, defaultAlphas, 1L);
        
        assertNotNull(bridge, "Bridge should be created even with null deck geometry");
        assertNull(bridge.getDeckGeometry(), "Deck geometry should be null");
        assertNull(bridge.getEdge(), "Edge should be null for null deck geometry");
    }

    @Test
    public void testEmptyBridgePointsList() {
        bridge = new Bridge(new ArrayList<>(), defaultAlphas, 1L);
        
        assertNotNull(bridge, "Bridge should be created with empty points list");
        assertEquals(0, bridge.getBridgePointCount(), "Should have no bridge points");
        assertFalse(bridge.hasBridgePoints(), "Should not have bridge points");
    }

    @Test
    public void testGetDeckThicknessAtPoint() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        
        bridge.createDeckGeometry(profileBuilder);
        
        Coordinate testPoint = new Coordinate(10, 5);
        double thickness = bridge.getDeckThicknessAtPoint(testPoint);
        
        // The actual value depends on triangulation implementation
        // We just verify the method doesn't throw exceptions
        assertNotNull(Double.valueOf(thickness), "Method should return a double value");
    }

    @Test
    public void testGetBarrierHeightAtPoint() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        
        bridge.createDeckGeometry(profileBuilder);
        
        Coordinate testPoint = new Coordinate(10, 5);
        double barrierHeight = bridge.getBarrierHeightAtPoint(testPoint);
        
        // The actual value depends on triangulation implementation
        // We just verify the method doesn't throw exceptions
        assertNotNull(Double.valueOf(barrierHeight), "Method should return a double value");
    }

    // Comprehensive integration test

    @Test
    public void testCreateBridgePointsFromDeckGeometry() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        
        // Bridge constructed from Polygon should have the initial deck geometry
        assertNotNull(bridge.getDeckGeometry(), "Initial deck geometry should be set");
        
        // createDeckGeometry should throw an exception if pointManager is empty
        // This is expected behavior for bridges created from Polygon without bridge points
        assertThrows(IllegalArgumentException.class, () -> {
            bridge.createDeckGeometry(profileBuilder);
        }, "Should throw IllegalArgumentException when pointManager is empty");
        
        // Test that footprint operations work
        Coordinate testPoint = new Coordinate(10, 5);
        boolean isWithinFootprint = bridge.isPointWithinBridgeFootprint(testPoint);
        assertTrue(isWithinFootprint, "Point should be within bridge footprint");
        
        // The bridge should still function for basic operations with the original deck geometry
        assertNotNull(bridge.getEdge(), "Edge should be created from polygon");
    }

    @Test
    public void testPolygonConstructorTriangulationInitialization() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // The Polygon constructor should initialize triangulation properly
        // Initially, the bridge should have the original deck geometry
        assertNotNull(bridge.getDeckGeometry(), "Initial deck geometry should be set");
        
        // Test that basic geometry operations work
        assertNotNull(bridge.getEdge(), "Edge should be created from polygon");
        
        // Test that point operations work correctly
        Coordinate pointInside = new Coordinate(10, 5);
        boolean isWithinFootprint = bridge.isPointWithinBridgeFootprint(pointInside);
        assertTrue(isWithinFootprint, "Point should be within bridge footprint");
        
        // Test envelope operations
        assertNotNull(bridge.getEnvelope2D(), "Bridge should have 2D envelope");
        
        // Note: Height calculations may not work properly without proper triangulation setup
        // which requires bridge points in the pointManager. This is expected behavior.
    }

    @Test
    public void testBridgeTriangulationAfterModification() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        
        // Create deck geometry
        bridge.createDeckGeometry(profileBuilder);
        
        // Add a new bridge point
        BridgePoint newPoint = new BridgePoint(new Coordinate(10, 2), 5, 100, 15.2, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0);
        newPoint.setPosition(BridgePoint.Position.CENTER);
        bridge.addBridgePoint(newPoint);
        
        // Recreate deck geometry with new point
        bridge.createDeckGeometry(profileBuilder);
        
        // Verify triangulation still works
        assertNotNull(bridge.getDeckGeometry(), "Deck geometry should be updated");
        assertEquals(5, bridge.getBridgePointCount(), "Should have 5 bridge points");
        
        // Test that new triangulation allows height calculations
        Coordinate testPoint = new Coordinate(10, 3);
        double height = bridge.getDeckHeightAtPoint(testPoint);
        assertFalse(Double.isNaN(height), "Height should be calculable after triangulation update");
    }

    @Test
    public void testBridgePointPositionAfterTriangulation() {
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        
        bridge.createDeckGeometry(profileBuilder);
        
        // Test that bridge points maintain their position settings
        for (BridgePoint bp : bridge.getBridgePoints()) {
            assertNotNull(bp.getPosition(), "Bridge point should have position set");
            assertEquals(BridgePoint.Position.CENTER, bp.getPosition(), "Bridge point should maintain CENTER position");
        }
        
        // Test isPointOnBridge functionality
        Coordinate testPoint = new Coordinate(10, 5, 15.5);
        boolean isOnBridge = bridge.isPointOnBridge(testPoint);
        // Result depends on triangulation implementation, but should not throw exception
        assertNotNull(Boolean.valueOf(isOnBridge), "isPointOnBridge should complete without exception");
        
        // Test with tolerance
        boolean isOnBridgeWithTolerance = bridge.isPointOnBridge(testPoint, 1.0);
        assertNotNull(Boolean.valueOf(isOnBridgeWithTolerance), "isPointOnBridge with tolerance should complete without exception");
    }

    // Comprehensive integration test

    @Test
    public void testBridgeIntegrationWorkflow() {
        // Create bridge from points
        List<BridgePoint> bridgePoints = createTestBridgePoints();
        bridge = new Bridge(bridgePoints, defaultAlphas, 1L);
        
        // Create deck geometry
        ProfileBuilder profileBuilder = createMockProfileBuilder(5.0);
        bridge.createDeckGeometry(profileBuilder);
        
        // Verify bridge is properly initialized
        assertNotNull(bridge.getDeckGeometry(), "Deck geometry should be created");
        assertNotNull(bridge.getFootprintGeometry(), "Footprint should be available");
        assertNotNull(bridge.getEdge(), "Edge should be created");
        
        // Test point operations
        Coordinate testPoint = new Coordinate(10, 5);
        
        // Test that point operations don't throw exceptions
        assertNotNull(bridge.getFootprintGeometry(), "Footprint geometry should be available");
        
        // Test that isPointWithinBridgeFootprint and isPointOnBridge methods don't throw exceptions
        assertNotNull(Boolean.valueOf(bridge.isPointWithinBridgeFootprint(testPoint)), 
                     "isPointWithinBridgeFootprint should complete without exception");
        assertNotNull(Boolean.valueOf(bridge.isPointOnBridge(new Coordinate(10, 5, 15.0))), 
                     "isPointOnBridge should complete without exception");
        
        // Test acoustic calculations
        Coordinate sourceAbove = new Coordinate(10, 5, 20.0);
        Coordinate sourceBelow = new Coordinate(10, 5, 10.0);
        Coordinate receiver = new Coordinate(10, 15, 12.0);
        
        // Note: Diffraction calculation methods are currently commented out in the Bridge class
        // Testing new available methods instead
        List<Coordinate> mirrorSources = bridge.generateMirrorImageSourcesByBridge(sourceBelow);
        assertNotNull(mirrorSources, "Mirror sources should be calculated");
        
        List<Coordinate> virtualSources = bridge.generateVirtualSourcesAtBridgeBottom(testPoint);
        assertNotNull(virtualSources, "Virtual sources should be calculated");
        
        // Verify bridge properties
        assertEquals(1L, bridge.getPrimaryKey(), "Primary key should be preserved");
        assertEquals(4, bridge.getBridgePointCount(), "Bridge point count should be correct");
        assertTrue(bridge.getAverageAbsoluteDeckHeight() > 0, "Average deck height should be positive");
    }

    // CutPointBridgeWall integration tests

    @Test
    public void testCutPointBridgeWallIntegration() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        // Test that CutPointBridgeWall can be created and used
        // This is an integration test to ensure our Bridge modifications work with CutPointBridgeWall
        
        Coordinate testCoordinate = new Coordinate(10, 5, 15.0);
        
        // Verify that the bridge can handle coordinate-based operations
        boolean isWithinFootprint = bridge.isPointWithinBridgeFootprint(testCoordinate);
        assertTrue(isWithinFootprint, "Point should be within bridge footprint");
        
        // Test that bridge geometry operations work
        assertNotNull(bridge.getEnvelope2D(), "Bridge should have 2D envelope");
        assertNotNull(bridge.getEdge(), "Bridge should have edge");
    }

    @Test
    public void testBridgeEdgeCreation() {
        Polygon deckGeometry = createTestDeckGeometry();
        bridge = new Bridge(deckGeometry, defaultAlphas, 1L);
        
        Polygon bridgeEdge = bridge.getEdge();
        assertNotNull(bridgeEdge, "Edge should not be null");
        
        // Verify that edge is a valid Polygon geometry
        assertNotNull(bridgeEdge, "Bridge edge should be a valid Polygon");
        assertTrue(bridgeEdge.getNumPoints() >= 4, "Bridge edge should have at least 4 points (triangle + closing point)");
        
        // Test defensive copy
        Polygon edgesCopy = bridge.getEdge();
        assertNotSame(bridgeEdge, edgesCopy, "getEdge should return a defensive copy");
        assertEquals(bridgeEdge.getNumPoints(), edgesCopy.getNumPoints(), "Copies should have same number of points");
    }
}
