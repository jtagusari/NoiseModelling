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
 * Test for RoadAsjParameters class
 * Tests parameter validation and configuration for ASJ bridge virtual source calculation
 * 
 * @author NoiseModelling Team
 */
public class RoadAsjParametersTest {

    private RoadAsjParameters parameters;

    @BeforeEach
    void setUp() {
        // Create parameters with valid values
        parameters = new RoadAsjParameters(
            50.0, 60.0, 70.0, 40.0, 50.0, // speeds
            100.0, 50.0, 20.0, 10.0, 5.0, // flows
            1000, 20.0, "STANDARD" // frequency, temperature, surface
        );
    }

    @Test
    @DisplayName("Test constructor with valid parameters")
    void testConstructorValidParameters() {
        assertNotNull(parameters, "Parameters should be created successfully");
        assertEquals(100.0, parameters.getLvPerHour(), "Light vehicle flow should be set correctly");
        assertEquals(50.0, parameters.getMvPerHour(), "Medium vehicle flow should be set correctly");
        assertEquals(20.0, parameters.getHgvPerHour(), "Heavy vehicle flow should be set correctly");
        assertEquals(1000, parameters.getFrequency(), "Frequency should be set correctly");
        assertEquals(20.0, parameters.getTemperature(), "Temperature should be set correctly");
        assertEquals("STANDARD", parameters.getRoadSurface(), "Road surface should be set correctly");
    }

    @Test
    @DisplayName("Test constructor with negative flow values")
    void testConstructorNegativeFlows() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RoadAsjParameters(50.0, 60.0, 70.0, 40.0, 50.0, -10.0, 50.0, 20.0, 10.0, 5.0, 1000, 20.0, "STANDARD");
        }, "Should throw exception for negative light vehicle flow");

        assertThrows(IllegalArgumentException.class, () -> {
            new RoadAsjParameters(50.0, 60.0, 70.0, 40.0, 50.0, 100.0, -50.0, 20.0, 10.0, 5.0, 1000, 20.0, "STANDARD");
        }, "Should throw exception for negative medium vehicle flow");
    }

    @Test
    @DisplayName("Test constructor with negative speed values")
    void testConstructorNegativeSpeeds() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RoadAsjParameters(-50.0, 60.0, 70.0, 40.0, 50.0, 100.0, 50.0, 20.0, 10.0, 5.0, 1000, 20.0, "STANDARD");
        }, "Should throw exception for negative light vehicle speed");

        assertThrows(IllegalArgumentException.class, () -> {
            new RoadAsjParameters(50.0, -60.0, 70.0, 40.0, 50.0, 100.0, 50.0, 20.0, 10.0, 5.0, 1000, 20.0, "STANDARD");
        }, "Should throw exception for negative medium vehicle speed");
    }

    @Test
    @DisplayName("Test constructor with negative frequency")
    void testConstructorNegativeFrequency() {
        // Constructor should enforce minimum frequency of 0
        RoadAsjParameters negativeFreqParams = new RoadAsjParameters(
            50.0, 60.0, 70.0, 40.0, 50.0, // speeds
            100.0, 50.0, 20.0, 10.0, 5.0, // flows
            -100, 20.0, "STANDARD" // negative frequency
        );
        assertEquals(0, negativeFreqParams.getFrequency(), "Constructor should enforce minimum frequency of 0");
    }

    @Test
    @DisplayName("Test default constructor")
    void testDefaultConstructor() {
        RoadAsjParameters defaultParams = new RoadAsjParameters();
        assertNotNull(defaultParams, "Default constructor should work");
        assertEquals(0.0, defaultParams.getLvPerHour(), "Default light vehicle flow should be 0");
        assertEquals(false, defaultParams.hasBridge(), "Default should have no bridge");
    }

    @Test
    @DisplayName("Test speed getters with minimum speed enforcement")
    void testSpeedGettersMinimumSpeed() throws IOException {
        // Create parameters with low speeds
        RoadAsjParameters lowSpeedParams = new RoadAsjParameters(
            10.0, 15.0, 5.0, 8.0, 12.0, // very low speeds
            100.0, 50.0, 20.0, 10.0, 5.0, // flows
            1000, 20.0, "STANDARD" // frequency, temperature, surface
        );

        // All speeds should be enforced to minimum 20 km/h
        assertEquals(20.0, lowSpeedParams.getSpeedLv(), "Light vehicle speed should be minimum 20 km/h");
        assertEquals(20.0, lowSpeedParams.getSpeedMv(), "Medium vehicle speed should be minimum 20 km/h");
        assertEquals(20.0, lowSpeedParams.getSpeedHgv(), "Heavy vehicle speed should be minimum 20 km/h");
        assertEquals(20.0, lowSpeedParams.getSpeedWav(), "Light 2-wheel speed should be minimum 20 km/h");
        assertEquals(20.0, lowSpeedParams.getSpeedWbv(), "Heavy 2-wheel speed should be minimum 20 km/h");
    }

    @Test
    @DisplayName("Test flow setters with non-negative enforcement")
    void testFlowSettersNonNegative() {
        parameters.setLvPerHour(-10.0);
        parameters.setMvPerHour(-20.0);
        parameters.setHgvPerHour(-5.0);

        assertEquals(0.0, parameters.getLvPerHour(), "Negative light vehicle flow should be set to 0");
        assertEquals(0.0, parameters.getMvPerHour(), "Negative medium vehicle flow should be set to 0");
        assertEquals(0.0, parameters.getHgvPerHour(), "Negative heavy vehicle flow should be set to 0");
    }

    @Test
    @DisplayName("Test slope percentage limits")
    void testSlopePercentageLimits() {
        parameters.setSlopePercentage(15.0); // Above maximum
        assertEquals(12.0, parameters.getSlopePercentage(), "Slope should be limited to maximum 12%");

        parameters.setSlopePercentage(-15.0); // Below minimum
        assertEquals(-12.0, parameters.getSlopePercentage(), "Slope should be limited to minimum -12%");

        parameters.setSlopePercentage(5.0); // Within range
        assertEquals(5.0, parameters.getSlopePercentage(), "Slope within range should be preserved");
    }

    @Test
    @DisplayName("Test bridge parameter setters")
    void testBridgeParameters() {
        parameters.setHasBridge(true);
        parameters.setBridgeGirderType("STEEL_BOX");
        parameters.setBridgeSlabType("CONCRETE");
        parameters.setBridgeThickness(0.3);
        parameters.setBridgeLength(120.0);
        parameters.setBridgeWidth(15.0);

        assertTrue(parameters.hasBridge(), "Bridge flag should be set");
        assertEquals("STEEL_BOX", parameters.getBridgeGirderType(), "Bridge girder type should be set");
        assertEquals("CONCRETE", parameters.getBridgeSlabType(), "Bridge slab type should be set");
        assertEquals(0.3, parameters.getBridgeThickness(), "Bridge thickness should be set");
        assertEquals(120.0, parameters.getBridgeLength(), "Bridge length should be set");
        assertEquals(15.0, parameters.getBridgeWidth(), "Bridge width should be set");
    }

    @Test
    @DisplayName("Test bridge thickness minimum value")
    void testBridgeThicknessMinimum() {
        parameters.setBridgeThickness(0.05); // Below minimum
        assertEquals(0.1, parameters.getBridgeThickness(), "Bridge thickness should be minimum 0.1m");

        parameters.setBridgeThickness(-1.0); // Negative value
        assertEquals(0.1, parameters.getBridgeThickness(), "Negative bridge thickness should be set to minimum");
    }

    @Test
    @DisplayName("Test average speed calculation with minimum enforcement")
    void testAverageSpeedMinimum() {
        parameters.setAverageSpeed(15.0); // Below minimum
        assertEquals(20.0, parameters.getAverageSpeed(), "Average speed should be minimum 20 km/h");

        parameters.setAverageSpeed(80.0); // Above minimum
        assertEquals(80.0, parameters.getAverageSpeed(), "Average speed above minimum should be preserved");
    }

    @Test
    @DisplayName("Test total traffic flow calculation")
    void testTotalTrafficFlow() {
        double expectedTotal = parameters.getLvPerHour() + parameters.getMvPerHour() + 
                              parameters.getHgvPerHour() + parameters.getWavPerHour() + 
                              parameters.getWbvPerHour();
        assertEquals(expectedTotal, parameters.getTotalTrafficFlow(), 0.01, 
                    "Total traffic flow should sum all vehicle categories");
    }

    @Test
    @DisplayName("Test coefficient version setter")
    void testCoefficientVersion() {
        parameters.setCoefficientVersion(2);
        assertEquals(2, parameters.getCoefficientVersion(), "Coefficient version should be set");
    }

    @Test
    @DisplayName("Test way parameter")
    void testWayParameter() {
        parameters.setWay(2);
        assertEquals(2, parameters.getWay(), "Way parameter should be set");

        parameters.setWay(3);
        assertEquals(3, parameters.getWay(), "Way parameter should support bidirectional");
    }

    @Test
    @DisplayName("Test frequency setter")
    void testFrequencySetter() {
        parameters.setFrequency(500);
        assertEquals(500, parameters.getFrequency(), "Frequency should be set correctly");

        // Note: setFrequency doesn't validate, but constructor does
        parameters.setFrequency(-100);
        assertEquals(-100, parameters.getFrequency(), "setFrequency allows negative values directly");
    }

    @Test
    @DisplayName("Test temperature setter")
    void testTemperatureSetter() {
        parameters.setTemperature(25.5);
        assertEquals(25.5, parameters.getTemperature(), "Temperature should be set correctly");

        parameters.setTemperature(-10.0);
        assertEquals(-10.0, parameters.getTemperature(), "Negative temperature should be allowed");
    }

    @Test
    @DisplayName("Test road surface setter")
    void testRoadSurfaceSetter() {
        parameters.setRoadSurface("CONCRETE");
        assertEquals("CONCRETE", parameters.getRoadSurface(), "Road surface should be set correctly");

        parameters.setRoadSurface(null);
        assertNull(parameters.getRoadSurface(), "Road surface should accept null");
    }

    @Test
    @DisplayName("Test parameters with zero traffic flow")
    void testZeroTrafficFlow() {
        RoadAsjParameters zeroTrafficParams = new RoadAsjParameters(
            50.0, 60.0, 70.0, 40.0, 50.0, // speeds
            0.0, 0.0, 0.0, 0.0, 0.0, // zero flows
            1000, 20.0, "STANDARD" // frequency, temperature, surface
        );

        assertEquals(0.0, zeroTrafficParams.getTotalTrafficFlow(), "Total traffic flow should be 0");
        assertEquals(50.0, zeroTrafficParams.getAverageSpeed(), "Average speed should default to 50 when no traffic");
    }

    @Test
    @DisplayName("Test parameters for bridge virtual source calculation")
    void testBridgeVirtualSourceConfiguration() {
        parameters.setHasBridge(true);
        parameters.setBridgeGirderType("CONCRETE_BOX");
        parameters.setBridgeSlabType("CONCRETE");
        parameters.setMvPerHour(30.0);
        parameters.setHgvPerHour(15.0);

        assertTrue(parameters.hasBridge(), "Should have bridge for virtual source calculation");
        assertEquals("CONCRETE_BOX", parameters.getBridgeGirderType(), "Bridge girder type should be set for calculation");
        assertTrue(parameters.getMvPerHour() > 0, "Should have medium vehicle traffic for calculation");
        assertTrue(parameters.getHgvPerHour() > 0, "Should have heavy vehicle traffic for calculation");
    }

    @Test
    @DisplayName("Test all bridge types combinations")
    void testBridgeTypeCombinations() {
        String[] girderTypes = {"STEEL_BOX", "STEEL_PLATE", "CONCRETE_BOX", "CONCRETE_PLATE"};
        String[] slabTypes = {"STEEL", "CONCRETE"};

        for (String girderType : girderTypes) {
            parameters.setBridgeGirderType(girderType);
            assertEquals(girderType, parameters.getBridgeGirderType(), 
                        "Girder type should be set correctly: " + girderType);

            for (String slabType : slabTypes) {
                parameters.setBridgeSlabType(slabType);
                assertEquals(slabType, parameters.getBridgeSlabType(), 
                            "Slab type should be set correctly: " + slabType);
            }
        }
    }
}
