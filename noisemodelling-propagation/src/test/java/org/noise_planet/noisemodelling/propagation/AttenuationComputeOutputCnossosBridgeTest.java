/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.propagation;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.math.Vector3D;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgePoint;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.path.BridgeRelationship;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig.FrequencyBand;
import org.noise_planet.noisemodelling.pathfinder.ReceiverPointInfo;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;
import org.noise_planet.noisemodelling.propagation.cnossos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.IntStream;

import static java.lang.Double.NaN;
import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.*;

/**
 * Test class evaluation and testing attenuation values for bridge scenarios.
 */
public class AttenuationComputeOutputCnossosBridgeTest {

    private final static Logger LOGGER = LoggerFactory.getLogger(AttenuationComputeOutputCnossosBridgeTest.class);
    
    private static final double HUMIDITY = 70;
    private static final double TEMPERATURE = 10;
    private static final double[] SOUND_POWER_LEVELS = new double[]{93, 93, 93, 93, 93, 93, 93, 93};
    private static final double[] A_WEIGHTING = new double[]{-26.2, -16.1, -8.6, -3.2, 0.0, 1.2, 1.0, -1.1};

    /**
     * Test case data structure for parameterized tests
     */
    public static class TestCase {
        public String testName;
        public ReceiverCoord receiverCoord;
        public double sourceZ;
        public String relationType;
        public long bridgeId;
        public long mirrorBridgeId;

        public static class ReceiverCoord {
            public double x;
            public double y;
            public double z;
        }

        @Override
        public String toString() {
            return testName;
        }
    }

    /**
     * Test cases wrapper for JSON deserialization
     */
    public static class TestCases {
        public List<TestCase> testCases;
    }

    /**
     * Create a bridge for testing (same as PathFinderBridgeTest.createBridge1())
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
                    .withBarrierHeight(1.0,1.0)
                    .withGirderType(Bridge.GirderType.STEEL_BOX)
                    .withSlabType(Bridge.SlabType.STEEL)
                    .build();
                bridgePoints.add(point);
        }

        return new Bridge.Builder(bridgePoints).withAlphas(defaultAlphas).setPrimaryKey(100L).build();
    }

    public static double[] addArray(double[] first, double[] second) {
        if (first.length != second.length) {
            throw new IllegalArgumentException("Not same size arrays");
        } else {
            double[] sum = new double[first.length];
            for (int i = 0; i < first.length; i++) {
                sum[i] = first[i] + second[i];
            }
            return sum;
        }
    }

    public static double[] sumArray(double[] array1, double[] array2) {
        double[] sum = new double[array1.length];
        for (int i = 0; i < array1.length; i++) {
            sum[i] = array1[i] + array2[i];
        }
        return sum;
    }

    /**
     * Export propagation paths to JSON file
     * This method exports only the propagation paths and their attenuation data to a JSON file
     */
    private static void exportRays(String path, AttenuationComputeOutput attenuationComputeOutput) throws IOException {
        // Create a simple wrapper object with only the data we need
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("propagationPaths", attenuationComputeOutput.getPropagationPaths());
        exportData.put("verticesSoundLevel", attenuationComputeOutput.getVerticesSoundLevel());
        
        JsonMapper.Builder builder = JsonMapper.builder();
        JsonMapper mapper = builder.build();
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(path), exportData);
        LOGGER.info("Exported rays to: " + path);
    }

    /**
     * Helper method to run TBC01 test with different receiver positions and source configurations
     */
    private void runTBC01WithReceiverAndSource(String testName, Coordinate receiverCoord, double sourceZ, 
                                               BridgeRelationship.RelationType relationType, long bridgeId, long mirrorBridgeId) throws Exception {
        //Profile building
        ProfileBuilder profileBuilder = new ProfileBuilder(new FrequencyConfig(FrequencyBand.OCTAVE))
                .addBridge(createBridge1())
                .finishFeeding();

        GeometryFactory geometryFactory = new GeometryFactory();
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, sourceZ),
                new Coordinate(10, 15, sourceZ)
        });

        //Propagation data building
        SceneWithAttenuation sceneWithAttenuation = new SceneWithAttenuation(profileBuilder);
        sceneWithAttenuation.addSource(1L, source, null, new Orientation(0,0,0),
            new BridgeRelationship(relationType, bridgeId, mirrorBridgeId));
        sceneWithAttenuation.addReceiver(0L, receiverCoord);
        sceneWithAttenuation.setComputeHorizontalDiffraction(false);
        sceneWithAttenuation.setComputeVerticalDiffraction(true);

        //Propagation process path data building
        AttenuationParameters attData = new AttenuationParameters(FrequencyConfig.FrequencyBand.OCTAVE);
        attData.setFrequencies(profileBuilder.getFrequencyArray());
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);
        sceneWithAttenuation.setAttenuationParameters(attData);

        //Out and computation settings
        AttenuationComputeOutput propDataOut = new AttenuationComputeOutput(true, true, sceneWithAttenuation);
        
        PathFinder computeRays = new PathFinder(sceneWithAttenuation);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Export to JSON file
        String outputPath = String.format("target/test-classes/org/noise_planet/noisemodelling/propagation/%s_attenuation_output.json", testName);
        exportRays(outputPath, propDataOut);
    }

    /**
     * Helper method with default source configuration (z=10.5, ACTUAL_SOURCE_ON_BRIDGE)
     */
    private void runTBC01WithReceiver(String testName, Coordinate receiverCoord) throws Exception {
        runTBC01WithReceiverAndSource(testName, receiverCoord, 10.5, 
            BridgeRelationship.RelationType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1);
    }

    /**
     * Load test cases from JSON file
     */
    private static List<TestCase> loadTestCases() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = AttenuationComputeOutputCnossosBridgeTest.class
                .getResourceAsStream("/org/noise_planet/noisemodelling/propagation/bridge_test_cases.json");
        if (is == null) {
            throw new FileNotFoundException("Test cases JSON file not found");
        }
        TestCases testCases = mapper.readValue(is, TestCases.class);
        return testCases.testCases;
    }

    /**
     * Provide test cases for parameterized test
     */
    private static List<TestCase> provideTestCases() {
        try {
            return loadTestCases();
        } catch (IOException e) {
            LOGGER.error("Failed to load test cases", e);
            return Collections.emptyList();
        }
    }

    /**
     * Parameterized test that runs all test cases from JSON file
     */
    @ParameterizedTest
    @MethodSource("provideTestCases")
    public void testBridgeWithParameters(TestCase testCase) throws Exception {
        Coordinate receiverCoord = new Coordinate(
            testCase.receiverCoord.x, 
            testCase.receiverCoord.y, 
            testCase.receiverCoord.z
        );
        
        BridgeRelationship.RelationType relationType = 
            BridgeRelationship.RelationType.valueOf(testCase.relationType);
        
        runTBC01WithReceiverAndSource(
            testCase.testName, 
            receiverCoord, 
            testCase.sourceZ,
            relationType,
            testCase.bridgeId,
            testCase.mirrorBridgeId
        );
    }

    @Test
    public void TBC01_WALL_Z_10_5_R50_15() throws Exception {
        LOGGER.info("=== Starting TBC01_WALL_Z_10_5_R50_15 test ===");
        LOGGER.info("Test conditions:");
        LOGGER.info("  - Receiver: (50, 15, 4)");
        LOGGER.info("  - Wall: from (10, 0, 12) to (10, 20, 12)");
        
        //Profile building with wall instead of bridge
        ProfileBuilder profileBuilder = new ProfileBuilder(new FrequencyConfig(FrequencyBand.OCTAVE));
        
        // Add thin building as wall from (10, 0, 0) to (10, 20, 0) with height 12
        GeometryFactory geometryFactory = new GeometryFactory();
        profileBuilder.addBuilding(new Coordinate[]{
            new Coordinate(14.9, 0, 0),
            new Coordinate(15.1, 0, 0),
            new Coordinate(15.1, 20, 0),
            new Coordinate(14.9, 20, 0),
            new Coordinate(14.9, 0, 0)
        }, 12, -1);
        profileBuilder.finishFeeding();

        // Create line source from (10, 5, 10.5) to (10, 15, 10.5)
        LineString source = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(10, 5, 10.5),
                new Coordinate(10, 15, 10.5)
        });

        //Propagation data building
        SceneWithAttenuation sceneWithAttenuation = new SceneWithAttenuation(profileBuilder);
        sceneWithAttenuation.addSource(1L, source, null, new Orientation(0,0,0));
        sceneWithAttenuation.addReceiver(0L, new Coordinate(50, 15, 4));
        sceneWithAttenuation.setComputeHorizontalDiffraction(false);
        sceneWithAttenuation.setComputeVerticalDiffraction(true);

        //Propagation process path data building
        AttenuationParameters attData = new AttenuationParameters(FrequencyConfig.FrequencyBand.OCTAVE);
        attData.setFrequencies(profileBuilder.getFrequencyArray());
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);
        sceneWithAttenuation.setAttenuationParameters(attData);

        //Out and computation settings
        AttenuationComputeOutput propDataOut = new AttenuationComputeOutput(true, true, sceneWithAttenuation);
        
        PathFinder computeRays = new PathFinder(sceneWithAttenuation);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();

        //Run computation
        computeRays.run(propDataOut);

        // Export to JSON file
        String outputPath = "target/test-classes/org/noise_planet/noisemodelling/propagation/TBC01_WALL_S10_15_R50_15_attenuation_output.json";
        exportRays(outputPath, propDataOut);
        
        LOGGER.info("=== TBC01_WALL_S10_15_R50_15 test completed ===");
    }

    
    @Test
    public void TBC01_IMAGINARY_Z19_R050() throws Exception {
        LOGGER.info("=== Starting TBC01_IMAGINARY_Z19_R050 test ===");
        LOGGER.info("Test conditions:");
        LOGGER.info("  - Source type: IMAGINARY_SOURCE_UNDER_BRIDGE");
        LOGGER.info("  - Source Z: 19.0");
        LOGGER.info("  - Receiver: (50, 10, 4)");
        LOGGER.info("  - BridgeId: -1, MirrorBridgeId: 100");
        
        runTBC01WithReceiverAndSource(
            "TBC01_IMAGINARY_Z19_R050", 
            new Coordinate(50, 10, 4), 
            19.0,
            BridgeRelationship.RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE,
            -1,
            100
        );
        
        LOGGER.info("=== TBC01_IMAGINARY_Z19_R050 test completed ===");
    }

        /**
     * Individual test for TBC01_IMAGINARY_Z10_R050 case
     * This test case is also included in the parameterized tests but extracted here for specific analysis
     */
    @Test
    public void TBC01_IMAGINARY_Z10_R050() throws Exception {
        LOGGER.info("=== Starting TBC01_IMAGINARY_Z10_R050 test ===");
        LOGGER.info("Test conditions:");
        LOGGER.info("  - Source type: IMAGINARY_SOURCE_UNDER_BRIDGE");
        LOGGER.info("  - Source Z: 10.0");
        LOGGER.info("  - Receiver: (50, 10, 4)");
        LOGGER.info("  - BridgeId: -1, MirrorBridgeId: 100");
        
        runTBC01WithReceiverAndSource(
            "TBC01_IMAGINARY_Z10_R050", 
            new Coordinate(50, 10, 4), 
            10.0,
            BridgeRelationship.RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE,
            -1,
            100
        );
        
        LOGGER.info("=== TBC01_IMAGINARY_Z10_R050 test completed ===");
    }

}

