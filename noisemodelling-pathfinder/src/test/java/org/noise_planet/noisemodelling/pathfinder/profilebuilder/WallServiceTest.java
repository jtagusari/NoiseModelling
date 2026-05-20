package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WallServiceTest {

    // Test: adding a raw Wall and reading it back from the service
    // Expectation: wall count increases and the stored wall is retrievable
    @Test
    public void testAddWallAndCount() {
        WallService svc = new WallService(4);
        LineSegment ls = new LineSegment(new Coordinate(0,0), new Coordinate(1,0));
        Wall w = new Wall(ls, 0, ProfileBuilder.IntersectionType.WALL);
        svc.addWall(w);

        assertEquals(1, svc.getWallCount(), "Wall count should be 1 after add");
        Wall got = svc.getWall(0);
        assertNotNull(got);
        assertEquals(w.getLineSegment().p0.x, got.getLineSegment().p0.x, 1e-12);
    }

    // Test: export raw walls into ProcessedWallService
    // Expectation: processed wall list size increases to match exported facets
    @Test
    public void testExportRawWallToProcessedWalls() {
        WallService svc = new WallService(4);
        LineSegment ls = new LineSegment(new Coordinate(0,0), new Coordinate(2,0));
        Wall w = new Wall(ls, 0, ProfileBuilder.IntersectionType.WALL);
        svc.addWall(w);

        ProcessedWallService pws = new ProcessedWallService(4);
        svc.exportFacetsToProcessedWalls(pws, new GeometryFactory());

        // A single raw wall with two coordinates produces one processed facet
        assertEquals(1, pws.getProcessedWalls().size(), "Processed walls should be created for each raw wall edge");
    }

    // Test: compute wall bottom elevations using ProfileBuilder's topography
    // Expectation: endpoints Z are set to wall.height + zGround when initial Z is zero
    @Test
    public void testComputeWallBottomElevations() {
        WallService svc = new WallService(4);
        LineSegment ls = new LineSegment(new Coordinate(0,0), new Coordinate(1,0));
        Wall w = new Wall(ls, 0, ProfileBuilder.IntersectionType.WALL);
        w.height = 3.5;
        svc.addWall(w);

        ProfileBuilder pb = new ProfileBuilder();
        // default topography returns 0; compute should set endpoints to height
        svc.computeElevations(pb);

        Wall got = svc.getWall(0);
        assertEquals(w.height + pb.getZGround(got.getP0()), got.getP0().z, 1e-9);
        assertEquals(w.height + pb.getZGround(got.getP1()), got.getP1().z, 1e-9);
    }

}
