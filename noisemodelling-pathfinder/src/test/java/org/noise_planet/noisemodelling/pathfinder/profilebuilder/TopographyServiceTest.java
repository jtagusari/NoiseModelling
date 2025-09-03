package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TopographyServiceTest {

    @Test
    public void testInitialStateAndDem() {
        // Verify the service initial state before any points are added:
        // - triangles list exists but is empty
        // - topo R-tree is not yet created
        // - DEM multi-polygon representation exists and is empty
        TopographyService svc = new TopographyService(4);
        assertNotNull(svc.getTriangles());
        assertEquals(0, svc.getTriangles().size());
        assertNull(svc.getTopoRtree());
        MultiPolygon dem = svc.demAsMultiPolygon();
        assertNotNull(dem);
        assertTrue(dem.isEmpty());
    }

    @Test
    public void testGetZGround_noTin_returnsZero() {
        // When no TIN is available, getZGround should return a sensible default
        // (zero) and not update a triangle hint.
        TopographyService svc = new TopographyService(4);
        double z = svc.getZGround(new Coordinate(0, 0));
        assertEquals(0.0, z, 1e-9);
        AtomicInteger hint = new AtomicInteger(-1);
        assertEquals(0.0, svc.getZGround(new Coordinate(10, 10), hint), 1e-9);
    }

    @Test
    public void testFetchTopographicProfile_noTin_returnsTrueAndNoPoints() {
        // fetchTopographicProfile should behave gracefully when no TIN exists:
        // it returns true (segment is free) and does not add cut points.
        TopographyService svc = new TopographyService(4);
        List<Coordinate> out = new ArrayList<>();
        boolean res = svc.fetchTopographicProfile(out, new Coordinate(0, 0), new Coordinate(1, 1), true);
        assertTrue(res);
        assertTrue(out.isEmpty());
    }

    @Test
    public void testFindClosestTriangleIntersection_noTin_returnsFalse() {
        // With no TIN built, findClosestTriangleIntersection should return false
        // indicating there is no triangle intersecting the given segment.
        TopographyService svc = new TopographyService(4);
        LineSegment seg = new LineSegment(new Coordinate(0, 0), new Coordinate(1, 1));
        assertFalse(svc.findClosestTriangleIntersection(seg, new Coordinate(), new AtomicInteger()));
    }

    @Test
    public void testGetTriangleIdByCoordinate_noTin_returnsMinusOne() {
        // When no triangulation exists, getTriangleIdByCoordinate should
        // indicate no containing triangle by returning -1.
        TopographyService svc = new TopographyService(4);
        assertEquals(-1, svc.getTriangleIdByCoordinate(new Coordinate(0, 0)));
    }

    @Test
    public void testAddPointProcessDelaunay_insufficientData() {
        // Adding too few points should cause triangulation to fail. This test
        // adds only one point and asserts buildDelaunayTriangulation returns false.
        TopographyService svc = new TopographyService(4);
        svc.addTopographicPoint(new Coordinate(0, 0, 100));
        // With a single point buildDelaunayTriangulation should return false (not enough data)
        assertFalse(svc.buildDelaunayTriangulation());
    }

    @Test
    public void testBuildDelaunayTriangulation_successfulCase() {
        // Test successful triangulation with sufficient data points
        TopographyService svc = new TopographyService(4);
        
        // Add enough points to form triangles
        svc.addTopographicPoint(new Coordinate(0, 0, 10));
        svc.addTopographicPoint(new Coordinate(10, 0, 15));
        svc.addTopographicPoint(new Coordinate(5, 10, 20));
        svc.addTopographicPoint(new Coordinate(15, 10, 25));
        
        // Build triangulation
        assertTrue(svc.buildDelaunayTriangulation());
        
        // Verify triangulation results
        assertNotNull(svc.getTriangles());
        assertTrue(svc.getTriangles().size() > 0);
        assertNotNull(svc.getVertices());
        assertTrue(svc.getVertices().size() >= 4);
        assertNotNull(svc.getTopoRtree());
        assertNotNull(svc.getNeighbors());
        assertEquals(svc.getTriangles().size(), svc.getNeighbors().size());
    }

    @Test
    public void testGetZGround_withValidTin() {
        // Test Z-ground interpolation with a valid TIN
        TopographyService svc = new TopographyService(4);
        
        // Create a simple triangle with known elevation values
        svc.addTopographicPoint(new Coordinate(0, 0, 10));
        svc.addTopographicPoint(new Coordinate(10, 0, 20));
        svc.addTopographicPoint(new Coordinate(5, 10, 30));
        
        assertTrue(svc.buildDelaunayTriangulation());
        
        // Test interpolation at triangle center
        double z = svc.getZGround(new Coordinate(5, 3));
        assertTrue(z > 0); // Should interpolate to some positive value
        
        // Test with triangle hint
        AtomicInteger hint = new AtomicInteger(-1);
        double zWithHint = svc.getZGround(new Coordinate(5, 3), hint);
        assertEquals(z, zWithHint, 1e-9);
        assertTrue(hint.get() >= 0); // Hint should be updated
    }

    @Test
    public void testGetTriangleIdByCoordinate_withValidTin() {
        // Test triangle ID lookup with a valid TIN
        TopographyService svc = new TopographyService(4);
        
        svc.addTopographicPoint(new Coordinate(0, 0, 10));
        svc.addTopographicPoint(new Coordinate(10, 0, 15));
        svc.addTopographicPoint(new Coordinate(5, 10, 20));
        
        assertTrue(svc.buildDelaunayTriangulation());
        
        // Point inside the triangle should return valid ID
        int triId = svc.getTriangleIdByCoordinate(new Coordinate(5, 3));
        assertTrue(triId >= 0);
        
        // Point far outside should return -1
        int outsideId = svc.getTriangleIdByCoordinate(new Coordinate(100, 100));
        assertEquals(-1, outsideId);
    }

    @Test
    public void testFetchTopographicProfile_withValidTin() {
        // Test topographic profile fetching with a valid TIN
        TopographyService svc = new TopographyService(4);
        
        // Create a simple terrain
        svc.addTopographicPoint(new Coordinate(0, 0, 5));
        svc.addTopographicPoint(new Coordinate(10, 0, 10));
        svc.addTopographicPoint(new Coordinate(20, 0, 5));
        svc.addTopographicPoint(new Coordinate(10, 10, 15));
        
        assertTrue(svc.buildDelaunayTriangulation());
        
        List<Coordinate> profile = new ArrayList<>();
        svc.fetchTopographicProfile(profile, 
            new Coordinate(2, 2), new Coordinate(18, 2), false);
        
        // Should have at least start and end points
        assertTrue(profile.size() >= 2);
        
        // First point should be near start coordinate
        Coordinate first = profile.get(0);
        assertEquals(2.0, first.x, 1e-6);
        assertEquals(2.0, first.y, 1e-6);
        assertTrue(first.z > 0); // Should have interpolated Z
        
        // Last point should be near end coordinate
        Coordinate last = profile.get(profile.size() - 1);
        assertEquals(18.0, last.x, 1e-6);
        assertEquals(2.0, last.y, 1e-6);
        assertTrue(last.z > 0); // Should have interpolated Z
    }

    @Test
    public void testFindClosestTriangleIntersection_withValidTin() {
        // Test triangle intersection finding with a valid TIN
        TopographyService svc = new TopographyService(4);
        
        svc.addTopographicPoint(new Coordinate(0, 0, 10));
        svc.addTopographicPoint(new Coordinate(10, 0, 15));
        svc.addTopographicPoint(new Coordinate(5, 10, 20));
        
        assertTrue(svc.buildDelaunayTriangulation());
        
        LineSegment segment = new LineSegment(new Coordinate(1, 1), new Coordinate(8, 8));
        Coordinate intersection = new Coordinate();
        AtomicInteger triangleId = new AtomicInteger();
        
        boolean found = svc.findClosestTriangleIntersection(segment, intersection, triangleId);
        
        if (found) {
            assertTrue(triangleId.get() >= 0);
            assertNotNull(intersection);
            assertFalse(Double.isNaN(intersection.z));
        }
    }

    @Test
    public void testGetTriangleVertices() {
        // Test triangle vertex retrieval
        TopographyService svc = new TopographyService(4);
        
        svc.addTopographicPoint(new Coordinate(0, 0, 10));
        svc.addTopographicPoint(new Coordinate(10, 0, 15));
        svc.addTopographicPoint(new Coordinate(5, 10, 20));
        
        assertTrue(svc.buildDelaunayTriangulation());
        
        if (svc.getTriangles().size() > 0) {
            Coordinate[] vertices = svc.getTriangleVertices(0);
            assertNotNull(vertices);
            assertEquals(3, vertices.length);
            
            // All vertices should have valid coordinates
            for (Coordinate vertex : vertices) {
                assertNotNull(vertex);
                assertFalse(Double.isNaN(vertex.x));
                assertFalse(Double.isNaN(vertex.y));
                assertFalse(Double.isNaN(vertex.z));
            }
        }
    }

    @Test
    public void testDemAsMultiPolygon_withValidTin() {
        // Test DEM as MultiPolygon conversion
        TopographyService svc = new TopographyService(4);
        
        svc.addTopographicPoint(new Coordinate(0, 0, 10));
        svc.addTopographicPoint(new Coordinate(10, 0, 15));
        svc.addTopographicPoint(new Coordinate(5, 10, 20));
        
        assertTrue(svc.buildDelaunayTriangulation());
        
        MultiPolygon dem = svc.demAsMultiPolygon();
        assertNotNull(dem);
        assertFalse(dem.isEmpty());
        assertTrue(dem.getNumGeometries() > 0);
    }

    @Test
    public void testClearService() {
        // Test service clearing functionality
        TopographyService svc = new TopographyService(4);
        
        // Add data and build triangulation
        svc.addTopographicPoint(new Coordinate(0, 0, 10));
        svc.addTopographicPoint(new Coordinate(10, 0, 15));
        svc.addTopographicPoint(new Coordinate(5, 10, 20));
        assertTrue(svc.buildDelaunayTriangulation());
        
        // Verify data exists
        assertTrue(svc.getTriangles().size() > 0);
        assertNotNull(svc.getTopoRtree());
        
        // Clear and verify everything is reset
        svc.clear();
        assertEquals(0, svc.getTriangles().size());
        assertEquals(0, svc.getVertices().size());
        assertEquals(0, svc.getNeighbors().size());
        assertNull(svc.getTopoRtree());
        
        // DEM should be empty after clear
        MultiPolygon dem = svc.demAsMultiPolygon();
        assertTrue(dem.isEmpty());
    }

    @Test
    public void testAddTopographicLine() {
        // Test adding topographic line constraints
        TopographyService svc = new TopographyService(4);
        
        // Add some points
        svc.addTopographicPoint(new Coordinate(0, 0, 10));
        svc.addTopographicPoint(new Coordinate(10, 0, 15));
        svc.addTopographicPoint(new Coordinate(5, 10, 20));
        
        // Add a line constraint
        GeometryFactory factory = new GeometryFactory();
        Coordinate[] lineCoords = {new Coordinate(2, 2, 12), new Coordinate(8, 8, 18)};
        LineString line = factory.createLineString(lineCoords);
        svc.addTopographicLine(line);
        
        // Should still be able to build triangulation
        assertTrue(svc.buildDelaunayTriangulation());
        assertTrue(svc.getTriangles().size() > 0);
    }

    @Test
    public void testNullInputHandling() {
        // Test handling of null inputs
        TopographyService svc = new TopographyService(4);
        
        // Adding null point should not cause issues
        svc.addTopographicPoint(null);
        svc.addTopographicLine(null);
        
        // Should still function normally
        svc.addTopographicPoint(new Coordinate(0, 0, 10));
        svc.addTopographicPoint(new Coordinate(10, 0, 15));
        svc.addTopographicPoint(new Coordinate(5, 10, 20));
        
        assertTrue(svc.buildDelaunayTriangulation());
    }
}
