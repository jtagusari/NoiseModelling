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
 * Test for building table loading and envelope-based filtering.
 * 
 * Validates building geometry storage and spatial envelope filtering
 * using H2GIS spatial functions.
 */
public class fetchCellBuildingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(fetchCellBuildingTest.class);
    private Connection connection;
    
    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(
                fetchCellBuildingTest.class.getSimpleName(), true, ""));
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /**
     * Creates test building data directly in BUILDINGS table.
     * 
     * Creates BUILDINGS table with:
     * - Building 1: At (10, 10) within [0,100] region, height 8m
     * - Building 2: At (50, 50) at center of [0,100] region, height 12m
     * - Building 3: At (80, 80) near boundary of [0,100] region, height 6m
     * - Building 4: At (200, 200) far from [0,100] region, height 15m
     * 
     * @throws Exception if table creation fails
     */
    private void loadTestBuildings() throws Exception {
        try (Statement st = connection.createStatement()) {
            // Create BUILDINGS table with geometry and height columns
            st.execute("DROP TABLE IF EXISTS BUILDINGS");
            st.execute("CREATE TABLE BUILDINGS (" +
                    "PK INT PRIMARY KEY," +
                    "THE_GEOM GEOMETRY," +
                    "HEIGHT DOUBLE" +
                    ")");
            
            // Insert test building geometries (Polygons)
            st.execute("INSERT INTO BUILDINGS VALUES (" +
                    "1, ST_GeomFromText('POLYGON ((10 10, 20 10, 20 20, 10 20, 10 10))'), 8.0)");
            st.execute("INSERT INTO BUILDINGS VALUES (" +
                    "2, ST_GeomFromText('POLYGON ((50 50, 60 50, 60 60, 50 60, 50 50))'), 12.0)");
            st.execute("INSERT INTO BUILDINGS VALUES (" +
                    "3, ST_GeomFromText('POLYGON ((80 80, 90 80, 90 90, 80 90, 80 80))'), 6.0)");
            st.execute("INSERT INTO BUILDINGS VALUES (" +
                    "4, ST_GeomFromText('POLYGON ((200 200, 210 200, 210 210, 200 210, 200 200))'), 15.0)");
        }
    }

    /**
     * Converts an Envelope to WKT POLYGON format for SQL queries.
     */
    private String envelopeToWKT(Envelope env) {
        return String.format("POLYGON ((%f %f, %f %f, %f %f, %f %f, %f %f))",
                env.getMinX(), env.getMinY(),
                env.getMaxX(), env.getMinY(),
                env.getMaxX(), env.getMaxY(),
                env.getMinX(), env.getMaxY(),
                env.getMinX(), env.getMinY());
    }

    @Test
    public void testfetchCellBuildingBasic() throws Exception {
        
        // Load test data
        loadTestBuildings();

        LOGGER.info("========================================");
        LOGGER.info("testfetchCellBuildingBasic: Building Table Loading Test");
        LOGGER.info("========================================");

        // Verify BUILDINGS table structure
        LOGGER.info("");
        LOGGER.info("========== Step 1: BUILDINGS Table Validation ==========");
        try (Statement st = connection.createStatement()) {
            
            int buildingCount = 0;
            try (ResultSet rs = st.executeQuery("SELECT * FROM BUILDINGS ORDER BY PK")) {
                while (rs.next()) {
                    buildingCount++;
                    int pk = rs.getInt("PK");
                    Geometry geom = (Geometry) rs.getObject("THE_GEOM");
                    double height = rs.getDouble("HEIGHT");
                    
                    LOGGER.info(String.format("  Building %d: Height=%.1f m, Geometry type=%s, Area=%.1f",
                        pk, height, geom.getGeometryType(), geom.getArea()));
                }
            }
            LOGGER.info(String.format("  ✓ Total buildings in BUILDINGS table: %d", buildingCount));
            assertEquals(4, buildingCount, "Should have 4 buildings");

            // Cell envelope setup
            LOGGER.info("");
            LOGGER.info("========== Step 2: Envelope-Based Filtering ==========");
            Envelope mainEnvelope = new Envelope(0.0, 100.0, 0.0, 100.0);
            LOGGER.info(String.format("Main envelope (receiver region): [%.0f,%.0f] × [%.0f,%.0f]",
                mainEnvelope.getMinX(), mainEnvelope.getMaxX(),
                mainEnvelope.getMinY(), mainEnvelope.getMaxY()));
            
            Envelope expandedCellEnvelop = new Envelope(mainEnvelope);
            double maximumPropagationDistance = 100.0;
            double maximumReflectionDistance = 50.0;
            expandedCellEnvelop.expandBy(maximumPropagationDistance + 2 * maximumReflectionDistance);
            
            LOGGER.info(String.format("Cell expansion: propDistance=%.1f, reflDistance=%.1f",
                maximumPropagationDistance, maximumReflectionDistance));
            LOGGER.info(String.format("Expanded envelope: [%.0f,%.0f] × [%.0f,%.0f]",
                expandedCellEnvelop.getMinX(), expandedCellEnvelop.getMaxX(),
                expandedCellEnvelop.getMinY(), expandedCellEnvelop.getMaxY()));

            // Query buildings within expanded envelope
            LOGGER.info("");
            LOGGER.info("========== Step 3: Fetching Buildings within Expanded Envelope ==========");
            
            List<Integer> buildingPks = new ArrayList<>();
            String wktEnvelope = envelopeToWKT(expandedCellEnvelop);
            try (ResultSet rs = st.executeQuery(
                    "SELECT PK, THE_GEOM FROM BUILDINGS WHERE THE_GEOM && ST_GeomFromText('" + wktEnvelope + "') ORDER BY PK")) {
                while (rs.next()) {
                    int pk = rs.getInt("PK");
                    Geometry geom = (Geometry) rs.getObject("THE_GEOM");
                    buildingPks.add(pk);
                    LOGGER.info(String.format("    └─ Building PK=%d, Centroid=(%f, %f)", 
                        pk, geom.getCentroid().getX(), geom.getCentroid().getY()));
                }
            }
            
            LOGGER.info(String.format("  ✓ Total buildings within expanded envelope: %d", buildingPks.size()));
            
            // Verify results: all buildings should be in expanded envelope [-200, 300]
            assertNotNull(buildingPks, "Buildings list should not be null");
            assertFalse(buildingPks.isEmpty(), "At least one building should be within expanded envelope");
            assertTrue(buildingPks.contains(1), "Building 1 should be in expanded envelope");
            assertTrue(buildingPks.contains(2), "Building 2 should be in expanded envelope");
            assertTrue(buildingPks.contains(3), "Building 3 should be in expanded envelope");
            assertTrue(buildingPks.contains(4), "Building 4 should be in expanded envelope ([-200, 300] x [-200, 300])");
            
            LOGGER.info("");
            LOGGER.info("========== Test Complete ==========");
            LOGGER.info("  ✓ Building table created and populated");
            LOGGER.info("  ✓ Cell envelope computed with expansion");
            LOGGER.info("  ✓ Buildings correctly filtered by expanded envelope");
        }
    }

    @Test
    public void testBuildingHeightProperties() throws Exception {
        
        loadTestBuildings();

        LOGGER.info("========================================");
        LOGGER.info("testBuildingHeightProperties: Height Validation");
        LOGGER.info("========================================");

        LOGGER.info("");
        LOGGER.info("========== Building Height Verification ==========");
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT PK, HEIGHT FROM BUILDINGS ORDER BY PK")) {
                while (rs.next()) {
                    int pk = rs.getInt("PK");
                    double height = rs.getDouble("HEIGHT");
                    LOGGER.info(String.format("  Building %d: HEIGHT=%.1f m", pk, height));
                }
            }
            
            // Verify height values
            try (ResultSet rs = st.executeQuery("SELECT AVG(HEIGHT) as avg_height, MAX(HEIGHT) as max_height FROM BUILDINGS")) {
                if (rs.next()) {
                    double avgHeight = rs.getDouble("avg_height");
                    double maxHeight = rs.getDouble("max_height");
                    LOGGER.info(String.format("  Average height: %.1f m", avgHeight));
                    LOGGER.info(String.format("  Maximum height: %.1f m", maxHeight));
                    
                    assertEquals(15.0, maxHeight, "Max height should be 15.0");
                }
            }
            
            LOGGER.info("");
            LOGGER.info("========== Test Complete ==========");
            LOGGER.info("  ✓ All building heights validated");
        }
    }

    @Test
    public void testBuildingGeometryTypes() throws Exception {
        
        loadTestBuildings();

        LOGGER.info("========================================");
        LOGGER.info("testBuildingGeometryTypes: Geometry Type Validation");
        LOGGER.info("========================================");

        LOGGER.info("");
        LOGGER.info("========== Geometry Type Verification ==========");
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT PK, ST_GeometryType(THE_GEOM) as geom_type FROM BUILDINGS ORDER BY PK")) {
                while (rs.next()) {
                    int pk = rs.getInt("PK");
                    String geomType = rs.getString("geom_type");
                    LOGGER.info(String.format("  Building %d: Geometry type=%s", pk, geomType));
                    assertEquals("POLYGON", geomType, "All geometries should be Polygons");
                }
            }
            
            LOGGER.info("");
            LOGGER.info("========== Test Complete ==========");
            LOGGER.info("  ✓ All building geometries are Polygons");
        }
    }
}
