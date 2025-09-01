package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import java.util.Arrays;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.List;
import java.util.Collection;

/**
 * Container for default frequency definitions and helper to initialize frequency-related arrays.
 */
public final class FrequencyConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(FrequencyConfig.class);
    public static final int[] DEFAULT_FREQUENCIES_THIRD_OCTAVE = new int[] {50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000};
    public static final Double[] DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE = new Double[] {50.1187234, 63.0957344, 79.4328235, 100.0, 125.892541, 158.489319, 199.526231, 251.188643, 316.227766, 398.107171, 501.187234, 630.957344, 794.328235, 1000.0, 1258.92541, 1584.89319, 1995.26231, 2511.88643, 3162.27766, 3981.07171, 5011.87234, 6309.57344, 7943.28235, 10000.0};
    public static final Double[] DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE = new Double[] {-30.2, -26.2, -22.5, -19.1, -16.1, -13.4, -10.9, -8.6, -6.6, -4.8, -3.2, -1.9, -0.8, 0.0, 0.6, 1.0, 1.2, 1.3, 1.2, 1.0, 0.5, -0.1, -1.1, -2.5};

    // Instance fields holding the configured frequency arrays
    private List<Integer> frequencyArray;
    private List<Double> exactFrequencyArray;
    private List<Double> aWeightingArray;

    /**
     * Default constructor initializing this config with the library defaults.
     */
    public FrequencyConfig() {
        this.exactFrequencyArray = new ArrayList<>();
        this.aWeightingArray = new ArrayList<>();
        this.frequencyArray = new ArrayList<>();
        for (int f : DEFAULT_FREQUENCIES_THIRD_OCTAVE) {
            this.frequencyArray.add(f);
        }
        initializeFromReference();
    }

    /**
     * Create a FrequencyConfig using the provided reference frequency list.
     */
    public FrequencyConfig(Collection<Integer> frequencyArray) {
        this.exactFrequencyArray = new ArrayList<>();
        this.aWeightingArray = new ArrayList<>();
        this.frequencyArray = new ArrayList<>(frequencyArray);
        initializeFromReference();
    }

    private void initializeFromReference() {
        Collections.sort(this.frequencyArray);
        this.exactFrequencyArray.clear();
        this.aWeightingArray.clear();
        for (int freq : this.frequencyArray) {
            int index = Arrays.binarySearch(DEFAULT_FREQUENCIES_THIRD_OCTAVE, freq);
            this.exactFrequencyArray.add(DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE[index]);
            this.aWeightingArray.add(DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE[index]);
        }
    }

    public void setFrequencyArray(Collection<Integer> frequencyArray) {
        if (frequencyArray == null) {
            LOGGER.debug("FrequencyConfig.setFrequencyArray: called with null -> using default");
        } else {
            LOGGER.debug("FrequencyConfig.setFrequencyArray: called with size=" + frequencyArray.size() + " values=" + frequencyArray);
        }
        this.frequencyArray = new ArrayList<>(frequencyArray);
        initializeFromReference();
    }

    public List<Integer> getFrequencyArray() {
        return Collections.unmodifiableList(frequencyArray);
    }

    public List<Double> getExactFrequencyArray() {
        return Collections.unmodifiableList(exactFrequencyArray);
    }

    public List<Double> getAWeightingArray() {
        return Collections.unmodifiableList(aWeightingArray);
    }

    /**
     * Backwards-compatible static helper retained: fills provided lists using
     * the default reference arrays. Kept for callers that still use the
     * old static API.
     */
    public static void initializeFrequencyArrayFromReference(List<Integer> frequencyArray,
                                                             List<Double> exactFrequencyArray,
                                                             List<Double> aWeightingArray) {
        Collections.sort(frequencyArray);
        for (int freq : frequencyArray) {
            int index = Arrays.binarySearch(DEFAULT_FREQUENCIES_THIRD_OCTAVE, freq);
            exactFrequencyArray.add(DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE[index]);
            aWeightingArray.add(DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE[index]);
        }
    }
}
