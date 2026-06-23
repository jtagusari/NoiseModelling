package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.api.EmptyProgressVisitor;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;
import org.noise_planet.noisemodelling.jdbc.input.SourceEmission;
import org.noise_planet.noisemodelling.jdbc.output.AttenuationOutputMultiThread;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.propagation.AttenuationParameters;
import org.noise_planet.noisemodelling.propagation.ReceiverNoiseLevel;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.dBToW;

/**
 * Unit tests for AttenuationOutputSingleThread using an in-memory scene (no database required).
 * PathFinder.run() drives onNewCutPlane + finalizeReceiver through AttenuationOutputMultiThread.
 */
public class AttenuationOutputSingleThreadTest {

    private static final double HUMIDITY    = 70;
    private static final double TEMPERATURE = 10;

    /** Build a minimal flat scene with attenuation parameters for a single period. */
    private static SceneWithEmission buildScene(String period) {
        FrequencyConfig freqConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.OCTAVE);
        ProfileBuilder profileBuilder = new ProfileBuilder(freqConfig).finishFeeding();
        SceneWithEmission scene = new SceneWithEmission(profileBuilder);
        AttenuationParameters attData = new AttenuationParameters(freqConfig);
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);
        scene.cnossosParametersPerPeriod.put(period, attData);
        return scene;
    }

    private static PathFinder runPathFinder(SceneWithEmission scene, AttenuationOutputMultiThread output) {
        PathFinder pf = new PathFinder(scene);
        pf.setThreadCount(1);
        pf.ensureAbsoluteReceiverHeights();
        pf.run(output);
        return pf;
    }

    // ----------------------------------------------------------------
    // onNewCutPlane: attenuation-only mode (no emissions)
    // ----------------------------------------------------------------

    @Test
    void testOnNewCutPlaneProducesFiniteAttenuationForSingleSourceReceiver() {
        SceneWithEmission scene = buildScene("D");
        GeometryFactory gf = new GeometryFactory();
        scene.addSource(1L, gf.createPoint(new Coordinate(0, 0, 1)), null);
        scene.addReceiver(0L, new Coordinate(100, 0, 2));

        AttenuationOutputMultiThread output = new AttenuationOutputMultiThread(scene);
        runPathFinder(scene, output);

        List<ReceiverNoiseLevel> levels = List.copyOf(output.resultsCache.receiverLevels);
        assertFalse(levels.isEmpty(), "Expected at least one receiver noise level");
        for (double v : levels.get(0).getLevels()) {
            assertTrue(Double.isFinite(v), "All attenuation values must be finite");
        }
    }

    // ----------------------------------------------------------------
    // mergeSources=false (default): one entry per source
    // ----------------------------------------------------------------

    @Test
    void testMergeSourcesFalseProducesOneEntryPerSource() {
        SceneWithEmission scene = buildScene("D");
        GeometryFactory gf = new GeometryFactory();
        scene.addSource(1L, gf.createPoint(new Coordinate(0,  0, 1)), null);
        scene.addSource(2L, gf.createPoint(new Coordinate(0, 10, 1)), null);
        scene.addReceiver(0L, new Coordinate(100, 5, 2));

        AttenuationOutputMultiThread output = new AttenuationOutputMultiThread(scene);
        output.noiseMapDatabaseParameters.mergeSources = false;
        runPathFinder(scene, output);

        List<ReceiverNoiseLevel> levels = List.copyOf(output.resultsCache.receiverLevels);
        assertEquals(2, levels.size(), "mergeSources=false: one entry per source");
    }

    // ----------------------------------------------------------------
    // mergeSources=true: sources summed into one entry per receiver
    // ----------------------------------------------------------------

    @Test
    void testMergeSourcesTrueProducesOneEntryPerReceiver() {
        SceneWithEmission scene = buildScene("D");
        GeometryFactory gf = new GeometryFactory();
        scene.addSource(1L, gf.createPoint(new Coordinate(0,  0, 1)), null);
        scene.addSource(2L, gf.createPoint(new Coordinate(0, 10, 1)), null);
        scene.addReceiver(0L, new Coordinate(100, 5, 2));

        AttenuationOutputMultiThread output = new AttenuationOutputMultiThread(scene);
        output.noiseMapDatabaseParameters.mergeSources = true;
        runPathFinder(scene, output);

        List<ReceiverNoiseLevel> levels = List.copyOf(output.resultsCache.receiverLevels);
        assertEquals(1, levels.size(), "mergeSources=true: sources merged into one entry");
    }

    // ----------------------------------------------------------------
    // Ray export: cnossosPaths populated when TO_RAYS_TABLE is set
    // ----------------------------------------------------------------

    @Test
    void testCnossosPathsExportedWhenRaysTableEnabled() {
        SceneWithEmission scene = buildScene("D");
        GeometryFactory gf = new GeometryFactory();
        scene.addSource(1L, gf.createPoint(new Coordinate(0, 0, 1)), null);
        scene.addReceiver(0L, new Coordinate(100, 0, 2));

        AttenuationOutputMultiThread output = new AttenuationOutputMultiThread(scene);
        output.noiseMapDatabaseParameters.setExportRaysMethod(
                NoiseMapDatabaseParameters.ExportRaysMethods.TO_RAYS_TABLE);
        output.noiseMapDatabaseParameters.setExportAttenuationMatrix(false);
        runPathFinder(scene, output);

        assertFalse(output.resultsCache.cnossosPaths.isEmpty(),
                "cnossosPaths should be populated when TO_RAYS_TABLE is enabled");
    }

    // ----------------------------------------------------------------
    // With emissions: noise level = emission × attenuation (in watts)
    // ----------------------------------------------------------------

    @Test
    void testWithEmissionNoiseLevelLowerThanSourceEmission() {
        FrequencyConfig freqConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.OCTAVE);
        ProfileBuilder profileBuilder = new ProfileBuilder(freqConfig).finishFeeding();
        SceneWithEmission scene = new SceneWithEmission(profileBuilder);

        AttenuationParameters attData = new AttenuationParameters(freqConfig);
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);
        scene.cnossosParametersPerPeriod.put("D", attData);

        // 80 dB source emission (all octave bands)
        double[] emissionW = new double[freqConfig.getFrequencyArray().size()];
        Arrays.fill(emissionW, dBToW(80.0));
        GeometryFactory gf = new GeometryFactory();
        scene.addSource(1L, gf.createPoint(new Coordinate(0, 0, 1)), null);
        scene.registerSourceEmission(1L, new SourceEmission("D", emissionW));
        scene.addReceiver(0L, new Coordinate(100, 0, 2));

        AttenuationOutputMultiThread output = new AttenuationOutputMultiThread(scene);
        runPathFinder(scene, output);

        List<ReceiverNoiseLevel> levels = List.copyOf(output.resultsCache.receiverLevels);
        assertFalse(levels.isEmpty());
        ReceiverNoiseLevel level = levels.stream()
                .filter(l -> "D".equals(l.getPeriod()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No result for period D"));

        // Receiver level must be lower than the 80 dB source (attenuation over 100 m)
        for (double v : level.getLevels()) {
            assertTrue(v < 80.0, "Receiver level (" + v + " dB) must be below source emission (80 dB)");
        }
    }
}
