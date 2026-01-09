package org.noise_planet.noisemodelling.pathfinder;

import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeometryUtilsTest {

    private List<Coordinate> testCoordinates;

    @BeforeEach
    void setUp() {
        testCoordinates = new ArrayList<>();
        testCoordinates.add(new Coordinate(0, 0, 0));
        testCoordinates.add(new Coordinate(10, 10, 5));
        testCoordinates.add(new Coordinate(-5, 5, 3));
        testCoordinates.add(new Coordinate(5, -5, 2));
        testCoordinates.add(new Coordinate(15, 0, 7));
    }

    @Test
    @DisplayName("GeometryUtils should compute zero-rad plane correctly")
    void testComputeZeroRadPlane() {
        // Arrange
        Coordinate p0 = new Coordinate(0, 0, 0);
        Coordinate p1 = new Coordinate(10, 0, 0);

        // Act
        Plane result = GeometryUtils.computeZeroRadPlane(p0, p1);

        // Assert
        assertNotNull(result, "Should return a valid plane");
        Vector3D normal = result.getNormal();
        assertNotNull(normal, "Plane should have a normal vector");
        assertTrue(normal.getZ() >= 0, "Normal Z component should be non-negative (upward facing)");
    }

    @Test
    @DisplayName("GeometryUtils should compute zero-rad plane for diagonal line")
    void testComputeZeroRadPlaneDiagonal() {
        // Arrange
        Coordinate p0 = new Coordinate(0, 0, 0);
        Coordinate p1 = new Coordinate(10, 10, 0);

        // Act
        Plane result = GeometryUtils.computeZeroRadPlane(p0, p1);

        // Assert
        assertNotNull(result, "Should return a valid plane for diagonal line");
        Vector3D normal = result.getNormal();
        assertTrue(normal.getZ() >= 0, "Normal Z component should be non-negative");
    }

    @Test
    @DisplayName("GeometryUtils should compute zero-rad plane for vertical differences")
    void testComputeZeroRadPlaneVertical() {
        // Arrange
        Coordinate p0 = new Coordinate(0, 0, 0);
        Coordinate p1 = new Coordinate(0, 0, 10);

        // Act
        Plane result = GeometryUtils.computeZeroRadPlane(p0, p1);

        // Assert
        assertNotNull(result, "Should return a valid plane for vertical line");
        Vector3D normal = result.getNormal();
        assertTrue(normal.getZ() >= 0, "Normal Z component should be non-negative");
    }

    @Test
    @DisplayName("GeometryUtils should filter points by left side correctly")
    void testFilterPointsBySideLeft() {
        // Arrange
        LineSegment segment = new LineSegment(new Coordinate(0, 0, 0), new Coordinate(10, 0, 0));
        List<Coordinate> points = Arrays.asList(
            new Coordinate(5, 5, 0),   // Left side (positive Y)
            new Coordinate(5, -5, 0),  // Right side (negative Y)
            new Coordinate(5, 0, 0),   // On line
            new Coordinate(2, 3, 0),   // Left side
            new Coordinate(8, -2, 0)   // Right side
        );

        // Act
        List<Coordinate> result = GeometryUtils.filterPointsBySide(segment, true, points);

        // Assert
        assertNotNull(result, "Should return a valid list");
        assertEquals(2, result.size(), "Should filter to left-side points only");
        assertTrue(result.contains(new Coordinate(5, 5, 0)), "Should include point on left side");
        assertTrue(result.contains(new Coordinate(2, 3, 0)), "Should include another point on left side");
    }

    @Test
    @DisplayName("GeometryUtils should filter points by right side correctly")
    void testFilterPointsBySideRight() {
        // Arrange
        LineSegment segment = new LineSegment(new Coordinate(0, 0, 0), new Coordinate(10, 0, 0));
        List<Coordinate> points = Arrays.asList(
            new Coordinate(5, 5, 0),   // Left side (positive Y)
            new Coordinate(5, -5, 0),  // Right side (negative Y)
            new Coordinate(5, 0, 0),   // On line
            new Coordinate(2, 3, 0),   // Left side
            new Coordinate(8, -2, 0)   // Right side
        );

        // Act
        List<Coordinate> result = GeometryUtils.filterPointsBySide(segment, false, points);

        // Assert
        assertNotNull(result, "Should return a valid list");
        assertEquals(2, result.size(), "Should filter to right-side points only");
        assertTrue(result.contains(new Coordinate(5, -5, 0)), "Should include point on right side");
        assertTrue(result.contains(new Coordinate(8, -2, 0)), "Should include another point on right side");
    }

    @Test
    @DisplayName("GeometryUtils should handle empty coordinate list for filtering")
    void testFilterPointsBySideEmpty() {
        // Arrange
        LineSegment segment = new LineSegment(new Coordinate(0, 0, 0), new Coordinate(10, 0, 0));
        List<Coordinate> emptyPoints = new ArrayList<>();

        // Act
        List<Coordinate> result = GeometryUtils.filterPointsBySide(segment, true, emptyPoints);

        // Assert
        assertNotNull(result, "Should return a valid list for empty input");
        assertTrue(result.isEmpty(), "Should return empty list for empty input");
    }

    @Test
    @DisplayName("GeometryUtils should filter points with different line orientations")
    void testFilterPointsBySideDifferentOrientations() {
        // Test with different line segment orientations
        LineSegment[] segments = {
            new LineSegment(new Coordinate(0, 0, 0), new Coordinate(0, 10, 0)), // Vertical
            new LineSegment(new Coordinate(0, 0, 0), new Coordinate(10, 10, 0)), // Diagonal
            new LineSegment(new Coordinate(10, 10, 0), new Coordinate(0, 0, 0)), // Reverse diagonal
            new LineSegment(new Coordinate(0, 10, 0), new Coordinate(0, 0, 0))   // Reverse vertical
        };

        List<Coordinate> testPoint = Arrays.asList(new Coordinate(5, 5, 0));

        for (int i = 0; i < segments.length; i++) {
            // Act
            List<Coordinate> leftResult = GeometryUtils.filterPointsBySide(segments[i], true, testPoint);
            List<Coordinate> rightResult = GeometryUtils.filterPointsBySide(segments[i], false, testPoint);

            // Assert
            assertNotNull(leftResult, "Left result should not be null for segment " + i);
            assertNotNull(rightResult, "Right result should not be null for segment " + i);
            // The point should be on one side or the other (or neither if on the line)
            assertTrue(leftResult.size() + rightResult.size() <= 1, 
                "Point should be on at most one side for segment " + i);
        }
    }

    @Test
    @DisplayName("GeometryUtils should cut roof points with plane correctly")
    void testCutRoofPointsWithPlane() {
        // Arrange
        Coordinate p0 = new Coordinate(0, 0, 0);
        Coordinate p1 = new Coordinate(10, 0, 0);
        Plane plane = GeometryUtils.computeZeroRadPlane(p0, p1);
        
        List<Coordinate> roofPoints = Arrays.asList(
            new Coordinate(5, 2, 5),   // Above plane
            new Coordinate(5, -2, 3),  // Below plane
            new Coordinate(7, 1, 4),   // Above plane
            new Coordinate(3, -1, 2)   // Below plane
        );

        // Act
        List<Coordinate> result = GeometryUtils.cutRoofPointsWithPlane(plane, roofPoints);

        // Assert
        assertNotNull(result, "Should return a valid list");
        // Result should contain intersection points and points above the plane
        assertFalse(result.isEmpty(), "Should have some resulting points");
    }

    @Test
    @DisplayName("GeometryUtils should handle empty roof points list")
    void testCutRoofPointsWithPlaneEmpty() {
        // Arrange
        Coordinate p0 = new Coordinate(0, 0, 0);
        Coordinate p1 = new Coordinate(10, 0, 0);
        Plane plane = GeometryUtils.computeZeroRadPlane(p0, p1);
        List<Coordinate> emptyRoofPoints = new ArrayList<>();

        // Act
        List<Coordinate> result = GeometryUtils.cutRoofPointsWithPlane(plane, emptyRoofPoints);

        // Assert
        assertNotNull(result, "Should return a valid list for empty input");
        assertTrue(result.isEmpty(), "Should return empty list for empty input");
    }

    @Test
    @DisplayName("GeometryUtils should convert coordinate to vector correctly")
    void testCoordinateToVector() {
        // Arrange
        Coordinate[] testCoords = {
            new Coordinate(0, 0, 0),
            new Coordinate(1, 2, 3),
            new Coordinate(-5, 10, -3),
            new Coordinate(100.5, -50.7, 25.3)
        };

        for (Coordinate coord : testCoords) {
            // Act
            Vector3D result = GeometryUtils.coordinateToVector(coord);

            // Assert
            assertNotNull(result, "Should return a valid vector");
            assertEquals(coord.x, result.getX(), 1e-10, "X coordinate should match");
            assertEquals(coord.y, result.getY(), 1e-10, "Y coordinate should match");
            assertEquals(coord.z, result.getZ(), 1e-10, "Z coordinate should match");
        }
    }

    @Test
    @DisplayName("GeometryUtils should handle coordinate with NaN values")
    void testCoordinateToVectorWithNaN() {
        // Arrange
        Coordinate coordWithNaN = new Coordinate(Double.NaN, 5, 10);

        // Act
        Vector3D result = GeometryUtils.coordinateToVector(coordWithNaN);

        // Assert
        assertNotNull(result, "Should return a vector even with NaN values");
        assertTrue(Double.isNaN(result.getX()), "X should be NaN");
        assertEquals(5, result.getY(), 1e-10, "Y should be preserved");
        assertEquals(10, result.getZ(), 1e-10, "Z should be preserved");
    }

    @Test
    @DisplayName("GeometryUtils should handle extreme coordinate values")
    void testCoordinateToVectorExtreme() {
        // Arrange
        Coordinate extremeCoord = new Coordinate(Double.MAX_VALUE, Double.MIN_VALUE, -Double.MAX_VALUE);

        // Act
        Vector3D result = GeometryUtils.coordinateToVector(extremeCoord);

        // Assert
        assertNotNull(result, "Should handle extreme coordinate values");
        assertEquals(Double.MAX_VALUE, result.getX(), "X should handle MAX_VALUE");
        assertEquals(Double.MIN_VALUE, result.getY(), "Y should handle MIN_VALUE");
        assertEquals(-Double.MAX_VALUE, result.getZ(), "Z should handle negative MAX_VALUE");
    }

    @Test
    @DisplayName("GeometryUtils should handle identical points with appropriate exception")
    void testComputeZeroRadPlaneIdenticalPoints() {
        // Arrange
        Coordinate p0 = new Coordinate(5, 5, 5);
        Coordinate p1 = new Coordinate(5, 5, 5); // Same point

        // Act & Assert - Identical points should cause zero norm exception
        assertThrows(Exception.class, () -> {
            GeometryUtils.computeZeroRadPlane(p0, p1);
        }, "Should throw exception for identical points (zero norm)");
    }

    @Test
    @DisplayName("GeometryUtils should handle complex roof cutting scenarios")
    void testCutRoofPointsWithPlaneComplex() {
        // Arrange
        Coordinate p0 = new Coordinate(0, 0, 5);
        Coordinate p1 = new Coordinate(10, 0, 5);
        Plane plane = GeometryUtils.computeZeroRadPlane(p0, p1);
        
        List<Coordinate> complexRoofPoints = Arrays.asList(
            new Coordinate(2, 3, 8),   // Above
            new Coordinate(3, 2, 4),   // Below
            new Coordinate(5, 0, 5),   // On plane
            new Coordinate(8, -1, 6),  // Above
            new Coordinate(9, 1, 3),   // Below
            new Coordinate(1, -2, 7)   // Above
        );

        // Act
        List<Coordinate> result = GeometryUtils.cutRoofPointsWithPlane(plane, complexRoofPoints);

        // Assert
        assertNotNull(result, "Should handle complex roof cutting");
        // Should include points above plane and intersection points
        assertFalse(result.isEmpty(), "Should have intersection or above-plane points");
    }

    @Test
    @DisplayName("GeometryUtils should handle filtering with points on line")
    void testFilterPointsBySideOnLine() {
        // Arrange
        LineSegment segment = new LineSegment(new Coordinate(0, 0, 0), new Coordinate(10, 0, 0));
        List<Coordinate> pointsOnLine = Arrays.asList(
            new Coordinate(1, 0, 0),
            new Coordinate(5, 0, 0),
            new Coordinate(9, 0, 0)
        );

        // Act
        List<Coordinate> leftResult = GeometryUtils.filterPointsBySide(segment, true, pointsOnLine);
        List<Coordinate> rightResult = GeometryUtils.filterPointsBySide(segment, false, pointsOnLine);

        // Assert
        assertNotNull(leftResult, "Left result should not be null");
        assertNotNull(rightResult, "Right result should not be null");
        assertTrue(leftResult.isEmpty(), "Points on line should not be on left side");
        assertTrue(rightResult.isEmpty(), "Points on line should not be on right side");
    }

    @Test
    @DisplayName("GeometryUtils should maintain coordinate precision in vector conversion")
    void testCoordinateToVectorPrecision() {
        // Arrange
        double preciseValue = 1.23456789012345;
        Coordinate preciseCoord = new Coordinate(preciseValue, preciseValue * 2, preciseValue * 3);

        // Act
        Vector3D result = GeometryUtils.coordinateToVector(preciseCoord);

        // Assert
        assertEquals(preciseValue, result.getX(), 1e-15, "Should maintain X precision");
        assertEquals(preciseValue * 2, result.getY(), 1e-15, "Should maintain Y precision");
        assertEquals(preciseValue * 3, result.getZ(), 1e-15, "Should maintain Z precision");
    }
}
