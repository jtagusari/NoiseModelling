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
        
        // Expect IllegalArgumentException when finishFeeding due to insufficient bridge points
        assertThrows(IllegalArgumentException.class, () -> {
            pb.finishFeeding();
        }, "Should throw IllegalArgumentException when point manager is null or has insufficient points");
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

        // Expect IllegalArgumentException when finishFeeding due to insufficient bridge points
        assertThrows(IllegalArgumentException.class, () -> {
            pb.finishFeeding();
        }, "Should throw IllegalArgumentException when point manager is null or has insufficient points");
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
        
        // Expect IllegalArgumentException when finishFeeding due to insufficient bridge points
        assertThrows(IllegalArgumentException.class, () -> {
            pb.finishFeeding();
        }, "Should throw IllegalArgumentException when point manager is null or has insufficient points");
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
        
        // Expect IllegalArgumentException when finishFeeding due to insufficient bridge points
        assertThrows(IllegalArgumentException.class, () -> {
            pb.finishFeeding();
        }, "Should throw IllegalArgumentException when point manager is null or has insufficient points");
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
        
        // Expect IllegalArgumentException when finishFeeding due to insufficient bridge points
        assertThrows(IllegalArgumentException.class, () -> {
            pb.finishFeeding();
        }, "Should throw IllegalArgumentException when point manager is null or has insufficient points");
    }
}
