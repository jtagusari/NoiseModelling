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
 * Regression tests for CNOSSOS-EU attenuation in TC standard test cases (TC01–TC28 + TC18Altered).
 *
 * <p>Each test loads pre-saved {@link CutProfile} JSONs from the pathfinder module's
 * {@code test_cases/} resource directory (via {@link PathFinder#getClass()}), feeds them
 * through {@link AttenuationVisitor}, and either writes the result to a reference JSON file
 * (when {@link #overwriteTestCase} is {@code true}) or asserts that the current result
 * matches the stored reference (when {@code false}).
 *
 * <p><b>Workflow to establish reference values:</b>
 * <ol>
 *   <li>Set {@code overwriteTestCase = true} and run all tests once.</li>
 *   <li>Copy the generated {@code *_attenuation_output.json} files from
 *       {@code target/test-classes/org/noise_planet/noisemodelling/propagation/} to
 *       {@code src/main/resources/org/noise_planet/noisemodelling/propagation/test_cases/}.</li>
 *   <li>Set {@code overwriteTestCase = false}. Subsequent runs assert against the stored files.</li>
 * </ol>
 *
 * <p>Compared field: {@code L = aGlobal + SOUND_POWER_LEVELS} (total sound level per path per band),
 * tolerance ±{@value #ERROR_EPSILON_VERY_LOW} dB.
 */
public class AttenuationComputeOutputCnossosTCTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AttenuationComputeOutputCnossosTCTest.class);

    /**
     * Set to {@code true} to regenerate reference JSON files, then revert to {@code false}.
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
    // Serialization helper
    // -------------------------------------------------------------------------

    private static List<Map<String, Object>> buildPathList(AttenuationComputeOutput attenuationOutput) {
        List<Map<String, Object>> paths = new ArrayList<>();
        List<CnossosPathExt> propagationPaths = attenuationOutput.getPropagationPaths();
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

    // -------------------------------------------------------------------------
    // Write reference JSON (temp output; copy to src/main/resources/... manually)
    // -------------------------------------------------------------------------

    private void writeResults(String testName, AttenuationComputeOutput attenuationOutput) throws IOException {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("testName",           testName);
        output.put("numPaths",           attenuationOutput.getPropagationPaths().size());
        output.put("propagationPaths",   buildPathList(attenuationOutput));
        output.put("verticesSoundLevel", attenuationOutput.getVerticesSoundLevel());

        String fileName = testName + "_attenuation_output.json";
        URL resourceURL = AttenuationComputeOutputCnossosTCTest.class.getResource("");
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

    @SuppressWarnings("unchecked")
    private void assertResults(String testName, AttenuationComputeOutput attenuationOutput) throws IOException {
        String fileName = testName + "_attenuation_output.json";
        InputStream is = AttenuationComputeOutput.class.getResourceAsStream("test_cases/" + fileName);
        if (is == null) {
            throw new AssertionError("Reference file not found in test_cases/" + fileName
                    + " — copy generated file to src/main/resources/org/noise_planet/noisemodelling/propagation/test_cases/");
        }

        Map<String, Object> reference = buildMapper().readValue(is, new TypeReference<Map<String, Object>>() {});
        int refNumPaths = ((Number) reference.get("numPaths")).intValue();
        List<Map<String, Object>> refPaths = (List<Map<String, Object>>) reference.get("propagationPaths");

        List<CnossosPathExt> actualPaths = attenuationOutput.getPropagationPaths();
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

    private void verifyResults(String testName, AttenuationComputeOutput attenuationOutput) throws IOException {
        if (overwriteTestCase) {
            writeResults(testName, attenuationOutput);
        } else {
            assertResults(testName, attenuationOutput);
        }
    }

    // =========================================================================
    // TC01 — Reflecting ground (G = 0)
    // =========================================================================
    @Test
    public void TC01() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC01_Direct");
        verifyResults("TC01", result);
    }

    // =========================================================================
    // TC02 — Mixed ground (G = 0.5)
    // =========================================================================
    @Test
    public void TC02() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC02_Direct");
        verifyResults("TC02", result);
    }

    // =========================================================================
    // TC03 — Absorbing ground (G = 1)
    // =========================================================================
    @Test
    public void TC03() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC03_Direct");
        verifyResults("TC03", result);
    }

    // =========================================================================
    // TC04 — Ground with spatially varying acoustic properties
    // =========================================================================
    @Test
    public void TC04() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC04_Direct");
        verifyResults("TC04", result);
    }

    // =========================================================================
    // TC05 — Elevated source and receiver, flat ground (G = 0)
    // =========================================================================
    @Test
    public void TC05() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC05_Direct");
        verifyResults("TC05", result);
    }

    // =========================================================================
    // TC06 — Elevated source and receiver, flat ground (G = 1)
    // =========================================================================
    @Test
    public void TC06() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC06_Direct");
        verifyResults("TC06", result);
    }

    // =========================================================================
    // TC07 — Elevated source and receiver, flat ground (G = 0.5)
    // =========================================================================
    @Test
    public void TC07() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC07_Direct");
        verifyResults("TC07", result);
    }

    // =========================================================================
    // TC08 — Flat ground with spatially varying acoustic properties and short barrier
    // =========================================================================
    @Test
    public void TC08() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC08_Direct", "TC08_Right", "TC08_Left");
        verifyResults("TC08", result);
    }

    // =========================================================================
    // TC09 — Flat ground with spatially varying acoustic properties and tall barrier
    // =========================================================================
    @Test
    public void TC09() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC09_Direct", "TC09_Right", "TC09_Left");
        verifyResults("TC09", result);
    }

    // =========================================================================
    // TC10 — Slanted barrier on flat ground
    // =========================================================================
    @Test
    public void TC10() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC10_Direct", "TC10_Right", "TC10_Left");
        verifyResults("TC10", result);
    }

    // =========================================================================
    // TC11 — Thick barrier on flat ground
    // =========================================================================
    @Test
    public void TC11() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC11_Direct", "TC11_Right", "TC11_Left");
        verifyResults("TC11", result);
    }

    // =========================================================================
    // TC12 — Barrier with absorbing top on flat ground
    // =========================================================================
    @Test
    public void TC12() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC12_Direct", "TC12_Right", "TC12_Left");
        verifyResults("TC12", result);
    }

    // =========================================================================
    // TC13 — Two barriers on flat ground
    // =========================================================================
    @Test
    public void TC13() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC13_Direct", "TC13_Right", "TC13_Left");
        verifyResults("TC13", result);
    }

    // =========================================================================
    // TC14 — Barrier on sloping ground
    // =========================================================================
    @Test
    public void TC14() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC14_Direct", "TC14_Right", "TC14_Left");
        verifyResults("TC14", result);
    }

    // =========================================================================
    // TC15 — Barrier on ground with varying height and acoustic properties
    // =========================================================================
    @Test
    public void TC15() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC15_Direct", "TC15_Right", "TC15_Left");
        verifyResults("TC15", result);
    }

    // =========================================================================
    // TC16 — Reflecting barrier on flat ground
    // =========================================================================
    @Test
    public void TC16() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC16_Direct", "TC16_Reflection");
        verifyResults("TC16", result);
    }

    // =========================================================================
    // TC17 — Reflecting barrier on ground with spatially varying heights and acoustic properties
    // =========================================================================
    @Test
    public void TC17() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC17_Direct", "TC17_Reflection");
        verifyResults("TC17", result);
    }

    // =========================================================================
    // TC18 — Screening and reflecting barrier on ground with spatially varying heights
    // =========================================================================
    @Test
    public void TC18() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC18_Direct", "TC18_Reflection");
        verifyResults("TC18", result);
    }

    // =========================================================================
    // TC18Altered — Modified TC18 with lateral diffraction
    // =========================================================================
    // @Test
    // public void TC18Altered() throws IOException {
    //     AttenuationComputeOutput result = computeCnossosPath("TC18Altered_Direct", "TC18Altered_Left");
    //     verifyResults("TC18Altered", result);
    // }

    // =========================================================================
    // TC19 — Complex object and two barriers
    // TC19_Left is excluded due to a known error in the CNOSSOS-EU reference document.
    // =========================================================================
    @Test
    public void TC19() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC19_Direct", "TC19_Right");
        verifyResults("TC19", result);
    }

    // =========================================================================
    // TC20 — Ground with spatially varying heights and acoustic properties
    // =========================================================================
    @Test
    public void TC20() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC20_Direct");
        verifyResults("TC20", result);
    }

    // =========================================================================
    // TC21 — Building on ground with spatially varying heights and acoustic properties
    // =========================================================================
    @Test
    public void TC21() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC21_Direct", "TC21_Right", "TC21_Left");
        verifyResults("TC21", result);
    }

    // =========================================================================
    // TC22 — Building with receiver backside on ground with spatially varying heights
    // =========================================================================
    @Test
    public void TC22() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC22_Direct", "TC22_Right", "TC22_Left");
        verifyResults("TC22", result);
    }

    // =========================================================================
    // TC23 — Two buildings behind an earth-berm on flat ground
    // =========================================================================
    @Test
    public void TC23() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC23_Direct");
        verifyResults("TC23", result);
    }

    // =========================================================================
    // TC24 — Two buildings behind an earth-berm, receiver position modified
    // =========================================================================
    @Test
    public void TC24() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC24_Direct", "TC24_Reflection");
        verifyResults("TC24", result);
    }

    // =========================================================================
    // TC25 — Complex mixed-propagation scenario
    // =========================================================================
    @Test
    public void TC25() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC25_Direct", "TC25_Right", "TC25_Left", "TC25_Reflection");
        verifyResults("TC25", result);
    }

    // =========================================================================
    // TC26 — Road source with influence of retrodiffraction
    // =========================================================================
    @Test
    public void TC26() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC26_Direct", "TC26_Reflection");
        verifyResults("TC26", result);
    }

    // =========================================================================
    // TC27 — Road source with influence of retrodiffraction (variant)
    // =========================================================================
    @Test
    public void TC27() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC27_Direct", "TC27_Reflection");
        verifyResults("TC27", result);
    }

    // =========================================================================
    // TC28 — Propagation over a large distance with many buildings
    // TC28_Left is excluded (same limitation as in PathFinderTest).
    // =========================================================================
    @Test
    public void TC28() throws IOException {
        AttenuationComputeOutput result = computeCnossosPath("TC28_Direct", "TC28_Right");
        verifyResults("TC28", result);
    }
}
