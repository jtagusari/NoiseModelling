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
 * Test for DEM (terrain) point loading and envelope-based filtering.
 *
 * This mirrors the style of FetchCellBuildingsTest but focuses on DEM points
 * that represent ground elevation samples used to seed the ProfileBuilder.
 */
public class FetchCellDemTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(FetchCellDemTest.class);
    private Connection connection;

    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(
                FetchCellDemTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void loadTestDemPoints() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS DEM_POINTS");
            st.execute("CREATE TABLE DEM_POINTS (PK INT PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT DOUBLE)");

            // Points inside [0,100] region
            st.execute("INSERT INTO DEM_POINTS VALUES (1, ST_GeomFromText('POINT (10 10)'), 5.0)");
            st.execute("INSERT INTO DEM_POINTS VALUES (2, ST_GeomFromText('POINT (50 50)'), 6.0)");
            st.execute("INSERT INTO DEM_POINTS VALUES (3, ST_GeomFromText('POINT (80 80)'), 7.0)");

            // A far point outside the main region
            st.execute("INSERT INTO DEM_POINTS VALUES (4, ST_GeomFromText('POINT (200 200)'), 10.0)");
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
    public void testFetchCellDemBasic() throws Exception {
        loadTestDemPoints();

        LOGGER.info("========================================");
        LOGGER.info("testFetchCellDemBasic: DEM Points Loading Test");
        LOGGER.info("========================================");

        try (Statement st = connection.createStatement()) {
            int count = 0;
            try (ResultSet rs = st.executeQuery("SELECT PK, THE_GEOM, HEIGHT FROM DEM_POINTS ORDER BY PK")) {
                while (rs.next()) {
                    count++;
                    int pk = rs.getInt("PK");
                    Geometry geom = (Geometry) rs.getObject("THE_GEOM");
                    double h = rs.getDouble("HEIGHT");
                    LOGGER.info(String.format("  DEM Point %d: Height=%.2f, Centroid=(%.2f, %.2f), Type=%s", pk, h, geom.getCentroid().getX(), geom.getCentroid().getY(), geom.getGeometryType()));
                }
            }

            LOGGER.info(String.format("  ✓ Total DEM points: %d", count));
            assertEquals(4, count, "Should have 4 DEM sample points");

            // Envelope filtering similar to cell expansion logic
            Envelope mainEnvelope = new Envelope(0.0, 100.0, 0.0, 100.0);
            Envelope expanded = new Envelope(mainEnvelope);
            double prop = 100.0;
            double refl = 50.0;
            expanded.expandBy(prop + 2 * refl);

            LOGGER.info("Expanded envelope for DEM fetch: [{}..{}] x [{}..{}]", expanded.getMinX(), expanded.getMaxX(), expanded.getMinY(), expanded.getMaxY());

            List<Integer> pks = new ArrayList<>();
            String wkt = envelopeToWKT(expanded);
            try (ResultSet rs = st.executeQuery("SELECT PK, THE_GEOM, HEIGHT FROM DEM_POINTS WHERE THE_GEOM && ST_GeomFromText('" + wkt + "') ORDER BY PK")) {
                while (rs.next()) {
                    int pk = rs.getInt("PK");
                    Geometry g = (Geometry) rs.getObject("THE_GEOM");
                    double h = rs.getDouble("HEIGHT");
                    pks.add(pk);
                    LOGGER.info(String.format("    └─ DEM PK=%d, Centroid=(%.2f,%.2f), H=%.2f", pk, g.getCentroid().getX(), g.getCentroid().getY(), h));
                }
            }

            assertFalse(pks.isEmpty(), "Should fetch at least one DEM point within expanded envelope");
            assertTrue(pks.contains(1), "DEM point 1 should be fetched");
            assertTrue(pks.contains(2), "DEM point 2 should be fetched");
            assertTrue(pks.contains(3), "DEM point 3 should be fetched");
            // Given the expansion used (prop + 2*refl), point 4 at (200,200) falls inside the expanded envelope
            assertTrue(pks.contains(4), "DEM point 4 should be included in expanded envelope");
        }
    }

    @Test
    public void testDemHeightStatistics() throws Exception {
        loadTestDemPoints();

        LOGGER.info("testDemHeightStatistics: Height aggregation");
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT AVG(HEIGHT) as avg_h, MIN(HEIGHT) as min_h, MAX(HEIGHT) as max_h FROM DEM_POINTS")) {
                if (rs.next()) {
                    double avg = rs.getDouble("avg_h");
                    double min = rs.getDouble("min_h");
                    double max = rs.getDouble("max_h");
                    LOGGER.info(String.format("  DEM height stats — avg=%.2f, min=%.2f, max=%.2f", avg, min, max));
                    assertEquals(10.0, max, 1e-9);
                    assertEquals(5.0, min, 1e-9);
                } else {
                    fail("Expected a statistics row");
                }
            }
        }
    }
}
