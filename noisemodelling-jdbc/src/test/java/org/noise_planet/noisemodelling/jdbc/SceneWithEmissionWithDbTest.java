package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.SpatialResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;
import org.noise_planet.noisemodelling.jdbc.input.SourceEmission;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig.FrequencyBand;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for SceneWithEmission.addSourceDb() method.
 * Tests emission parsing from database for CNOSSOS road sources.
 */
public class SceneWithEmissionWithDbTest {
    private Connection connection;

    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(
            H2GISDBFactory.createSpatialDataBase("SceneWithEmissionTestDB", true, "")
        );
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * Test addSourceDb() with INPUT_MODE_LW_DEN: frequency-specific sound power levels
     * for Day, Evening, Night periods.
     * 
     * Creates a table with D, E, N period columns containing per-frequency LW values (dB),
     * inserts a LineString source, and verifies that addSourceDb() correctly parses
     * and registers all emissions.
     */
    @Test
    public void testAddSourceDbWithLwDenEmission() throws Exception {
        try (Statement st = connection.createStatement()) {
            // Create sources table with DEN period columns (8 frequencies: 63-8000 Hz octave)
            st.execute("CREATE TABLE LW_DEN_SOURCES (" +
                    "PK BIGINT PRIMARY KEY," +
                    "THE_GEOM GEOMETRY," +
                    "GS DOUBLE," +
                    "DIR_ID INTEGER," +
                    "YAW FLOAT, PITCH FLOAT, ROLL FLOAT," +
                    "HEIGHT_TYPE VARCHAR(10)," +
                    "BRIDGE_PK BIGINT," +
                    "EMISSION_TYPE VARCHAR(20)," +
                    // Day period (D): 8 frequencies
                    "HZ63_D DOUBLE, HZ125_D DOUBLE, HZ250_D DOUBLE, HZ500_D DOUBLE," +
                    "HZ1000_D DOUBLE, HZ2000_D DOUBLE, HZ4000_D DOUBLE, HZ8000_D DOUBLE," +
                    // Evening period (E): 8 frequencies
                    "HZ63_E DOUBLE, HZ125_E DOUBLE, HZ250_E DOUBLE, HZ500_E DOUBLE," +
                    "HZ1000_E DOUBLE, HZ2000_E DOUBLE, HZ4000_E DOUBLE, HZ8000_E DOUBLE," +
                    // Night period (N): 8 frequencies
                    "HZ63_N DOUBLE, HZ125_N DOUBLE, HZ250_N DOUBLE, HZ500_N DOUBLE," +
                    "HZ1000_N DOUBLE, HZ2000_N DOUBLE, HZ4000_N DOUBLE, HZ8000_N DOUBLE" +
                    ")");

            // Insert a test road source with LW values per frequency and period
            st.execute("INSERT INTO LW_DEN_SOURCES (" +
                    "PK, THE_GEOM, GS, DIR_ID, YAW, PITCH, ROLL, HEIGHT_TYPE, BRIDGE_PK, EMISSION_TYPE," +
                    "HZ63_D, HZ125_D, HZ250_D, HZ500_D, HZ1000_D, HZ2000_D, HZ4000_D, HZ8000_D," +
                    "HZ63_E, HZ125_E, HZ250_E, HZ500_E, HZ1000_E, HZ2000_E, HZ4000_E, HZ8000_E," +
                    "HZ63_N, HZ125_N, HZ250_N, HZ500_N, HZ1000_N, HZ2000_N, HZ4000_N, HZ8000_N" +
                    ") VALUES (" +
                    "1, " +
                    "ST_GeomFromText('LINESTRING(0 0, 10 0)'), " +
                    "0.7, 2, 0.0, 0.0, 0.0, 'RELATIVE', NULL, 'ROAD'," +
                    // Day: realistic road noise levels across frequencies
                    "70.0, 78.0, 82.0, 85.0, 88.0, 85.0, 80.0, 75.0," +
                    // Evening: slightly lower
                    "68.0, 76.0, 80.0, 83.0, 86.0, 83.0, 78.0, 73.0," +
                    // Night: lowest
                    "65.0, 73.0, 77.0, 80.0, 83.0, 80.0, 75.0, 70.0" +
                    ")");
        }

        // Setup SceneWithEmission with OCTAVE frequency configuration
        ProfileBuilder profileBuilder = new ProfileBuilder(new FrequencyConfig(FrequencyBand.OCTAVE));
        SceneDatabaseInputSettings settings = new SceneDatabaseInputSettings.Builder()
                .setInputMode(SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN)
                .build();
        SceneWithEmission scene = new SceneWithEmission(profileBuilder, settings);
        // Note: frequencyFieldPrepend is public, but already defaults to "HZ"

        // Load and parse the source
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM LW_DEN_SOURCES WHERE PK=1");
             SpatialResultSet rs = ps.executeQuery().unwrap(SpatialResultSet.class)) {
            assertTrue(rs.next(), "Should retrieve source row");
            long pk = rs.getLong("PK");
            Geometry geom = rs.getGeometry("THE_GEOM");

            // Call addSourceDb which should parse emissions
            scene.addSourceDb(pk, geom, rs);

            // Verify source was registered
            assertEquals(1, scene.bridgeRelationships.size(), "One source should be registered");
            assertEquals(0.7, scene.sourceGs.get(pk), "GS value should be 0.7");
            assertEquals(2, scene.sourceEmissionAttenuation.get(pk), "Emission attenuation ID should be 2");

            // Verify emissions were parsed and registered
            var emissionsMap = scene.getSourceEmissionsMap();
            assertNotNull(emissionsMap.get(pk), "Emissions should be registered for source");
            assertTrue(emissionsMap.get(pk).size() > 0, "At least one emission should be registered");

            // Check that we have 3 emissions (D, E, N periods)
            assertEquals(3, emissionsMap.get(pk).size(), 
                    "Should have D, E, N period emissions");
        }
    }

    /**
     * Test addSourceDb() with multiple LineString sources to verify all are
     * correctly parsed with their respective emissions.
     */
    @Test
    public void testAddMultipleSourcesDbWithEmissions() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE LW_DEN_SOURCES (" +
                    "PK BIGINT PRIMARY KEY," +
                    "THE_GEOM GEOMETRY," +
                    "GS DOUBLE," +
                    "DIR_ID INTEGER," +
                    "YAW FLOAT, PITCH FLOAT, ROLL FLOAT," +
                    "HEIGHT_TYPE VARCHAR(10)," +
                    "BRIDGE_PK BIGINT," +
                    "EMISSION_TYPE VARCHAR(20)," +
                    "HZ63_D DOUBLE, HZ125_D DOUBLE, HZ250_D DOUBLE, HZ500_D DOUBLE," +
                    "HZ1000_D DOUBLE, HZ2000_D DOUBLE, HZ4000_D DOUBLE, HZ8000_D DOUBLE," +
                    "HZ63_E DOUBLE, HZ125_E DOUBLE, HZ250_E DOUBLE, HZ500_E DOUBLE," +
                    "HZ1000_E DOUBLE, HZ2000_E DOUBLE, HZ4000_E DOUBLE, HZ8000_E DOUBLE," +
                    "HZ63_N DOUBLE, HZ125_N DOUBLE, HZ250_N DOUBLE, HZ500_N DOUBLE," +
                    "HZ1000_N DOUBLE, HZ2000_N DOUBLE, HZ4000_N DOUBLE, HZ8000_N DOUBLE" +
                    ")");

            // Insert two road sources with different emission profiles
            st.execute("INSERT INTO LW_DEN_SOURCES (" +
                    "PK, THE_GEOM, GS, DIR_ID, YAW, PITCH, ROLL, HEIGHT_TYPE, BRIDGE_PK, EMISSION_TYPE," +
                    "HZ63_D, HZ125_D, HZ250_D, HZ500_D, HZ1000_D, HZ2000_D, HZ4000_D, HZ8000_D," +
                    "HZ63_E, HZ125_E, HZ250_E, HZ500_E, HZ1000_E, HZ2000_E, HZ4000_E, HZ8000_E," +
                    "HZ63_N, HZ125_N, HZ250_N, HZ500_N, HZ1000_N, HZ2000_N, HZ4000_N, HZ8000_N" +
                    ") VALUES (" +
                    "1, ST_GeomFromText('LINESTRING(0 0, 10 0)'), 0.7, 2, 0.0, 0.0, 0.0, 'RELATIVE', NULL, 'ROAD'," +
                    "70, 78, 82, 85, 88, 85, 80, 75," +
                    "68, 76, 80, 83, 86, 83, 78, 73," +
                    "65, 73, 77, 80, 83, 80, 75, 70" +
                    ")");
            
            st.execute("INSERT INTO LW_DEN_SOURCES (" +
                    "PK, THE_GEOM, GS, DIR_ID, YAW, PITCH, ROLL, HEIGHT_TYPE, BRIDGE_PK, EMISSION_TYPE," +
                    "HZ63_D, HZ125_D, HZ250_D, HZ500_D, HZ1000_D, HZ2000_D, HZ4000_D, HZ8000_D," +
                    "HZ63_E, HZ125_E, HZ250_E, HZ500_E, HZ1000_E, HZ2000_E, HZ4000_E, HZ8000_E," +
                    "HZ63_N, HZ125_N, HZ250_N, HZ500_N, HZ1000_N, HZ2000_N, HZ4000_N, HZ8000_N" +
                    ") VALUES (" +
                    "2, ST_GeomFromText('LINESTRING(20 0, 30 0)'), 1.2, 3, 10.0, 0.0, 0.0, 'ABSOLUTE', NULL, 'ROAD'," +
                    "75, 83, 87, 90, 93, 90, 85, 80," +
                    "73, 81, 85, 88, 91, 88, 83, 78," +
                    "70, 78, 82, 85, 88, 85, 80, 75" +
                    ")");
        }

        ProfileBuilder profileBuilder = new ProfileBuilder(new FrequencyConfig(FrequencyBand.OCTAVE));
        SceneDatabaseInputSettings settings = new SceneDatabaseInputSettings();
        settings.setInputMode("INPUT_MODE_LW_DEN");
        SceneWithEmission scene = new SceneWithEmission(profileBuilder, settings);

        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM LW_DEN_SOURCES ORDER BY PK");
             SpatialResultSet rs = ps.executeQuery().unwrap(SpatialResultSet.class)) {
            
            while (rs.next()) {
                long pk = rs.getLong("PK");
                Geometry geom = rs.getGeometry("THE_GEOM");
                scene.addSourceDb(pk, geom, rs);
            }

            // Verify both sources were registered
            assertEquals(2, scene.bridgeRelationships.size(), "Two sources should be registered");
            
            // Check emissions for both sources
            var emissionsMap = scene.getSourceEmissionsMap();
            for (long pk = 1; pk <= 2; pk++) {
                assertNotNull(emissionsMap.get(pk), "Emissions should be registered for source " + pk);
                assertEquals(3, emissionsMap.get(pk).size(), 
                        "Should have 3 emissions (D, E, N) for source " + pk);
            }
        }
    }

    /**
     * Test that emissions are properly registered as Watts (not dB).
     * Verify that the SourceEmission objects contain power values, not dB values.
     */
    @Test
    public void testEmissionValuesAreInWatts() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE LW_DEN_SOURCES (" +
                    "PK BIGINT PRIMARY KEY," +
                    "THE_GEOM GEOMETRY," +
                    "GS DOUBLE," +
                    "DIR_ID INTEGER," +
                    "YAW FLOAT, PITCH FLOAT, ROLL FLOAT," +
                    "HEIGHT_TYPE VARCHAR(10)," +
                    "BRIDGE_PK BIGINT," +
                    "EMISSION_TYPE VARCHAR(20)," +
                    "HZ63_D DOUBLE, HZ125_D DOUBLE, HZ250_D DOUBLE, HZ500_D DOUBLE," +
                    "HZ1000_D DOUBLE, HZ2000_D DOUBLE, HZ4000_D DOUBLE, HZ8000_D DOUBLE," +
                    "HZ63_E DOUBLE, HZ125_E DOUBLE, HZ250_E DOUBLE, HZ500_E DOUBLE," +
                    "HZ1000_E DOUBLE, HZ2000_E DOUBLE, HZ4000_E DOUBLE, HZ8000_E DOUBLE," +
                    "HZ63_N DOUBLE, HZ125_N DOUBLE, HZ250_N DOUBLE, HZ500_N DOUBLE," +
                    "HZ1000_N DOUBLE, HZ2000_N DOUBLE, HZ4000_N DOUBLE, HZ8000_N DOUBLE" +
                    ")");

            // Insert source with known dB values (90 dB per frequency, all periods)
            st.execute("INSERT INTO LW_DEN_SOURCES (" +
                    "PK, THE_GEOM, GS, DIR_ID, YAW, PITCH, ROLL, HEIGHT_TYPE, BRIDGE_PK, EMISSION_TYPE," +
                    "HZ63_D, HZ125_D, HZ250_D, HZ500_D, HZ1000_D, HZ2000_D, HZ4000_D, HZ8000_D," +
                    "HZ63_E, HZ125_E, HZ250_E, HZ500_E, HZ1000_E, HZ2000_E, HZ4000_E, HZ8000_E," +
                    "HZ63_N, HZ125_N, HZ250_N, HZ500_N, HZ1000_N, HZ2000_N, HZ4000_N, HZ8000_N" +
                    ") VALUES (" +
                    "1, ST_GeomFromText('LINESTRING(0 0, 10 0)'), 0.7, 2, 0.0, 0.0, 0.0, 'RELATIVE', NULL, 'ROAD'," +
                    "90, 90, 90, 90, 90, 90, 90, 90," +
                    "90, 90, 90, 90, 90, 90, 90, 90," +
                    "90, 90, 90, 90, 90, 90, 90, 90" +
                    ")");
        }

        ProfileBuilder profileBuilder = new ProfileBuilder(new FrequencyConfig(FrequencyBand.OCTAVE));
        SceneDatabaseInputSettings settings = new SceneDatabaseInputSettings();
        settings.setInputMode("INPUT_MODE_LW_DEN");
        SceneWithEmission scene = new SceneWithEmission(profileBuilder, settings);

        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM LW_DEN_SOURCES WHERE PK=1");
             SpatialResultSet rs = ps.executeQuery().unwrap(SpatialResultSet.class)) {
            
            assertTrue(rs.next());
            long pk = rs.getLong("PK");
            Geometry geom = rs.getGeometry("THE_GEOM");
            scene.addSourceDb(pk, geom, rs);

            // Get the registered emissions
            var emissionsMap = scene.getSourceEmissionsMap();
            var emissions = emissionsMap.get(pk);
            assertNotNull(emissions, "Emissions should be registered");
            assertTrue(emissions.size() > 0, "At least one emission should exist");

            // Check that the first emission (Day period) has emission in Watts
            SourceEmission dayEmission = emissions.get(0);
            double[] emissionPowers = dayEmission.getEmissionInWatts();
            assertNotNull(emissionPowers, "Emission array should not be null");
            assertEquals(8, emissionPowers.length, "Should have 8 frequency bands");

            // For 90 dB input, check that values are in Watts (not dB)
            // 90 dB = 10^(90/10) W = 10^9 W (approximately)
            for (double power : emissionPowers) {
                assertTrue(power > 100, "Power values should be large Watts (not dB): " + power);
            }
        }
    }
}
