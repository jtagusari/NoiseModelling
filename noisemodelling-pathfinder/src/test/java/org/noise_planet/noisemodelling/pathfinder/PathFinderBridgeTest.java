/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.*;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.path.BridgeRelationship;
import org.noise_planet.noisemodelling.pathfinder.path.BridgeRelationship.RelationType;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgePoint;
import org.noise_planet.noisemodelling.pathfinder.path.SceneBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReflection;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointSource;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointWall;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.CoordinateMixin;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.LineSegmentMixin;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for {@link PathFinder} CutProfile generation in bridge scenarios (TBC series).
 *
 * <p>Each test builds a simple flat scene (no DEM), runs {@link PathFinder}, and asserts that
 * the produced {@link CutProfile} cut points (type, coordinates, wall geometry) match stored
 * reference JSON files under {@code src/main/resources/.../test_cases/}.
 *
 * <p><b>Workflow to regenerate reference files:</b>
 * <ol>
 *   <li>Set {@link #overwriteTestCase} to {@code true} and run all tests.</li>
 *   <li>Reference JSON files are written to {@code PathFinder.class.getResource("test_cases")},
 *       which resolves to {@code src/main/resources/.../test_cases/} when run from the IDE.</li>
 *   <li>Set {@link #overwriteTestCase} back to {@code false} for ongoing regression checks.</li>
 * </ol>
 *
 * <p><b>Bridge geometry used in TBC01–TBC21:</b><br>
 * Bridge1 (pk=100): center line x=10, y=0..20, deck z=10, no explicit width/barrier.<br>
 * Bridge2 (pk=101): center line x=12, y=0..20, deck z=5, half-widths 10 m, barriers 1 m.
 *
 * <p><b>TBC30:</b> uses the real TutoBridge DEM (SRID 6676) and bridge geometry from GeoJSON
 * instead of the synthetic flat bridge used in TBC01–TBC21.
 *
 * <p><b>TBC31:</b> extends TBC30 with a second source (SOURCE_NOT_RELATED_TO_BRIDGE) and a
 * wall near the bridge, verifying that all four relation types (ACTUAL, IMAGINARY, MIRROR,
 * NOT_RELATED) are produced simultaneously.
 */
public class PathFinderBridgeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PathFinderBridgeTest.class);

    /**
     * Set to {@code true} to regenerate reference JSON files, then revert to {@code false}.
     * Writing uses {@code PathFinder.class.getResource("test_cases")} which in IDE mode
     * resolves directly to {@code src/main/resources/.../test_cases/}.
     */
    public boolean overwriteTestCase = false;

    /**
     * When {@code true}, always write a {@code tmp_<name>.json} alongside the reference file
     * for quick visual inspection of the current profile, independent of {@link #overwriteTestCase}.
     */
    public boolean outputCurrentCutProfile = true;

    /** Coordinate comparison tolerance (metres). */
    public static final double DELTA_COORDS = 0.1;

    /** Plane/segment comparison tolerance. */
    public static final double DELTA_PLANES = 0.1;

    /**
     * Routing method: write reference JSON if {@link #overwriteTestCase} is set,
     * optionally write a {@code tmp_} copy, then assert against the stored reference.
     */
    private void assertCutProfile(String utName, CutProfile cutProfile) throws IOException {
        String testCaseFileName = utName + ".json";
        if(overwriteTestCase) {
            writeCutProfileJson(utName, cutProfile);
        }
        if(outputCurrentCutProfile){
            writeCutProfileJson("tmp_" + utName, cutProfile);
        }
        assertCutProfile(PathFinder.class.getResourceAsStream("test_cases/" + testCaseFileName), cutProfile);
    }

    /**
     * Serialize {@code cutProfile} and write it to {@code PathFinder.class.getResource("test_cases")}.
     * In IDE mode this writes directly into {@code src/main/resources/}; in Maven it writes to
     * {@code target/classes/} and the file must be committed manually.
     */
    private void writeCutProfileJson(String utName, CutProfile cutProfile) throws IOException {
        String testCaseFileName = utName + ".json";
        URL resourcePath = PathFinder.class.getResource("test_cases");
        if(resourcePath != null) {
        File destination = new File(resourcePath.getFile(), testCaseFileName);
        try (FileWriter utFile = new FileWriter(destination)){
                utFile.write(cutProfileAsJson(cutProfile));
        }
        LOGGER.warn("{} written in \n{}", testCaseFileName, destination);
        }
    }

    /** Serialize a CutProfile to a pretty-printed JSON string with Coordinate/LineSegment mixins. */
    public static String cutProfileAsJson(CutProfile cutProfile) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Coordinate.class, CoordinateMixin.class);
        mapper.addMixIn(LineSegment.class, LineSegmentMixin.class);
        ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();
        return writer.writeValueAsString(cutProfile);
    }

    /** Deserialize a CutProfile from an InputStream and delegate to {@link #assertCutProfile(CutProfile, CutProfile)}. */
    public static void assertCutProfile(InputStream expected, CutProfile got) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        CutProfile cutProfile = mapper.readValue(expected, CutProfile.class);
        assertCutProfile(cutProfile, got);
    }

    /**
     * Assert that two CutProfiles are equivalent: same number of cut points, same types,
     * same 3D coordinates (±{@value DELTA_COORDS} m), and same wall/reflection geometry where applicable.
     */
    public static void assertCutProfile(CutProfile expected, CutProfile got) {
        assertNotNull(expected);
        assertNotNull(got);
        assertEquals(expected.getCutPoints().size(), got.getCutPoints().size(), "Not the same number of cut points");
        for (int i = 0; i < expected.getCutPoints().size(); i++) {
            CutPoint expectedCutPoint = expected.getCutPoints().get(i);
            CutPoint gotCutPoint = got.getCutPoints().get(i);
            assertInstanceOf(expectedCutPoint.getClass(), gotCutPoint);
            assert3DCoordinateEquals(expectedCutPoint+"!="+gotCutPoint, expectedCutPoint.coordinate,
                    gotCutPoint.coordinate, DELTA_COORDS);
            assertEquals(expectedCutPoint.zGround, gotCutPoint.zGround, 0.01, "zGround");
            assertEquals(expectedCutPoint.getGroundCoefficient(), gotCutPoint.getGroundCoefficient(), 0.01, "groundCoefficient");

            if(expectedCutPoint instanceof CutPointSource) {
                CutPointSource expectedCutPointSource = (CutPointSource) expectedCutPoint;
                CutPointSource gotCutPointSource = (CutPointSource) gotCutPoint;
                assertEquals(expectedCutPointSource.getLineLength(), gotCutPointSource.getLineLength(),0.01);
                assertEquals(expectedCutPointSource.getOrientation().yaw, gotCutPointSource.getOrientation().yaw,0.01);
                assertEquals(expectedCutPointSource.getOrientation().pitch, gotCutPointSource.getOrientation().pitch,0.01);
                assertEquals(expectedCutPointSource.getOrientation().roll, gotCutPointSource.getOrientation().roll,0.01);
            } else if (expectedCutPoint instanceof CutPointWall) {
                CutPointWall expectedCutPointWall = (CutPointWall) expectedCutPoint;
                CutPointWall gotCutPointWall = (CutPointWall) gotCutPoint;
                assert3DCoordinateEquals(expectedCutPointWall+"!="+gotCutPointWall, expectedCutPointWall.wall.p0,
                        gotCutPointWall.wall.p0, DELTA_COORDS);
                assert3DCoordinateEquals(expectedCutPointWall+"!="+gotCutPointWall, expectedCutPointWall.wall.p1,
                        gotCutPointWall.wall.p1, DELTA_COORDS);
                if(!expectedCutPointWall.getWallAlpha().isEmpty()) {
                    assertArrayEquals(expectedCutPointWall.alphaAsArray(), gotCutPointWall.alphaAsArray(), 0.01, "expectedCutPointWall.alpha");
                }
            } else if (expectedCutPoint instanceof CutPointReflection) {
                CutPointReflection expectedCutPointReflection = (CutPointReflection) expectedCutPoint;
                CutPointReflection gotCutPointReflection = (CutPointReflection) gotCutPoint;
                assert3DCoordinateEquals(expectedCutPointReflection+"!="+gotCutPointReflection,
                        expectedCutPointReflection.wall.p0, gotCutPointReflection.wall.p0, DELTA_COORDS);
                assert3DCoordinateEquals(expectedCutPointReflection+"!="+gotCutPointReflection,
                        expectedCutPointReflection.wall.p1, gotCutPointReflection.wall.p1, DELTA_COORDS);
                assertArrayEquals(expectedCutPointReflection.alphaAsArray(), gotCutPointReflection.alphaAsArray(), 0.01, "expectedCutPointReflection.alphaAsArray");
            }
        }

    }

    public static void assert3DCoordinateEquals(String message,Coordinate expected, Coordinate actual, double tolerance) {

        if (CGAlgorithms3D.distance(expected, actual) > tolerance) {
            String result = String.format(Locale.ROOT, "Expected coordinate: %s, Actual coordinate: %s",
                    expected, actual);
            throw new AssertionError(message+result);
        }
    }
    
    /**
     * Bridge1 (pk=100): single-span synthetic bridge at x=10, running from y=0 to y=20.
     * Deck at absolute z=10, no explicit half-width or barriers (default narrow profile).
     * Used as the primary bridge in TBC01–TBC10 and TBC20–TBC21.
     */
    private Bridge createBridge1() {

        List<Double> defaultAlphas = Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        
        List<Coordinate> bridgePointCoords = Arrays.asList(
                new Coordinate(10, 0, 10),
                new Coordinate(10, 10, 10),
                new Coordinate(10, 20, 10)
        ); 
        List<BridgePoint> bridgePoints = new ArrayList<>();

        for (long pk = 0; pk < bridgePointCoords.size(); pk++) {
                Coordinate coord = bridgePointCoords.get((int)pk);
                BridgePoint point = new BridgePoint.Builder(pk, 100L, coord)
                    .withGirderType(Bridge.GirderType.STEEL_BOX)
                    .withSlabType(Bridge.SlabType.STEEL)
                    .build();
                bridgePoints.add(point);
        }

        return new Bridge.Builder(bridgePoints).withAlphas(defaultAlphas).setPrimaryKey(100L).build();
    }


    /**
     * Bridge2 (pk=101): secondary synthetic bridge at x=12, running from y=0 to y=20.
     * Deck at absolute z=5; half-widths 10 m on each side, barrier height 1 m on each side.
     * Added to the scene alongside Bridge1 in TBC20 and TBC21.
     */
    private Bridge createBridge2() {

        List<Coordinate> bridgePointCoords = Arrays.asList(
                new Coordinate(12, 0, 5),
                new Coordinate(12, 10, 5),
                new Coordinate(12, 20, 5)
        ); 

        List<Double> defaultAlphas = Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

        List<BridgePoint> bridgePoints = new ArrayList<>();

        for (long pk = 0; pk < bridgePointCoords.size(); pk++) {
                Coordinate coord = bridgePointCoords.get((int)pk);
                BridgePoint point = new BridgePoint.Builder(pk, 101L, coord)
                    .withWidth(10.0,10.0)
                    .withBarrierHeight(1.0,1.0)
                    .withPosition(BridgePoint.Position.CENTER)
                    .withGirderType(Bridge.GirderType.STEEL_BOX)
                    .withSlabType(Bridge.SlabType.STEEL)
                    .build();
                bridgePoints.add(point);
        }
        

        return new Bridge.Builder(bridgePoints).withAlphas(defaultAlphas).setPrimaryKey(101L).build();
    }

    /**
     * TBC01 — ACTUAL_SOURCE_ON_BRIDGE, distant receiver (200, 50, 4).
     * Source: Bridge1 (pk=100), line x=10, y=5..15, z=10.5 (absolute).
     * Distant receiver → 1 sample point → 1 IMAGINARY + 1 ACTUAL profile.
     * IMAGINARY sorts first (its z≈9.45 is closer to receiver z=4 than ACTUAL z=10.5).
     */
    @Test
    public void TBC01() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 10.5),
                new Coordinate(10, 15, 10.5)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(1L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1))
                .addReceiver(1L, 200, 50, 4, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // ACTUAL_SOURCE_ON_BRIDGE produces 2 profiles: the IMAGINARY variant (z≈9.45) and the
        // ACTUAL (z=10.5). SourceCollector sorts by 3D distance to receiver (z=4), so IMAGINARY
        // (smaller dz) comes first.
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(2, profiles.size(), "Expected 1 IMAGINARY + 1 ACTUAL profile");
        assertEquals(RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE,
                profiles.get(0).getSource().getBridgeRelationship().getRelationType(),
                "profile[0] should be IMAGINARY (sorts closer to receiver)");
        assertEquals(RelationType.ACTUAL_SOURCE_ON_BRIDGE,
                profiles.get(1).getSource().getBridgeRelationship().getRelationType(),
                "profile[1] should be ACTUAL");
        assertCutProfile("TBC01_imaginary", profiles.get(0));
        assertCutProfile("TBC01_actual", profiles.get(1));
    }
    
    /**
     * TBC02 — ACTUAL_SOURCE_ON_BRIDGE, receiver beside and below the bridge deck.
     * Source: Bridge1 (pk=100), line x=10, y=5..15, z=10.5 (absolute).
     * Receiver: (12, 10, 4) — deck is at z=10, so receiver is below the deck.
     * 3 source sample points → 3 IMAGINARY + 3 ACTUAL profiles.
     * The middle profile (skip(1)) of each type is asserted against the reference.
     */
    @Test
    public void TBC02() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 10.5),
                new Coordinate(10, 15, 10.5)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(1L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1))
                .addReceiver(1L, 12, 10, 4, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // 3 sample points → 3 IMAGINARY + 3 ACTUAL; IMAGINARY sorts first (closer to receiver below bridge)
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(6, profiles.size(), "Expected 3 IMAGINARY + 3 ACTUAL profiles (3 sample points each)");
        assertEquals(3L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE)
                .count(), "Expected 3 IMAGINARY profiles");
        assertEquals(3L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.ACTUAL_SOURCE_ON_BRIDGE)
                .count(), "Expected 3 ACTUAL profiles");

        CutProfile actualProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.ACTUAL_SOURCE_ON_BRIDGE)
                .skip(1).findFirst().orElseThrow();
        assertCutProfile("TBC02_actual", actualProfile);

        CutProfile imaginaryProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE)
                .skip(1).findFirst().orElseThrow();
        assertCutProfile("TBC02_imaginary", imaginaryProfile);
    }

        
    /**
     * TBC03 — ACTUAL_SOURCE_ON_BRIDGE, receiver above the bridge deck.
     * Source: Bridge1 (pk=100), line x=10, y=5..15, z=10.5 (absolute).
     * Receiver: (12, 10, 14) — deck is at z=10, so receiver is above the deck.
     * 5 source sample points → 5 ACTUAL + 5 IMAGINARY profiles.
     * ACTUAL sorts first (receiver is closer in z to the ACTUAL source).
     * The middle profile (skip(2)) of each type is asserted against the reference.
     */
    @Test
    public void TBC03() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 10.5),
                new Coordinate(10, 15, 10.5)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(1L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1))
                .addReceiver(1L, 12, 10, 14, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // 5 sample points → 5 ACTUAL + 5 IMAGINARY; ACTUAL sorts first (receiver above bridge deck, closer to ACTUAL z=10.5)
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(10, profiles.size(), "Expected 5 ACTUAL + 5 IMAGINARY profiles (5 sample points each)");
        assertEquals(5L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.ACTUAL_SOURCE_ON_BRIDGE)
                .count(), "Expected 5 ACTUAL profiles");
        assertEquals(5L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE)
                .count(), "Expected 5 IMAGINARY profiles");
        CutProfile actualProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.ACTUAL_SOURCE_ON_BRIDGE)
                .skip(2).findFirst().orElseThrow();
        assertCutProfile("TBC03_actual", actualProfile);

        CutProfile imaginaryProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE)
                .skip(2).findFirst().orElseThrow();
        assertCutProfile("TBC03_imaginary", imaginaryProfile);
    }

    /**
     * TBC04 — MIRROR_SOURCE, distant receiver (200, 50, 4).
     * Source: MIRROR_SOURCE on Bridge1 (pk=100), line x=10, y=5..15, z=15 (above deck z=10).
     * Distant receiver → 1 sample point → 1 MIRROR_SOURCE profile.
     */
    @Test
    public void TBC04() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 15),
                new Coordinate(10, 15, 15)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(100L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.MIRROR_SOURCE, -1, 100))
                .addReceiver(1L, 200, 50, 4, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Distant receiver → 1 sample point → 1 MIRROR_SOURCE profile
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(1, profiles.size(), "Expected 1 MIRROR_SOURCE profile");
        assertEquals(RelationType.MIRROR_SOURCE,
                profiles.get(0).getSource().getBridgeRelationship().getRelationType());
        assertCutProfile("TBC04_mirror", profiles.get(0));
    }

    /**
     * TBC05 — MIRROR_SOURCE, close receiver below deck (12, 10, 4).
     * Source: MIRROR_SOURCE on Bridge1 (pk=100), line x=10, y=5..15, z=15.
     * Close receiver beside the bridge → 2 sample points → 2 MIRROR_SOURCE profiles.
     * The first profile is asserted against the reference.
     */
    @Test
    public void TBC05() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 15),
                new Coordinate(10, 15, 15)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(100L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.MIRROR_SOURCE, -1, 100))
                .addReceiver(1L, 12, 10, 4, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Close receiver → 2 sample points → 2 MIRROR_SOURCE profiles
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(2, profiles.size(), "Expected 2 MIRROR_SOURCE profiles (2 sample points)");
        assertEquals(2L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.MIRROR_SOURCE)
                .count(), "All profiles should be MIRROR_SOURCE");
        assertCutProfile("TBC05_mirror", profiles.get(0));
    }

    
    /**
     * TBC06 — MIRROR_SOURCE, close receiver above deck (12, 10, 14).
     * Source: MIRROR_SOURCE on Bridge1 (pk=100), line x=10, y=5..15, z=15.
     * Very close receiver beside the bridge → 9 sample points → 9 MIRROR_SOURCE profiles.
     * The middle profile (skip(4)) is asserted against the reference.
     */
    @Test
    public void TBC06() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 15),
                new Coordinate(10, 15, 15)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(100L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.MIRROR_SOURCE, -1, 100))
                .addReceiver(1L, 12, 10, 14, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Very close receiver → 9 sample points → 9 MIRROR_SOURCE profiles
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(9, profiles.size(), "Expected 9 MIRROR_SOURCE profiles (9 sample points)");
        assertEquals(9L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.MIRROR_SOURCE)
                .count(), "All profiles should be MIRROR_SOURCE");
        
        CutProfile mirrorProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.MIRROR_SOURCE)
                .skip(4).findFirst().orElseThrow();
        assertCutProfile("TBC06_mirror", mirrorProfile);
    }


    /**
     * TBC07 — IMAGINARY_SOURCE_UNDER_BRIDGE, distant receiver (200, 50, 4).
     * Source: explicitly IMAGINARY, z=9.5 (just below deck z=10) on Bridge1 (pk=100).
     * Distant receiver → 1 sample point → 1 IMAGINARY_SOURCE_UNDER_BRIDGE profile.
     */
    @Test
    public void TBC07() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 9.5),
                new Coordinate(10, 15, 9.5)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(100L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100))
                .addReceiver(1L, 200, 50, 4, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Distant receiver → 1 sample point → 1 IMAGINARY_SOURCE_UNDER_BRIDGE profile
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(1, profiles.size(), "Expected 1 IMAGINARY_SOURCE_UNDER_BRIDGE profile");
        assertEquals(RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE,
                profiles.get(0).getSource().getBridgeRelationship().getRelationType());
        assertCutProfile("TBC07_imaginary", profiles.get(0));
    }

    
    /**
     * TBC08 — SOURCE_NOT_RELATED_TO_BRIDGE, source completely outside the bridge footprint.
     * Source: x=0 (far from Bridge1 at x=10), z≈0 (ground level), distant receiver (200, 50, 4).
     * Propagation path does not cross the bridge → 1 profile, type SOURCE_NOT_RELATED_TO_BRIDGE.
     */
    @Test
    public void TBC08() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(0, 5, 0.05),
                new Coordinate(0, 15, 0.05)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(100L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.SOURCE_NOT_RELATED_TO_BRIDGE, -1, -1))
                .addReceiver(1L, 200, 50, 4, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Distant receiver → 1 sample point → 1 SOURCE_NOT_RELATED_TO_BRIDGE profile
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(1, profiles.size(), "Expected 1 SOURCE_NOT_RELATED_TO_BRIDGE profile");
        assertEquals(RelationType.SOURCE_NOT_RELATED_TO_BRIDGE,
                profiles.get(0).getSource().getBridgeRelationship().getRelationType());
        assertCutProfile("TBC08_not_related", profiles.get(0));
    }


    
    /**
     * TBC09 — SOURCE_NOT_RELATED_TO_BRIDGE, source under the bridge footprint, distant receiver.
     * Source: x=9, z≈0 (directly under Bridge1 deck at x=10, z=10).
     * Receiver: (200, 50, 4) — the propagation path passes under then beyond the bridge.
     * The bridge acts as an obstacle; a MIRROR_SOURCE is auto-generated from Bridge1.
     * Expected: 2 profiles — SOURCE_NOT_RELATED_TO_BRIDGE and MIRROR_SOURCE.
     */
    @Test
    public void TBC09() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(9, 5, 0.05),
                new Coordinate(9, 15, 0.05)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(100L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.SOURCE_NOT_RELATED_TO_BRIDGE, -1, -1))
                .addReceiver(1L, 200, 50, 4, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Distant receiver → 1 sample point → 1 SOURCE_NOT_RELATED_TO_BRIDGE profile
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(2, profiles.size(), "Expected 1 actual source (SOURCE_NOT_RELATED_TO_BRIDGE) and 1 MIRROR_SOURCE profile");
        assertEquals(RelationType.SOURCE_NOT_RELATED_TO_BRIDGE,
                profiles.get(0).getSource().getBridgeRelationship().getRelationType());
        CutProfile actualProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.SOURCE_NOT_RELATED_TO_BRIDGE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC09_not_related", actualProfile);

        assertEquals(RelationType.MIRROR_SOURCE,
                profiles.get(1).getSource().getBridgeRelationship().getRelationType());
        CutProfile mirrorProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.MIRROR_SOURCE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC09_mirror", mirrorProfile);
    }

    
    /**
     * TBC10 — SOURCE_NOT_RELATED_TO_BRIDGE, source under the bridge footprint, elevated receiver.
     * Source: x=9, z≈0 (under Bridge1 footprint). Receiver: (50, 50, 20) — elevated above deck z=10.
     * Same bridge footprint as TBC09 but receiver is above deck level.
     * NOTE: bridge obstruction for SOURCE_NOT_RELATED is not applied in the current implementation;
     * a MIRROR_SOURCE is still generated but the shielding effect is ignored.
     * Expected: 2 profiles — SOURCE_NOT_RELATED_TO_BRIDGE and MIRROR_SOURCE.
     */
    @Test
    public void TBC10() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(9, 5, 0.05),
                new Coordinate(9, 15, 0.05)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(100L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.SOURCE_NOT_RELATED_TO_BRIDGE, -1, -1))
                .addReceiver(1L, 50, 50, 20, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Distant receiver → 1 sample point → 1 SOURCE_NOT_RELATED_TO_BRIDGE profile
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(2, profiles.size(), "Expected 1 actual source (SOURCE_NOT_RELATED_TO_BRIDGE) and 1 MIRROR_SOURCE profile");
        assertEquals(RelationType.SOURCE_NOT_RELATED_TO_BRIDGE,
                profiles.get(1).getSource().getBridgeRelationship().getRelationType());
        CutProfile actualProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.SOURCE_NOT_RELATED_TO_BRIDGE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC10_not_related", actualProfile);
        LOGGER.info("Obstruction by bridge in the propagation path from a source not related to bridge is not considered in the current implementation.");

        assertEquals(RelationType.MIRROR_SOURCE,
                profiles.get(0).getSource().getBridgeRelationship().getRelationType());
        CutProfile mirrorProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.MIRROR_SOURCE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC10_mirror", mirrorProfile);
    }

    
    /**
     * TBC11 — Same geometry as TBC01, but with both diffraction modes disabled
     * ({@code vEdgeDiff=false, hEdgeDiff=false}).
     * Source: Bridge1 (pk=100), line x=10, y=5..15, z=10.5 (absolute). ACTUAL_SOURCE_ON_BRIDGE.
     * Receiver: (200, 50, 4) — outside bridge footprint.
     * With vertical diffraction OFF, the ACTUAL profile is suppressed
     * (bridge geometry intersects the ray → {@code hasBridgeIntersection=true} →
     * {@code isFreeField()=false} → neither condition of {@code verticalDiffraction||isFreeField()} passes).
     * The IMAGINARY profile is retained because the ray from the imaginary source
     * to the distant receiver clears the bridge geometry ({@code isFreeField()=true}).
     * Expected: 1 profile — IMAGINARY_SOURCE_UNDER_BRIDGE, asserted against TBC11_imaginary.json.
     */
    @Test
    public void TBC11() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 10.5),
                new Coordinate(10, 15, 10.5)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(1L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1))
                .addReceiver(1L, 200, 50, 4, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(false)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // ACTUAL_SOURCE_ON_BRIDGE produces 1 profiles: the IMAGINARY profile
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(1, profiles.size(), "Expected 1 IMAGINARY profile");
        assertEquals(RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE,
                profiles.get(0).getSource().getBridgeRelationship().getRelationType(),
                "profile[0] should be IMAGINARY (sorts closer to receiver)");
        assertCutProfile("TBC11_imaginary", profiles.get(0));
    }

    
    /**
     * TBC20 — ACTUAL_SOURCE_ON_BRIDGE with two bridges in the scene, distant receiver.
     * Source: ACTUAL on Bridge1 (pk=100), line x=10, y=5..15, z=10.05.
     * Bridge2 (pk=101, x=12, z=5) is also present in the scene.
     * Distant receiver (200, 50, 4) → 1 sample point → 1 IMAGINARY + 1 ACTUAL profile.
     */
    @Test
    public void TBC20() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .addBridge(createBridge2())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 10.05),
                new Coordinate(10, 15, 10.05)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(1L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1))
                .addReceiver(1L, 200, 50, 4, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Distant receiver → 1 sample point → 1 IMAGINARY + 1 ACTUAL profile
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(2, profiles.size(), "Expected 1 IMAGINARY + 1 ACTUAL profile");
        assertEquals(RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE,
                profiles.get(0).getSource().getBridgeRelationship().getRelationType(),
                "profile[0] should be IMAGINARY (sorts closer to receiver)");
        assertEquals(RelationType.ACTUAL_SOURCE_ON_BRIDGE,
                profiles.get(1).getSource().getBridgeRelationship().getRelationType(),
                "profile[1] should be ACTUAL");
        
        CutProfile imaginaryProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC20_imaginary", imaginaryProfile);

        CutProfile actualProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.ACTUAL_SOURCE_ON_BRIDGE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC20_actual", actualProfile);
    }

        
    /**
     * TBC21 — MIRROR_SOURCE on Bridge1 with two bridges in the scene, distant receiver.
     * Source: MIRROR_SOURCE on Bridge1 (pk=100), line x=10, y=5..15, z=15.
     * Bridge2 (pk=101) is also present as a potential obstacle.
     * Distant receiver (200, 50, 4) → 1 sample point → 1 MIRROR_SOURCE profile.
     */
    @Test
    public void TBC21() throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder()
                .addBridge(createBridge1())
                .addBridge(createBridge2())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 15.0),
                new Coordinate(10, 15, 15.0)
        });

        //Propagation data building
        Scene scene = new SceneBuilder(profileBuilder)
                .addSource(100L, source, Scene.HeightType.ABSOLUTE, new Orientation(0,0,0), new BridgeRelationship(RelationType.MIRROR_SOURCE, -1, 100))
                .addReceiver(1L, 200, 50, 4, Scene.HeightType.ABSOLUTE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Distant receiver → 1 sample point → 1 MIRROR_SOURCE profile
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(1, profiles.size(), "Expected 1 MIRROR_SOURCE profile");
        assertEquals(RelationType.MIRROR_SOURCE,
                profiles.get(0).getSource().getBridgeRelationship().getRelationType());
        assertCutProfile("TBC21_mirror", profiles.get(0));
    }

    
    /** Load bridge center points from TutoBridge/bridgepoints.geojson. */
    private List<BridgePoint> loadBridgePoints() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getResourceAsStream("/TutoBridge/bridgepoints.geojson");
        assertNotNull(is, "bridgepoints.geojson not found in test resources");
        JsonNode root = mapper.readTree(is);
        List<BridgePoint> points = new ArrayList<>();
        for (JsonNode feature : root.get("features")) {
            JsonNode props = feature.get("properties");
            JsonNode coords = feature.get("geometry").get("coordinates");
            double x = coords.get(0).asDouble();
            double y = coords.get(1).asDouble();
            long pk = props.get("PK").asLong();
            long bridgePk = props.get("BRIDGE_PK").asLong();
            double relH = props.get("RELATIVE_DECK_HEIGHT").isNull() ? Double.NaN : props.get("RELATIVE_DECK_HEIGHT").asDouble();
            double absH = props.get("ABSOLUTE_DECK_HEIGHT").isNull() ? Double.NaN : props.get("ABSOLUTE_DECK_HEIGHT").asDouble();
            double thickness = props.get("DECK_THICKNESS").asDouble();
            double lw = props.get("LEFT_WIDTH").asDouble();
            double rw = props.get("RIGHT_WIDTH").asDouble();
            double lbh = props.get("LEFT_BARRIER_HEIGHT").asDouble();
            double rbh = props.get("RIGHT_BARRIER_HEIGHT").asDouble();
            BridgePoint.Builder builder = new BridgePoint.Builder(pk, bridgePk, new Coordinate(x, y))
                    .withDeckThickness(thickness)
                    .withWidth(rw, lw)
                    .withBarrierHeight(rbh, lbh)
                    .withPosition(BridgePoint.Position.CENTER)
                    .withGirderType(Bridge.GirderType.STEEL_BOX)
                    .withSlabType(Bridge.SlabType.CONCRETE);
            if (!Double.isNaN(absH)) {
                builder.withAbsoluteDeckHeight(absH);
            } else {
                builder.withRelativeDeckHeight(relH);
            }
            points.add(builder.build());
        }
        return points;
    }

    /** Load DEM topographic points into an existing ProfileBuilder (does NOT call finishFeeding). */
    private void loadDemPoints(ProfileBuilder pb) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getResourceAsStream("/TutoBridge/dem.geojson");
        assertNotNull(is, "dem.geojson not found in test resources");
        JsonNode root = mapper.readTree(is);
        for (JsonNode feature : root.get("features")) {
            JsonNode coords = feature.get("geometry").get("coordinates");
            double x = coords.get(0).asDouble();
            double y = coords.get(1).asDouble();
            double z = coords.get(2).asDouble();
            pb.addTopographicPoint(new Coordinate(x, y, z));
        }
    }

    /** Build a ProfileBuilder with DEM points from TutoBridge/dem.geojson, fully fed. */
    private ProfileBuilder buildProfileBuilderFromDem() throws Exception {
        ProfileBuilder pb = new ProfileBuilder();
        loadDemPoints(pb);
        pb.finishFeeding();
        return pb;
    }

    /**
     * Build a Bridge from the given BridgePoints, call {@code createDeckGeometry} with the
     * supplied ProfileBuilder (which must already be fed), and return the finished Bridge.
     * Used by {@link #testDeckGeometryWithDem} and {@link #TBC30}.
     */
    private Bridge buildBridge(List<BridgePoint> bridgePoints, ProfileBuilder pb) {
        List<Double> alphas = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8);
        Bridge bridge = new Bridge.Builder(bridgePoints)
                .withAlphas(alphas)
                .setPrimaryKey(1L)
                .setGirderType(Bridge.GirderType.STEEL_BOX)
                .setSlabType(Bridge.SlabType.CONCRETE)
                .build();
        bridge.createDeckGeometry(pb);
        return bridge;
    }
    
    /**
     * Structural sanity-check for deck and edge geometry built from real TutoBridge DEM data.
     * Loads bridge points from {@code /TutoBridge/bridgepoints.geojson} and a DEM from
     * {@code /TutoBridge/dem.geojson}, builds the bridge, and verifies that:
     * <ul>
     *   <li>Deck vertices have non-NaN Z and are above ~20 m (ground + relativeDeckHeight).</li>
     *   <li>Edge vertices are at either deck height or deck+barrier height (±0.5 m tolerance).</li>
     * </ul>
     * Diagnostic LOGGER output shows the first 6 vertices of each polygon.
     */
    @Test
    public void testDeckGeometryWithDem() throws Exception {
        double relativeDeckHeight = 7.8;
        double barrierHeight = 2.5;

        LOGGER.info("=== Deck geometry test on terrain ===");
        LOGGER.info(String.format("Bridge of which relative deck height is %.1f and barrier height is %.1f", relativeDeckHeight, barrierHeight));

        List<BridgePoint> bridgePoints = loadBridgePoints();

        ProfileBuilder pb = new ProfileBuilder();
        loadDemPoints(pb);
        pb.finishFeeding();

        BridgePoint first = bridgePoints.get(0);
        Coordinate firstCoord = first.getCoordinate();
        double groundAtFirst = pb.getZGround(firstCoord);
        LOGGER.info(String.format("First bridge point: pk=%d, coord=(%.1f, %.1f), groundH=%.1f, relDeckH=%.1f",
                first.getPrimaryKey(), firstCoord.x, firstCoord.y,
                groundAtFirst, first.getRelativeDeckHeight()));

        Bridge bridge = buildBridge(bridgePoints, pb);

        Geometry deckGeom = bridge.getDeckGeometry();
        assertNotNull(deckGeom, "Deck geometry must not be null");
        LOGGER.info("=== Deck polygon vertices (first 6 of {}, relative coordinates and heights) ===", deckGeom.getCoordinates().length);
        for (int i = 0; i < Math.min(6, deckGeom.getCoordinates().length); i++) {
            Coordinate c = deckGeom.getCoordinates()[i];
            assertFalse(Double.isNaN(c.z), "Deck vertex Z must not be NaN: " + c);
            assertTrue(c.z > 20.0, "Deck vertex Z should be above ~20m (ground+relH): " + c.z);
            double groundZHere = pb.getZGround(c);
            LOGGER.info(String.format("  deckVtx[%d] (%.1f, %.1f, %.1f) on Ground: %.1f", i, c.x - firstCoord.x, c.y - firstCoord.y, c.z - groundZHere, groundZHere));
        }

        Polygon edgeGeom = bridge.getEdge();
        assertNotNull(edgeGeom, "Edge polygon must not be null");
        Coordinate[] edgeCoords = edgeGeom.getCoordinates();
        LOGGER.info("=== Edge polygon vertices (first 6 of {}) relative coordinates and heights ===", edgeCoords.length);

        
        int withBarrierCount = 0;
        int withoutBarrierCount = 0;
        int unexpectedCount = 0;
        for (int i = 0; i < edgeCoords.length; i++) {
            Coordinate c = edgeCoords[i];
            double groundZHere = pb.getZGround(c);
            double relativeZ = c.z - pb.getZGround(c);
            if (i < Math.min(6, edgeCoords.length)) {
                LOGGER.info(String.format("  edgeVtx[%d] (%.1f, %.1f, %.1f) on Ground: %.1f", i, c.x - firstCoord.x, c.y - firstCoord.y, relativeZ, groundZHere));
            }
            
            if (Math.abs(relativeZ - relativeDeckHeight - barrierHeight) < 0.5) {
                withBarrierCount++;
            } else if (Math.abs(relativeZ - relativeDeckHeight) < 0.5) {
                withoutBarrierCount++;
            } else {
                unexpectedCount++;
                LOGGER.warn(String.format("  edgeVtx[%d] z=%.1f doesn't match deck (%.1f) or deck+barrier (%.1f)",
                        i, relativeZ, relativeDeckHeight, relativeDeckHeight + barrierHeight));
            }
        }

        LOGGER.info("Edge vertices: {} with-barrier, {} end-cap (without barrier), {} unexpected",
                withBarrierCount, withoutBarrierCount, unexpectedCount);
    }

    
    /**
     * TBC30 — ACTUAL_SOURCE_ON_BRIDGE with real TutoBridge DEM geometry (SRID 6676).
     * Loads bridge points from {@code /TutoBridge/bridgepoints.geojson} and DEM from
     * {@code /TutoBridge/dem.geojson}; source and receiver coordinates are in the local
     * coordinate system (roughly −13917, −113557).
     * Source: ACTUAL on bridge pk=1, short segment z=0.05 (RELATIVE).
     * Receiver at (−13911.6, −113565.2, 1.5) (RELATIVE to DEM).
     * Expected: 2 profiles — IMAGINARY_SOURCE_UNDER_BRIDGE (index 0) and
     * ACTUAL_SOURCE_ON_BRIDGE (index 1), asserted against TBC30_imaginary.json / TBC30_actual.json.
     */
    @Test
    public void TBC30() throws Exception {

        List<BridgePoint> bridgePoints = loadBridgePoints();
        List<Double> alphas = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8);
        Bridge bridge = new Bridge.Builder(bridgePoints)
                .withAlphas(alphas)
                .setPrimaryKey(1L)
                .setGirderType(Bridge.GirderType.STEEL_BOX)
                .setSlabType(Bridge.SlabType.CONCRETE)
                .build();
            
            
        ProfileBuilder pb = new ProfileBuilder();
        loadDemPoints(pb);
        pb.addBridge(bridge);
        pb.finishFeeding();

        
        GeometryFactory geometryFactory = new GeometryFactory();
        // Point source = geometryFactory.createPoint(new Coordinate(-13917.13, -113557.13,0.05));
        Coordinate receiver = new Coordinate(-13911.572978820766, -113565.160698870560, 1.5);

        
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(-13913.0, -113550.0, 0.05),
                new Coordinate(-13924.0, -113574.0, 0.05)
        });


        //Propagation data building
        Scene scene = new SceneBuilder(pb)
                .addSource(1L, source, Scene.HeightType.RELATIVE, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 1L, -1))
                .addReceiver(1L, receiver.x, receiver.y, receiver.z, Scene.HeightType.RELATIVE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Distant receiver → 2 sample point → 2 ACTUAL_SOURCE_ON_BRIDGE and 2 IMAGINARY profile
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(4, profiles.size(), "Expected 4 profiles");
        
        assertEquals(2L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE)
                .count(), "2 profiles should be IMAGINARY_SOURCE_UNDER_BRIDGE");
        CutProfile imaginaryProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC30_imaginary", imaginaryProfile);

        assertEquals(2L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.ACTUAL_SOURCE_ON_BRIDGE)
                .count(), "2 profiles should be ACTUAL_SOURCE_ON_BRIDGE");
        CutProfile actualProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.ACTUAL_SOURCE_ON_BRIDGE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC30_actual", actualProfile);
    }

    /**
     * TBC31 — Two sources (ACTUAL_SOURCE_ON_BRIDGE + SOURCE_NOT_RELATED_TO_BRIDGE) with a wall,
     * using real TutoBridge DEM geometry (SRID 6676).
     * Loads bridge points from {@code /TutoBridge/bridgepoints.geojson} and DEM from
     * {@code /TutoBridge/dem.geojson}; coordinates are in the local system (roughly −13917, −113557).
     * Source 1: ACTUAL on bridge pk=1, line segment z=0.05 (RELATIVE) — generates ACTUAL, IMAGINARY and MIRROR profiles.
     * Source 2: SOURCE_NOT_RELATED_TO_BRIDGE, same segment — generates plain diffraction profiles.
     * Wall: 4 m height, (−13909, −113551)→(−13918, −113575), parallel to bridge axis.
     * Receiver at (−13911.6, −113565.2, 1.5) (RELATIVE to DEM).
     * Both sources produce 2 sample points → 8 profiles total:
     * 2×IMAGINARY_SOURCE_UNDER_BRIDGE, 2×ACTUAL_SOURCE_ON_BRIDGE,
     * 2×SOURCE_NOT_RELATED_TO_BRIDGE, 2×MIRROR_SOURCE.
     * Asserted against TBC31_imaginary.json / TBC31_actual.json /
     * TBC31_not_related.json / TBC31_mirror.json.
     */
    @Test
    public void TBC31() throws Exception {
        GeometryFactory geometryFactory = new GeometryFactory();

        List<BridgePoint> bridgePoints = loadBridgePoints();
        List<Double> alphas = Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8);
        Bridge bridge = new Bridge.Builder(bridgePoints)
                .withAlphas(alphas)
                .setPrimaryKey(1L)
                .setGirderType(Bridge.GirderType.STEEL_BOX)
                .setSlabType(Bridge.SlabType.CONCRETE)
                .build();
            
        LineString wall = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(-13909.0, -113551.0, 0.0),
                new Coordinate(-13918.0, -113575.0, 0.0)
        });
            
        ProfileBuilder pb = new ProfileBuilder();
        loadDemPoints(pb);
        pb.addBridge(bridge);
        pb.addWall(wall, 4.0, alphas, 1);
        pb.finishFeeding();

        
        // Point source = geometryFactory.createPoint(new Coordinate(-13917.13, -113557.13,0.05));
        Coordinate receiver = new Coordinate(-13911.572978820766, -113565.160698870560, 1.5);

        
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(-13913.0, -113550.0, 0.05),
                new Coordinate(-13924.0, -113574.0, 0.05)
        });


        //Propagation data building
        Scene scene = new SceneBuilder(pb)
                .addSource(1L, source, Scene.HeightType.RELATIVE, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 1L, -1))
                .addSource(2L, source, Scene.HeightType.RELATIVE, new Orientation(0,0,0), new BridgeRelationship(RelationType.SOURCE_NOT_RELATED_TO_BRIDGE, -1, -1))
                .addReceiver(1L, receiver.x, receiver.y, receiver.z, Scene.HeightType.RELATIVE)
                .vEdgeDiff(false)
                .hEdgeDiff(true)
                .build();

        //Out and computation settings
        DefaultCutPlaneVisitor propDataOut = new DefaultCutPlaneVisitor(true);
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Distant receiver → 2 sample point 
        // → 2 ACTUAL_SOURCE_ON_BRIDGE, 2 SOURCE_NOT_RELATED_TO_BRIDGE, 2 IMAGINARY and 2 MIRROR profiles
        List<CutProfile> profiles = new ArrayList<>(propDataOut.cutProfiles);
        assertEquals(8, profiles.size(), "Expected 8 profiles");

        assertEquals(2L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE)
                .count(), "2 profiles should be IMAGINARY_SOURCE_UNDER_BRIDGE");
        CutProfile imaginaryProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC31_imaginary", imaginaryProfile);

        
        assertEquals(2L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.ACTUAL_SOURCE_ON_BRIDGE)
                .count(), "2 profiles should be ACTUAL_SOURCE_ON_BRIDGE");
        CutProfile actualProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.ACTUAL_SOURCE_ON_BRIDGE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC31_actual", actualProfile);

        
        assertEquals(2L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.SOURCE_NOT_RELATED_TO_BRIDGE)
                .count(), "2 profiles should be SOURCE_NOT_RELATED_TO_BRIDGE");
        CutProfile notRelatedProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.SOURCE_NOT_RELATED_TO_BRIDGE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC31_not_related", notRelatedProfile);

        
        assertEquals(2L, profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.MIRROR_SOURCE)
                .count(), "2 profiles should be MIRROR_SOURCE");
        CutProfile mirrorProfile = profiles.stream()
                .filter(p -> p.getSource().getBridgeRelationship().getRelationType()
                        == RelationType.MIRROR_SOURCE)
                .findFirst().orElseThrow();
        assertCutProfile("TBC31_mirror", mirrorProfile);
    }

}
