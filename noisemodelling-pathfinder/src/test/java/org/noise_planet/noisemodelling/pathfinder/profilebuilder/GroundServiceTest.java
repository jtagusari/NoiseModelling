package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LinearRing;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GroundServiceTest {

    @Test
    public void testIndexGroundEffectsAndFacetsAddsProcessedWalls() {
        GeometryFactory gf = new GeometryFactory();
        GroundService gs = new GroundService(4);
        ProcessedWallService ws = new ProcessedWallService(4);

        // create a simple square polygon as ground absorption
        Coordinate[] coords = new Coordinate[]{new Coordinate(0,0), new Coordinate(1,0), new Coordinate(1,1), new Coordinate(0,1), new Coordinate(0,0)};
        LinearRing lr = gf.createLinearRing(coords);
        Polygon poly = gf.createPolygon(lr, null);
        GroundAbsorption ga = new GroundAbsorption(poly, 0.5);
        gs.addGroundAbsorption(ga);

        // index into wall service
        gs.exportFacetsToProcessedWalls(ws, gf);
        ws.buildProcessedWallRtree();

        assertTrue(ws.getProcessedWalls().size() >= 4, "Processed walls should be created for polygon edges");
        // processedRtree must be queryable: make a small envelope over the polygon and query
        List<?> hits = ws.getProcessedRtree().query(new org.locationtech.jts.geom.Envelope(0,1,0,1));
        assertFalse(hits.isEmpty());
    }

    // Test: find intersecting ground absorption polygon for a given geometry
    // Expectation: point inside polygon returns the correct index; outside returns -1
    @Test
    public void testGetIntersectingGroundAbsorption() {
        GeometryFactory gf = new GeometryFactory();
        GroundService gs = new GroundService(4);

        Coordinate[] coords = new Coordinate[]{new Coordinate(0,0), new Coordinate(2,0), new Coordinate(2,2), new Coordinate(0,2), new Coordinate(0,0)};
        Polygon poly = gf.createPolygon(gf.createLinearRing(coords), null);
        GroundAbsorption ga = new GroundAbsorption(poly, 0.7);
        gs.addGroundAbsorption(ga);
        gs.insertGroundEffect(poly.getEnvelopeInternal(), 0);
        gs.buildGroundEffectsRtree();

        Point inside = gf.createPoint(new Coordinate(1,1));
        int idx = gs.getIntersectingGroundAbsorption(inside);
        assertEquals(0, idx);

        Point outside = gf.createPoint(new Coordinate(5,5));
        assertEquals(-1, gs.getIntersectingGroundAbsorption(outside));
    }

    // Test: creating a cut point for ground-effect boundary should add a CutPointGroundEffect
    // Expectation: method returns true and newCutPoints contains a CutPointGroundEffect instance
    @Test
    public void testProcessGroundEffectAddsCutPointGroundEffect() {
        GeometryFactory gf = new GeometryFactory();
        GroundService gs = new GroundService(4);
        WallService ws = new WallService(4);

        Coordinate[] coords = new Coordinate[]{new Coordinate(0,0), new Coordinate(2,0), new Coordinate(2,2), new Coordinate(0,2), new Coordinate(0,0)};
        Polygon poly = gf.createPolygon(gf.createLinearRing(coords), null);
        GroundAbsorption ga = new GroundAbsorption(poly, 0.25);
        gs.addGroundAbsorption(ga);
        // create a facet representing the polygon edge (originId = 0)
        LineSegment ls = new LineSegment(new Coordinate(0,0), new Coordinate(2,0));
        Wall facet = new Wall(ls, 0, ProfileBuilder.IntersectionType.GROUND_EFFECT).setProcessedWallIndex(0);

        List<CutPoint> newCutPoints = new ArrayList<>();
        LineSegment fullLine = new LineSegment(new Coordinate(-1,0,0), new Coordinate(3,0,0));

        boolean res = gs.createGroundCutPointAndCheckObstruction(0, new Coordinate(0.5,0,0), facet, fullLine, newCutPoints, false, new CutProfile(new CutPointSource(new Coordinate(-1,0,0)), new CutPointReceiver(new Coordinate(3,0,0))), gf);
        assertTrue(res);
        assertFalse(newCutPoints.isEmpty());
        assertTrue(newCutPoints.get(0) instanceof CutPointGroundEffect);
    }
}
