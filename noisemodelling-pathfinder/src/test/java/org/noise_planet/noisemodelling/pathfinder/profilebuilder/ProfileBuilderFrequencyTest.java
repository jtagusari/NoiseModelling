package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.util.Arrays;
import java.util.List;

/**
 * Test class for ProfileBuilder with FrequencyConfig integration.
 * Tests the proper initialization and usage of frequency configurations.
 */
public class ProfileBuilderFrequencyTest {

    private static final WKTReader READER = new WKTReader();

    @Test
    public void testProfileBuilderDefaultFrequencyConfig() {
        ProfileBuilder profileBuilder = new ProfileBuilder();
        
        // The default constructor should use one-third octave configuration
        FrequencyConfig config = profileBuilder.getFrequencyConfig();
        assertNotNull(config, "FrequencyConfig should not be null");
        assertEquals(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE, config.getFrequencyBand(),
                "Default frequency band should be ONE_THIRD_OCTAVE");
    }

    @Test
    public void testProfileBuilderWithCustomFrequencyConfig() {
        FrequencyConfig customConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.OCTAVE);
        ProfileBuilder profileBuilder = new ProfileBuilder(customConfig);
        
        FrequencyConfig retrievedConfig = profileBuilder.getFrequencyConfig();
        assertNotNull(retrievedConfig, "FrequencyConfig should not be null");
        assertEquals(FrequencyConfig.FrequencyBand.OCTAVE, retrievedConfig.getFrequencyBand(),
                "Custom frequency band should be OCTAVE");
    }

    @Test
    public void testFrequencyConfigAfterFinishFeeding() throws ParseException {
        FrequencyConfig customConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        customConfig.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        
        ProfileBuilder profileBuilder = new ProfileBuilder(customConfig);
        
        // Add some building to test frequency initialization
        profileBuilder.addBuilding(READER.read("POLYGON((1 1,5 1,5 5,1 5,1 1))"), 10);
        profileBuilder.finishFeeding();
        
        // After finish feeding, frequency arrays should be properly initialized
        FrequencyConfig config = profileBuilder.getFrequencyConfig();
        assertNotNull(config.getFrequencyArray(), "Frequency array should not be null");
        assertTrue(config.getFrequencyArray().size() > 0, "Frequency array should not be empty");
        assertEquals(24, config.getFrequencyArray().size(), "One-third octave should have 24 frequencies");
    }

    @Test
    public void testOctaveBandConfiguration() {
        FrequencyConfig octaveConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.OCTAVE);
        octaveConfig.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.OCTAVE);
        
        ProfileBuilder profileBuilder = new ProfileBuilder(octaveConfig);
        profileBuilder.finishFeeding();
        
        FrequencyConfig config = profileBuilder.getFrequencyConfig();
        assertEquals(FrequencyConfig.FrequencyBand.OCTAVE, config.getFrequencyBand(),
                "Should maintain OCTAVE frequency band");
        assertEquals(8, config.getFrequencyArray().size(), "Octave band should have 8 frequencies");
        
        // Test specific octave frequencies
        List<Integer> expectedOctaveFreqs = Arrays.asList(63, 125, 250, 500, 1000, 2000, 4000, 8000);
        assertEquals(expectedOctaveFreqs, config.getFrequencyArray(),
                "Octave frequencies should match expected values");
    }

    @Test
    public void testOneThirdOctaveBandConfiguration() {
        FrequencyConfig thirdOctaveConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        thirdOctaveConfig.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        
        ProfileBuilder profileBuilder = new ProfileBuilder(thirdOctaveConfig);
        profileBuilder.finishFeeding();
        
        FrequencyConfig config = profileBuilder.getFrequencyConfig();
        assertEquals(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE, config.getFrequencyBand(),
                "Should maintain ONE_THIRD_OCTAVE frequency band");
        assertEquals(24, config.getFrequencyArray().size(), "One-third octave band should have 24 frequencies");
        
        // Test that array contains expected range
        List<Integer> freqs = config.getFrequencyArray();
        assertTrue(freqs.contains(50), "Should contain 50 Hz");
        assertTrue(freqs.contains(1000), "Should contain 1000 Hz");
        assertTrue(freqs.contains(10000), "Should contain 10000 Hz");
    }

    @Test
    public void testFrequencyConfigWithBuildingService() throws ParseException {
        FrequencyConfig config = new FrequencyConfig(FrequencyConfig.FrequencyBand.OCTAVE);
        config.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.OCTAVE);
        
        ProfileBuilder profileBuilder = new ProfileBuilder(config);
        
        // Add buildings with different absorption coefficients
        List<Double> alphas1 = Arrays.asList(0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1);
        List<Double> alphas2 = Arrays.asList(0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2);
        
        profileBuilder.addBuilding(READER.read("POLYGON((1 1,5 1,5 5,1 5,1 1))"), 10, alphas1);
        profileBuilder.addBuilding(READER.read("POLYGON((10 10,15 10,15 15,10 15,10 10))"), 15, alphas2);
        
        profileBuilder.finishFeeding();
        
        // Verify buildings were added correctly
        assertEquals(2, profileBuilder.getBuildingCount(), "Should have 2 buildings");
        
        // Verify frequency configuration is maintained
        FrequencyConfig retrievedConfig = profileBuilder.getFrequencyConfig();
        assertEquals(FrequencyConfig.FrequencyBand.OCTAVE, retrievedConfig.getFrequencyBand(),
                "Should maintain OCTAVE frequency band");
        assertEquals(8, retrievedConfig.getFrequencyArray().size(), "Should have 8 octave frequencies");
    }

    @Test
    public void testAWeightingArrayCorrespondence() {
        FrequencyConfig config = new FrequencyConfig(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        config.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        
        ProfileBuilder profileBuilder = new ProfileBuilder(config);
        profileBuilder.finishFeeding();
        
        FrequencyConfig retrievedConfig = profileBuilder.getFrequencyConfig();
        
        // Check A-weighting array correspondence
        assertEquals(retrievedConfig.getFrequencyArray().size(), 
                    retrievedConfig.getAWeightingArray().size(),
                    "A-weighting array size should match frequency array size");
        
        // Test specific A-weighting value at 1000 Hz (should be 0.0 dB)
        List<Integer> frequencies = retrievedConfig.getFrequencyArray();
        List<Double> aWeighting = retrievedConfig.getAWeightingArray();
        
        int index1000Hz = frequencies.indexOf(1000);
        if (index1000Hz >= 0) {
            assertEquals(0.0, aWeighting.get(index1000Hz), 0.01,
                    "A-weighting at 1000 Hz should be 0.0 dB");
        }
    }

    @Test
    public void testCustomFrequencyConfiguration() {
        // Test with custom frequencies
        List<Integer> customFreqs = Arrays.asList(100, 200, 400, 800, 1600, 3200);
        FrequencyConfig customConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.OTHER, customFreqs);
        
        ProfileBuilder profileBuilder = new ProfileBuilder(customConfig);
        profileBuilder.finishFeeding();
        
        FrequencyConfig retrievedConfig = profileBuilder.getFrequencyConfig();
        assertEquals(FrequencyConfig.FrequencyBand.OTHER, retrievedConfig.getFrequencyBand(),
                "Should be OTHER frequency band for custom configuration");
        assertEquals(customFreqs, retrievedConfig.getFrequencyArray(),
                "Custom frequency array should be preserved");
    }

    @Test
    public void testEmptyProfileBuilderFrequencyConfig() {
        ProfileBuilder profileBuilder = new ProfileBuilder();
        profileBuilder.finishFeeding();
        
        FrequencyConfig config = profileBuilder.getFrequencyConfig();
        assertNotNull(config, "FrequencyConfig should not be null even for empty profile");
        assertEquals(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE, config.getFrequencyBand(),
                "Default should be ONE_THIRD_OCTAVE");
        
        // Even empty profile should have default frequency configuration
        assertTrue(config.getFrequencyArray().size() > 0, 
                "Empty profile should still have default frequency array");
    }

    @Test
    public void testProfileBuilderFrequencyConfigImmutability() {
        ProfileBuilder profileBuilder = new ProfileBuilder();
        FrequencyConfig config = profileBuilder.getFrequencyConfig();
        
        // Test that retrieved frequency arrays are unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            config.getFrequencyArray().add(999);
        }, "getFrequencyArray should return unmodifiable list");
        
        assertThrows(UnsupportedOperationException.class, () -> {
            config.getExactFrequencyArray().add(999.0);
        }, "getExactFrequencyArray should return unmodifiable list");
        
        assertThrows(UnsupportedOperationException.class, () -> {
            config.getAWeightingArray().add(999.0);
        }, "getAWeightingArray should return unmodifiable list");
    }
}
