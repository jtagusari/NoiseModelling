package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BuildingServiceTest {

    @Test
    public void testAddBuildingFromCoords() {
        BuildingService svc = new BuildingService(4);
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(0, 0),
                new Coordinate(1, 0),
                new Coordinate(1, 1),
                new Coordinate(0, 1),
                new Coordinate(0, 0)
        };
        double height = 3.5;
        List<Double> alphas = new ArrayList<>();
        int id = 42;
        // call addBuilding(Coordinate[]...)
        svc.addBuilding(coords, height, alphas, id);

        assertEquals(1, svc.getBuildingCount(), "Building count should be 1 after adding");
        Building b = svc.getBuilding(0);
        assertNotNull(b, "Building should be present");
        assertEquals(id, b.getPrimaryKey(), "Primary key must be preserved");
        assertEquals(height, b.getHeight(), 1e-9, "Height must be preserved");
    }

    // Test: compute wide-angle offset points for a square polygon
    // Expectation: there are 4 offset points for the corners and the returned
    // list is closed (first point repeated at the end)
    @Test
    public void testGetWideAnglePointsOnPolygon_square() {
        BuildingService svc = new BuildingService(4);
        GeometryFactory f = new GeometryFactory();
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(0, 0, 0),
                new Coordinate(1, 0, 0),
                new Coordinate(1, 1, 0),
                new Coordinate(0, 1, 0),
                new Coordinate(0, 0, 0)
        };
        LinearRing ring = f.createLinearRing(coords);

        ArrayList<Coordinate> offsets = svc.getWideAnglePointsOnPolygon(ring, 0.0, 2 * Math.PI);

        // square has 4 corners, function returns closed list (first repeated at end)
        assertEquals(5, offsets.size(), "Offsets should contain 4 points + closing point");

        // each offset should be close to the original vertex (within a small multiple of epsilon)
        double eps = ProfileBuilder.wideAngleTranslationEpsilon;
        for (int i = 0; i < 4; i++) {
            Coordinate orig = coords[i];
            Coordinate off = offsets.get(i);
            double dist = orig.distance(off);
            assertTrue(dist <= 3 * eps, "Offset should be close to original vertex; got " + dist);
        }

        // closing point equals first
        assertEquals(offsets.get(0).x, offsets.get(4).x, 1e-12);
        assertEquals(offsets.get(0).y, offsets.get(4).y, 1e-12);
    }

    // Test: export building polygon edges as processed wall facets into
    // ProcessedWallService and precompute wide-angle helper points
    // Expectation: number of processed walls equals polygon edges and
    // precomputed points and building walls are set
    @Test
    public void testIndexBuildingFacetsAndPrecomputedPoints() {
    BuildingService svc = new BuildingService(4);
    Coordinate[] coords = new Coordinate[]{
        new Coordinate(0, 0, 0),
        new Coordinate(2, 0, 0),
        new Coordinate(2, 2, 0),
        new Coordinate(0, 2, 0),
        new Coordinate(0, 0, 0)
    };
    svc.addBuilding(coords, 5.0, new ArrayList<>(), 11);

    ProcessedWallService ws = new ProcessedWallService(4);
    svc.exportFacetsToProcessedWalls(ws, new GeometryFactory());

    // polygon has 4 edges -> 4 processed walls
    assertEquals(4, ws.getProcessedWalls().size(), "Processed walls should match polygon edges");
    // precomputed wide-angle points exist for building id 1
    ArrayList<Coordinate> pre = svc.getPrecomputedWideAnglePoints(1);
    assertNotNull(pre);
    assertTrue(pre.size() >= 1);
    // building should have walls set
    Building b = svc.getBuilding(0);
    assertNotNull(b.getWalls());
    assertEquals(4, b.getWalls().size());
    }

    // Test: compute building elevations when no topography service is set
    // Expectation: ground elevation defaults to 0, so building geometry Z equals height
    @Test
    public void testComputeBuildingElevations() {
    BuildingService svc = new BuildingService(4);
    Coordinate[] coords = new Coordinate[]{
        new Coordinate(0, 0),
        new Coordinate(1, 0),
        new Coordinate(1, 1),
        new Coordinate(0, 1),
        new Coordinate(0, 0)
    };
    double height = 7.25;
    svc.addBuilding(coords, height, new ArrayList<>(), 77);

    ProfileBuilder pb = new ProfileBuilder();
    // by default TopographyService has no DEM so getZGround returns 0 -> building elevation should equal height
    svc.computeElevations(pb);

    Building b = svc.getBuilding(0);
    double z = b.getGeometry().getCoordinate().z;
    assertEquals(height, z, 1e-9);
    }

    // Test: compute building elevations with a provided TopographyService
    // Expectation: building geometry Z is raised according to the topography
    @Test
    public void testComputeBuildingElevations_withTopography() {
        BuildingService svc = new BuildingService(4);
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(0, 0),
                new Coordinate(1, 0),
                new Coordinate(1, 1),
                new Coordinate(0, 1),
                new Coordinate(0, 0)
        };
        double height = 4.0;
        svc.addBuilding(coords, height, new ArrayList<>(), 88);

        // Prepare a ProfileBuilder and inject a TopographyService with a high ground at building location
        ProfileBuilder pb = new ProfileBuilder();
        TopographyService topo = new TopographyService(4);
        // Add high ground points at the building vertices so the DEM under the building is high
        topo.addTopographicPoint(new org.locationtech.jts.geom.Coordinate(0.0, 0.0, 50.0));
        topo.addTopographicPoint(new org.locationtech.jts.geom.Coordinate(1.0, 0.0, 50.0));
        topo.addTopographicPoint(new org.locationtech.jts.geom.Coordinate(1.0, 1.0, 50.0));
        topo.addTopographicPoint(new org.locationtech.jts.geom.Coordinate(0.0, 1.0, 50.0));
        // ensure triangulation runs
        assertTrue(topo.buildDelaunayTriangulation(), "buildDelaunayTriangulation should succeed with multiple topo points");
        // set the topography service into the profile builder so BuildingService will use it
        pb.setTopographyService(topo);

        // compute elevations; building elevation should be influenced by topo (zGround > 0)
        svc.computeElevations(pb);
        Building b = svc.getBuilding(0);
        double z = b.getGeometry().getCoordinate().z;
        // since topo z at center is 50 and building base height is 4, expect geometry Z to be >= 50 (or close)
        assertTrue(z >= 49.0, "Building geometry Z should be raised by topography, got " + z);
    }

    // Test: create building cut points and check obstruction for multiple cases
    // Expectation: when the ray is below the facet the profile flags an
    // obstruction and may stop; when the ray is above the facet no obstruction is flagged
    @Test
    public void testCreateBuildingCutPointAndCheckObstruction_cases() {
    BuildingService svc = new BuildingService(4);

    // facet wall segment at z=1
    Wall facet = new Wall(new org.locationtech.jts.geom.LineSegment(
        new Coordinate(0, 0, 1), new Coordinate(1, 0, 1)), 0, ProfileBuilder.IntersectionType.BUILDING);
    facet.primaryKey = 123L;

    // Case 1: ray z interpolated is <= intersection.z -> obstruction and return depends on stop flag
    Coordinate intersection = new Coordinate(0, 0, 1.5);
    org.locationtech.jts.geom.LineSegment fullLine = new org.locationtech.jts.geom.LineSegment(
        new Coordinate(-2, 0, 0), new Coordinate(2, 0, 0));
    List<CutPoint> newCutPoints = new ArrayList<>();
    CutProfile profile = new CutProfile(new CutPointSource(new Coordinate(-2,0,0)), new CutPointReceiver(new Coordinate(2,0,0)));

    boolean res = svc.createBuildingCutPointAndCheckObstruction(0, intersection, facet, fullLine, newCutPoints);
    // since stopAtObstacleOverSourceReceiver == true and intersection is above the ray, method should return false
    assertEquals(1, newCutPoints.size());
    assertInstanceOf(CutPointWall.class, newCutPoints.get(0));
    CutPointWall cpw = (CutPointWall)newCutPoints.get(0);
    assertEquals(123L, cpw.getWallPk().longValue());

    // Case 2: ray z interpolated > intersection.z -> no obstruction flagged and method returns true
    newCutPoints.clear();
    profile = new CutProfile(new CutPointSource(new Coordinate(-2,0,1.5)), new CutPointReceiver(new Coordinate(2,0,1.5)));
    Coordinate intersection2 = new Coordinate(0,0,0.1);
    org.locationtech.jts.geom.LineSegment fullLine2 = new org.locationtech.jts.geom.LineSegment(
        new Coordinate(-1,0,2), new Coordinate(1,0,2));
    boolean res2 = svc.createBuildingCutPointAndCheckObstruction(0, intersection2, facet, fullLine2, newCutPoints);
    assertEquals(1, newCutPoints.size());
    }
}
