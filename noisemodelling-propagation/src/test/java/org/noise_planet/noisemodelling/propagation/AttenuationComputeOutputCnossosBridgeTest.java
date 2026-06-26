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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.ReceiverPointInfo;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig.FrequencyBand;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPathExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.sumArray;

/**
 * Regression tests for CNOSSOS-EU attenuation in bridge scenarios (TBC series).
 *
 * <p>Each test builds a scene from scratch, runs {@link PathFinder}, and either writes
 * the result to a reference JSON file (when {@link #overwriteTestCase} is {@code true})
 * or asserts that the current result matches the stored reference (when {@code false}).
 *
 * <p><b>Workflow to establish reference values:</b>
 * <ol>
 *   <li>Set {@code overwriteTestCase = true} and run all tests once.</li>
 *   <li>Reference JSON files are written to the class resource directory
 *       (in the IDE this is {@code src/test/resources/org/noise_planet/noisemodelling/propagation/}).</li>
 *   <li>Set {@code overwriteTestCase = false}. Subsequent runs assert against the stored files.</li>
 * </ol>
 *
 * <p>Compared field: {@code L = aGlobal + SOUND_POWER_LEVELS} (total sound level per path per band),
 * tolerance ±{@value #ERROR_EPSILON_VERY_LOW} dB.
 *
 * <p>Bridge geometry: a single bridge (pk=100) with deck at z=10, width 5 m each side,
 * barriers 1 m, extending from y=0 to y=20. A second bridge (pk=101, deck z=5) is used
 * in TBC20/TBC21.
 */
public class AttenuationComputeOutputCnossosBridgeTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttenuationComputeOutputCnossosBridgeTest.class);

    /**
     * Set to {@code true} to regenerate reference JSON files, then revert to {@code false}.
     * Mirrors the pattern used in {@code PathFinderBridgeTest.overwriteTestCase}.
     */
    public boolean overwriteTestCase = false;

    private static final double HUMIDITY = 70;
    private static final double TEMPERATURE = 10;
    private static final double[] SOUND_POWER_LEVELS = new double[]{93, 93, 93, 93, 93, 93, 93, 93};

    /** Tolerance (dB) used when asserting L values against reference. */
    private static final double ERROR_EPSILON_VERY_LOW = 0.1;
    

    // -------------------------------------------------------------------------
    // CutProfile loading (reads from pathfinder main resources)
    // -------------------------------------------------------------------------

    private static CutProfile loadCutProfile(String utName) throws IOException {
        String testCaseFileName = utName + ".json";
        try (InputStream inputStream = PathFinder.class.getResourceAsStream("test_cases/" + testCaseFileName)) {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(inputStream, CutProfile.class);
        }
    }

    // -------------------------------------------------------------------------
    // Attenuation computation from pre-saved CutProfiles
    // -------------------------------------------------------------------------

    private static AttenuationComputeOutput computeCnossosPath(String... utNames) throws IOException {
        ProfileBuilder profileBuilder = new ProfileBuilder(new FrequencyConfig(FrequencyBand.OCTAVE))
                .finishFeeding();

        SceneWithAttenuation sceneWithAttenuation = new SceneWithAttenuation(profileBuilder);

        AttenuationParameters attenuationParameters = new AttenuationParameters(FrequencyBand.OCTAVE);
        attenuationParameters.setFrequencies(profileBuilder.getFrequencyArray());
        attenuationParameters.setHumidity(HUMIDITY);
        attenuationParameters.setTemperature(TEMPERATURE);
        sceneWithAttenuation.setAttenuationParameters(attenuationParameters);

        AttenuationComputeOutput attenuationOutput = new AttenuationComputeOutput(true, true, sceneWithAttenuation);
        AttenuationVisitor attenuationVisitor = new AttenuationVisitor(attenuationOutput);
        ReceiverPointInfo lastReceiver = new ReceiverPointInfo(-1, -1, new Coordinate());
        for (String utName : utNames) {
            CutProfile cutProfile = loadCutProfile(utName);
            attenuationVisitor.onNewCutPlane(cutProfile);
            if (lastReceiver.getReceiverPk() != -1
                    && cutProfile.getReceiver().getReceiverPk() != lastReceiver.getReceiverPk()) {
                attenuationVisitor.finalizeReceiver(new ReceiverPointInfo(cutProfile.getReceiver()));
            }
            lastReceiver = new ReceiverPointInfo(cutProfile.getReceiver());
        }
        attenuationVisitor.finalizeReceiver(lastReceiver);
        return attenuationOutput;
    }

    // -------------------------------------------------------------------------
    // JSON mapper
    // -------------------------------------------------------------------------

    private static JsonMapper buildMapper() {
        JsonMapper mapper = JsonMapper.builder().build();
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        return mapper;
    }

    // -------------------------------------------------------------------------
    // Write reference JSON
    // -------------------------------------------------------------------------

    /**
     * Serialize the computation result and write it to the class resource directory.
     * In IDE mode, {@code getClass().getResource("")} resolves to
     * {@code src/test/resources/org/noise_planet/noisemodelling/propagation/},
     * so the file is written directly into the source tree.
     * In Maven mode it resolves to {@code target/test-classes/...}; the file must
     * then be copied manually to {@code src/test/resources/}.
     */
    private void writeResults(String testName, AttenuationComputeOutput propDataOut) throws IOException {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("testName",           testName);
        output.put("numPaths",           propDataOut.getPropagationPaths().size());
        output.put("propagationPaths",   buildPathList(propDataOut));
        output.put("verticesSoundLevel", propDataOut.getVerticesSoundLevel());

        String fileName = testName + "_attenuation_output.json";
        URL resourceURL = AttenuationComputeOutputCnossosBridgeTest.class.getResource("");
        if (resourceURL != null) {
            File destination = new File(resourceURL.getFile(), fileName);
            buildMapper().writerWithDefaultPrettyPrinter().writeValue(destination, output);
            LOGGER.warn("{} written to {}", fileName, destination);
        } else {
            LOGGER.error("Cannot resolve resource URL — skipping write for {}", testName);
        }
    }

    // -------------------------------------------------------------------------
    // Assert against reference
    // -------------------------------------------------------------------------

    /**
     * Asserts that {@code actual} matches the array stored under {@code field} in the reference
     * path map, within {@value #ERROR_EPSILON_VERY_LOW} dB. Silently skips if either side is
     * null or the field is absent from the reference (backward-compatible with older files).
     */
    @SuppressWarnings("unchecked")
    private static void assertDoubleArray(String prefix, Map<String, Object> ref,
                                          String field, double[] actual) {
        List<Double> refList = (List<Double>) ref.get(field);
        if (refList == null || actual == null) return;
        assertEquals(refList.size(), actual.length, prefix + " " + field + ": length mismatch");
        for (int b = 0; b < actual.length; b++) {
            assertEquals(refList.get(b), actual[b], ERROR_EPSILON_VERY_LOW,
                    prefix + " " + field + " band[" + b + "]");
        }
    }

    /**
     * Load the stored reference JSON for {@code testName} and assert that all attenuation
     * fields match within {@value #ERROR_EPSILON_VERY_LOW} dB for every path and frequency band.
     *
     * @throws AssertionError if the reference file is missing (run with
     *                        {@code overwriteTestCase = true} first)
     */
    @SuppressWarnings("unchecked")
    private void assertResults(String testName, AttenuationComputeOutput propDataOut) throws IOException {
        String fileName = testName + "_attenuation_output.json";
        InputStream is = AttenuationComputeOutput.class.getResourceAsStream("test_cases/" + fileName);
        if (is == null) {
            throw new AssertionError("Reference file not found in test_cases/" + fileName
                    + " — copy generated file to src/main/resources/org/noise_planet/noisemodelling/propagation/test_cases/");
        }

        Map<String, Object> reference = buildMapper().readValue(is, new TypeReference<Map<String, Object>>() {});
        int refNumPaths = ((Number) reference.get("numPaths")).intValue();
        List<Map<String, Object>> refPaths = (List<Map<String, Object>>) reference.get("propagationPaths");

        List<CnossosPathExt> actualPaths = propDataOut.getPropagationPaths();
        assertEquals(refNumPaths, actualPaths.size(), testName + ": numPaths mismatch");

        for (int i = 0; i < actualPaths.size(); i++) {
            CnossosPathExt p = actualPaths.get(i);
            Map<String, Object> ref = refPaths.get(i);
            String pfx = testName + " path[" + i + "]";
            assertDoubleArray(pfx, ref, "wH",         p.groundAttenuation != null ? p.groundAttenuation.wH       : null);
            assertDoubleArray(pfx, ref, "cfH",        p.groundAttenuation != null ? p.groundAttenuation.cfH      : null);
            assertDoubleArray(pfx, ref, "aGroundH",   p.groundAttenuation != null ? p.groundAttenuation.aGroundH : null);
            assertDoubleArray(pfx, ref, "wF",         p.groundAttenuation != null ? p.groundAttenuation.wF       : null);
            assertDoubleArray(pfx, ref, "cfF",        p.groundAttenuation != null ? p.groundAttenuation.cfF      : null);
            assertDoubleArray(pfx, ref, "aGroundF",   p.groundAttenuation != null ? p.groundAttenuation.aGroundF : null);
            assertDoubleArray(pfx, ref, "aAtm",       p.aAtm);
            assertDoubleArray(pfx, ref, "aDiv",       p.aDiv);
            assertDoubleArray(pfx, ref, "aBoundaryH", p.double_aBoundaryH);
            assertDoubleArray(pfx, ref, "aBoundaryF", p.double_aBoundaryF);
            assertDoubleArray(pfx, ref, "aGlobalH",   p.aGlobalH);
            assertDoubleArray(pfx, ref, "aGlobalF",   p.aGlobalF);
            assertDoubleArray(pfx, ref, "aGlobal",    p.aGlobal);
            assertDoubleArray(pfx, ref, "LH",         p.aGlobalH != null ? sumArray(p.aGlobalH, SOUND_POWER_LEVELS) : null);
            assertDoubleArray(pfx, ref, "LF",         p.aGlobalF != null ? sumArray(p.aGlobalF, SOUND_POWER_LEVELS) : null);
            assertDoubleArray(pfx, ref, "L",          p.aGlobal  != null ? sumArray(p.aGlobal,  SOUND_POWER_LEVELS) : null);
        }
        LOGGER.info("{}: {} paths — assertion passed", testName, actualPaths.size());
    }

    // -------------------------------------------------------------------------
    // Routing: write or assert
    // -------------------------------------------------------------------------

    private void verifyResults(String testName, AttenuationComputeOutput propDataOut) throws IOException {
        if (overwriteTestCase) {
            writeResults(testName, propDataOut);
        } else {
            assertResults(testName, propDataOut);
        }
    }

    // -------------------------------------------------------------------------
    // Serialization helper
    // -------------------------------------------------------------------------

    /** Build the per-path map list stored in the reference JSON. */
    private static List<Map<String, Object>> buildPathList(AttenuationComputeOutput propDataOut) {
        List<Map<String, Object>> paths = new ArrayList<>();
        List<CnossosPathExt> propagationPaths = propDataOut.getPropagationPaths();
        for (int i = 0; i < propagationPaths.size(); i++) {
            CnossosPathExt path = propagationPaths.get(i);
            Map<String, Object> pathMap = new LinkedHashMap<>();
            pathMap.put("pathIndex",  i);
            pathMap.put("wH",         path.groundAttenuation != null ? path.groundAttenuation.wH       : null);
            pathMap.put("cfH",        path.groundAttenuation != null ? path.groundAttenuation.cfH      : null);
            pathMap.put("aGroundH",   path.groundAttenuation != null ? path.groundAttenuation.aGroundH : null);
            pathMap.put("wF",         path.groundAttenuation != null ? path.groundAttenuation.wF       : null);
            pathMap.put("cfF",        path.groundAttenuation != null ? path.groundAttenuation.cfF      : null);
            pathMap.put("aGroundF",   path.groundAttenuation != null ? path.groundAttenuation.aGroundF : null);
            pathMap.put("aAtm",       path.aAtm);
            pathMap.put("aDiv",       path.aDiv);
            pathMap.put("aBoundaryH", path.double_aBoundaryH);
            pathMap.put("aBoundaryF", path.double_aBoundaryF);
            pathMap.put("aGlobalH",   path.aGlobalH);
            pathMap.put("aGlobalF",   path.aGlobalF);
            pathMap.put("aGlobal",    path.aGlobal);
            pathMap.put("LH",         path.aGlobalH != null ? sumArray(path.aGlobalH, SOUND_POWER_LEVELS) : null);
            pathMap.put("LF",         path.aGlobalF != null ? sumArray(path.aGlobalF, SOUND_POWER_LEVELS) : null);
            pathMap.put("L",          path.aGlobal  != null ? sumArray(path.aGlobal,  SOUND_POWER_LEVELS) : null);
            paths.add(pathMap);
        }
        return paths;
    }


    // =========================================================================
    // TBC01 — ACTUAL source on bridge, far receiver
    // Source at z=10.5 (deck surface), ACTUAL_SOURCE_ON_BRIDGE (pk=100).
    // Receiver at (200, 50, 4): outside bridge footprint, free-field path.
    // =========================================================================
    @Test
    public void TBC01() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC01_imaginary", "TBC01_actual");
        verifyResults("TBC01", result);
    }

    // =========================================================================
    // TBC02 — ACTUAL source on bridge, receiver below deck inside footprint
    // Receiver at (12, 10, 4): under the bridge deck (z=10), same y-range.
    // Expected: ACTUAL_SOURCE_TO_LOWER_RECEIVER path with BridgeWall (DOWNWARD).
    // =========================================================================
    @Test
    public void TBC02() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC02_imaginary", "TBC02_actual");
        verifyResults("TBC02", result);
    }

    // =========================================================================
    // TBC03 — ACTUAL source on bridge, receiver above deck inside footprint
    // Receiver at (12, 10, 14): above the bridge deck, inside the footprint.
    // Expected: IMAGINARY_SOURCE_TO_UPPER_RECEIVER path with BridgeWall (DOWNWARD).
    // =========================================================================
    @Test
    public void TBC03() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC03_actual", "TBC03_imaginary");
        verifyResults("TBC03", result);
    }

    // =========================================================================
    // TBC04 — MIRROR source above deck, far receiver
    // Mirror source at z=15 (above deck), MIRROR_SOURCE referencing bridge pk=100.
    // Receiver at (200, 50, 4): outside footprint.
    // =========================================================================
    @Test
    public void TBC04() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC04_mirror");
        verifyResults("TBC04", result);
    }

    // =========================================================================
    // TBC05 — MIRROR source, receiver below deck inside footprint
    // =========================================================================
    @Test
    public void TBC05() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC05_mirror");
        verifyResults("TBC05", result);
    }

    // =========================================================================
    // TBC06 — MIRROR source, receiver above deck inside footprint
    // =========================================================================
    @Test
    public void TBC06() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC06_mirror");
        verifyResults("TBC06", result);
    }

    // =========================================================================
    // TBC07 — IMAGINARY source under bridge, far receiver
    // Imaginary source at z=9.5 (below deck z=10), IMAGINARY_SOURCE_UNDER_BRIDGE.
    // Receiver at (200, 50, 4): outside footprint.
    // =========================================================================
    @Test
    public void TBC07() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC07_imaginary");
        verifyResults("TBC07", result);
    }

    // =========================================================================
    // TBC08 — Unrelated source far from bridge, far receiver
    // Source at x=0 (bridge is at x=10), no bridge relationship.
    // Receiver at (200, 50, 4): bridge lies between source and receiver in x.
    // =========================================================================
    @Test
    public void TBC08() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC08_not_related");
        verifyResults("TBC08", result);
    }

    // =========================================================================
    // TBC09 — Unrelated source under bridge footprint, far receiver
    // Source at x=9 (inside bridge footprint x=[5,15]), z=0.05.
    // Tests bridge shielding effect for an unrelated low source.
    // =========================================================================
    @Test
    public void TBC09() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC09_not_related", "TBC09_mirror");
        verifyResults("TBC09", result);
    }

    // =========================================================================
    // TBC10 — Unrelated source under bridge footprint, elevated receiver
    // Same source as TBC09. Receiver at (50, 50, 20): elevated, still outside footprint.
    // Tests whether the bridge produces diffraction over the deck for this geometry.
    // =========================================================================
    @Test
    public void TBC10() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC10_mirror", "TBC10_not_related");
        verifyResults("TBC10", result);
    }

    // =========================================================================
    // TBC20 — ACTUAL source on bridge1, two bridges in scene, far receiver
    // Source just above deck (z=10.05). Bridge2 (pk=101) is at x=12.
    // Tests that only bridge1 is used for the source relationship.
    // =========================================================================
    @Test
    public void TBC20() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC20_imaginary", "TBC20_actual");
        verifyResults("TBC20", result);
    }

    // =========================================================================
    // TBC21 — MIRROR source on bridge1, two bridges in scene, far receiver
    // Mirror source at z=15 referencing bridge1 (pk=100). Bridge2 also present.
    // =========================================================================
    @Test
    public void TBC21() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC21_mirror");
        verifyResults("TBC21", result);
    }

    // =========================================================================
    // TBC30 — ACTUAL source on bridge with real TutoBridge DEM (SRID 6676)
    // Bridge points and DEM loaded from /TutoBridge/*.geojson (Seishin Bypass, Shizuoka).
    // Source: ACTUAL on bridge pk=1, short segment z=0.05 (RELATIVE to DEM).
    // Receiver at (−13911.6, −113565.2, 1.5), RELATIVE to DEM.
    // Expected: IMAGINARY + ACTUAL profiles (mirrors PathFinderBridgeTest.TBC30).
    // =========================================================================
    @Test
    public void TBC30() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC30_imaginary", "TBC30_actual");

        verifyResults("TBC30", result);
    }

    
    @Test
    public void TBC31() throws Exception {
        AttenuationComputeOutput result = computeCnossosPath("TBC31_imaginary", "TBC31_actual", "TBC31_mirror", "TBC31_not_related");

        verifyResults("TBC31", result);
    }
}
