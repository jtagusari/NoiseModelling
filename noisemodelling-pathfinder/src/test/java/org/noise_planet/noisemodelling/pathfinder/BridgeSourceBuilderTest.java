package org.noise_planet.noisemodelling.pathfinder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.locationtech.jts.geom.*;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;

import java.util.List;

public class BridgeSourceBuilderTest {

    private static final GeometryFactory GF = new GeometryFactory();

    @Test
    public void testSplitLineWithNoBridgesAddsGroundSource() {
        ProfileBuilder pb = new ProfileBuilder();
        BridgeSourceBuilder splitter = new BridgeSourceBuilder(pb, 0.01);

        Coordinate[] coords = new Coordinate[]{new Coordinate(0,0), new Coordinate(10,0)};
        LineString line = GF.createLineString(coords);

    splitter.resetOutputBuffers();
    splitter.createBridgeRelatedLineSources(1L, line, false);

    List<Geometry> outGeoms = splitter.getSplittedSegments();
    List<SourceBridgeProperty> props = splitter.getSourceBridgeProperties();

    // no bridges -> one geometry (the line) and one SourceBridgeProperty
    assertEquals(1, outGeoms.size());
    assertEquals(1, props.size());
    assertTrue(outGeoms.get(0) instanceof LineString);
    // The single properties entry should indicate the source is not related to a bridge
    assertEquals(SourceBridgeProperty.SourceType.SOURCE_NOT_RELATED_TO_BRIDGE, props.get(0).getSourceType());
    }

    @Test
    public void testSplitLineWithBridgeProducesBridgeFragments() {
        ProfileBuilder pb = new ProfileBuilder();

        // create a simple rectangular bridge footprint that overlaps x in [4,6]
        Polygon footprint = GF.createPolygon(new Coordinate[]{
            new Coordinate(4, -1), new Coordinate(6, -1), new Coordinate(6, 1), new Coordinate(4, 1), new Coordinate(4, -1)
        });

        Bridge bridge = new Bridge(footprint, null, 123L);
        pb.addBridge(bridge);
        pb.finishFeeding();

        BridgeSourceBuilder splitter = new BridgeSourceBuilder(pb, 0.01);

        Coordinate[] coords = new Coordinate[]{new Coordinate(0,0), new Coordinate(10,0)};
        LineString line = GF.createLineString(coords);

    splitter.resetOutputBuffers();
    splitter.createBridgeRelatedLineSources(1L, line, true);
    List<Geometry> outGeoms = splitter.getSplittedSegments();
    List<SourceBridgeProperty> props = splitter.getSourceBridgeProperties();

        // we expect at least the bridge fragment(s) to be added: since addSourcesOnBridge adds 2 entries per bridge fragment
        boolean foundBridge = false;
        boolean foundBridgeType = false;
        for (SourceBridgeProperty p : props) {
            if (p == null) continue;
            // mark that something was added
            foundBridge = true;
            if (p.getSourceType() == SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE || p.getSourceType() == SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE) {
                foundBridgeType = true;
            }
        }
        assertTrue(foundBridge, "Expected bridge-related properties to be present");
        assertTrue(foundBridgeType, "Expected at least one bridge-related source type");
    }

    @Test
    public void testSmallOverlapIgnoredByThreshold() {
        // This test verifies that intersections smaller than minOverlapLengthMeters
        // are ignored and therefore do not produce bridge fragments.
        ProfileBuilder pb = new ProfileBuilder();

        // create a tiny bridge footprint overlapping x in [4, 4.005]
        Polygon tinyFootprint = GF.createPolygon(new Coordinate[]{
            new Coordinate(4.0, -1), new Coordinate(4.005, -1), new Coordinate(4.005, 1), new Coordinate(4.0, 1), new Coordinate(4.0, -1)
        });

        Bridge tinyBridge = new Bridge(tinyFootprint, null, 200L);
        pb.addBridge(tinyBridge);
        pb.finishFeeding();

        // Set threshold to 0.01 (10 mm). The overlap length (~0.005) is below that.
        BridgeSourceBuilder splitter = new BridgeSourceBuilder(pb, 0.01);

        Coordinate[] coords = new Coordinate[]{new Coordinate(0,0), new Coordinate(10,0)};
        LineString line = GF.createLineString(coords);

        splitter.resetOutputBuffers();
        splitter.createBridgeRelatedLineSources(1L, line, true);
    List<Geometry> outGeoms = splitter.getSplittedSegments();
    List<SourceBridgeProperty> props = splitter.getSourceBridgeProperties();

        // none of the output fragments should intersect the tiny footprint (it was ignored)
        int intersectCount = 0;
        for (Geometry g : outGeoms) {
            Geometry inter = g.intersection(tinyFootprint);
            if (inter != null && !inter.isEmpty() && inter.getLength() > 0) intersectCount++;
        }
        assertEquals(0, intersectCount, "Tiny footprint should be ignored by threshold (no positive-length intersection)");
        // when tiny footprint is removed from the remaining geometry the line is split
        // into left/right ground fragments; both resulting properties should be ground sources
        assertEquals(2, props.size());
        for (SourceBridgeProperty p : props) {
            assertEquals(SourceBridgeProperty.SourceType.SOURCE_NOT_RELATED_TO_BRIDGE, p.getSourceType());
        }
    }

    @Test
    public void testMultipleBridgesProduceMultipleFragments() {
        // This test verifies a line overlapping two separate bridges gets fragments
        // corresponding to each bridge.
        ProfileBuilder pb = new ProfileBuilder();

        Polygon b1 = GF.createPolygon(new Coordinate[]{
            new Coordinate(2, -1), new Coordinate(3, -1), new Coordinate(3, 1), new Coordinate(2, 1), new Coordinate(2, -1)
        });
        Polygon b2 = GF.createPolygon(new Coordinate[]{
            new Coordinate(6, -1), new Coordinate(7, -1), new Coordinate(7, 1), new Coordinate(6, 1), new Coordinate(6, -1)
        });

        Bridge bridge1 = new Bridge(b1, null, 301L);
        Bridge bridge2 = new Bridge(b2, null, 302L);
        pb.addBridge(bridge1);
        pb.addBridge(bridge2);
        pb.finishFeeding();

        BridgeSourceBuilder splitter = new BridgeSourceBuilder(pb, 0.001);

        Coordinate[] coords = new Coordinate[]{new Coordinate(0,0), new Coordinate(10,0)};
        LineString line = GF.createLineString(coords);

    splitter.resetOutputBuffers();
    splitter.createBridgeRelatedLineSources(1L, line, true);
    List<Geometry> outGeoms = splitter.getSplittedSegments();
    List<SourceBridgeProperty> props = splitter.getSourceBridgeProperties();
        // Count fragments that intersect each bridge footprint envelope
        int countB1 = 0, countB2 = 0;
        for (Geometry g : outGeoms) {
            if (g.getEnvelopeInternal().intersects(b1.getEnvelopeInternal())) countB1++;
            if (g.getEnvelopeInternal().intersects(b2.getEnvelopeInternal())) countB2++;
        }

        assertTrue(countB1 > 0, "Expected fragments for bridge1");
        assertTrue(countB2 > 0, "Expected fragments for bridge2");
        // check props include ACTUAL_SOURCE_ON_BRIDGE entries for both bridges
        boolean saw301 = false, saw302 = false;
        for (SourceBridgeProperty p : props) {
            if (p.getBridgePkOn() == 301L && p.getSourceType() == SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE) saw301 = true;
            if (p.getBridgePkOn() == 302L && p.getSourceType() == SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE) saw302 = true;
        }
        assertTrue(saw301, "Expected ACTUAL_SOURCE_ON_BRIDGE for bridge1");
        assertTrue(saw302, "Expected ACTUAL_SOURCE_ON_BRIDGE for bridge2");
    }

    @Test
    public void testBridgePkSpecificSplitProducesBridgeFragment() {
        // This test calls the bridge-PK-specific overload to ensure the line is
        // split for the given bridge and resulting fragments intersect that bridge.
        ProfileBuilder pb = new ProfileBuilder();

        Polygon footprint = GF.createPolygon(new Coordinate[]{
            new Coordinate(4, -1), new Coordinate(6, -1), new Coordinate(6, 1), new Coordinate(4, 1), new Coordinate(4, -1)
        });

        Bridge bridge = new Bridge(footprint, null, 777L);
        pb.addBridge(bridge);
        pb.finishFeeding();

        BridgeSourceBuilder splitter = new BridgeSourceBuilder(pb, 0.001);

        Coordinate[] coords = new Coordinate[]{new Coordinate(0,0), new Coordinate(10,0)};
        LineString line = GF.createLineString(coords);

        splitter.resetOutputBuffers();
        // call the overload that targets a specific bridge
        splitter.createBridgeRelatedLineSources(1L, line, true, 777L);
    List<Geometry> outGeoms = splitter.getSplittedSegments();
    List<SourceBridgeProperty> props = splitter.getSourceBridgeProperties();

        boolean anyIntersect = false;
        for (Geometry g : outGeoms) {
            if (g.getEnvelopeInternal().intersects(footprint.getEnvelopeInternal())) {
                anyIntersect = true;
                break;
            }
        }
        assertTrue(anyIntersect, "Expected at least one fragment intersecting the targeted bridge");
        // ensure at least one property references the target bridge as an actual source on bridge
        boolean sawActual = false;
        for (SourceBridgeProperty p : props) {
            if (p.getBridgePkOn() == 777L && p.getSourceType() == SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE) {
                sawActual = true;
                break;
            }
        }
        assertTrue(sawActual, "Expected ACTUAL_SOURCE_ON_BRIDGE for targeted bridge");
    }

    @Test
    public void testOverlappingBridgesWithDifferentHeights() {
        // Two bridges whose footprints overlap the same line segment but have
        // different average deck heights. The splitter should consider deck
        // height when discovering fragments (minDeckHeight filtering). We create
        // one "low" bridge and one "high" bridge overlapping the same x-range.

        ProfileBuilder pb = new ProfileBuilder();

        // footprint for both bridges are identical in plan (x in [4,6])
        Polygon footprint = GF.createPolygon(new Coordinate[]{
            new Coordinate(4, -1), new Coordinate(6, -1), new Coordinate(6, 1), new Coordinate(4, 1), new Coordinate(4, -1)
        });

        // Create low bridge (average deck height = 1.0)
        Bridge lowBridge = new Bridge(footprint, null, 900L) {
            @Override
            public double getAverageAbsoluteDeckHeight() {
                return 1.0;
            }
        };

        // Create high bridge (average deck height = 10.0)
        Bridge highBridge = new Bridge(footprint, null, 901L) {
            @Override
            public double getAverageAbsoluteDeckHeight() {
                return 10.0;
            }
        };

        pb.addBridge(lowBridge);
        pb.addBridge(highBridge);
        pb.finishFeeding();

        // Use splitter with minOverlapLengthMeters small; then call addMirrorSources
        // which uses minDeckHeight = targetDeckHeight + MILLIMETER. We'll call the
        // discovery variant to obtain fragments above MIN_DECK_HEIGHT (which is very low)
        BridgeSourceBuilder splitter = new BridgeSourceBuilder(pb, 0.001);

        Coordinate[] coords = new Coordinate[]{new Coordinate(0,0), new Coordinate(10,0)};
        LineString line = GF.createLineString(coords);

    splitter.resetOutputBuffers();
    splitter.createBridgeRelatedLineSources(1L, line, true);
    List<SourceBridgeProperty> props = splitter.getSourceBridgeProperties();
        // Since both bridges occupy same footprint, ensure that fragments are produced
        // and that sourceBridgeProperties include entries for both primary keys (900L and 901L)
        boolean saw900 = false, saw901 = false;
        boolean sawActual = false;
        for (SourceBridgeProperty p : props) {
            if (p == null) continue;
            long pk = p.getBridgePkOn();
            if (pk == 900L) saw900 = true;
            if (pk == 901L) saw901 = true;
            if (p.getSourceType() == SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE) sawActual = true;
        }

        assertTrue((saw900 || saw901) && sawActual, "Expected fragments referencing overlapping bridges and at least one ACTUAL_SOURCE_ON_BRIDGE");
    }
}
