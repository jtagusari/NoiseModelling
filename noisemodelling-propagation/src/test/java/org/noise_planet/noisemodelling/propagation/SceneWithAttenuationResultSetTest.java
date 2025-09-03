package org.noise_planet.noisemodelling.propagation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SceneWithAttenuation that exercise the ResultSet-based
 * addSourceDb overload without requiring an H2GIS SpatialResultSet.
 *
 * These tests use small fake ResultSet/ResultSetMetaData implementations
 * that provide just enough behavior for the method under test.
 */
public class SceneWithAttenuationResultSetTest {

    /**
     * Test that addSourceDb reads orientation and GS fields from the provided
     * ResultSet and registers the source geometry and GS value in the scene.
     *
     * Setup: create a SceneWithAttenuation backed by default ProfileBuilder.
     * Provide a fake ResultSet that exposes columns: YAW, PITCH, ROLL, GS.
     * Action: call addSourceDb and validate that the returned PK list has
     * one element and that the scene maps (sourceGs and sourceGeometries)
     * were updated accordingly.
     */
    @Test
    public void testAddSourceDbWithResultSet() throws SQLException {
        ProfileBuilder pb = new ProfileBuilder();
        SceneWithAttenuation scene = new SceneWithAttenuation(pb);

        GeometryFactory gf = new GeometryFactory();
        Point p = gf.createPoint(new Coordinate(1, 2));

        // Fake metadata with columns: YAW, PITCH, ROLL, GS
        String[] columns = new String[]{"YAW", "PITCH", "ROLL", "GS"};
        FakeResultSetMetaData meta = new FakeResultSetMetaData(columns);
        // Prepare value maps used by the dynamic proxy
        final java.util.Map<Integer, Float> floatValues = new java.util.HashMap<>();
        final java.util.Map<Integer, Double> doubleValues = new java.util.HashMap<>();
        final java.util.Map<Integer, Boolean> booleanValues = new java.util.HashMap<>();
        final java.util.Map<Integer, Long> longValues = new java.util.HashMap<>();
        final java.util.Map<Integer, Integer> intValues = new java.util.HashMap<>();
        floatValues.put(1, 10.0f);
        floatValues.put(2, 5.0f);
        floatValues.put(3, 2.0f);
        doubleValues.put(4, 3.14);

        java.lang.reflect.InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getMetaData".equals(name)) return meta;
            if ("getFloat".equals(name)) return floatValues.getOrDefault((Integer) args[0], 0.0f);
            if ("getDouble".equals(name)) return doubleValues.getOrDefault((Integer) args[0], 0.0);
            if ("getBoolean".equals(name)) return booleanValues.getOrDefault((Integer) args[0], false);
            if ("getLong".equals(name)) return longValues.getOrDefault((Integer) args[0], 0L);
            if ("getInt".equals(name)) return intValues.getOrDefault((Integer) args[0], 0);
            return null;
        };

        java.sql.ResultSet rs = (java.sql.ResultSet) java.lang.reflect.Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class[]{java.sql.ResultSet.class},
            handler
        );

        List<Long> pks = scene.addSourceDb(100L, p, rs);

        assertNotNull(pks);
        assertEquals(1, pks.size(), "Expected a single registered PK when no bridges are configured");
        Long registeredPk = pks.get(0);
        assertTrue(scene.getSourceGeometries().contains(p), "Geometry should have been registered in the scene");
        assertEquals(3.14, scene.sourceGs.get(registeredPk), 1e-9, "GS value should be read from ResultSet and stored");
    }

    /**
     * Larger-scale test: register multiple sources by creating a new
     * ResultSet proxy per source with slightly different values. This
     * verifies bulk ingestion logic and that per-source GS values are
     * correctly stored for many rows.
     */
    @Test
    public void testAddMultipleSourcesDbWithResultSet() throws SQLException {
        ProfileBuilder pb = new ProfileBuilder();
        SceneWithAttenuation scene = new SceneWithAttenuation(pb);

        GeometryFactory gf = new GeometryFactory();

        // Use same minimal columns as the simple test
        String[] columns = new String[]{"YAW", "PITCH", "ROLL", "GS"};
        FakeResultSetMetaData meta = new FakeResultSetMetaData(columns);

        final int N = 10;
        for (int i = 0; i < N; i++) {
            Point p = gf.createPoint(new Coordinate(i, i + 1));

            final Map<Integer, Float> floatValues = new HashMap<>();
            final Map<Integer, Double> doubleValues = new HashMap<>();
            final Map<Integer, Boolean> booleanValues = new HashMap<>();
            final Map<Integer, Long> longValues = new HashMap<>();
            final Map<Integer, Integer> intValues = new HashMap<>();

            floatValues.put(1, 10.0f * i);
            floatValues.put(2, 5.0f * i);
            floatValues.put(3, 2.0f * i);
            doubleValues.put(4, i + 0.25);

            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("getMetaData".equals(name)) return meta;
                if ("getFloat".equals(name)) return floatValues.getOrDefault((Integer) args[0], 0.0f);
                if ("getDouble".equals(name)) return doubleValues.getOrDefault((Integer) args[0], 0.0);
                if ("getBoolean".equals(name)) return booleanValues.getOrDefault((Integer) args[0], false);
                if ("getLong".equals(name)) return longValues.getOrDefault((Integer) args[0], 0L);
                if ("getInt".equals(name)) return intValues.getOrDefault((Integer) args[0], 0);
                return null;
            };

            java.sql.ResultSet rs = (java.sql.ResultSet) Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(),
                    new Class[]{java.sql.ResultSet.class},
                    handler
            );

            List<Long> pks = scene.addSourceDb(100L + i, p, rs);
            assertNotNull(pks);
            assertEquals(1, pks.size(), "Each provided row should register a single PK");
            Long registeredPk = pks.get(0);
            assertTrue(scene.getSourceGeometries().contains(p), "Geometry should have been registered in the scene");
            assertEquals(i + 0.25, scene.sourceGs.get(registeredPk), 1e-9, "GS must match the provided value for each row");
        }
        // Final sanity: scene should contain N registered geometries
        assertEquals(N, scene.getSourcePks().size(), "Scene should contain N source primary keys");
        assertEquals(N, scene.getSourceCount(), "Scene should contain N geometries");
    }
}
