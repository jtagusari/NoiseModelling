/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.road.asj;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import java.io.IOException;
import java.io.InputStream;

import static org.noise_planet.noisemodelling.emission.utils.Utils.*;

/**
 * Compute bridge virtual source emission sound level using ASJ (Acoustical Society of Japan) methodology
 * This implementation focuses specifically on bridge structures and virtual source calculation
 * Formula: L(f) = a(f) + b(f) * log10(V) + traffic_flow_contribution
 * 
 * Reference: ASJ Model 2023 for Bridge Virtual Source Calculation
 * @author Based on RoadCnossos structure, modified for ASJ bridge methodology
 */
public class RoadAsj {
    
    private static JsonNode asjData2023 = parse(RoadAsj.class.getResourceAsStream("RoadAsj_2023.json"));
    
    /**
     * Bridge coefficients for a specific frequency
     */
    public static class BridgeCoefficients {
        public final double a; // Base level coefficient
        public final double b; // Speed coefficient
        
        public BridgeCoefficients(double a, double b) {
            this.a = a;
            this.b = b;
        }
    }
    
    private static JsonNode parse(InputStream inputStream) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readTree(inputStream);
        } catch (IOException ex) {
            return NullNode.getInstance();
        }
    }
    
    /**
     * Get ASJ bridge coefficient for a specific frequency and bridge type
     * @param bridgeType Bridge type identifier (e.g., "STEEL_BOX_STEEL", "CONCRETE_BOX_CONCRETE")
     * @param coeff Coefficient type ("a" for base level, "b" for speed coefficient)
     * @param freq Frequency in Hz (octave band)
     * @return Bridge coefficient value
     * @throws IOException If bridge type is not found in database
     */
    public static Double getBridgeCoeff(String bridgeType, String coeff, int freq) throws IOException {
        int freqIndex = getFrequencyIndex(freq);
        
        if (asjData2023.get("bridges").get(bridgeType) == null) {
            throw new IOException("Error: Bridge type " + bridgeType + " doesn't exist in the ASJ database.");
        }
        
        return asjData2023.get("bridges").get(bridgeType).get(coeff).get(freqIndex).doubleValue();
    }
    
    /**
     * Get frequency index for octave band arrays
     * @param freq Frequency in Hz
     * @return Array index (0-7)
     */
    private static int getFrequencyIndex(int freq) {
        switch (freq) {
            case 63: return 0;
            case 125: return 1;
            case 250: return 2;
            case 500: return 3;
            case 1000: return 4;
            case 2000: return 5;
            case 4000: return 6;
            case 8000: return 7;
            default: return 4; // Default to 1000Hz
        }
    }
    
    /**
     * Get bridge coefficients for specific bridge structure and frequency
     * 
     * @param girderType Bridge girder type (STEEL_BOX, STEEL_PLATE, CONCRETE_BOX, etc.)
     * @param slabType Bridge slab type (STEEL, CONCRETE)
     * @param frequency Frequency in Hz
     * @return Bridge coefficients for the given combination
     * @throws IOException If bridge type is not found in database or JSON loading fails
     */
    public static BridgeCoefficients getBridgeCoefficients(String girderType, String slabType, int frequency) throws IOException {
        String bridgeType = girderType + "_" + slabType;
        
        double a = getBridgeCoeff(bridgeType, "a", frequency);
        double b = getBridgeCoeff(bridgeType, "b", frequency);
        return new BridgeCoefficients(a, b);
    }
    
    /**
     * Get bridge virtual source sound level in dB at a specific speed
     * Similar to RoadCnossos.getNoiseLvl() method
     * @param a Base coefficient (a)
     * @param b Speed coefficient (b)
     * @param speed Vehicle speed in km/h
     * @return Sound level in dB
     */
    public static Double getBridgeNoiseLvl(double a, double b, double speed) {
        return a + b * Math.log10(speed);
    }
    
    /**
     * Calculate bridge virtual source level for specific frequency using ASJ method
     * Formula: a(f) + b(f) * log10(V) - similar to RoadCnossos structure
     * 
     * @param girderType Bridge girder type (STEEL_BOX, STEEL_PLATE, CONCRETE_BOX, etc.)
     * @param slabType Bridge slab type (STEEL, CONCRETE)
     * @param frequency Target frequency in Hz
     * @param speed Vehicle speed in km/h
     * @return Sound level for the specific frequency band in dB
     * @throws IOException If bridge type is not found in database or JSON loading fails
     */
    public static double calculateBridgeVirtualSourceLevelForFrequency(String girderType, String slabType, 
                                                                      int frequency, double speed) throws IOException {
        if (speed <= 0) {
            return -99.0; // Silent source
        }
        
        BridgeCoefficients coeffs = getBridgeCoefficients(girderType, slabType, frequency);
        
        // ASJ formula with frequency-specific coefficients, similar to RoadCnossos.getNoiseLvl()
        return getBridgeNoiseLvl(coeffs.a, coeffs.b, speed);
    }
    
    /**
     * Calculate average speed for medium and heavy vehicles based on RoadCnossos style
     * ASJ method primarily uses MV and HGV for bridge virtual source calculation
     * as these vehicle categories have sufficient mass to generate structural vibration
     * @param roadAsjParameters ASJ parameters containing MV and HGV speeds and flows
     * @return Average speed weighted by MV and HGV flows
     * @throws IOException If speed calculation fails
     */
    private static double getAverageSpeedMvHgv(RoadAsjParameters roadAsjParameters) throws IOException {
        double mvPerHour = roadAsjParameters.getMvPerHour();
        double hgvPerHour = roadAsjParameters.getHgvPerHour();
        double speedMv = roadAsjParameters.getSpeedMv();
        double speedHgv = roadAsjParameters.getSpeedHgv();
        
        double totalFlow = mvPerHour + hgvPerHour;
        if (totalFlow <= 0) {
            return 0.0; // Default speed when no MV/HGV traffic
        }
        
        return (speedMv * mvPerHour + speedHgv * hgvPerHour) / totalFlow;
    }
    
    /**
     * Get total traffic flow for medium and heavy vehicles
     * @param roadAsjParameters ASJ parameters
     * @return Total MV + HGV flow in vehicles per hour
     */
    private static double getTotalMvHgvFlow(RoadAsjParameters roadAsjParameters) {
        return roadAsjParameters.getMvPerHour() + roadAsjParameters.getHgvPerHour();
    }
    
    /**
     * Evaluate bridge virtual source using ASJ parameters - RoadCnossos style implementation
     * @param roadAsjParameters ASJ road parameters including bridge information
     * @return Noise level in dB for the specified frequency
     * @throws IOException If calculation fails
     */
    public static double evaluateBridgeVirtualSource(RoadAsjParameters roadAsjParameters) throws IOException {
        if (!roadAsjParameters.hasBridge()) {
            return -99.0; // No bridge, no virtual source
        }
        
        final int freqParam = roadAsjParameters.getFrequency();
        final String bridgeGirderType = roadAsjParameters.getBridgeGirderType();
        final String bridgeSlabType = roadAsjParameters.getBridgeSlabType();
        
        // Calculate average speed for MV and HGV only (ASJ focuses on heavy traffic for bridge calculation)
        double avgSpeedMvHgv = getAverageSpeedMvHgv(roadAsjParameters);
        
        // Get total traffic flow for MV and HGV only
        double totalMvHgvFlow = getTotalMvHgvFlow(roadAsjParameters);
        
        if (totalMvHgvFlow <= 0 || avgSpeedMvHgv <= 0) {
            return -99.0; // No relevant traffic, no virtual source
        }
        
        // Get bridge virtual source base level using frequency-specific calculation
        double bridgeVirtualSourceLevel = calculateBridgeVirtualSourceLevelForFrequency(
            bridgeGirderType,
            bridgeSlabType, 
            freqParam,
            avgSpeedMvHgv
        );
        
        // Apply traffic flow contribution using RoadCnossos style Vperhour2NoiseLevel
        // This follows the same pattern as RoadCnossos equation 2.2.1
        // Formula: LW + 10*log10(Q/(1000*v)) where Q is flow rate, v is speed
        double bridgeNoiseLevel = Vperhour2NoiseLevel(bridgeVirtualSourceLevel, totalMvHgvFlow, avgSpeedMvHgv);
        
        return bridgeNoiseLevel;
    }
    
    /**
     * Return the noise emission level of a road segment in dB/m - ASJ method placeholder
     * ASJ methodology is primarily used for bridge virtual source calculation
     * @param roadAsjParameters ASJ road parameters
     * @return Always returns -99.0 (silent) as ASJ is not used for standard road noise
     * @throws IOException Never thrown in this implementation
     */
    public static double evaluate(RoadAsjParameters roadAsjParameters) throws IOException {
        // ASJ methodology is only used for bridge virtual source calculation
        // Standard road noise should use RoadCnossos methodology
        return -99.0;
    }
}
