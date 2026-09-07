package org.noise_planet.noisemodelling.emission.road.asj;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoadAsjFormulaTest {

    private static final double EPSILON = 1e-12;

    @Test
    void bridgeCoefficientLookupMatchesJsonValues() throws IOException {
        assertEquals(30.6, RoadAsj.getBridgeCoeff("STEEL_BOX_CONCRETE", "a", 1000), EPSILON);
        assertEquals(30.0, RoadAsj.getBridgeCoeff("STEEL_BOX_CONCRETE", "b", 1000), EPSILON);
    }

    @Test
    void calculateBridgeVirtualSourceMatchesEquation() throws IOException {
        double speed = 60.0;
        double expected = 30.6 + 30.0 * Math.log10(speed);

        assertEquals(expected,
                RoadAsj.calculateBridgeVirtualSourceLevelForFrequency("STEEL_BOX", "CONCRETE", 1000, speed),
                EPSILON);
    }

    @Test
    void evaluateBridgeVirtualSourceUsesOnlyMvAndHgvTraffic() throws IOException {
        RoadAsjParameters parameters = new RoadAsjParameters(
                10.0, 20.0, 20.0, 40.0, 50.0,
                900.0, 50.0, 20.0, 1000.0, 500.0,
                1000, 20.0, "STANDARD");
        parameters.setHasBridge(true);
        parameters.setBridgeGirderType("STEEL_BOX");
        parameters.setBridgeSlabType("CONCRETE");

        double mvPerHour = 50.0;
        double hgvPerHour = 20.0;
        double averageSpeed = (20.0 * mvPerHour + 20.0 * hgvPerHour) / (mvPerHour + hgvPerHour);
        double bridgeVirtualSourceLevel = 30.6 + 30.0 * Math.log10(averageSpeed);
        double expected = bridgeVirtualSourceLevel + 10.0 * Math.log10((mvPerHour + hgvPerHour) / (1000.0 * averageSpeed));

        assertEquals(expected, RoadAsj.evaluateBridgeVirtualSource(parameters), EPSILON);
    }
}
