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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PathFinderBridgeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PathFinderTest.class);

    public boolean overwriteTestCase = true;

    public boolean outputCurrentCutProfile = true;

    public static final double DELTA_COORDS = 0.1;

    public static final double DELTA_PLANES = 0.1;

    private void assertCutProfile(String utName, CutProfile cutProfile) throws IOException {
        String testCaseFileName = utName + ".json";
        if(overwriteTestCase) {
            writeCutProfileJson(utName, cutProfile);
        }
        if(outputCurrentCutProfile){
            writeCutProfileJson("tmp_" + utName, cutProfile);
        }
        try {
                assertCutProfile(PathFinder.class.getResourceAsStream("test_cases/"+testCaseFileName),cutProfile);
        } catch (IOException e) {
            LOGGER.error("Error while asserting cut profile for {}", testCaseFileName, e);
        } catch (IllegalArgumentException e) {
            LOGGER.error("Invalid argument while asserting cut profile for {}", testCaseFileName, e);
            LOGGER.error("Maybe JSON file does not exist");
        }
    }

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

    public static String cutProfileAsJson(CutProfile cutProfile) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        // mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        // mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.addMixIn(Coordinate.class, CoordinateMixin.class);
        mapper.addMixIn(LineSegment.class, LineSegmentMixin.class);
        ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();
        return writer.writeValueAsString(cutProfile);
    }

    public static void assertCutProfile(InputStream expected, CutProfile got) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        // mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CutProfile cutProfile = mapper.readValue(expected, CutProfile.class);
        assertCutProfile(cutProfile, got);
    }

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
     * Test TBC01 -- Reflecting ground (G = 0), with a source on bridge
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
                .addSource(1L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1))
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

        assertCutProfile("TBC01", propDataOut.cutProfiles.getFirst());
    }
    
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
                .addSource(1L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1))
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

        assertCutProfile("TBC02", propDataOut.cutProfiles.getFirst());
    }

        
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
                .addSource(1L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1))
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

        assertCutProfile("TBC03", propDataOut.cutProfiles.getFirst());
    }

    /**
     * Test TBC02 -- Reflecting ground (G = 0), with a source on bridge
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
                .addSource(100L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.MIRROR_SOURCE, -1, 100))
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

        assertCutProfile("TBC04", propDataOut.cutProfiles.getFirst());
    }

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
                .addSource(100L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.MIRROR_SOURCE, -1, 100))
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

        assertCutProfile("TBC05", propDataOut.cutProfiles.getFirst());
    }

    
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
                .addSource(100L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.MIRROR_SOURCE, -1, 100))
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

        assertCutProfile("TBC06", propDataOut.cutProfiles.getFirst());
    }


    /**
     * Test TBC02 -- Reflecting ground (G = 0), with a source on bridge
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
                .addSource(100L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100))
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

        assertCutProfile("TBC07", propDataOut.cutProfiles.getFirst());
    }

    @Test
    public void TBC08() throws Exception {
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
                .addSource(100L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100))
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

        assertCutProfile("TBC08", propDataOut.cutProfiles.getFirst());
    }
    

    @Test
    public void TBC09() throws Exception {
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
                .addSource(100L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100))
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

        assertCutProfile("TBC09", propDataOut.cutProfiles.getFirst());
    }

    
    @Test
    public void TBC10() throws Exception {
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
                .addSource(100L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.SOURCE_NOT_RELATED_TO_BRIDGE, -1, -1))
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

        assertCutProfile("TBC10", propDataOut.cutProfiles.getFirst());
    }

    
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
                .addSource(1L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1))
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

        assertCutProfile("TBC20", propDataOut.cutProfiles.getFirst());
    }

        
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
                .addSource(100L, source, null, new Orientation(0,0,0), new BridgeRelationship(RelationType.MIRROR_SOURCE, -1, 100))
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

        assertCutProfile("TBC21", propDataOut.cutProfiles.getFirst());
    }

}
