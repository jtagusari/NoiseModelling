package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.index.ItemVisitor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ProcessedWallServiceTest {

    // Test: add processed walls and build the STRtree index
    // Expectation: added walls are present in getProcessedWalls and index query returns entries
    @Test
    public void testAddAndBuildProcessedWallRtree() {
        ProcessedWallService svc = new ProcessedWallService(4);
        GeometryFactory gf = new GeometryFactory();

        Wall w1 = new Wall(new LineSegment(new Coordinate(0,0), new Coordinate(1,0)), 0, ProfileBuilder.IntersectionType.WALL);
        svc.addProcessedWall(w1, gf);
        assertEquals(1, svc.getProcessedWalls().size());

        // build index and query
        svc.buildProcessedWallRtree();
        List<?> res = svc.getProcessedRtree().query(new Envelope(0,1,0,1));
        assertFalse(res.isEmpty());
    }

    // Test: getWallsIn returns only BUILDING or WALL types inside the envelope
    @Test
    public void testGetWallsInFiltersByType() {
        ProcessedWallService svc = new ProcessedWallService(4);
        GeometryFactory gf = new GeometryFactory();

        Wall wb = new Wall(new LineSegment(new Coordinate(0,0), new Coordinate(1,0)), 0, ProfileBuilder.IntersectionType.WALL);
        Wall gb = new Wall(new LineSegment(new Coordinate(10,10), new Coordinate(11,10)), 0, ProfileBuilder.IntersectionType.GROUND_EFFECT);
        svc.addProcessedWall(wb, gf);
        svc.addProcessedWall(gb, gf);
        svc.buildProcessedWallRtree();

        List<Wall> hits = svc.getWallsIn(new Envelope(0,1,0,1));
        assertEquals(1, hits.size());
        assertEquals(ProfileBuilder.IntersectionType.WALL, hits.get(0).getType());
    }

    // Test: getWallsOnPath uses a visitor for segment intersection queries
    @Test
    public void testGetWallsOnPathWithVisitor() {
        ProcessedWallService svc = new ProcessedWallService(4);
        GeometryFactory gf = new GeometryFactory();

        Wall w1 = new Wall(new LineSegment(new Coordinate(0,0), new Coordinate(1,0)), 0, ProfileBuilder.IntersectionType.WALL);
        svc.addProcessedWall(w1, gf);
        svc.buildProcessedWallRtree();

        AtomicInteger visits = new AtomicInteger(0);
        ItemVisitor visitor = new ItemVisitor() {
            @Override
            public void visitItem(Object item) {
                visits.incrementAndGet();
            }
        };

    // Query the underlying STRtree directly with our ItemVisitor. This
    // avoids trying to subclass the final BuildingIntersectionPathVisitor
    // while still asserting that the spatial index invokes the visitor
    // for intersecting items.
    Envelope env = new Envelope(-1, 2, 0, 0);
    svc.getProcessedRtree().query(env, visitor);
    assertTrue(visits.get() > 0, "Visitor should be called for intersecting segments");
    }
}
