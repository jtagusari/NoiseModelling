package org.noise_planet.noisemodelling.pathfinder.path;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Iterator;

public class SceneTest {
    /**
     * Verify that the default constructor initializes a usable Scene.
     * Checks:
     * - profileBuilder is created
     * - maxSrcDist and maxRefDist match the class defaults
     * - horizontal diffraction computation is enabled by default
     */
    @Test
    public void testConstructorAndDefaults() {
        Scene scene = new Scene();
        assertNotNull(scene.profileBuilder);
        assertEquals(Scene.DEFAULT_MAX_PROPAGATION_DISTANCE, scene.getMaxSrcDist());
        assertEquals(Scene.DEFAULT_MAXIMUM_REF_DIST, scene.maxRefDist);
        assertTrue(scene.computeHorizontalDiffraction);
    }

    /**
     * Test adding a source and then clearing all sources.
     * Verifies:
     * - addSource returns a registered key and adds geometry to the list
     * - the internal sourcesPk list receives the registered key
     * - clearSources empties geometries and registered keys
     */
    @Test
    public void testAddAndClearSources() {
        Scene scene = new Scene(new ProfileBuilder());
        GeometryFactory gf = new GeometryFactory();
        Point p = gf.createPoint(new Coordinate(1,2));
        long registeredPk = scene.addSource(42L, p);
        assertEquals(1, scene.getSourceCount());
        // Scene stores a registered key in sourcesPk
        assertTrue(registeredPk == 42L);
        assertEquals(1, scene.getSourceCount());

        scene.clearSources();
        assertEquals(0, scene.getSourceCount());
    }

    /**
     * Test that adding sources with orientations registers orientations and
     * that the spatial index can be queried by an envelope around a source.
     * Also verifies that clearSources resets the index and clears orientations.
     */
    @Test
    public void testSourceOrientationAndIndexQuery() {
        Scene scene = new Scene();
        GeometryFactory gf = new GeometryFactory();
        Point p1 = gf.createPoint(new Coordinate(0,0));
        Point p2 = gf.createPoint(new Coordinate(100,100));

        // Add two sources with PK and orientation
        scene.addSource(1L, p1, new org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation(10,0,0));
        scene.addSource(2L, p2, new org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation(20,0,0));

        // Scene registers its own keys for sources; ensure two orientations were stored
        assertEquals(2, scene.getSourceOrientations().size());

        // Query index around p1 should return at least one entry
        org.locationtech.jts.geom.Envelope env = p1.getEnvelopeInternal();
        env.expandBy(1.0);
        Iterator<Integer> it = scene.getSourceQuery().query(env);
        assertTrue(it.hasNext());

        // clear sources resets index
        scene.clearSources();
        Iterator<Integer> it2 = scene.getSourceQuery().query(env);
        assertFalse(it2.hasNext());
        // and clears stored orientations
        assertEquals(0, scene.getSourceOrientations().size());
    }

    /**
     * Test receiver addition and several setter/getter behaviors.
     * Verifies:
     * - receivers list grows when adding receivers (with and without pk)
     * - reflexion order setter/getter
     * - toggling diffraction computation flags
     * - default ground attenuation setter
     */
    @Test
    public void testAddReceiversAndSetters() {
        Scene scene = new Scene();
        scene.addReceiver(1L, new Coordinate(0,0));
        scene.addReceiver(new Coordinate(1,1));
        assertEquals(2, scene.receivers.size());

        scene.setReflexionOrder(3);
        assertEquals(3, scene.getReflexionOrder());

        scene.setComputeHorizontalDiffraction(false);
        assertFalse(scene.computeHorizontalDiffraction);

        scene.setComputeVerticalDiffraction(true);
        assertTrue(scene.computeVerticalDiffraction);

        scene.setDefaultGroundAttenuation(0.5);
        assertEquals(0.5, scene.getDefaultGroundAttenuation());
    }
}
