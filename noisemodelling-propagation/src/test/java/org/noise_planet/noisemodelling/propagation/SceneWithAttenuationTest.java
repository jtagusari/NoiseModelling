package org.noise_planet.noisemodelling.propagation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class SceneWithAttenuationTest {


    @Test
    public void testAddSourceWithGsAndClear() {
        SceneWithAttenuation scene = new SceneWithAttenuation(new ProfileBuilder());
        GeometryFactory gf = new GeometryFactory();
        Point p = gf.createPoint(new Coordinate(10, 20));

        long pk = scene.addSource(123L, p, null, 2.5);
        assertEquals(1, scene.getSourceCount());
        assertEquals(1, scene.getSourcePks().size());
        assertEquals(2.5, scene.sourceGs.get(pk));

        // getSourceGs by index
        double gsByIndex = scene.getSourceGs(0);
        assertEquals(2.5, gsByIndex);

        // Clear and ensure maps are empty
        scene.clearSources();
        assertEquals(0, scene.getSourceCount());
        assertEquals(0, scene.getSourcePks().size());
        assertEquals(0, scene.sourceGs.size());
    }

    @Test
    public void testOmnidirectionalAndAttenuationDefault() {
        SceneWithAttenuation scene = new SceneWithAttenuation(new ProfileBuilder());
        GeometryFactory gf = new GeometryFactory();
        Point p = gf.createPoint(new Coordinate(0,0));
        scene.addSource(5L, p, null, 0.0);

        // No directivity set -> omnidirectional
        assertTrue(scene.isOmnidirectional(0));

        // getSourceAttenuation should return zero array of requested length when no directivity found
        double[] freqs = new double[]{100.0, 200.0, 400.0};
        double[] atten = scene.getSourceAttenuation(0, freqs, 0.0, 0.0);
        assertNotNull(atten);
        assertEquals(freqs.length, atten.length);
        for(double v : atten) {
            assertEquals(0.0, v);
        }
    }
}
