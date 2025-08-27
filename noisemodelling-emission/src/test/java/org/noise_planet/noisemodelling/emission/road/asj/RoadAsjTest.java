/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.road.asj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;

/**
 * Test for RoadAsj class
 * Tests the ASJ (Acoustical Society of Japan) methodology for bridge virtual source calculation
 * 
 * @author NoiseModelling Team
 */
public class RoadAsjTest {

    private RoadAsjParameters roadAsjParameters;

    @BeforeEach
    void setUp() {
        // Standard test parameters for bridge virtual source calculation
        roadAsjParameters = new RoadAsjParameters(
            50.0, 60.0, 70.0, 40.0, 50.0, // speeds (lv, mv, hgv, wav, wbv)
            100.0, 50.0, 20.0, 10.0, 5.0, // flows per hour
            1000, 20.0, "STANDARD" // frequency, temperature, road surface
        );
        
        // Configure bridge parameters for testing
        roadAsjParameters.setHasBridge(true);
        roadAsjParameters.setBridgeGirderType("STEEL_BOX");
        roadAsjParameters.setBridgeSlabType("CONCRETE");
        roadAsjParameters.setBridgeThickness(0.5);
        roadAsjParameters.setBridgeLength(100.0);
        roadAsjParameters.setBridgeWidth(12.0);
    }

    @Test
    @DisplayName("Test getBridgeCoeff method with valid bridge type and frequency")
    void testGetBridgeCoeff() throws IOException {
        // Test steel box with concrete slab at 1000Hz
        Double aCoeff = RoadAsj.getBridgeCoeff("STEEL_BOX_CONCRETE", "a", 1000);
        Double bCoeff = RoadAsj.getBridgeCoeff("STEEL_BOX_CONCRETE", "b", 1000);
        
        assertNotNull(aCoeff, "A coefficient should not be null");
        assertNotNull(bCoeff, "B coefficient should not be null");
        
        // Expected values from JSON: a=30.6, b=30.0 for 1000Hz
        assertEquals(30.6, aCoeff, 0.1, "A coefficient should match expected value");
        assertEquals(30.0, bCoeff, 0.1, "B coefficient should match expected value");
    }

    @Test
    @DisplayName("Test getBridgeCoeff with invalid bridge type")
    void testGetBridgeCoeffInvalidType() {
        // Test with non-existent bridge type
        assertThrows(IOException.class, () -> {
            RoadAsj.getBridgeCoeff("INVALID_BRIDGE_TYPE", "a", 1000);
        }, "Should throw IOException for invalid bridge type");
    }

    @Test
    @DisplayName("Test frequency index mapping")
    void testFrequencyIndex() throws IOException {
        // Test different frequencies
        Double coeff63 = RoadAsj.getBridgeCoeff("STEEL_BOX_CONCRETE", "a", 63);
        Double coeff125 = RoadAsj.getBridgeCoeff("STEEL_BOX_CONCRETE", "a", 125);
        Double coeff8000 = RoadAsj.getBridgeCoeff("STEEL_BOX_CONCRETE", "a", 8000);
        
        assertNotNull(coeff63, "63Hz coefficient should not be null");
        assertNotNull(coeff125, "125Hz coefficient should not be null");
        assertNotNull(coeff8000, "8000Hz coefficient should not be null");
        
        // From JSON: 63Hz=36.2, 125Hz=34.1, 8000Hz=-64.0
        assertEquals(36.2, coeff63, 0.1, "63Hz coefficient should match expected value");
        assertEquals(34.1, coeff125, 0.1, "125Hz coefficient should match expected value");
        assertEquals(-64.0, coeff8000, 0.1, "8000Hz coefficient should match expected value");
    }

    @Test
    @DisplayName("Test getBridgeCoefficients method")
    void testGetBridgeCoefficients() throws IOException {
        RoadAsj.BridgeCoefficients coeffs = RoadAsj.getBridgeCoefficients("STEEL_BOX", "CONCRETE", 1000);
        
        assertNotNull(coeffs, "Bridge coefficients should not be null");
        assertEquals(30.6, coeffs.a, 0.1, "A coefficient should match expected value");
        assertEquals(30.0, coeffs.b, 0.1, "B coefficient should match expected value");
    }

    @Test
    @DisplayName("Test getBridgeNoiseLvl calculation")
    void testGetBridgeNoiseLvl() {
        double a = 30.6;
        double b = 30.0;
        double speed = 60.0; // km/h
        
        double noiseLevel = RoadAsj.getBridgeNoiseLvl(a, b, speed);
        
        // Expected: 30.6 + 30.0 * log10(60) = 30.6 + 30.0 * 1.778 = 30.6 + 53.34 = 83.94
        double expected = a + b * Math.log10(speed);
        assertEquals(expected, noiseLevel, 0.01, "Noise level calculation should be correct");
        assertEquals(83.94, noiseLevel, 0.1, "Noise level should be approximately 83.94 dB");
    }

    @Test
    @DisplayName("Test calculateBridgeVirtualSourceLevelForFrequency")
    void testCalculateBridgeVirtualSourceLevelForFrequency() throws IOException {
        double speed = 60.0;
        double result = RoadAsj.calculateBridgeVirtualSourceLevelForFrequency(
            "STEEL_BOX", "CONCRETE", 1000, speed);
        
        assertTrue(result > 0, "Result should be positive");
        assertEquals(83.94, result, 0.1, "Result should match expected calculation");
    }

    @Test
    @DisplayName("Test calculateBridgeVirtualSourceLevelForFrequency with zero speed")
    void testCalculateBridgeVirtualSourceLevelForFrequencyZeroSpeed() throws IOException {
        double result = RoadAsj.calculateBridgeVirtualSourceLevelForFrequency(
            "STEEL_BOX", "CONCRETE", 1000, 0.0);
        
        assertEquals(-99.0, result, "Zero speed should return silent source");
    }

    @Test
    @DisplayName("Test evaluateBridgeVirtualSource with bridge")
    void testEvaluateBridgeVirtualSource() throws IOException {
        double result = RoadAsj.evaluateBridgeVirtualSource(roadAsjParameters);
        
        assertTrue(result > -99.0, "Result should not be silent when bridge is present");
        assertTrue(result > 0, "Result should be positive for normal traffic conditions");
    }

    @Test
    @DisplayName("Test evaluateBridgeVirtualSource without bridge")
    void testEvaluateBridgeVirtualSourceNoBridge() throws IOException {
        roadAsjParameters.setHasBridge(false);
        
        double result = RoadAsj.evaluateBridgeVirtualSource(roadAsjParameters);
        
        assertEquals(-99.0, result, "No bridge should return silent source");
    }

    @Test
    @DisplayName("Test evaluateBridgeVirtualSource with no MV/HGV traffic")
    void testEvaluateBridgeVirtualSourceNoMvHgv() throws IOException {
        // Set MV and HGV flow to zero
        roadAsjParameters.setMvPerHour(0.0);
        roadAsjParameters.setHgvPerHour(0.0);
        
        double result = RoadAsj.evaluateBridgeVirtualSource(roadAsjParameters);
        
        assertEquals(-99.0, result, "No MV/HGV traffic should return silent source");
    }

    @Test
    @DisplayName("Test evaluate method returns silent for standard road")
    void testEvaluateStandardRoad() throws IOException {
        double result = RoadAsj.evaluate(roadAsjParameters);
        
        assertEquals(-99.0, result, "ASJ should not be used for standard road noise calculation");
    }

    @Test
    @DisplayName("Test different bridge types")
    void testDifferentBridgeTypes() throws IOException {
        String[] girderTypes = {"STEEL_BOX", "STEEL_PLATE", "CONCRETE_BOX", "CONCRETE_PLATE"};
        String[] slabTypes = {"STEEL", "CONCRETE"};
        
        for (String girderType : girderTypes) {
            for (String slabType : slabTypes) {
                try {
                    RoadAsj.BridgeCoefficients coeffs = RoadAsj.getBridgeCoefficients(girderType, slabType, 1000);
                    assertNotNull(coeffs, "Coefficients should not be null for " + girderType + "_" + slabType);
                    assertTrue(coeffs.b > 0, "B coefficient should be positive for " + girderType + "_" + slabType);
                } catch (IOException e) {
                    // Some combinations might not exist in the JSON, which is acceptable
                    assertTrue(e.getMessage().contains("doesn't exist"), 
                        "Exception should indicate missing bridge type");
                }
            }
        }
    }

    @Test
    @DisplayName("Test all octave band frequencies")
    void testAllOctaveBandFrequencies() throws IOException {
        int[] frequencies = {63, 125, 250, 500, 1000, 2000, 4000, 8000};
        
        for (int freq : frequencies) {
            Double aCoeff = RoadAsj.getBridgeCoeff("STEEL_BOX_CONCRETE", "a", freq);
            Double bCoeff = RoadAsj.getBridgeCoeff("STEEL_BOX_CONCRETE", "b", freq);
            
            assertNotNull(aCoeff, "A coefficient should not be null for " + freq + "Hz");
            assertNotNull(bCoeff, "B coefficient should not be null for " + freq + "Hz");
            assertEquals(30.0, bCoeff, 0.1, "B coefficient should be 30.0 for all frequencies");
        }
    }

    @Test
    @DisplayName("Test BridgeCoefficients class")
    void testBridgeCoefficientsClass() {
        RoadAsj.BridgeCoefficients coeffs = new RoadAsj.BridgeCoefficients(25.5, 28.3);
        
        assertEquals(25.5, coeffs.a, "A coefficient should be set correctly");
        assertEquals(28.3, coeffs.b, "B coefficient should be set correctly");
    }

    @Test
    @DisplayName("Test bridge virtual source with different speed values")
    void testBridgeVirtualSourceDifferentSpeeds() throws IOException {
        double[] speeds = {20.0, 40.0, 60.0, 80.0, 100.0};
        
        for (double speed : speeds) {
            double result = RoadAsj.calculateBridgeVirtualSourceLevelForFrequency(
                "STEEL_BOX", "CONCRETE", 1000, speed);
            
            assertTrue(result > 0, "Result should be positive for speed " + speed);
            
            // Higher speeds should generally produce higher noise levels
            if (speed > 20.0) {
                double lowerSpeedResult = RoadAsj.calculateBridgeVirtualSourceLevelForFrequency(
                    "STEEL_BOX", "CONCRETE", 1000, 20.0);
                assertTrue(result > lowerSpeedResult, 
                    "Higher speed should produce higher noise level");
            }
        }
    }
}
