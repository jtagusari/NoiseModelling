package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Unit tests for FrequencyConfig class.
 * Tests frequency band configuration, frequency arrays, and A-weighting factors.
 */
public class FrequencyConfigTest {

    private FrequencyConfig frequencyConfig;

    @BeforeEach
    public void setUp() {
        frequencyConfig = new FrequencyConfig();
    }

    @Test
    public void testDefaultConstructor() {
        // Test default constructor creates ONE_THIRD_OCTAVE configuration
        assertEquals(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE, frequencyConfig.getFrequencyBand(),
                "Default constructor should set ONE_THIRD_OCTAVE band");
    }

    @Test
    public void testConstructorWithFrequencyBand() {
        // Test constructor with OCTAVE band
        FrequencyConfig octaveConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.OCTAVE);
        assertEquals(FrequencyConfig.FrequencyBand.OCTAVE, octaveConfig.getFrequencyBand(),
                "Constructor should set OCTAVE band");
        
        // Test constructor with ONE_THIRD_OCTAVE band
        FrequencyConfig thirdOctaveConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        assertEquals(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE, thirdOctaveConfig.getFrequencyBand(),
                "Constructor should set ONE_THIRD_OCTAVE band");
    }

    @Test
    public void testConstructorWithCustomFrequencyArray() {
        List<Integer> customFreqs = Arrays.asList(100, 200, 400, 800);
        FrequencyConfig customConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.OTHER, customFreqs);
        
        assertEquals(FrequencyConfig.FrequencyBand.OTHER, customConfig.getFrequencyBand(),
                "Constructor should set OTHER band for custom frequencies");
        assertEquals(customFreqs, customConfig.getFrequencyArray(),
                "Custom frequency array should be set correctly");
    }

    @Test
    public void testSetFrequencyArrayWithIntArray() {
        int[] frequencies = {125, 250, 500, 1000, 2000, 4000};
        frequencyConfig.setFrequencyArray(frequencies);
        
        List<Integer> expectedList = Arrays.asList(125, 250, 500, 1000, 2000, 4000);
        assertEquals(expectedList, frequencyConfig.getFrequencyArray(),
                "Frequency array should be set correctly from int array");
    }

    @Test
    public void testSetFrequencyArrayWithNullArray() {
        frequencyConfig.setFrequencyArray((int[]) null);
        // Should not throw exception and should handle null gracefully
        assertTrue(true, "Setting null frequency array should not throw exception");
    }

    @Test
    public void testSetFrequencyArrayWithCollection() {
        List<Integer> frequencies = Arrays.asList(63, 125, 250, 500, 1000, 2000, 4000, 8000);
        frequencyConfig.setFrequencyArray(frequencies);
        
        assertEquals(frequencies.size(), frequencyConfig.getFrequencyArray().size(),
                "Frequency array size should match input collection size");
        
        // Test that frequencies are sorted
        List<Integer> resultFreqs = frequencyConfig.getFrequencyArray();
        for (int i = 1; i < resultFreqs.size(); i++) {
            assertTrue(resultFreqs.get(i - 1) <= resultFreqs.get(i),
                    "Frequencies should be sorted in ascending order");
        }
    }

    @Test
    public void testSetFrequencyArraysUsingOctaveBand() {
        frequencyConfig.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.OCTAVE);
        
        assertEquals(FrequencyConfig.FrequencyBand.OCTAVE, frequencyConfig.getFrequencyBand(),
                "Frequency band should be set to OCTAVE");
        
        // Convert array to list for comparison
        List<Integer> expectedOctaveFreqs = new ArrayList<>();
        for (int freq : FrequencyConfig.DEFAULT_FREQUENCIES_OCTAVE) {
            expectedOctaveFreqs.add(freq);
        }
        assertEquals(expectedOctaveFreqs, frequencyConfig.getFrequencyArray(),
                "Frequency array should match default octave frequencies");
        
        assertEquals(8, frequencyConfig.getFrequencyArray().size(),
                "Octave band should have 8 frequencies");
    }

    @Test
    public void testSetFrequencyArraysUsingOneThirdOctaveBand() {
        frequencyConfig.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        
        assertEquals(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE, frequencyConfig.getFrequencyBand(),
                "Frequency band should be set to ONE_THIRD_OCTAVE");
        
        // Convert array to list for comparison
        List<Integer> expectedThirdOctaveFreqs = new ArrayList<>();
        for (int freq : FrequencyConfig.DEFAULT_FREQUENCIES_THIRD_OCTAVE) {
            expectedThirdOctaveFreqs.add(freq);
        }
        assertEquals(expectedThirdOctaveFreqs, frequencyConfig.getFrequencyArray(),
                "Frequency array should match default one-third octave frequencies");
        
        assertEquals(24, frequencyConfig.getFrequencyArray().size(),
                "One-third octave band should have 24 frequencies");
    }

    @Test
    public void testSetFrequencyArraysUsingOtherBand() {
        // Set up initial state
        frequencyConfig.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        List<Integer> initialFreqs = new ArrayList<>(frequencyConfig.getFrequencyArray());
        
        // Test OTHER band - should not change frequencies
        frequencyConfig.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.OTHER);
        
        assertEquals(initialFreqs, frequencyConfig.getFrequencyArray(),
                "OTHER band should not modify existing frequency array");
    }

    @Test
    public void testSetExactFrequencyArray() {
        List<Integer> frequencies = Arrays.asList(100, 200, 400);
        List<Double> exactFrequencies = Arrays.asList(100.0, 200.0, 400.0);
        
        frequencyConfig.setExactFrequencyArray(frequencies, exactFrequencies);
        
        assertEquals(FrequencyConfig.FrequencyBand.OTHER, frequencyConfig.getFrequencyBand(),
                "Setting exact frequencies should set band to OTHER");
        assertEquals(frequencies, frequencyConfig.getFrequencyArray(),
                "Frequency array should match input");
        assertEquals(exactFrequencies, frequencyConfig.getExactFrequencyArray(),
                "Exact frequency array should match input");
    }

    @Test
    public void testGettersReturnUnmodifiableCollections() {
        // Test that getters return unmodifiable lists
        assertThrows(UnsupportedOperationException.class, () -> {
            frequencyConfig.getFrequencyArray().add(999);
        }, "getFrequencyArray should return unmodifiable list");
        
        assertThrows(UnsupportedOperationException.class, () -> {
            frequencyConfig.getExactFrequencyArray().add(999.0);
        }, "getExactFrequencyArray should return unmodifiable list");
        
        assertThrows(UnsupportedOperationException.class, () -> {
            frequencyConfig.getAWeightingArray().add(999.0);
        }, "getAWeightingArray should return unmodifiable list");
    }

    @Test
    public void testFrequencyBandEnumValues() {
        // Test all enum values exist
        assertNotNull(FrequencyConfig.FrequencyBand.OCTAVE, "OCTAVE enum should exist");
        assertNotNull(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE, "ONE_THIRD_OCTAVE enum should exist");
        assertNotNull(FrequencyConfig.FrequencyBand.OTHER, "OTHER enum should exist");
        
        // Test enum has exactly 3 values
        assertEquals(3, FrequencyConfig.FrequencyBand.values().length,
                "FrequencyBand enum should have exactly 3 values");
    }

    @Test
    public void testDefaultFrequencyConstants() {
        // Test that default frequency arrays have expected lengths
        assertEquals(24, FrequencyConfig.DEFAULT_FREQUENCIES_THIRD_OCTAVE.length,
                "Default one-third octave frequencies should have 24 elements");
        assertEquals(8, FrequencyConfig.DEFAULT_FREQUENCIES_OCTAVE.length,
                "Default octave frequencies should have 8 elements");
        assertEquals(24, FrequencyConfig.DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE.length,
                "Default exact one-third octave frequencies should have 24 elements");
        assertEquals(24, FrequencyConfig.DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE.length,
                "Default A-weighting factors should have 24 elements");
        
        // Test that frequencies are in ascending order
        for (int i = 1; i < FrequencyConfig.DEFAULT_FREQUENCIES_THIRD_OCTAVE.length; i++) {
            assertTrue(FrequencyConfig.DEFAULT_FREQUENCIES_THIRD_OCTAVE[i - 1] < 
                      FrequencyConfig.DEFAULT_FREQUENCIES_THIRD_OCTAVE[i],
                    "One-third octave frequencies should be in ascending order");
        }
        
        for (int i = 1; i < FrequencyConfig.DEFAULT_FREQUENCIES_OCTAVE.length; i++) {
            assertTrue(FrequencyConfig.DEFAULT_FREQUENCIES_OCTAVE[i - 1] < 
                      FrequencyConfig.DEFAULT_FREQUENCIES_OCTAVE[i],
                    "Octave frequencies should be in ascending order");
        }
    }

    @Test
    public void testAWeightingArrayCorrespondence() {
        // Test that A-weighting array corresponds to frequency setting
        frequencyConfig.setFrequencyArraysUsingBand(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        
        assertEquals(frequencyConfig.getFrequencyArray().size(), 
                    frequencyConfig.getAWeightingArray().size(),
                    "A-weighting array size should match frequency array size");
        
        // Test specific A-weighting value at 1000 Hz (should be 0.0 dB)
        List<Integer> frequencies = frequencyConfig.getFrequencyArray();
        List<Double> aWeighting = frequencyConfig.getAWeightingArray();
        
        int index1000Hz = frequencies.indexOf(1000);
        if (index1000Hz >= 0) {
            assertEquals(0.0, aWeighting.get(index1000Hz), 0.01,
                    "A-weighting at 1000 Hz should be 0.0 dB");
        }
    }

    @Test
    public void testSetAndGetFrequencyBand() {
        // Test setter and getter for frequency band
        frequencyConfig.setFrequencyBand(FrequencyConfig.FrequencyBand.OCTAVE);
        assertEquals(FrequencyConfig.FrequencyBand.OCTAVE, frequencyConfig.getFrequencyBand(),
                "Frequency band should be set and retrieved correctly");
        
        frequencyConfig.setFrequencyBand(FrequencyConfig.FrequencyBand.OTHER);
        assertEquals(FrequencyConfig.FrequencyBand.OTHER, frequencyConfig.getFrequencyBand(),
                "Frequency band should be updated correctly");
    }
}
