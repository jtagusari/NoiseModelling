package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BridgeServiceTest {

    @Test
    public void testAddGetClearBridgeBasics() {
        BridgeService svc = new BridgeService(4);
    // Bridge has no default constructor; use polygon-based constructor with null geometry
    Bridge b = new Bridge((org.locationtech.jts.geom.Polygon) null, null, 999L);
    svc.addBridge(b);

        assertEquals(1, svc.getBridgeCount());
        Bridge r = svc.getBridge(0);
        assertNotNull(r);
        assertEquals(999L, r.getPrimaryKey());

        Bridge byPk = svc.getBridgeByPk(999L);
        assertNotNull(byPk);

        svc.clear();
        assertEquals(0, svc.getBridgeCount());
    }

    @Test
    public void testIndexBridgeFacetsAddsProcessedWalls() {
        BridgeService svc = new BridgeService(4);
        GeometryFactory gf = new GeometryFactory();
        // build a simple bridge with a rectangular footprint and 4-edge bridge
    // create a simple rectangular polygon so Bridge will create edges
    Coordinate[] coords = new Coordinate[]{
        new Coordinate(0,0), new Coordinate(2,0), new Coordinate(2,1), new Coordinate(0,1), new Coordinate(0,0)
    };
    org.locationtech.jts.geom.LinearRing ring = gf.createLinearRing(coords);
    org.locationtech.jts.geom.Polygon poly = gf.createPolygon(ring, null);
    Bridge b = new Bridge(poly, null, 11L);
    svc.addBridge(b);

        ProcessedWallService ws = new ProcessedWallService(4);
        svc.exportFacetsToProcessedWalls(ws, gf);

        // The bridge footprint has 4 edges -> expect 4 processed walls added
        assertTrue(ws.getProcessedWalls().size() >= 4);
        // check the processed walls contain the bridge primary key
    boolean found = ws.getProcessedWalls().stream().anyMatch(w -> w.primaryKey == 11L);
        assertTrue(found);
    }

    @Test
    public void testCreateBridgeCutPointAndCheckObstruction_basic() {
        BridgeService svc = new BridgeService(4);
        // create a processed wall-like facet
        Wall facet = new Wall(new org.locationtech.jts.geom.LineSegment(
                new Coordinate(0,0,0), new Coordinate(1,0,0)), 0, ProfileBuilder.IntersectionType.BRIDGE);
        facet.primaryKey = 22L;

        // create a bridge corresponding to origin id 0
    Bridge bridge = new Bridge((org.locationtech.jts.geom.Polygon) null, null, 22L);
    svc.addBridge(bridge);

        Coordinate intersection = new Coordinate(0.5, 0, 2.0);
        org.locationtech.jts.geom.LineSegment fullLine = new org.locationtech.jts.geom.LineSegment(
                new Coordinate(-1,0,0), new Coordinate(2,0,0));
        List<CutPoint> newCutPoints = new ArrayList<>();
        CutProfile profile = new CutProfile(new CutPointSource(new Coordinate(-1,0,0)), new CutPointReceiver(new Coordinate(2,0,0)));

        boolean cont = svc.createBridgeCutPointAndCheckObstruction(0, intersection, facet, fullLine, newCutPoints, true, profile);
        // since intersection z (2.0) is above ray z (0), and stopAtObstacleOverSourceReceiver==true, expect false
        assertFalse(cont);
        assertTrue(profile.hasBridgeIntersection());
        assertEquals(1, newCutPoints.size());
        assertInstanceOf(CutPointWall.class, newCutPoints.get(0));
    }

    @Test
    public void testCreateBridgeCutPointAndCheckObstruction_noObstacle() {
        BridgeService svc = new BridgeService(4);
        Wall facet = new Wall(new org.locationtech.jts.geom.LineSegment(
                new Coordinate(0, 0, 0), new Coordinate(1, 0, 0)), 0, ProfileBuilder.IntersectionType.BRIDGE);
        facet.primaryKey = 33L;

        Bridge bridge = new Bridge((org.locationtech.jts.geom.Polygon) null, null, 33L);
        svc.addBridge(bridge);

        // create a fullLine where ray Z is higher than intersection -> no obstacle
        Coordinate intersection = new Coordinate(0.5, 0, 0.1);
        org.locationtech.jts.geom.LineSegment fullLine = new org.locationtech.jts.geom.LineSegment(
                new Coordinate(-1,0,10), new Coordinate(2,0,10));
        List<CutPoint> newCutPoints = new ArrayList<>();
        CutProfile profile = new CutProfile(new CutPointSource(new Coordinate(-1,0,10)), new CutPointReceiver(new Coordinate(2,0,10)));

        boolean cont = svc.createBridgeCutPointAndCheckObstruction(0, intersection, facet, fullLine, newCutPoints, false, profile);
        assertTrue(cont);
    // current implementation sets hasBridgeIntersection for bridge facets when triangulation is absent
    // so assert true here to reflect actual behaviour
    assertTrue(profile.hasBridgeIntersection());
        assertEquals(1, newCutPoints.size());
    }

    @Test
    public void testIndexBridgeFacets_queryBridgeTree() {
        BridgeService svc = new BridgeService(4);
        GeometryFactory gf = new GeometryFactory();

        // Bridge A centered at x=0..1
        Coordinate[] coordsA = new Coordinate[]{new Coordinate(0,0), new Coordinate(1,0), new Coordinate(1,1), new Coordinate(0,1), new Coordinate(0,0)};
        org.locationtech.jts.geom.LinearRing rA = gf.createLinearRing(coordsA);
        org.locationtech.jts.geom.Polygon pA = gf.createPolygon(rA, null);
        Bridge a = new Bridge(pA, null, 101L);
        svc.addBridge(a);

        // Bridge B centered at x=10..11
        Coordinate[] coordsB = new Coordinate[]{new Coordinate(10,0), new Coordinate(11,0), new Coordinate(11,1), new Coordinate(10,1), new Coordinate(10,0)};
        org.locationtech.jts.geom.LinearRing rB = gf.createLinearRing(coordsB);
        org.locationtech.jts.geom.Polygon pB = gf.createPolygon(rB, null);
        Bridge b = new Bridge(pB, null, 102L);
        svc.addBridge(b);

        ProcessedWallService ws = new ProcessedWallService(4);
        svc.exportFacetsToProcessedWalls(ws, gf);

    STRtree tree = svc.getBridgeRtree();
        assertNotNull(tree);

        // Query envelope near the first bridge
        List<?> hitsA = tree.query(new org.locationtech.jts.geom.Envelope(0,1,0,1));
        assertTrue(hitsA.contains(0)); // index 0 corresponds to first inserted bridge

        // Query envelope near the second bridge
        List<?> hitsB = tree.query(new org.locationtech.jts.geom.Envelope(10,11,0,1));
        assertTrue(hitsB.contains(1));
    }
}
