package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProfileUtils utility class.
 * Tests segment splitting and obstacle cut point processing functionality.
 */
public class ProfileUtilsTest {

    @Test
    public void testSplitToSegments_shortAndLong() {
        Coordinate a = new Coordinate(0, 0, 0);
        Coordinate b = new Coordinate(1, 0, 0);
        List<LineSegment> single = ProfileUtils.splitToSegments(a, b, 2.0);
        assertEquals(1, single.size());
        assertEquals(a.x, single.get(0).p0.x, 1e-9);
        assertEquals(b.x, single.get(0).p1.x, 1e-9);

        // long segment
        double length = 10.0;
        Coordinate c0 = new Coordinate(0, 0, 0);
        Coordinate c1 = new Coordinate(length, 0, length); // z varies
        double maxLen = 3.0;
        List<LineSegment> parts = ProfileUtils.splitToSegments(c0, c1, maxLen);
        int expected = (int) Math.ceil(length / maxLen);
        assertEquals(expected, parts.size());
        // check z interpolation: first p0.z == c0.z, last p1.z == c1.z
        assertEquals(c0.z, parts.get(0).p0.z, 1e-9);
        assertEquals(c1.z, parts.get(parts.size() - 1).p1.z, 1e-9);
    }

    // Additional comprehensive tests for splitToSegments

    @Test
    public void testSplitToSegments_exactLength() {
        // Test segment that is exactly the max length
        Coordinate start = new Coordinate(0, 0, 5);
        Coordinate end = new Coordinate(3, 0, 8); // distance = 3
        double maxLen = 3.0;
        
        List<LineSegment> segments = ProfileUtils.splitToSegments(start, end, maxLen);
        assertEquals(1, segments.size());
        assertEquals(start.x, segments.get(0).p0.x, 1e-9);
        assertEquals(end.x, segments.get(0).p1.x, 1e-9);
    }

    @Test
    public void testSplitToSegments_zeroLength() {
        // Test zero-length segment
        Coordinate point = new Coordinate(5, 5, 10);
        
        List<LineSegment> segments = ProfileUtils.splitToSegments(point, point, 1.0);
        assertEquals(1, segments.size());
        assertEquals(point.x, segments.get(0).p0.x, 1e-9);
        assertEquals(point.x, segments.get(0).p1.x, 1e-9);
    }

    @Test
    public void testSplitToSegments_zInterpolation() {
        // Test Z coordinate interpolation in split segments
        Coordinate start = new Coordinate(0, 0, 0);
        Coordinate end = new Coordinate(10, 0, 20); // Linear z progression
        double maxLen = 2.5;
        
        List<LineSegment> segments = ProfileUtils.splitToSegments(start, end, maxLen);
        assertTrue(segments.size() > 1);
        
        // Check first segment
        assertEquals(0.0, segments.get(0).p0.z, 1e-9);
        // Z should be interpolated proportionally
        assertTrue(segments.get(0).p1.z > 0 && segments.get(0).p1.z < 20);
        
        // Check last segment ends at correct Z
        LineSegment lastSegment = segments.get(segments.size() - 1);
        assertEquals(20.0, lastSegment.p1.z, 1e-9);
    }

    @Test
    public void testSplitToSegments_largeSegment() {
        // Test very long segment that requires many splits
        Coordinate start = new Coordinate(0, 0, 0);
        Coordinate end = new Coordinate(100, 0, 50);
        double maxLen = 5.0;
        
        List<LineSegment> segments = ProfileUtils.splitToSegments(start, end, maxLen);
        int expectedCount = (int) Math.ceil(100.0 / maxLen);
        assertEquals(expectedCount, segments.size());
        
        // Verify continuity - each segment should start where the previous ends
        for (int i = 1; i < segments.size(); i++) {
            assertEquals(segments.get(i-1).p1.x, segments.get(i).p0.x, 1e-9);
            assertEquals(segments.get(i-1).p1.y, segments.get(i).p0.y, 1e-9);
            assertEquals(segments.get(i-1).p1.z, segments.get(i).p0.z, 1e-9);
        }
    }

    @Test
    public void testSplitToSegments_withNaNZ() {
        // Test segment with NaN Z coordinates
        Coordinate start = new Coordinate(0, 0, Double.NaN);
        Coordinate end = new Coordinate(10, 0, Double.NaN);
        double maxLen = 3.0;
        
        List<LineSegment> segments = ProfileUtils.splitToSegments(start, end, maxLen);
        assertTrue(segments.size() > 1);
        
        // All segments should have NaN Z coordinates
        for (LineSegment segment : segments) {
            assertTrue(Double.isNaN(segment.p0.z));
            assertTrue(Double.isNaN(segment.p1.z));
        }
    }
}
