package org.noise_planet.noisemodelling.pathfinder;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SourceCollectorTest {

    private static final double DELTA = 1e-6;

    @Test
    public void testCollectPointSource() {
        GeometryFactory gf = new GeometryFactory();
        Scene scene = new Scene();
        // Point source at (10,0,0)
        Point p = gf.createPoint(new Coordinate(10, 0, 0));
        scene.addSource(p);

        ReceiverPointInfo rcv = new ReceiverPointInfo(0, 0L, new Coordinate(0, 0, 0));

        List<SourcePointInfo> sources = SourceCollector.collectSourcePoints(rcv, scene);

        assertEquals(1, sources.size());
        SourcePointInfo s = sources.get(0);
        assertEquals(0, s.getSourceIndex());
        assertEquals(1.0, s.getLineLength(), DELTA);
        assertEquals(10.0, s.getCoordinate().x, DELTA);
        assertEquals(0.0, s.getCoordinate().y, DELTA);
        // default behavior uses srcIndex when no sourcesPk provided
        assertEquals(0L, s.getSourcePk());
        assertNotNull(s.getOrientation());
    }

    @Test
    public void testCollectLineStringSource() {
        GeometryFactory gf = new GeometryFactory();
        Scene scene = new Scene();
        // Line from (10,0,0) to (20,0,0)
        Coordinate[] coords = new Coordinate[]{new Coordinate(10, 0, 0), new Coordinate(20, 0, 0)};
        LineString ls = gf.createLineString(coords);
        scene.addSource(ls);

        ReceiverPointInfo rcv = new ReceiverPointInfo(0, 0L, new Coordinate(0, 0, 0));

        List<SourcePointInfo> sources = SourceCollector.collectSourcePoints(rcv, scene);

    // LineStringSplitter should provide at least one sampled midpoint (implementation may provide more)
    assertTrue(sources.size() >= 1);
        SourcePointInfo s = sources.get(0);
        assertEquals(0, s.getSourceIndex());
    // Current splitter places the sample at 12.5 for this configuration
    assertEquals(12.5, s.getCoordinate().x, DELTA);
        assertEquals(0.0, s.getCoordinate().y, DELTA);
        // expected li == target segment size (line length 10 with segment constraint 5 -> 5)
        assertEquals(5.0, s.getLineLength(), 1e-3);
        assertNotNull(s.getOrientation());
    }

    @Test
    public void testCollectMultiLineStringSource() {
        GeometryFactory gf = new GeometryFactory();
        Scene scene = new Scene();
        // Two disjoint line segments
        LineString ls1 = gf.createLineString(new Coordinate[]{new Coordinate(10, 0, 0), new Coordinate(20, 0, 0)});
        LineString ls2 = gf.createLineString(new Coordinate[]{new Coordinate(30, 0, 0), new Coordinate(40, 0, 0)});
        MultiLineString mls = gf.createMultiLineString(new LineString[]{ls1, ls2});
        scene.addSource(mls);

        ReceiverPointInfo rcv = new ReceiverPointInfo(0, 0L, new Coordinate(0, 0, 0));

        List<SourcePointInfo> sources = SourceCollector.collectSourcePoints(rcv, scene);

        // Expect at least two sampled points (one per sub-line; implementation may provide more)
        assertTrue(sources.size() >= 2);
    // First sample should be the closest to the receiver (12.5 in current implementation)
    assertEquals(12.5, sources.get(0).getCoordinate().x, DELTA);
    // Ensure there is at least one sample near 35.0 for the far sub-line
    boolean hasFar = sources.stream().anyMatch(s -> Math.abs(s.getCoordinate().x - 35.0) < 1e-6);
    assertTrue(hasFar, "Should contain a sample near 35.0 for the far sub-line");
    }
}
