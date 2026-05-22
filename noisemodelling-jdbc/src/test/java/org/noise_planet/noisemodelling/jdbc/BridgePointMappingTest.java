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

public class BridgePointMappingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgePointMappingTest.class);
    private Connection connection;

    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(
                BridgePointMappingTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    private void loadTestBridges() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS BRIDGE_POINTS");
            st.execute("CREATE TABLE BRIDGE_POINTS (" +
                    "PK INT PRIMARY KEY," +
                    "BRIDGE_PK INT," +
                    "THE_GEOM GEOMETRY," +
                    "ABSOLUTE_DECK_HEIGHT DOUBLE," +
                    "RELATIVE_DECK_HEIGHT DOUBLE," +
                    "DECK_THICKNESS DOUBLE," +
                    "RIGHT_WIDTH DOUBLE," +
                    "LEFT_WIDTH DOUBLE," +
                    "RIGHT_BARRIER_HEIGHT DOUBLE," +
                    "LEFT_BARRIER_HEIGHT DOUBLE," +
                    "POSITION VARCHAR," +
                    "GIRDER_TYPE VARCHAR," +
                    "SLAB_TYPE VARCHAR" +
                    ")");

            st.execute("INSERT INTO BRIDGE_POINTS VALUES (1, 100, ST_GeomFromText('POINT (10 10)'), 5.0, NULL, 0.3, 2.0, 2.0, 0.5, 0.5, 'CENTER', 'CONCRETE', 'CONCRETE')");
            st.execute("INSERT INTO BRIDGE_POINTS VALUES (2, 100, ST_GeomFromText('POINT (50 50)'), NULL, 4.0, 0.25, 2.5, 2.5, 0.6, 0.6, 'LEFT', 'STEEL_BOX', 'STEEL')");
            st.execute("INSERT INTO BRIDGE_POINTS VALUES (3, 200, ST_GeomFromText('POINT (200 200)'), 10.0, NULL, 0.4, 3.0, 3.0, 0.7, 0.7, 'RIGHT', 'CONCRETE', 'CONCRETE')");
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
    public void testFetchBridgePointsBasic() throws Exception {
        loadTestBridges();

        LOGGER.info("testFetchBridgePointsBasic: Bridge Points Loading and Envelope Fetch");

        try (Statement st = connection.createStatement()) {
            int count = 0;
            try (ResultSet rs = st.executeQuery("SELECT * FROM BRIDGE_POINTS ORDER BY PK")) {
                while (rs.next()) {
                    count++;
                    int pk = rs.getInt("PK");
                    int bridgePk = rs.getInt("BRIDGE_PK");
                    Geometry geom = (Geometry) rs.getObject("THE_GEOM");

                    Object absObj = rs.getObject("ABSOLUTE_DECK_HEIGHT");
                    Double absDeck = absObj == null ? null : ((Number) absObj).doubleValue();
                    Object relObj = rs.getObject("RELATIVE_DECK_HEIGHT");
                    Double relDeck = relObj == null ? null : ((Number) relObj).doubleValue();
                    Object thickObj = rs.getObject("DECK_THICKNESS");
                    Double thickness = thickObj == null ? null : ((Number) thickObj).doubleValue();
                    Object rWidthObj = rs.getObject("RIGHT_WIDTH");
                    Double rightWidth = rWidthObj == null ? null : ((Number) rWidthObj).doubleValue();
                    Object lWidthObj = rs.getObject("LEFT_WIDTH");
                    Double leftWidth = lWidthObj == null ? null : ((Number) lWidthObj).doubleValue();
                    Object rBarObj = rs.getObject("RIGHT_BARRIER_HEIGHT");
                    Double rightBarrier = rBarObj == null ? null : ((Number) rBarObj).doubleValue();
                    Object lBarObj = rs.getObject("LEFT_BARRIER_HEIGHT");
                    Double leftBarrier = lBarObj == null ? null : ((Number) lBarObj).doubleValue();
                    String position = rs.getString("POSITION");
                    String girder = rs.getString("GIRDER_TYPE");
                    String slab = rs.getString("SLAB_TYPE");

                    LOGGER.info(String.format("  BridgePoint %d: BRIDGE_PK=%d, Centroid=(%f,%f), ABS= %s, REL= %s, THICK= %s, R_W= %s, L_W= %s, R_BAR= %s, L_BAR= %s, POS=%s, GIRDER=%s, SLAB=%s",
                            pk, bridgePk, geom.getCentroid().getX(), geom.getCentroid().getY(),
                            absDeck, relDeck, thickness, rightWidth, leftWidth, rightBarrier, leftBarrier,
                            position, girder, slab));

                    if (pk == 1) {
                        assertEquals(100, bridgePk);
                        assertNotNull(absDeck);
                        assertEquals(5.0, absDeck, 1e-9);
                        assertNull(relDeck);
                        assertNotNull(thickness);
                        assertEquals(0.3, thickness, 1e-9);
                        assertEquals("CENTER", position);
                        assertEquals("CONCRETE", girder);
                        assertEquals("CONCRETE", slab);
                    } else if (pk == 2) {
                        assertEquals(100, bridgePk);
                        assertNull(absDeck);
                        assertNotNull(relDeck);
                        assertEquals(4.0, relDeck, 1e-9);
                        assertNotNull(thickness);
                        assertEquals(0.25, thickness, 1e-9);
                        assertEquals("LEFT", position);
                        assertEquals("STEEL_BOX", girder);
                        assertEquals("STEEL", slab);
                    } else if (pk == 3) {
                        assertEquals(200, bridgePk);
                        assertNotNull(absDeck);
                        assertEquals(10.0, absDeck, 1e-9);
                        assertNull(relDeck);
                        assertNotNull(thickness);
                        assertEquals(0.4, thickness, 1e-9);
                        assertEquals("RIGHT", position);
                    }
                }
            }
            assertEquals(3, count, "Should have 3 bridge points");

            Envelope mainEnvelope = new Envelope(0.0, 100.0, 0.0, 100.0);
            Envelope expanded = new Envelope(mainEnvelope);
            double prop = 100.0;
            double refl = 50.0;
            expanded.expandBy(prop + 2 * refl);

            List<Integer> pks = new ArrayList<>();
            String wkt = envelopeToWKT(expanded);
            try (ResultSet rs = st.executeQuery("SELECT PK, THE_GEOM FROM BRIDGE_POINTS WHERE THE_GEOM && ST_GeomFromText('" + wkt + "') ORDER BY PK")) {
                while (rs.next()) {
                    pks.add(rs.getInt("PK"));
                }
            }

            assertFalse(pks.isEmpty(), "Should fetch at least one bridge point within expanded envelope");
            assertTrue(pks.contains(1), "Bridge point 1 should be fetched");
            assertTrue(pks.contains(2), "Bridge point 2 should be fetched");
        }
    }

    @Test
    public void testGeometryTypes() throws Exception {
        loadTestBridges();

        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT PK, ST_GeometryType(THE_GEOM) as geom_type FROM BRIDGE_POINTS ORDER BY PK")) {
                while (rs.next()) {
                    String geomType = rs.getString("geom_type");
                    assertEquals("POINT", geomType, "Bridge geometries should be POINT");
                }
            }
        }
    }

}
