package org.noise_planet.noisemodelling.emission.road;

import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossos;
import org.noise_planet.noisemodelling.emission.utils.Utils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RoadCnossosFormulaTest {

    private static final double EPSILON = 1e-12;

    @Test
    void getNoiseLvlMatchesLogarithmicEquation() {
        double base = 30.6;
        double adj = 30.0;
        double speed = 60.0;
        double speedBase = 1.0;

        double expected = base + adj * Math.log10(speed / speedBase);

        assertEquals(expected, RoadCnossos.getNoiseLvl(base, adj, speed, speedBase), EPSILON);
    }

    @Test
    void vperHourToNoiseLevelMatchesTrafficFlowEquation() throws IOException {
        double sourceLevel = 80.0;
        double flowPerHour = 1000.0;
        double speed = 100.0;

        double expected = sourceLevel + 10.0 * Math.log10(flowPerHour / (1000.0 * speed));

        assertEquals(expected, Utils.Vperhour2NoiseLevel(sourceLevel, flowPerHour, speed), EPSILON);
    }
}
