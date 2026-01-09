package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import java.util.Arrays;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.List;
import java.util.Collection;

/**
 * Configuration class for frequency-dependent acoustic parameters in noise modeling.
 * 
 * <p>This class manages frequency bands, exact frequency arrays, and A-weighting coefficients
 * used throughout acoustic calculations. It provides predefined frequency sets for octave 
 * and one-third octave bands, along with their corresponding exact frequency values and 
 * A-weighting factors.</p>
 * 
 * <p>The class supports:</p>
 * <ul>
 *   <li>Standard octave and one-third octave frequency bands</li>
 *   <li>Custom frequency arrays for specialized calculations</li>
 *   <li>Exact frequency values for precise acoustic computations</li>
 *   <li>A-weighting coefficients for sound level adjustments</li>
 * </ul>
 * 
 * <p>Default frequency ranges cover the audible spectrum from 50 Hz to 10 kHz,
 * suitable for environmental noise modeling and building acoustics applications.</p>
 */
public final class FrequencyConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(FrequencyConfig.class);

    /**
     * Enumeration defining supported frequency band types for acoustic analysis.
     */
    public enum FrequencyBand {
        /** Standard octave bands (8 frequencies: 63, 125, 250, 500, 1000, 2000, 4000, 8000 Hz) */
        OCTAVE,
        /** One-third octave bands (24 frequencies: 50-10000 Hz) */
        ONE_THIRD_OCTAVE,
        /** Custom frequency configuration not matching standard bands */
        OTHER
    }

    /** Current frequency band configuration */
    private FrequencyBand frequencyBand = FrequencyBand.ONE_THIRD_OCTAVE;

    /** Predefined one-third octave band center frequencies in Hz (IEC 61260-1:2014) */
    public static final int[] DEFAULT_FREQUENCIES_THIRD_OCTAVE = new int[] {50, 63, 80, 100, 125, 160, 200, 250, 315, 400, 500, 630, 800, 1000, 1250, 1600, 2000, 2500, 3150, 4000, 5000, 6300, 8000, 10000};
    
    /** Predefined octave band center frequencies in Hz (IEC 61260-1:2014) */
    public static final int[] DEFAULT_FREQUENCIES_OCTAVE = new int[] {63, 125, 250, 500, 1000, 2000, 4000, 8000};
    
    /** Exact frequency values for one-third octave bands (calculated from 10^(N/10) formula) */
    public static final Double[] DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE = new Double[] {50.1187234, 63.0957344, 79.4328235, 100.0, 125.892541, 158.489319, 199.526231, 251.188643, 316.227766, 398.107171, 501.187234, 630.957344, 794.328235, 1000.0, 1258.92541, 1584.89319, 1995.26231, 2511.88643, 3162.27766, 3981.07171, 5011.87234, 6309.57344, 7943.28235, 10000.0};
    
    /** A-weighting correction factors in dB for one-third octave bands (IEC 61672-1:2013) */
    public static final Double[] DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE = new Double[] {-30.2, -26.2, -22.5, -19.1, -16.1, -13.4, -10.9, -8.6, -6.6, -4.8, -3.2, -1.9, -0.8, 0.0, 0.6, 1.0, 1.2, 1.3, 1.2, 1.0, 0.5, -0.1, -1.1, -2.5};

    /** Current frequency configuration for acoustic calculations */
    int[] frequenciesConfiguration = DEFAULT_FREQUENCIES_THIRD_OCTAVE;
    
    /** Current A-weighting correction factors corresponding to frequency configuration */
    Double[] aWeighting = DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE;

    // Instance fields holding the configured frequency arrays
    private List<Integer> frequencyArray = new ArrayList<>();
    private List<Double> exactFrequencyArray = new ArrayList<>();
    private List<Double> aWeightingArray = new ArrayList<>();

    /**
     * Default constructor initializing with one-third octave band configuration.
     * Sets up standard 24-band one-third octave frequency configuration
     * with corresponding A-weighting factors.
     */
    public FrequencyConfig() {
        setFrequencyArraysUsingBand(FrequencyBand.ONE_THIRD_OCTAVE);
    }

    /**
     * Constructor with custom frequency band configuration.
     * 
     * @param frequencyBand The frequency band type to use (OCTAVE, ONE_THIRD_OCTAVE, or OTHER)
     */
    public FrequencyConfig(FrequencyBand frequencyBand) {
        this.frequencyBand = frequencyBand;
        setFrequencyArraysUsingBand(frequencyBand);
    }

    /**
     * Constructor with custom frequency band and frequency array.
     * 
     * @param frequencyBand The frequency band type to use
     * @param frequencyArray Collection of center frequencies in Hz
     */
    public FrequencyConfig(FrequencyBand frequencyBand, Collection<Integer> frequencyArray) {
        this.frequencyBand = frequencyBand;
        this.frequencyArray = new ArrayList<>(frequencyArray);
        setFrequencyArray(frequencyArray);
    }

    /**
     * Sets the frequency array using an integer array.
     * Converts the array to a collection and applies frequency configuration.
     * 
     * @param referenceFrequencyArray Array of center frequencies in Hz, or null to clear
     */
    public void setFrequencyArray(int[] referenceFrequencyArray) {
        if (referenceFrequencyArray == null) {
            setFrequencyArray((Collection<Integer>) null);
            return;
        }
        List<Integer> refList = new ArrayList<>();
        for (int f : referenceFrequencyArray) {
            refList.add(f);
        }
        setFrequencyArray(refList);
    }

    /**
     * Sets the frequency array using a collection of integers.
     * Updates corresponding exact frequencies and A-weighting factors
     * based on predefined one-third octave values.
     * 
     * @param referenceFrequencyArray Collection of center frequencies in Hz
     */
    public void setFrequencyArray(Collection<Integer> referenceFrequencyArray){
        if (referenceFrequencyArray == null) {
            this.frequencyArray.clear();
            this.exactFrequencyArray.clear();
            this.aWeightingArray.clear();
            return;
        }
        
        List<Integer> refArray = new ArrayList<>(referenceFrequencyArray);
        Collections.sort(refArray);
        
        this.frequencyArray.clear();
        this.exactFrequencyArray.clear();
        this.aWeightingArray.clear();

        String logMsg = "FrequencyConfig.setFrequencyArraysFromReference: values=";

        for (int freq : refArray) {
            int index = Arrays.binarySearch(DEFAULT_FREQUENCIES_THIRD_OCTAVE, freq);
            this.frequencyArray.add(freq);
            
            if (index >= 0) {
                // Frequency found in standard array, use corresponding values
                this.exactFrequencyArray.add(DEFAULT_FREQUENCIES_EXACT_THIRD_OCTAVE[index]);
                this.aWeightingArray.add(DEFAULT_FREQUENCIES_A_WEIGHTING_THIRD_OCTAVE[index]);
            } else {
                LOGGER.info("FrequencyConfig.setFrequencyArraysFromReference: frequency {} Hz not found in standard one-third octave array", freq);
                // Custom frequency, use approximate values
                this.exactFrequencyArray.add((double) freq);
                // Approximate A-weighting for custom frequencies (0.0 as default)
                this.aWeightingArray.add(0.0);
            }
            logMsg += freq + ", ";
        }
        LOGGER.debug(logMsg);
    }

    /**
     * Configures frequency arrays based on the specified frequency band type.
     * Sets up predefined frequency configurations for octave or one-third octave bands.
     * 
     * @param frequencyBand The frequency band type (OCTAVE, ONE_THIRD_OCTAVE, or OTHER)
     */
    public void setFrequencyArraysUsingBand(FrequencyBand frequencyBand){
        switch (frequencyBand) {
            case OCTAVE:
                LOGGER.debug("FrequencyConfig.setFrequencyArraysUsingBand: called with OCTAVE");
                setFrequencyArray(DEFAULT_FREQUENCIES_OCTAVE);
                this.frequencyBand = FrequencyBand.OCTAVE;
                break;
            case ONE_THIRD_OCTAVE:
                LOGGER.debug("FrequencyConfig.setFrequencyArraysUsingBand: called with ONE_THIRD_OCTAVE");
                setFrequencyArray(DEFAULT_FREQUENCIES_THIRD_OCTAVE);
                this.frequencyBand = FrequencyBand.ONE_THIRD_OCTAVE;
                break;
            default:
                LOGGER.warn("FrequencyConfig.setFrequencyArraysUsingBand: called with OTHER band - no action taken");
                break;
        }
        return;
    }

    /**
     * Sets exact frequency arrays for custom frequency configurations.
     * Used when working with OTHER frequency band type with precise frequency values.
     * 
     * @param frequencyArray Collection of center frequencies in Hz
     * @param exactFrequencyArray Collection of exact frequency values in Hz
     */
    public void setExactFrequencyArray(Collection<Integer> frequencyArray, Collection<Double> exactFrequencyArray) {
        this.frequencyBand = FrequencyBand.OTHER;
        this.frequencyArray = new ArrayList<>(frequencyArray);
        this.exactFrequencyArray = new ArrayList<>(exactFrequencyArray);
        this.aWeightingArray = new ArrayList<>(exactFrequencyArray.size());
    }
    
    /**
     * Gets the current frequency band configuration type.
     * 
     * @return The frequency band type (OCTAVE, ONE_THIRD_OCTAVE, or OTHER)
     */
    public FrequencyBand getFrequencyBand() {
        return frequencyBand;
    }

    /**
     * Sets the frequency band configuration type.
     * 
     * @param frequencyBand The frequency band type to set
     */
    public void setFrequencyBand(FrequencyBand frequencyBand) {
        this.frequencyBand = frequencyBand;
    }
    
    /**
     * Gets the current frequency array as an unmodifiable list.
     * Returns the center frequencies in Hz for acoustic calculations.
     * 
     * @return Unmodifiable list of center frequencies in Hz
     */
    public List<Integer> getFrequencyArray() {
        return Collections.unmodifiableList(frequencyArray);
    }

    /**
     * Gets the exact frequency array as an unmodifiable list.
     * Returns precise frequency values calculated from frequency band formulas.
     * 
     * @return Unmodifiable list of exact frequency values in Hz
     */
    public List<Double> getExactFrequencyArray() {
        return Collections.unmodifiableList(exactFrequencyArray);
    }

    /**
     * Gets the A-weighting correction array as an unmodifiable list.
     * Returns the A-weighting factors in dB corresponding to each frequency.
     * 
     * @return Unmodifiable list of A-weighting correction factors in dB
     */
    public List<Double> getAWeightingArray() {
        return Collections.unmodifiableList(aWeightingArray);
    }

}
