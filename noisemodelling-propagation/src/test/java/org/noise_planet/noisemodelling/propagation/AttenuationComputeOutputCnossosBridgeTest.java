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
import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.math.Vector2D;
import org.locationtech.jts.math.Vector3D;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgePoint;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;
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
     * Create a bridge for testing (same as PathFinderBridgeTest.createBridge1())
     */
    private Bridge createBridge1() {
        
        List<BridgePoint> points = new ArrayList<>();
        
        List<Double> defaultAlphas = Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        
        // Create a rectangular bridge: 20x10 units
        BridgePoint bp1 = 
            new BridgePoint(
                new Coordinate(10, 0), 1, 100, 10.0, Double.NaN, 0.5, 5.0, 5.0, 2.0, 2.0);
        points.add(bp1);
        
        BridgePoint bp2 = 
            new BridgePoint(
                new Coordinate(10, 10), 2, 100, 10.0, Double.NaN, 0.5, 5.0, 5.0, 2.0, 2.0);
        points.add(bp2);
        
        BridgePoint bp3 = 
            new BridgePoint(
                new Coordinate(10, 20), 3, 100, 10.0, Double.NaN, 0.5, 5.0, 5.0, 2.0, 2.0);
        points.add(bp3);

        return new Bridge(points, defaultAlphas, 100L);
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
                                               SourceBridgeProperty.SourceType sourceType, long bridgeId, long mirrorBridgeId) throws Exception {
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
        sceneWithAttenuation.addSource((long)1, source, new Orientation(0,0,0), 
            new SourceBridgeProperty(sourceType, bridgeId, mirrorBridgeId));
        sceneWithAttenuation.addReceiver(receiverCoord);
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
            SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1);
    }

    /**
     * Test TBC01 -- Bridge with source on bridge deck, receiver at x=20
     */
    @Test
    public void TBC01_R20() throws Exception {
        runTBC01WithReceiver("TBC01_R20", new Coordinate(20, 10, 4));
    }

    /**
     * Test TBC01 -- Bridge with source on bridge deck, receiver at x=50
     */
    @Test
    public void TBC01_R50() throws Exception {
        runTBC01WithReceiver("TBC01_R50", new Coordinate(50, 10, 4));
    }

    /**
     * Test TBC01 -- Bridge with source on bridge deck, receiver at x=100
     */
    @Test
    public void TBC01_R100() throws Exception {
        runTBC01WithReceiver("TBC01_R100", new Coordinate(100, 10, 4));
    }

    /**
     * Test TBC01 -- Bridge with source on bridge deck, receiver at x=150
     */
    @Test
    public void TBC01_R150() throws Exception {
        runTBC01WithReceiver("TBC01_R150", new Coordinate(150, 10, 4));
    }

    /**
     * Test TBC01 -- Bridge with source on bridge deck, receiver at x=200
     */
    @Test
    public void TBC01_R200() throws Exception {
        runTBC01WithReceiver("TBC01_R200", new Coordinate(200, 10, 4));
    }

    // ========== Tests with different source configurations ==========

    /**
     * Test TBC01 with IMAGINARY_SOURCE_UNDER_BRIDGE (z=10), receiver at x=20
     */
    @Test
    public void TBC01_SZ10_IMAGINARY_R20() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ10_IMAGINARY_R20", new Coordinate(20, 10, 4), 10.0,
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100);
    }

    /**
     * Test TBC01 with IMAGINARY_SOURCE_UNDER_BRIDGE (z=10), receiver at x=50
     */
    @Test
    public void TBC01_SZ10_IMAGINARY_R50() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ10_IMAGINARY_R50", new Coordinate(50, 10, 4), 10.0,
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100);
    }

    /**
     * Test TBC01 with IMAGINARY_SOURCE_UNDER_BRIDGE (z=10), receiver at x=100
     */
    @Test
    public void TBC01_SZ10_IMAGINARY_R100() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ10_IMAGINARY_R100", new Coordinate(100, 10, 4), 10.0,
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100);
    }

    /**
     * Test TBC01 with IMAGINARY_SOURCE_UNDER_BRIDGE (z=10), receiver at x=150
     */
    @Test
    public void TBC01_SZ10_IMAGINARY_R150() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ10_IMAGINARY_R150", new Coordinate(150, 10, 4), 10.0,
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100);
    }

    /**
     * Test TBC01 with IMAGINARY_SOURCE_UNDER_BRIDGE (z=10), receiver at x=200
     */
    @Test
    public void TBC01_SZ10_IMAGINARY_R200() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ10_IMAGINARY_R200", new Coordinate(200, 10, 4), 10.0,
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100);
    }

    /**
     * Test TBC01 with ACTUAL_SOURCE_ON_BRIDGE (z=10.5), receiver at x=20
     */
    @Test
    public void TBC01_SZ10_5_ACTUAL_R20() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ10_5_ACTUAL_R20", new Coordinate(20, 10, 4), 10.5,
            SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1);
    }

    /**
     * Test TBC01 with ACTUAL_SOURCE_ON_BRIDGE (z=10.5), receiver at x=50
     */
    @Test
    public void TBC01_SZ10_5_ACTUAL_R50() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ10_5_ACTUAL_R50", new Coordinate(50, 10, 4), 10.5,
            SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1);
    }

    /**
     * Test TBC01 with ACTUAL_SOURCE_ON_BRIDGE (z=10.5), receiver at x=100
     */
    @Test
    public void TBC01_SZ10_5_ACTUAL_R100() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ10_5_ACTUAL_R100", new Coordinate(100, 10, 4), 10.5,
            SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1);
    }

    /**
     * Test TBC01 with ACTUAL_SOURCE_ON_BRIDGE (z=10.5), receiver at x=150
     */
    @Test
    public void TBC01_SZ10_5_ACTUAL_R150() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ10_5_ACTUAL_R150", new Coordinate(150, 10, 4), 10.5,
            SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1);
    }

    /**
     * Test TBC01 with ACTUAL_SOURCE_ON_BRIDGE (z=10.5), receiver at x=200
     */
    @Test
    public void TBC01_SZ10_5_ACTUAL_R200() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ10_5_ACTUAL_R200", new Coordinate(200, 10, 4), 10.5,
            SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, 100, -1);
    }

    /**
     * Test TBC01 with IMAGINARY_SOURCE_UNDER_BRIDGE (z=19), receiver at x=20
     */
    @Test
    public void TBC01_SZ19_IMAGINARY_R20() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ19_IMAGINARY_R20", new Coordinate(20, 10, 4), 19.0,
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100);
    }

    /**
     * Test TBC01 with IMAGINARY_SOURCE_UNDER_BRIDGE (z=19), receiver at x=50
     */
    @Test
    public void TBC01_SZ19_IMAGINARY_R50() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ19_IMAGINARY_R50", new Coordinate(50, 10, 4), 19.0,
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100);
    }

    /**
     * Test TBC01 with IMAGINARY_SOURCE_UNDER_BRIDGE (z=19), receiver at x=100
     */
    @Test
    public void TBC01_SZ19_IMAGINARY_R100() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ19_IMAGINARY_R100", new Coordinate(100, 10, 4), 19.0,
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100);
    }

    /**
     * Test TBC01 with IMAGINARY_SOURCE_UNDER_BRIDGE (z=19), receiver at x=150
     */
    @Test
    public void TBC01_SZ19_IMAGINARY_R150() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ19_IMAGINARY_R150", new Coordinate(150, 10, 4), 19.0,
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100);
    }

    /**
     * Test TBC01 with IMAGINARY_SOURCE_UNDER_BRIDGE (z=19), receiver at x=200
     */
    @Test
    public void TBC01_SZ19_IMAGINARY_R200() throws Exception {
        runTBC01WithReceiverAndSource("TBC01_SZ19_IMAGINARY_R200", new Coordinate(200, 10, 4), 19.0,
            SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1, 100);
    }
}

