package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple test that imports a GeoJSON resource into an H2GIS database
 * using the GeoJsonRead stored procedure and verifies the target table
 * contains at least one row.
 */
public class GeoJsonToH2GISTest {

    private Connection connection;
    private static final Logger LOGGER = LoggerFactory.getLogger(GeoJsonToH2GISTest.class);

    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(GeoJsonToH2GISTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    public void testImportGeoJsonToH2Gis() throws Exception {
        try (Statement st = connection.createStatement()) {
            // Import GeoJSON resources (must be present under src/test/resources)
            String roadsPath = GeoJsonToH2GISTest.class.getResource("/roads.geojson").getFile();
            String buildingsPath = GeoJsonToH2GISTest.class.getResource("/buildings.geojson").getFile();
            String groundPath = GeoJsonToH2GISTest.class.getResource("/ground_areas.geojson").getFile();

            st.execute(String.format("CALL GeoJsonRead('%s', 'TEST_ROADS')", roadsPath));
            st.execute(String.format("CALL GeoJsonRead('%s', 'TEST_BUILDINGS')", buildingsPath));
            st.execute(String.format("CALL GeoJsonRead('%s', 'TEST_GROUND_AREAS')", groundPath));

            // Verify and dump tables
            verifyAndDumpTable(st, "TEST_ROADS");
            verifyAndDumpTable(st, "TEST_BUILDINGS");
            verifyAndDumpTable(st, "TEST_GROUND_AREAS");
        }
    }

    private void verifyAndDumpTable(Statement st, String tableName) throws Exception {
        try (ResultSet rsCnt = st.executeQuery("SELECT COUNT(*) AS cnt FROM " + tableName)) {
            rsCnt.next();
            int cnt = rsCnt.getInt("cnt");
            assertTrue(cnt > 0, "Imported " + tableName + " should contain at least one feature");
            LOGGER.info("Table {} contains {} rows", tableName, cnt);
        }

        try (ResultSet rs = st.executeQuery("SELECT * FROM " + tableName + " LIMIT 10")) {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                sb.append(tableName).append(" row:");
                for (int i = 1; i <= cols; i++) {
                    String colLabel = md.getColumnLabel(i);
                    Object val = rs.getObject(i);
                    if (val instanceof Geometry) {
                        Geometry g = (Geometry) val;
                        sb.append(String.format(" [%s=%s(%s)]", colLabel, g.getGeometryType(), g.toText()));
                    } else {
                        sb.append(String.format(" [%s=%s]", colLabel, String.valueOf(val)));
                    }
                }
                LOGGER.info(sb.toString());
            }
        }
    }

}
