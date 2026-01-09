package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TopographyServiceAdvancedTest {

    @Test
    public void testConstrainedLine_obstructionDetected() {
        // This test creates a square grid with an elevated ridge (constrained
        // line) crossing the domain. It verifies that the triangulation honors
        // the ridge and that fetchTopographicProfile detects the ridge as an
        // obstruction when requested (either by returning `false` for
        // `free` or by including a high-Z intersection point in the profile).
        TopographyService svc = new TopographyService(8);
        // corner points of a square
        svc.addTopographicPoint(new Coordinate(0.0, 0.0, 0.0));
        svc.addTopographicPoint(new Coordinate(10.0, 0.0, 0.0));
        svc.addTopographicPoint(new Coordinate(10.0, 10.0, 0.0));
        svc.addTopographicPoint(new Coordinate(0.0, 10.0, 0.0));

        // add a vertical ridge as a constrained line at x=5 with elevated Z
        GeometryFactory gf = new GeometryFactory();
        Coordinate[] ridge = new Coordinate[] {
            new Coordinate(5.0, 0.0, 100.0),
            new Coordinate(5.0, 5.0, 100.0),
            new Coordinate(5.0, 10.0, 100.0)
        };
        LineString ridgeLine = gf.createLineString(ridge);
        svc.addTopographicLine(ridgeLine);
        // Also add ridge sample points to enforce vertices
        svc.addTopographicPoint(ridge[0]);
        svc.addTopographicPoint(ridge[1]);
        svc.addTopographicPoint(ridge[2]);

        assertTrue(svc.buildDelaunayTriangulation(), "buildDelaunayTriangulation should succeed with square + ridge");

        // Trace from left to right across the ridge; expect obstruction (ridge above LOS)
        java.util.List<Coordinate> pts = new java.util.ArrayList<>();
        boolean free = svc.fetchTopographicProfile(pts, new Coordinate(0.0,5.0), new Coordinate(10.0,5.0), true);
        // Either the method returns false (obstruction detected) or the returned profile
        // contains a point with high Z coming from the ridge (defensive check).
        boolean containsRidgeZ = pts.stream().anyMatch(c -> !Double.isNaN(c.z) && c.z > 50.0);
        assertTrue(!free || containsRidgeZ, "Expected ridge obstruction or high-Z intersection; free=" + free + " pts=" + pts.size());
        assertTrue(pts.size() >= 2, "profile should contain at least endpoints or intersections");

        // add cut points into a profile and ensure it sets zGround and flags
        CutPointSource src = new CutPointSource(new Coordinate(0.0,5.0));
        CutPointReceiver recv = new CutPointReceiver(new Coordinate(10.0,5.0));
        CutProfile profile = new CutProfile(src, recv);
        svc.addTopoCutPts(new Coordinate(0.0,5.0), new Coordinate(10.0,5.0), profile, true);
        // Ensure zGround values have been set (not NaN)
        assertTrue(!Double.isNaN(profile.getSource().zGround) || !Double.isNaN(profile.getReceiver().zGround));
        }

    @Test
    public void testLargeSyntheticDEM_plane_accuracy_and_performance() {
        // This test constructs a large synthetic regular grid sampled from an
        // analytic plane z = a*x + b*y + c. It validates:
        // 1) Delaunay triangulation completes on the full grid.
        // 2) Interpolated Z samples at several points match the analytic plane
        //    within numerical tolerance (accuracy check).
        // 3) Triangulation performance is reasonable (sanity check for CI).
        TopographyService svc = new TopographyService(16);
        final int N = 30; // 30x30 grid = 900 points
        final double dx = 1.0;
        // plane coefficients
        final double a = 0.1;
        final double b = 0.2;
        final double c = 1.0;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                double x = i * dx;
                double y = j * dx;
                double z = a * x + b * y + c;
                svc.addTopographicPoint(new Coordinate(x, y, z));
            }
        }

        long t0 = System.nanoTime();
        assertTrue(svc.buildDelaunayTriangulation(), "buildDelaunayTriangulation should succeed on the regular grid");
        long t1 = System.nanoTime();
        long durationMs = (t1 - t0) / 1_000_000L;

        // sample a few points and check interpolated Z equals analytic plane
        Coordinate s1 = new Coordinate(0.5, 0.5);
        Coordinate s2 = new Coordinate(10.2, 5.7);
        Coordinate s3 = new Coordinate((N-1)*dx, (N-1)*dx);

        AtomicInteger hint = new AtomicInteger(-1);
        double z1 = svc.getZGround(s1, hint);
        double expected1 = a * s1.x + b * s1.y + c;
        assertEquals(expected1, z1, 1e-6);

        double z2 = svc.getZGround(s2, hint);
        double expected2 = a * s2.x + b * s2.y + c;
        assertEquals(expected2, z2, 1e-6);

        double z3 = svc.getZGround(s3, hint);
        double expected3 = a * s3.x + b * s3.y + c;
        assertEquals(expected3, z3, 1e-6);

        // performance sanity check: should finish within 10s on CI/workstation
        assertTrue(durationMs < 10000, "Triangulation took too long: " + durationMs + "ms");
    }
}
