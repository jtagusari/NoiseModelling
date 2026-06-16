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
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPathExt;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.propagation.cnossos.AttenuationCnossosExt;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class RayAttenuationComputeOutputTest {

    private static final double DELTA = 0.1;

    private static CnossosPathExt loadSpecialRay() throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        mapper.setVisibility(mapper.getSerializationConfig().getDefaultVisibilityChecker()
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE));
        return mapper.readValue(
                RayAttenuationComputeOutputTest.class.getResourceAsStream("special_ray.json"),
                CnossosPathExt.class);
    }

    /**
     * Regression test: special_ray.json has NaN z-coordinates. aBoundary must not produce NaN.
     * This specific edge case (receiver under an obstacle with near-zero zrH) previously caused NaN.
     */
    @Test
    public void testPropagationPathReceiverUnderNoNaN() throws IOException {
        CnossosPathExt cnossosPath = loadSpecialRay();
        AttenuationParameters data = new AttenuationParameters(FrequencyConfig.FrequencyBand.OCTAVE);

        double[] aBoundary = AttenuationCnossosExt.aBoundary(cnossosPath, data);

        for (double value : aBoundary) {
            assertFalse(Double.isNaN(value), "aBoundary must not contain NaN");
        }
    }

    /**
     * Verify the actual aBoundary values for special_ray.json (unfavorable, one DIFH point).
     * Expected: positive diffraction attenuation increasing with frequency.
     */
    @Test
    public void testPropagationPathReceiverUnderValues() throws IOException {
        CnossosPathExt cnossosPath = loadSpecialRay();
        AttenuationParameters data = new AttenuationParameters(FrequencyConfig.FrequencyBand.OCTAVE);

        double[] aBoundary = AttenuationCnossosExt.aBoundary(cnossosPath, data);

        double[] expected = {4.85, 4.92, 5.07, 5.34, 5.85, 12.64, 31.79, 41.14};
        assertEquals(expected.length, aBoundary.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], aBoundary[i], DELTA,
                    "aBoundary[" + i + "] mismatch");
        }
    }
}
