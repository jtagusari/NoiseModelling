package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TopographyServiceTinTest {

    @Test
    public void testProcessDelaunay_and_sampling() {
        // This test builds a minimal triangular TIN from three 3D points and
        // validates core TopographyService operations:
        // 1) Delaunay triangulation completes successfully.
        // 2) A sample point inside the triangle maps to a valid triangle id.
        // 3) Interpolated Z from the triangle matches the service's sampled Z
        //    (verifies barycentric interpolation correctness).
        // 4) fetchTopographicProfile produces cut points for a segment crossing
        //    the TIN and findClosestTriangleIntersection detects intersections.
        // 5) demAsMultiPolygon returns a non-empty representation after triangulation.
        TopographyService svc = new TopographyService(4);
        // Build a minimal TIN: right triangle with different Z values
        svc.addTopographicPoint(new Coordinate(0.0, 0.0, 0.0));
        svc.addTopographicPoint(new Coordinate(10.0, 0.0, 0.0));
        svc.addTopographicPoint(new Coordinate(0.0, 10.0, 10.0));

        boolean ok = svc.buildDelaunayTriangulation();
        assertTrue(ok, "buildDelaunayTriangulation should succeed with three points");

        assertNotNull(svc.getTriangles());
        assertTrue(svc.getTriangles().size() > 0);
        assertNotNull(svc.getTopoRtree());

        // Pick a point inside the triangle and check triangle id
        Coordinate sample = new Coordinate(2.0, 1.0);
        int triId = svc.getTriangleIdByCoordinate(sample);
        assertTrue(triId >= 0, "triangle id should be found for point inside TIN");

        // Get triangle vertices and compute expected interpolated Z
        Coordinate[] triVerts = svc.getTriangleVertices(triId);
        double expectedZ = Vertex.interpolateZ(sample, triVerts[0], triVerts[1], triVerts[2]);
        AtomicInteger hint = new AtomicInteger(triId);
        double z = svc.getZGround(sample, hint);
        assertEquals(expectedZ, z, 1e-9);
        assertEquals(triId, hint.get());

        // fetchTopographicProfile should produce at least two points along a segment crossing the TIN
        List<Coordinate> out = new ArrayList<>();
        svc.fetchTopographicProfile(out, new Coordinate(-1, -1), new Coordinate(5, 5), false);
        assertTrue(out.size() >= 2);

        // findClosestTriangleIntersection should find an intersection for a segment crossing the triangle
        LineSegment seg = new LineSegment(new Coordinate(-1, -1), new Coordinate(5, 5));
        Coordinate inter = new Coordinate();
        AtomicInteger interTri = new AtomicInteger(-1);
        boolean found = svc.findClosestTriangleIntersection(seg, inter, interTri);
        assertTrue(found);
        assertTrue(interTri.get() >= 0);

        // demAsMultiPolygon should not be empty after triangulation
        assertFalse(svc.demAsMultiPolygon().isEmpty());
    }
}
