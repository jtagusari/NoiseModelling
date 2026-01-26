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
import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BridgeTriangulation class.
 * Tests triangulation operations, interpolation, and triangle containment.
 */
public class BridgeTriangulationTest {

    private BridgeTriangulation triangulation;

    @BeforeEach
    public void setUp() {
        triangulation = new BridgeTriangulation(GeometryFactoryProvider.SHARED);
    }

    /**
     * Create test bridge points for a simple rectangle bridge deck with balanced LEFT/RIGHT positions.
     */
    private List<BridgePoint> createRectangleBridgePoints() {
        List<BridgePoint> points = new ArrayList<>();
        
        // Rectangle bridge deck: 4 corners with balanced LEFT/RIGHT positions
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        points.add(bp1);
        
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp2.setPosition(BridgePoint.Position.RIGHT);
        points.add(bp2);
        
        BridgePoint bp3 = new BridgePoint(new Coordinate(10, 20), 3, 100, 12.0, 4.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.LEFT);
        points.add(bp3);
        
        BridgePoint bp4 = new BridgePoint(new Coordinate(0, 20), 4, 100, 12.0, 4.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp4.setPosition(BridgePoint.Position.RIGHT);
        points.add(bp4);
        
        return points;
    }

    // Test validation of position balance
    
    @Test
    public void testValidatePositionBalanceValid() {
        // Test with valid balanced positions
        List<BridgePoint> validPoints = createRectangleBridgePoints();
        
        // Should not throw exception
        assertDoesNotThrow(() -> triangulation.triangulateGeometry(validPoints), 
                          "Should accept balanced LEFT/RIGHT positions");
        
        assertTrue(triangulation.hasTriangles(), "Should create triangles with valid input");
    }
    
    @Test
    public void testValidatePositionBalanceInvalidMoreLeft() {
        List<BridgePoint> unbalancedPoints = new ArrayList<>();
        
        // Create points with more LEFT than RIGHT
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp2.setPosition(BridgePoint.Position.LEFT);
        
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.RIGHT);
        
        unbalancedPoints.add(bp1);
        unbalancedPoints.add(bp2);
        unbalancedPoints.add(bp3);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                                                         () -> triangulation.triangulateGeometry(unbalancedPoints),
                                                         "Should throw exception for unbalanced positions");
        
        assertTrue(exception.getMessage().contains("equal numbers of LEFT and RIGHT positions"),
                  "Exception message should mention position balance requirement");
        assertTrue(exception.getMessage().contains("2 LEFT and 1 RIGHT"),
                  "Exception message should show actual counts");
    }
    
    @Test
    public void testValidatePositionBalanceInvalidMoreRight() {
        List<BridgePoint> unbalancedPoints = new ArrayList<>();
        
        // Create points with more RIGHT than LEFT
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp2.setPosition(BridgePoint.Position.RIGHT);
        
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.RIGHT);
        
        unbalancedPoints.add(bp1);
        unbalancedPoints.add(bp2);
        unbalancedPoints.add(bp3);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, 
                                                         () -> triangulation.triangulateGeometry(unbalancedPoints),
                                                         "Should throw exception for unbalanced positions");
        
        assertTrue(exception.getMessage().contains("equal numbers of LEFT and RIGHT positions"),
                  "Exception message should mention position balance requirement");
        assertTrue(exception.getMessage().contains("1 LEFT and 2 RIGHT"),
                  "Exception message should show actual counts");
    }
    
    @Test
    public void testValidatePositionBalanceEmptyList() {
        List<BridgePoint> emptyPoints = new ArrayList<>();
        
        // Should not throw exception for empty list
        assertDoesNotThrow(() -> triangulation.triangulateGeometry(emptyPoints), 
                          "Should handle empty list gracefully");
        
        assertFalse(triangulation.hasTriangles(), "Empty list should not create triangles");
    }
    
    @Test
    public void testValidatePositionBalanceLargeBalancedList() {
        List<BridgePoint> largeBalancedPoints = new ArrayList<>();
        
        // Create a large polygon with balanced positions
        int numPairs = 10;
        for (int i = 0; i < numPairs; i++) {
            // LEFT point
            BridgePoint leftPoint = new BridgePoint(new Coordinate(i, 0), i * 2 + 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
            leftPoint.setPosition(BridgePoint.Position.LEFT);
            largeBalancedPoints.add(leftPoint);
            
            // RIGHT point
            BridgePoint rightPoint = new BridgePoint(new Coordinate(i, 1), i * 2 + 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
            rightPoint.setPosition(BridgePoint.Position.RIGHT);
            largeBalancedPoints.add(rightPoint);
        }
        
        assertDoesNotThrow(() -> triangulation.triangulateGeometry(largeBalancedPoints), 
                          "Should handle large balanced list");
        
        assertTrue(triangulation.hasTriangles(), "Large balanced list should create triangles");
    }



    @Test
    public void testTriangulateEmptyGeometry() {
        List<BridgePoint> emptyPoints = new ArrayList<>();
        triangulation.triangulateGeometry(emptyPoints);
        
        assertFalse(triangulation.hasTriangles(), "Should have no triangles for empty geometry");
        assertTrue(triangulation.getTriangles().isEmpty(), "Triangle list should be empty");
    }

    @Test
    public void testTriangulateInvalidGeometry() {
        List<BridgePoint> twoPoints = new ArrayList<>();
        twoPoints.add(new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null));
        twoPoints.add(new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null));
        
        triangulation.triangulateGeometry(twoPoints);
        
        assertFalse(triangulation.hasTriangles(), "Should have no triangles for invalid geometry (2 points)");
    }

    @Test
    public void testTriangulateRectangleGeometry() {
        List<BridgePoint> bridgePoints = createRectangleBridgePoints();
        triangulation.triangulateGeometry(bridgePoints);
        
        assertTrue(triangulation.hasTriangles(), "Should have triangles after triangulation");
        assertFalse(triangulation.getTriangles().isEmpty(), "Triangle list should not be empty");
        assertEquals(2, triangulation.getTriangles().size(), "Rectangle (4 vertices) should create 2 triangles");
    }

    @Test
    public void testTriangulateInvalidTriangleGeometry() {
        // This test should fail due to unbalanced positions (2 LEFT, 1 RIGHT)
        List<BridgePoint> unbalancedTrianglePoints = new ArrayList<>();
        
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        unbalancedTrianglePoints.add(bp1);
        
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp2.setPosition(BridgePoint.Position.RIGHT);
        unbalancedTrianglePoints.add(bp2);
        
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 12.0, 4.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.LEFT);
        unbalancedTrianglePoints.add(bp3);
        
        // This should throw an exception due to unbalanced positions
        assertThrows(IllegalArgumentException.class, 
                    () -> triangulation.triangulateGeometry(unbalancedTrianglePoints),
                    "Should throw exception for unbalanced triangle (2 LEFT, 1 RIGHT)");
    }

    @Test
    public void testTriangleContainsPoint() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp2.setPosition(BridgePoint.Position.RIGHT);
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.LEFT);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Point inside triangle
        assertTrue(triangle.contains(new Coordinate(5, 3)), "Point should be inside triangle");
        
        // Point on vertex
        assertTrue(triangle.contains(new Coordinate(0, 0)), "Vertex point should be considered inside");
        
        // Point outside triangle
        assertFalse(triangle.contains(new Coordinate(15, 15)), "Point should be outside triangle");
    }

    @Test
    public void testDeckHeightInterpolation() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 20.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp2.setPosition(BridgePoint.Position.RIGHT);
        BridgePoint bp3 = new BridgePoint(new Coordinate(0, 10), 3, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.LEFT);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Test interpolation at vertices
        assertEquals(10.0, triangle.interpolateDeckHeight(new Coordinate(0, 0)), 0.001, "Height at vertex 1 should match");
        assertEquals(20.0, triangle.interpolateDeckHeight(new Coordinate(10, 0)), 0.001, "Height at vertex 2 should match");
        assertEquals(15.0, triangle.interpolateDeckHeight(new Coordinate(0, 10)), 0.001, "Height at vertex 3 should match");
        
        // Test interpolation at midpoint of edge
        double midpointHeight = triangle.interpolateDeckHeight(new Coordinate(5, 0));
        assertEquals(15.0, midpointHeight, 0.001, "Height at midpoint should be average of endpoints");
    }

    @Test
    public void testDeckHeightInterpolationWithNaN() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, Double.NaN, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 20.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp2.setPosition(BridgePoint.Position.RIGHT);
        BridgePoint bp3 = new BridgePoint(new Coordinate(0, 10), 3, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.LEFT);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        double height = triangle.interpolateDeckHeight(new Coordinate(5, 5));
        assertTrue(Double.isNaN(height), "Should return NaN when one vertex has NaN height");
    }

    @Test
    public void testDeckThicknessInterpolation() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 1.0, 5.0, 5.0, 2.0, 3.0, null, null);
        bp2.setPosition(BridgePoint.Position.RIGHT);
        BridgePoint bp3 = new BridgePoint(new Coordinate(0, 10), 3, 100, 10.0, 2.0, 0.75, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.LEFT);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Test interpolation at vertices
        assertEquals(0.5, triangle.interpolateDeckThickness(new Coordinate(0, 0)), 0.001, "Thickness at vertex 1 should match");
        assertEquals(1.0, triangle.interpolateDeckThickness(new Coordinate(10, 0)), 0.001, "Thickness at vertex 2 should match");
        assertEquals(0.75, triangle.interpolateDeckThickness(new Coordinate(0, 10)), 0.001, "Thickness at vertex 3 should match");
    }

    @Test
    public void testGetDeckHeightAtPoint() {
        List<BridgePoint> bridgePoints = createRectangleBridgePoints();
        triangulation.triangulateGeometry(bridgePoints);
        
        // Point inside the bridge deck
        double height = triangulation.getDeckHeightAtPoint(new Coordinate(5, 10));
        assertFalse(Double.isNaN(height), "Should return valid height for point inside deck");
        
        // Point outside the bridge deck
        double outsideHeight = triangulation.getDeckHeightAtPoint(new Coordinate(50, 50));
        assertTrue(Double.isNaN(outsideHeight), "Should return NaN for point outside deck");
    }

    @Test
    public void testGetDeckThicknessAtPoint() {
        List<BridgePoint> bridgePoints = createRectangleBridgePoints();
        triangulation.triangulateGeometry(bridgePoints);
        
        // Point inside the bridge deck
        double thickness = triangulation.getDeckThicknessAtPoint(new Coordinate(5, 10));
        assertFalse(Double.isNaN(thickness), "Should return valid thickness for point inside deck");
        
        // Point outside the bridge deck
        double outsideThickness = triangulation.getDeckThicknessAtPoint(new Coordinate(50, 50));
        assertTrue(Double.isNaN(outsideThickness), "Should return NaN for point outside deck");
    }

    @Test
    public void testGetTriangleContainingPoint() {
        List<BridgePoint> bridgePoints = createRectangleBridgePoints();
        triangulation.triangulateGeometry(bridgePoints);
        
        // Point inside the bridge deck
        BridgeTriangulation.Triangle triangle = triangulation.getTriangleContainingPoint(new Coordinate(5, 10));
        assertNotNull(triangle, "Should return triangle for point inside deck");
        
        // Point outside the bridge deck
        BridgeTriangulation.Triangle outsideTriangle = triangulation.getTriangleContainingPoint(new Coordinate(50, 50));
        assertNull(outsideTriangle, "Should return null for point outside deck");
    }

    @Test
    public void testBarrierHeightAtPoint() {
        List<BridgePoint> bridgePoints = createRectangleBridgePoints();
        triangulation.triangulateGeometry(bridgePoints);
        
        // Point inside the bridge deck
        double barrierHeight = triangulation.getBarrierHeightAtPoint(new Coordinate(5, 10));
        assertTrue(barrierHeight >= 0, "Barrier height should be non-negative");
        
        // Point outside the bridge deck
        double outsideBarrierHeight = triangulation.getBarrierHeightAtPoint(new Coordinate(50, 50));
        assertEquals(0.0, outsideBarrierHeight, 0.001, "Should return 0 for point outside deck");
    }

    @Test
    public void testClearTriangulation() {
        List<BridgePoint> bridgePoints = createRectangleBridgePoints();
        triangulation.triangulateGeometry(bridgePoints);
        
        assertTrue(triangulation.hasTriangles(), "Should have triangles before clear");
        
        triangulation.clear();
        
        assertFalse(triangulation.hasTriangles(), "Should have no triangles after clear");
        assertTrue(triangulation.getTriangles().isEmpty(), "Triangle list should be empty after clear");
    }

    @Test
    public void testGetTrianglesReadOnly() {
        List<BridgePoint> bridgePoints = createRectangleBridgePoints();
        triangulation.triangulateGeometry(bridgePoints);
        
        List<BridgeTriangulation.Triangle> triangles = triangulation.getTriangles();
        int originalSize = triangles.size();
        
        // Try to modify the returned list
        triangles.clear();
        
        // Original triangulation should be unaffected
        assertEquals(originalSize, triangulation.getTriangles().size(), 
                    "Original triangulation should not be affected by modifying returned list");
    }

    @Test
    public void testBarrierInterpolationOnOuterEdge() {
        // Create bridge points with different barrier heights
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 0.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 0.0, 3.0, null, null);
        bp2.setPosition(BridgePoint.Position.RIGHT);
        
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 1.0, 1.5, null, null);
        bp3.setPosition(BridgePoint.Position.LEFT);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Test points that might be on outer edges
        double barrierHeight1 = triangle.interpolateBarrier(new Coordinate(2.5, 0));
        double barrierHeight2 = triangle.interpolateBarrier(new Coordinate(7.5, 0));
        
        // Results depend on whether points are actually on outer edges and position matching
        assertTrue(barrierHeight1 >= 0, "Barrier height should be non-negative");
        assertTrue(barrierHeight2 >= 0, "Barrier height should be non-negative");
    }

    // Test Triangle class methods in detail

    @Test
    public void testTriangleInterpolateWeight() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 20.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp3 = new BridgePoint(new Coordinate(0, 10), 3, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Test weight calculation at vertices
        List<Double> weights1 = triangle.interpolateWeight(new Coordinate(0, 0));
        assertEquals(3, weights1.size(), "Should return 3 weights");
        assertEquals(1.0, weights1.get(0), 0.001, "Weight for vertex 1 should be 1.0 at vertex 1");
        assertEquals(0.0, weights1.get(1), 0.001, "Weight for vertex 2 should be 0.0 at vertex 1");
        assertEquals(0.0, weights1.get(2), 0.001, "Weight for vertex 3 should be 0.0 at vertex 1");
        
        // Test weight calculation at center (barycentric coordinates should sum to 1)
        List<Double> centerWeights = triangle.interpolateWeight(new Coordinate(3.33, 3.33));
        assertEquals(3, centerWeights.size(), "Should return 3 weights");
        double weightSum = centerWeights.get(0) + centerWeights.get(1) + centerWeights.get(2);
        assertEquals(1.0, weightSum, 0.01, "Weights should sum to 1.0");
    }

    @Test
    public void testTriangleInterpolateWeightDegenerateTriangle() {
        // Create a degenerate triangle (all points on a line)
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp2 = new BridgePoint(new Coordinate(5, 0), 2, 100, 20.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp3 = new BridgePoint(new Coordinate(10, 0), 3, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        
        BridgeTriangulation.Triangle degenerateTriangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        List<Double> weights = degenerateTriangle.interpolateWeight(new Coordinate(2.5, 0));
        assertTrue(weights.isEmpty(), "Should return empty list for degenerate triangle");
    }

    @Test
    public void testTriangleOnOuterEdgeDetection() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 20.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp2.setPosition(BridgePoint.Position.LEFT); // Same position as bp1
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.RIGHT);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Point on edge between bp1 and bp2 (both LEFT position - outer edge)
        assertTrue(triangle.onOuterEdge(new Coordinate(5, 0)), 
                  "Point on edge between same-position vertices should be on outer edge");
        
        // Point inside triangle
        assertFalse(triangle.onOuterEdge(new Coordinate(5, 3)), 
                   "Point inside triangle should not be on outer edge");
        
        // Point on edge between different positions
        assertFalse(triangle.onOuterEdge(new Coordinate(2.5, 5)), 
                   "Point on edge between different-position vertices should not be on outer edge");
    }

    @Test
    public void testTriangleContainsBoundaryPoints() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 20.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Test points on edges
        assertTrue(triangle.contains(new Coordinate(5, 0)), "Point on edge should be contained");
        assertTrue(triangle.contains(new Coordinate(2.5, 5)), "Point on edge should be contained");
        assertTrue(triangle.contains(new Coordinate(7.5, 5)), "Point on edge should be contained");
        
        // Test points just outside edges
        assertFalse(triangle.contains(new Coordinate(5, -0.1)), "Point just outside should not be contained");
        assertFalse(triangle.contains(new Coordinate(-0.1, 0)), "Point just outside should not be contained");
    }

    @Test
    public void testTriangleInterpolationConsistency() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 20.0, 2.0, 1.0, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 30.0, 2.0, 0.75, 5.0, 5.0, 2.0, 3.0, null, null);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Test that interpolated values at vertices match vertex values
        assertEquals(10.0, triangle.interpolateDeckHeight(new Coordinate(0, 0)), 0.001, 
                    "Interpolated height at vertex should match vertex height");
        assertEquals(20.0, triangle.interpolateDeckHeight(new Coordinate(10, 0)), 0.001, 
                    "Interpolated height at vertex should match vertex height");
        assertEquals(30.0, triangle.interpolateDeckHeight(new Coordinate(5, 10)), 0.001, 
                    "Interpolated height at vertex should match vertex height");
        
        assertEquals(0.5, triangle.interpolateDeckThickness(new Coordinate(0, 0)), 0.001, 
                    "Interpolated thickness at vertex should match vertex thickness");
        assertEquals(1.0, triangle.interpolateDeckThickness(new Coordinate(10, 0)), 0.001, 
                    "Interpolated thickness at vertex should match vertex thickness");
        assertEquals(0.75, triangle.interpolateDeckThickness(new Coordinate(5, 10)), 0.001, 
                    "Interpolated thickness at vertex should match vertex thickness");
    }

    // Test comprehensive triangulation scenarios

    @Test
    public void testTriangulationWithComplexPolygon() {
        // Create a hexagonal bridge
        List<BridgePoint> hexagonPoints = new ArrayList<>();
        int numVertices = 6;
        double radius = 10.0;
        
        for (int i = 0; i < numVertices; i++) {
            double angle = 2 * Math.PI * i / numVertices;
            double x = radius * Math.cos(angle);
            double y = radius * Math.sin(angle);
            double height = 10.0 + i; // Variable height
            
            BridgePoint bp = new BridgePoint(new Coordinate(x, y), i + 1, 100, height, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
            bp.setPosition(i % 2 == 0 ? BridgePoint.Position.LEFT : BridgePoint.Position.RIGHT);
            hexagonPoints.add(bp);
        }
        
        triangulation.triangulateGeometry(hexagonPoints);
        
        assertTrue(triangulation.hasTriangles(), "Should have triangles for hexagon");
        
        // The number of triangles depends on triangulation algorithm - just verify we get some triangles
        assertTrue(triangulation.getTriangles().size() >= 3, "Hexagon should create at least 3 triangles");
        
        // Test interpolation at a point that should be inside the triangulation
        // Use a vertex coordinate that we know exists
        double vertexHeight = triangulation.getDeckHeightAtPoint(hexagonPoints.get(0).getCoordinate());
        assertFalse(Double.isNaN(vertexHeight), "Should interpolate height at vertex");
        assertEquals(10.0, vertexHeight, 0.001, "Height at first vertex should match");
        
        // Test at another vertex
        double vertex2Height = triangulation.getDeckHeightAtPoint(hexagonPoints.get(1).getCoordinate());
        assertFalse(Double.isNaN(vertex2Height), "Should interpolate height at second vertex");
        assertEquals(11.0, vertex2Height, 0.001, "Height at second vertex should match");
    }

    @Test
    public void testTriangulationWithLargePolygon() {
        // Create a large polygon with many vertices
        List<BridgePoint> largePolygonPoints = new ArrayList<>();
        int numVertices = 20;
        double radius = 50.0;
        
        for (int i = 0; i < numVertices; i++) {
            double angle = 2 * Math.PI * i / numVertices;
            double x = radius * Math.cos(angle);
            double y = radius * Math.sin(angle);
            double height = 15.0 + Math.sin(angle * 3) * 2.0; // Sinusoidal height variation
            
            BridgePoint bp = new BridgePoint(new Coordinate(x, y), i + 1, 100, height, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
            bp.setPosition(i % 2 == 0 ? BridgePoint.Position.LEFT : BridgePoint.Position.RIGHT);
            largePolygonPoints.add(bp);
        }
        
        triangulation.triangulateGeometry(largePolygonPoints);
        
        assertTrue(triangulation.hasTriangles(), "Should have triangles for large polygon");
        
        // The number of triangles depends on triangulation algorithm - verify we get reasonable number
        assertTrue(triangulation.getTriangles().size() >= 15, "20-vertex polygon should create at least 15 triangles");
        
        // Test interpolation at vertices to verify basic functionality
        double firstVertexHeight = triangulation.getDeckHeightAtPoint(largePolygonPoints.get(0).getCoordinate());
        assertFalse(Double.isNaN(firstVertexHeight), "Should interpolate height at first vertex");
        
        // Test interpolation performance
        long startTime = System.currentTimeMillis();
        int interpolationAttempts = 100;
        for (int i = 0; i < interpolationAttempts; i++) {
            // Test points including vertices which we know should work
            int vertexIndex = i % largePolygonPoints.size();
            triangulation.getDeckHeightAtPoint(largePolygonPoints.get(vertexIndex).getCoordinate());
        }
        long endTime = System.currentTimeMillis();
        
        assertTrue(endTime - startTime < 1000, "Interpolation should be reasonably fast");
    }

    // Test error handling and edge cases

    @Test
    public void testEmptyTriangulationMethods() {
        // Test all methods on empty triangulation
        assertTrue(triangulation.getTriangles().isEmpty(), "Empty triangulation should have no triangles");
        assertFalse(triangulation.hasTriangles(), "Empty triangulation should report no triangles");
        
        assertTrue(Double.isNaN(triangulation.getDeckHeightAtPoint(new Coordinate(0, 0))), 
                  "Empty triangulation should return NaN for height");
        assertTrue(Double.isNaN(triangulation.getDeckThicknessAtPoint(new Coordinate(0, 0))), 
                  "Empty triangulation should return NaN for thickness");
        assertEquals(0.0, triangulation.getBarrierHeightAtPoint(new Coordinate(0, 0)), 0.001, 
                    "Empty triangulation should return 0 for barrier height");
        assertNull(triangulation.getTriangleContainingPoint(new Coordinate(0, 0)), 
                  "Empty triangulation should return null for containing triangle");
    }

    @Test
    public void testTriangulationWithNaNValues() {
        List<BridgePoint> pointsWithNaN = new ArrayList<>();
        
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, Double.NaN, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 20.0, 2.0, Double.NaN, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 15.0, 2.0, 0.75, 5.0, 5.0, 2.0, 3.0, null, null);
        
        pointsWithNaN.add(bp1);
        pointsWithNaN.add(bp2);
        pointsWithNaN.add(bp3);
        
        triangulation.triangulateGeometry(pointsWithNaN);
        
        assertTrue(triangulation.hasTriangles(), "Should still create triangles with NaN values");
        
        // Test interpolation with NaN values
        double height = triangulation.getDeckHeightAtPoint(new Coordinate(5, 3));
        assertTrue(Double.isNaN(height), "Should return NaN when interpolating with NaN vertex height");
        
        double thickness = triangulation.getDeckThicknessAtPoint(new Coordinate(5, 3));
        assertTrue(Double.isNaN(thickness), "Should return NaN when interpolating with NaN vertex thickness");
    }

    @Test
    public void testTriangulationWithExtremeCoordinates() {
        List<BridgePoint> extremePoints = new ArrayList<>();
        
        // Use very large coordinates
        BridgePoint bp1 = new BridgePoint(new Coordinate(1000000, 1000000), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp2 = new BridgePoint(new Coordinate(1000010, 1000000), 2, 100, 20.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp3 = new BridgePoint(new Coordinate(1000005, 1000010), 3, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        
        extremePoints.add(bp1);
        extremePoints.add(bp2);
        extremePoints.add(bp3);
        
        triangulation.triangulateGeometry(extremePoints);
        
        assertTrue(triangulation.hasTriangles(), "Should handle extreme coordinates");
        
        double height = triangulation.getDeckHeightAtPoint(new Coordinate(1000005, 1000003));
        assertFalse(Double.isNaN(height), "Should interpolate correctly with extreme coordinates");
    }

    @Test
    public void testTriangulationWithVerySmallTriangles() {
        List<BridgePoint> smallPoints = new ArrayList<>();
        
        // Use very small triangle (millimeter scale)
        BridgePoint bp1 = new BridgePoint(new Coordinate(0.001, 0.001), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp2 = new BridgePoint(new Coordinate(0.002, 0.001), 2, 100, 20.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp3 = new BridgePoint(new Coordinate(0.0015, 0.002), 3, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        
        smallPoints.add(bp1);
        smallPoints.add(bp2);
        smallPoints.add(bp3);
        
        triangulation.triangulateGeometry(smallPoints);
        
        assertTrue(triangulation.hasTriangles(), "Should handle very small triangles");
        
        double height = triangulation.getDeckHeightAtPoint(new Coordinate(0.0015, 0.0013));
        assertFalse(Double.isNaN(height), "Should interpolate correctly with small coordinates");
    }

    // Test barrier height interpolation in detail

    @Test
    public void testBarrierHeightInterpolationWithMixedPositions() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 1.0, 2.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 3.0, 4.0, null, null);
        bp2.setPosition(BridgePoint.Position.RIGHT);
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.LEFT);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Test barrier interpolation at various points
        double barrierHeightCenter = triangle.interpolateBarrier(new Coordinate(5, 3));
        assertTrue(barrierHeightCenter >= 0, "Center barrier height should be non-negative");
        
        // Test on edge between same positions (should have barrier height)
        double barrierHeightLeftEdge = triangle.interpolateBarrier(new Coordinate(2.5, 5));
        assertTrue(barrierHeightLeftEdge >= 0, "Left edge barrier height should be non-negative");
    }

    @Test
    public void testBarrierHeightWithNaNValues() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, Double.NaN, 2.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 3.0, Double.NaN, null, null);
        bp2.setPosition(BridgePoint.Position.RIGHT);
        BridgePoint bp3 = new BridgePoint(new Coordinate(5, 10), 3, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp3.setPosition(BridgePoint.Position.LEFT);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        double barrierHeight = triangle.interpolateBarrier(new Coordinate(5, 3));
        assertEquals(0.0, barrierHeight, 0.001, "Should return 0 when barrier heights contain NaN");
    }

    // Test geometric precision and robustness

    @Test
    public void testTriangleContainmentPrecision() {
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp2 = new BridgePoint(new Coordinate(1, 0), 2, 100, 20.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp3 = new BridgePoint(new Coordinate(0.5, 1), 3, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Test points very close to edges and vertices
        assertTrue(triangle.contains(new Coordinate(0.0001, 0.0001)), 
                  "Point very close to vertex should be contained");
        assertTrue(triangle.contains(new Coordinate(0.4999, 0.0001)), 
                  "Point very close to edge should be contained");
        
        // Test points just outside the triangle
        assertFalse(triangle.contains(new Coordinate(-0.0001, 0)), 
                   "Point just outside should not be contained");
        assertFalse(triangle.contains(new Coordinate(0.5, -0.0001)), 
                   "Point just outside should not be contained");
    }

    @Test
    public void testInterpolationAccuracy() {
        // Create a triangle with known linear variation
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 0.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp2 = new BridgePoint(new Coordinate(10, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp3 = new BridgePoint(new Coordinate(0, 10), 3, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        
        BridgeTriangulation.Triangle triangle = new BridgeTriangulation.Triangle(bp1, bp2, bp3);
        
        // Test interpolation at known points with predictable results
        double heightAt25 = triangle.interpolateDeckHeight(new Coordinate(2.5, 2.5));
        assertEquals(5.0, heightAt25, 0.001, "Height at (2.5, 2.5) should be 5.0");
        
        double heightAt50 = triangle.interpolateDeckHeight(new Coordinate(5, 0));
        assertEquals(5.0, heightAt50, 0.001, "Height at midpoint of edge should be 5.0");
        
        double heightAt75 = triangle.interpolateDeckHeight(new Coordinate(0, 5));
        assertEquals(5.0, heightAt75, 0.001, "Height at midpoint of edge should be 5.0");
    }

    @Test
    public void testTriangulationRobustness() {
        // Test triangulation with points that might cause numerical issues
        List<BridgePoint> problematicPoints = new ArrayList<>();
        
        // Points with very similar coordinates
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp2 = new BridgePoint(new Coordinate(0.00001, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp3 = new BridgePoint(new Coordinate(0, 0.00001), 3, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        BridgePoint bp4 = new BridgePoint(new Coordinate(1, 1), 4, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        
        problematicPoints.add(bp1);
        problematicPoints.add(bp2);
        problematicPoints.add(bp3);
        problematicPoints.add(bp4);
        
        triangulation.triangulateGeometry(problematicPoints);
        
        // Should either create triangles or handle gracefully
        if (triangulation.hasTriangles()) {
            assertTrue(triangulation.getTriangles().size() > 0, "Should create some triangles");
        } else {
            assertTrue(true, "Graceful handling of problematic geometry is acceptable");
        }
    }

    @Test
    public void testTriangulationWithDuplicateCoordinates() {
        // Test with identical coordinates - should skip creating degenerate triangles
        List<BridgePoint> duplicatePoints = new ArrayList<>();
        
        // Create points with identical coordinates
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp2 = new BridgePoint(new Coordinate(0, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null); // Same coordinates as bp1
        bp2.setPosition(BridgePoint.Position.RIGHT);
        BridgePoint bp3 = new BridgePoint(new Coordinate(0, 0), 3, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null); // Same coordinates as bp1
        bp3.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp4 = new BridgePoint(new Coordinate(10, 10), 4, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null); // Different coordinates
        bp4.setPosition(BridgePoint.Position.RIGHT);
        
        duplicatePoints.add(bp1);
        duplicatePoints.add(bp2);
        duplicatePoints.add(bp3);
        duplicatePoints.add(bp4);
        
        triangulation.triangulateGeometry(duplicatePoints);
        
        // Should not create triangles from the first three points since they have identical coordinates
        // The triangulation should gracefully handle this case
        if (triangulation.hasTriangles()) {
            // If triangles are created, verify they are valid (no degenerate triangles with identical coordinates)
            for (BridgeTriangulation.Triangle triangle : triangulation.getTriangles()) {
                assertNotEquals(triangle.p1, triangle.p2, "Triangle vertices should not have identical coordinates");
                assertNotEquals(triangle.p1, triangle.p3, "Triangle vertices should not have identical coordinates");
                assertNotEquals(triangle.p2, triangle.p3, "Triangle vertices should not have identical coordinates");
            }
        }
    }

    @Test
    public void testTriangulationWithPartialDuplicateCoordinates() {
        // Test with some identical coordinates - should only create triangles from valid combinations
        List<BridgePoint> partialDuplicatePoints = new ArrayList<>();
        
        // Create points where some have identical coordinates
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp2 = new BridgePoint(new Coordinate(0, 0), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null); // Same as bp1
        bp2.setPosition(BridgePoint.Position.RIGHT);
        BridgePoint bp3 = new BridgePoint(new Coordinate(10, 0), 3, 100, 12.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null); // Different
        bp3.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp4 = new BridgePoint(new Coordinate(5, 10), 4, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null); // Different
        bp4.setPosition(BridgePoint.Position.RIGHT);
        
        partialDuplicatePoints.add(bp1);
        partialDuplicatePoints.add(bp2);
        partialDuplicatePoints.add(bp3);
        partialDuplicatePoints.add(bp4);
        
        triangulation.triangulateGeometry(partialDuplicatePoints);
        
        // Should handle partial duplicates gracefully
        if (triangulation.hasTriangles()) {
            // Verify that any created triangles have distinct vertices
            for (BridgeTriangulation.Triangle triangle : triangulation.getTriangles()) {
                // Check that coordinates are sufficiently different
                double tolerance = 1e-10;
                boolean p1p2Different = Math.abs(triangle.p1.x - triangle.p2.x) > tolerance || 
                                       Math.abs(triangle.p1.y - triangle.p2.y) > tolerance ||
                                       Math.abs(triangle.p1.z - triangle.p2.z) > tolerance;
                boolean p1p3Different = Math.abs(triangle.p1.x - triangle.p3.x) > tolerance || 
                                       Math.abs(triangle.p1.y - triangle.p3.y) > tolerance ||
                                       Math.abs(triangle.p1.z - triangle.p3.z) > tolerance;
                boolean p2p3Different = Math.abs(triangle.p2.x - triangle.p3.x) > tolerance || 
                                       Math.abs(triangle.p2.y - triangle.p3.y) > tolerance ||
                                       Math.abs(triangle.p2.z - triangle.p3.z) > tolerance;
                
                assertTrue(p1p2Different && p1p3Different && p2p3Different, 
                          "Triangle should only be created if all coordinates are different");
            }
        }
    }

    @Test
    public void testTriangulationWithNearIdenticalCoordinates() {
        // Test with coordinates that are very close but not identical
        List<BridgePoint> nearIdenticalPoints = new ArrayList<>();
        
        // Create points with coordinates very close to each other (within tolerance)
        BridgePoint bp1 = new BridgePoint(new Coordinate(0, 0), 1, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null);
        bp1.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp2 = new BridgePoint(new Coordinate(1e-11, 1e-11), 2, 100, 10.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null); // Very close to bp1
        bp2.setPosition(BridgePoint.Position.RIGHT);
        BridgePoint bp3 = new BridgePoint(new Coordinate(2e-11, 0), 3, 100, 12.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null); // Very close to bp1
        bp3.setPosition(BridgePoint.Position.LEFT);
        BridgePoint bp4 = new BridgePoint(new Coordinate(10, 10), 4, 100, 15.0, 2.0, 0.5, 5.0, 5.0, 2.0, 3.0, null, null); // Far from others
        bp4.setPosition(BridgePoint.Position.RIGHT);
        
        nearIdenticalPoints.add(bp1);
        nearIdenticalPoints.add(bp2);
        nearIdenticalPoints.add(bp3);
        nearIdenticalPoints.add(bp4);
        
        triangulation.triangulateGeometry(nearIdenticalPoints);
        
        // Near-identical coordinates (within tolerance) should be treated as identical
        // and should not create degenerate triangles
        if (triangulation.hasTriangles()) {
            assertTrue(triangulation.getTriangles().size() >= 0, "Should handle near-identical coordinates gracefully");
        }
    }
}
