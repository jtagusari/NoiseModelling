package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ground/soil areas table loading and envelope-based filtering.
 * Mirrors fetchCellBuildingTest and fetchCellTerrainTest styles.
 */
public class fetchCellGroundTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(fetchCellGroundTest.class);
    private Connection connection;

    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(
                fetchCellGroundTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void loadTestSoilAreas() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS SOIL_AREAS");
            st.execute("CREATE TABLE SOIL_AREAS (PK INT PRIMARY KEY, THE_GEOM GEOMETRY, ALPHA_OVERALL DOUBLE, GROUND_TYPE VARCHAR, ROUGHNESS DOUBLE)");

            // Polygons inside [0,100] region
            st.execute("INSERT INTO SOIL_AREAS VALUES (1, ST_GeomFromText('POLYGON ((0 0, 40 0, 40 40, 0 40, 0 0))'), 0.3, 'GRASS', 0.05)");
            st.execute("INSERT INTO SOIL_AREAS VALUES (2, ST_GeomFromText('POLYGON ((40 40, 80 40, 80 80, 40 80, 40 40))'), 0.1, 'CONCRETE', 0.01)");
            st.execute("INSERT INTO SOIL_AREAS VALUES (3, ST_GeomFromText('POLYGON ((20 60, 60 60, 60 90, 20 90, 20 60))'), 0.5, 'SOIL', 0.2)");

            // Area outside
            st.execute("INSERT INTO SOIL_AREAS VALUES (4, ST_GeomFromText('POLYGON ((200 200, 220 200, 220 220, 200 220, 200 200))'), 0.05, 'WATER', 0.0)");
        }
    }

    private String envelopeToWKT(Envelope env) {
        return String.format("POLYGON ((%f %f, %f %f, %f %f, %f %f, %f %f))",
                env.getMinX(), env.getMinY(),
                env.getMaxX(), env.getMinY(),
                env.getMaxX(), env.getMaxY(),
                env.getMinX(), env.getMaxY(),
                env.getMinX(), env.getMinY());
    }

    @Test
    public void testfetchCellGroundBasic() throws Exception {
        loadTestSoilAreas();

        LOGGER.info("========================================");
        LOGGER.info("testfetchCellGroundBasic: Soil Areas Loading Test");
        LOGGER.info("========================================");

        try (Statement st = connection.createStatement()) {
            int count = 0;
            try (ResultSet rs = st.executeQuery("SELECT PK, THE_GEOM, ALPHA_OVERALL, GROUND_TYPE, ROUGHNESS FROM SOIL_AREAS ORDER BY PK")) {
                while (rs.next()) {
                    count++;
                    int pk = rs.getInt("PK");
                    Geometry geom = (Geometry) rs.getObject("THE_GEOM");
                    double alpha = rs.getDouble("ALPHA_OVERALL");
                    String gtype = rs.getString("GROUND_TYPE");
                    double rough = rs.getDouble("ROUGHNESS");
                    LOGGER.info(String.format("  SoilArea %d: alpha=%.2f, type=%s, rough=%.3f, centroid=(%.2f,%.2f)", pk, alpha, gtype, rough, geom.getCentroid().getX(), geom.getCentroid().getY()));
                }
            }

            LOGGER.info(String.format("  ✓ Total soil areas: %d", count));
            assertEquals(4, count, "Should have 4 soil areas");

            Envelope mainEnvelope = new Envelope(0.0, 100.0, 0.0, 100.0);
            Envelope expanded = new Envelope(mainEnvelope);
            double prop = 100.0;
            double refl = 50.0;
            expanded.expandBy(prop + 2 * refl);

            LOGGER.info(String.format("Expanded envelope: [%.0f,%.0f] x [%.0f,%.0f]", expanded.getMinX(), expanded.getMaxX(), expanded.getMinY(), expanded.getMaxY()));

            List<Integer> pks = new ArrayList<>();
            String wkt = envelopeToWKT(expanded);
            try (ResultSet rs = st.executeQuery("SELECT PK, THE_GEOM, ALPHA_OVERALL, GROUND_TYPE FROM SOIL_AREAS WHERE THE_GEOM && ST_GeomFromText('" + wkt + "') ORDER BY PK")) {
                while (rs.next()) {
                    int pk = rs.getInt("PK");
                    Geometry g = (Geometry) rs.getObject("THE_GEOM");
                    double a = rs.getDouble("ALPHA_OVERALL");
                    String gt = rs.getString("GROUND_TYPE");
                    pks.add(pk);
                    LOGGER.info(String.format("    └─ Soil PK=%d, centroid=(%.2f,%.2f), alpha=%.2f, type=%s", pk, g.getCentroid().getX(), g.getCentroid().getY(), a, gt));
                    // check alpha bounds
                    assertTrue(a >= 0.0 && a <= 1.0, "Alpha must be in [0,1]");
                }
            }

            assertFalse(pks.isEmpty(), "Should fetch at least one soil area within expanded envelope");
            // All areas created should be within expanded envelope
            assertTrue(pks.contains(1));
            assertTrue(pks.contains(2));
            assertTrue(pks.contains(3));
            assertTrue(pks.contains(4));
        }
    }
}
