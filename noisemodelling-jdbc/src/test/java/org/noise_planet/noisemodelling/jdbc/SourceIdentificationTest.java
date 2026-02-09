package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.h2gis.utilities.SpatialResultSet;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.ReceiverPointInfo;
import org.noise_planet.noisemodelling.pathfinder.SourceCollector;
import org.noise_planet.noisemodelling.pathfinder.SourcePointInfo;
import org.noise_planet.noisemodelling.pathfinder.path.BridgeRelationship;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgePoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.jdbc.input.DefaultTableLoader;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;
import org.noise_planet.noisemodelling.jdbc.input.SourceEmission;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.DefaultProgressVisitor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for Step 1-7: Source Identification and Height Type Processing.
 * 
 * This test suite validates the complete workflow from ROADS table creation
 * through height type processing for noise propagation calculations.
 */
public class SourceIdentificationTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SourceIdentificationTest.class);
    private Connection connection;
    
    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(SourceIdentificationTest.class.getSimpleName(), true, ""));
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
    
    /**
     * Helper method: Creates Bridge object from BRIDGE_POINTS table.
     * 
     * This method:
     * 1. Loads bridge points from BRIDGE_POINTS table (PK=100)
     * 2. Creates Bridge object with absorption coefficients
     * 3. Generates deck geometry using ProfileBuilder
     * 
     * @return Bridge object with deck geometry generated
     */
    private Bridge createBridgeFromDatabase() throws Exception {
        List<BridgePoint> bridgePointsList = new ArrayList<>();
        
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                    "SELECT THE_GEOM, " +
                    "BRIDGE_PK, PK, POSITION, ABSOLUTE_DECK_HEIGHT, RELATIVE_DECK_HEIGHT, " +
                    "DECK_THICKNESS, RIGHT_WIDTH, LEFT_WIDTH, RIGHT_BARRIER_HEIGHT, LEFT_BARRIER_HEIGHT, GIRDER_TYPE, SLAB_TYPE " +
                    "FROM BRIDGE_POINTS WHERE BRIDGE_PK=100 ORDER BY PK")) {
            
            // Load bridge points from BRIDGE_POINTS table for bridge PK=100
            while (rs.next()) {
                BridgePoint bridgePoint = new BridgePoint(rs);
                bridgePointsList.add(bridgePoint);
            }
        }
        
        assertEquals(3, bridgePointsList.size(), "Should have loaded 3 CENTER bridge points");
        
        // Create Bridge object from bridge points with absorption coefficients
        List<Double> alphas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            alphas.add(0.5); // Default alpha values for 8 frequency bands
        }
        Bridge bridge = new Bridge.Builder(bridgePointsList).withAlphas(alphas).build();

        return bridge;
    }
    
    /**
     * Helper method: Creates ROADS table with test data and performs bridge record duplication.
     * 
     * Creates:
     * - Road 1: Regular road (PK=1, BRIDGE_PK=NULL) at Y=0
     * - Road 2: Bridge road (PK=2, BRIDGE_PK=100) at Y=20
     * - Road 3: Crossing road (PK=3, BRIDGE_PK=NULL) intersecting both
     * 
     * After duplication:
     * - Road 4: Duplicated bridge record (PK=4, EMISSION_TYPE='BRIDGE', HEIGHT_TYPE='RELATIVE')
     * 
     * @return Statement for continued test operations
     */
    private void createRoadsTableWithBridgeDuplication() throws Exception {
        try (Statement st = connection.createStatement()) {
            // Step 0: Create BRIDGE_POINTS table with bridge point data
            // As per source_algorithms.md bridge geometry setup
            st.execute("CREATE TABLE BRIDGE_POINTS (" +
                    "PK LONG PRIMARY KEY, " +
                    "THE_GEOM GEOMETRY, " +
                    "BRIDGE_PK LONG, " +
                    "POSITION VARCHAR(10), " +
                    "ABSOLUTE_DECK_HEIGHT DOUBLE PRECISION, " +
                    "RELATIVE_DECK_HEIGHT DOUBLE PRECISION, " +
                    "DECK_THICKNESS DOUBLE PRECISION, " +
                    "RIGHT_WIDTH DOUBLE PRECISION, " +
                    "LEFT_WIDTH DOUBLE PRECISION, " +
                    "RIGHT_BARRIER_HEIGHT DOUBLE PRECISION, " +
                    "LEFT_BARRIER_HEIGHT DOUBLE PRECISION, " +
                    "GIRDER_TYPE VARCHAR(30), " +
                    "SLAB_TYPE VARCHAR(20))");
            
            // Insert bridge points for Bridge 100 that covers ROAD2 (Y=20) but not ROAD1 (Y=0)
            // Bridge deck is at Y=[15, 25] with deck height at 10m above ground
            // We'll create 3 CENTER points along the bridge centerline (X=0, X=50, X=100)
            // LEFT and RIGHT edge points will be generated automatically by BridgeGeometryBuilder
            
            // Center point at X=0 (bridge start)
            st.execute("INSERT INTO BRIDGE_POINTS VALUES (1, ST_GeomFromText('POINT(0 20)'), 100, 'CENTER', 10.0, 10.0, 0.5, 5.0, 5.0, 1.0, 1.0, 'STEEL_BOX', 'STEEL')");
            
            // Center point at X=50 (bridge middle)
            st.execute("INSERT INTO BRIDGE_POINTS VALUES (2, ST_GeomFromText('POINT(50 20)'), 100, 'CENTER', 10.0, 10.0, 0.5, 5.0, 5.0, 1.0, 1.0, 'STEEL_BOX', 'STEEL')");
            
            // Center point at X=100 (bridge end)
            st.execute("INSERT INTO BRIDGE_POINTS VALUES (3, ST_GeomFromText('POINT(100 20)'), 100, 'CENTER', 10.0, 10.0, 0.5, 5.0, 5.0, 1.0, 1.0, 'STEEL_BOX', 'STEEL')");
            
            // Step 1: Create ROADS table
            // As per source_algorithms.md Step 1: ROADS Table Creation
            st.execute("CREATE TABLE ROADS (" +
                    "PK SERIAL PRIMARY KEY, " +
                    "THE_GEOM GEOMETRY, " +
                    "LV_D DOUBLE PRECISION, " +
                    "MV_D DOUBLE PRECISION, " +
                    "HGV_D DOUBLE PRECISION, " +
                    "LV_SPD_D DOUBLE PRECISION, " +
                    "MV_SPD_D DOUBLE PRECISION, " +
                    "HGV_SPD_D DOUBLE PRECISION, " +
                    "LV_E DOUBLE PRECISION, " +
                    "MV_E DOUBLE PRECISION, " +
                    "HGV_E DOUBLE PRECISION, " +
                    "LV_SPD_E DOUBLE PRECISION, " +
                    "MV_SPD_E DOUBLE PRECISION, " +
                    "HGV_SPD_E DOUBLE PRECISION, " +
                    "LV_N DOUBLE PRECISION, " +
                    "MV_N DOUBLE PRECISION, " +
                    "HGV_N DOUBLE PRECISION, " +
                    "LV_SPD_N DOUBLE PRECISION, " +
                    "MV_SPD_N DOUBLE PRECISION, " +
                    "HGV_SPD_N DOUBLE PRECISION, " +
                    "HEIGHT_TYPE VARCHAR(10), " +
                    "BRIDGE_PK LONG)");
            
            // Insert test roads with Day/Evening/Night traffic data
            // Road 1: Regular road - MV/HGV ratio increases in evening/night, moderate speed, LV > MV > HGV
            st.execute("INSERT INTO ROADS (THE_GEOM, LV_D, MV_D, HGV_D, LV_SPD_D, MV_SPD_D, HGV_SPD_D, " +
                    "LV_E, MV_E, HGV_E, LV_SPD_E, MV_SPD_E, HGV_SPD_E, " +
                    "LV_N, MV_N, HGV_N, LV_SPD_N, MV_SPD_N, HGV_SPD_N, BRIDGE_PK) " +
                    "VALUES (ST_GeomFromText('LINESTRING(0 0, 100 0)'), " +
                    "600, 80, 40, 50, 45, 40, " +   // Day: LV dominant, moderate speed
                    "400, 100, 60, 55, 50, 45, " +  // Evening: MV/HGV ratio increased, slightly higher speed
                    "200, 120, 80, 60, 55, 50, " +  // Night: MV/HGV ratio further increased, highest speed
                    "NULL)");
            
            // Road 2: Highway on bridge - MV/HGV ratio increases in evening/night, high speed, LV > MV > HGV
            st.execute("INSERT INTO ROADS (THE_GEOM, LV_D, MV_D, HGV_D, LV_SPD_D, MV_SPD_D, HGV_SPD_D, " +
                    "LV_E, MV_E, HGV_E, LV_SPD_E, MV_SPD_E, HGV_SPD_E, " +
                    "LV_N, MV_N, HGV_N, LV_SPD_N, MV_SPD_N, HGV_SPD_N, BRIDGE_PK) " +
                    "VALUES (ST_GeomFromText('LINESTRING(0 20, 100 20)'), " +
                    "1200, 150, 100, 90, 85, 80, " +  // Day: high volume, high speed
                    "1000, 180, 120, 95, 90, 85, " +  // Evening: MV/HGV ratio increased, higher speed
                    "800, 200, 140, 100, 95, 90, " +  // Night: MV/HGV ratio further increased, highest speed
                    "100)");
            
            // Road 3: Low-speed crossing road - MV/HGV ratio increases in evening/night, speed increases, all equal speeds
            st.execute("INSERT INTO ROADS (THE_GEOM, LV_D, MV_D, HGV_D, LV_SPD_D, MV_SPD_D, HGV_SPD_D, " +
                    "LV_E, MV_E, HGV_E, LV_SPD_E, MV_SPD_E, HGV_SPD_E, " +
                    "LV_N, MV_N, HGV_N, LV_SPD_N, MV_SPD_N, HGV_SPD_N, BRIDGE_PK) " +
                    "VALUES (ST_GeomFromText('LINESTRING(50 -10, 50 30)'), " +
                    "600, 80, 40, 50, 50, 50, " +   // Day: low speed, all equal
                    "400, 100, 60, 55, 55, 55, " +  // Evening: MV/HGV ratio increased, higher equal speed
                    "200, 120, 80, 60, 60, 60, " +  // Night: MV/HGV ratio further increased, highest equal speed
                    "NULL)");
            
            // Step 2: Bridge record duplication
            // As per source_algorithms.md Step 2: Bridge Record Duplication and Classification
            st.execute("ALTER TABLE ROADS ADD COLUMN EMISSION_TYPE VARCHAR(20)");
            st.execute("UPDATE ROADS SET EMISSION_TYPE='ROAD'");
            st.execute("INSERT INTO ROADS (THE_GEOM, LV_D, MV_D, HGV_D, LV_SPD_D, MV_SPD_D, HGV_SPD_D, " +
                    "LV_E, MV_E, HGV_E, LV_SPD_E, MV_SPD_E, HGV_SPD_E, " +
                    "LV_N, MV_N, HGV_N, LV_SPD_N, MV_SPD_N, HGV_SPD_N, " +
                    "HEIGHT_TYPE, BRIDGE_PK, EMISSION_TYPE) " +
                    "SELECT THE_GEOM, LV_D, MV_D, HGV_D, LV_SPD_D, MV_SPD_D, HGV_SPD_D, " +
                    "LV_E, MV_E, HGV_E, LV_SPD_E, MV_SPD_E, HGV_SPD_E, " +
                    "LV_N, MV_N, HGV_N, LV_SPD_N, MV_SPD_N, HGV_SPD_N, " +
                    "'ABSOLUTE', BRIDGE_PK, 'BRIDGE' " +
                    "FROM ROADS WHERE BRIDGE_PK IS NOT NULL");
        }
    }
    


    /**
     * Test complete source identification workflow: ROADS table loading, bridge validation, 
     * and emission calculation.
     * 
     * This integrated test validates the complete workflow from ROADS table creation through
     * emission calculations, covering:
     * 
     * Step 0: BRIDGE_POINTS table setup
     * - Bridge 100: 3 CENTER points (X=0, 50, 100) along centerline at Y=20
     * - LEFT and RIGHT edge points are generated automatically by BridgeGeometryBuilder
     * 
     * Step 1: Creates a ROADS table with:
     * - Road 1: Regular road (PK=1, BRIDGE_PK=NULL) running parallel (Y=0)
     * - Road 2: Bridge road (PK=2, BRIDGE_PK=100) running parallel (Y=20)
     * - Road 3: Crossing road (PK=3, BRIDGE_PK=NULL) intersecting both parallel roads
     * 
     * Step 2: Duplicates bridge records (BRIDGE_PK IS NOT NULL):
     * - Original records: EMISSION_TYPE='ROAD'
     * - Duplicated records: EMISSION_TYPE='BRIDGE', HEIGHT_TYPE='RELATIVE'
     * 
     * Step 3: Calculates emissions:
     * - Road sources: CNOSSOS-EU methodology
     * - Bridge structural sources: ASJ methodology
     * - Creates LW_ROADS table with emission results
     */
    @Test
    public void testSourceIdentificationWorkflow() throws Exception {
        // Create ROADS table with bridge duplication
        createRoadsTableWithBridgeDuplication();
        
        try (Statement st = connection.createStatement()) {
            // ============================================================
            // Step 1: ROADS Table Validation
            // Validates the ROADS table created in createRoadsTableWithBridgeDuplication() 
            // as per source_algorithms.md Step 1: ROADS Table Creation
            // ============================================================
            
            LOGGER.info("========================================");
            LOGGER.info("Step 1: ROADS Table Validation");
            LOGGER.info("========================================");
            
            // Verify initial data (3 original roads)
            try (ResultSet rs = st.executeQuery("SELECT PK, ST_AsText(THE_GEOM) as GEOM_TEXT, " +
                    "LV_D, BRIDGE_PK FROM ROADS WHERE EMISSION_TYPE='ROAD' ORDER BY PK")) {
                
                // Road 1: Regular road
                assertTrue(rs.next());
                assertEquals(1, rs.getLong("PK"));
                assertEquals(600.0, rs.getDouble("LV_D"), 0.001);
                rs.getLong("BRIDGE_PK");
                assertTrue(rs.wasNull(), "Road 1 should not be on a bridge");
                
                // Road 2: Highway on bridge
                assertTrue(rs.next());
                assertEquals(2, rs.getLong("PK"));
                assertEquals(1200.0, rs.getDouble("LV_D"), 0.001);
                assertEquals(100, rs.getLong("BRIDGE_PK"), "Road 2 should be on bridge 100");
                
                // Road 3: Crossing road
                assertTrue(rs.next());
                assertEquals(3, rs.getLong("PK"));
                assertEquals(600.0, rs.getDouble("LV_D"), 0.001);
                rs.getLong("BRIDGE_PK");
                assertTrue(rs.wasNull(), "Road 3 should not be on a bridge");
                
                assertFalse(rs.next(), "Should have exactly 3 roads");
            }
            
            LOGGER.info("--- ROADS Table Verification ---");
            LOGGER.info("Road 1: Regular road (Y=0), LV_D=600, BRIDGE_PK=NULL");
            LOGGER.info("Road 2: Highway on bridge (Y=20), LV_D=1200, BRIDGE_PK=100");
            LOGGER.info("Road 3: Crossing road, LV_D=600, BRIDGE_PK=NULL");
            LOGGER.info("Total: 3 roads with EMISSION_TYPE='ROAD'");

            // Verify Bridge PK=100 has exactly 3 CENTER points (no LEFT/RIGHT in database)
            try (ResultSet rs = st.executeQuery(
                    "SELECT POSITION, COUNT(*) as cnt FROM BRIDGE_POINTS GROUP BY POSITION")) {
                
                assertTrue(rs.next());
                assertEquals("CENTER", rs.getString("POSITION"));
                assertEquals(3, rs.getInt("cnt"), "Should have exactly 3 CENTER points");
                
                // No LEFT or RIGHT points (they will be generated by BridgeGeometryBuilder)
                assertFalse(rs.next(), "Should only have CENTER points in database");
            }
                        
            // ============================================================
            // Step 2: Bridge Record Duplication and Classification
            // Validates bridge record duplication performed in createRoadsTableWithBridgeDuplication()
            // as per source_algorithms.md Step 2: Bridge Record Duplication and Classification
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("========================================");
            LOGGER.info("Step 2: Bridge Record Duplication");
            LOGGER.info("========================================");
            
            // Verify bridge record duplication
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) as cnt FROM ROADS WHERE EMISSION_TYPE='BRIDGE'")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt("cnt"), "Should have 1 duplicated BRIDGE record");
                LOGGER.info("Duplicated record count: 1 (Road 2 → Road 4 with EMISSION_TYPE='BRIDGE')");
            }
            
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) as cnt FROM ROADS")) {
                assertTrue(rs.next());
                assertEquals(4, rs.getInt("cnt"), "Should have 4 total records after duplication");
                LOGGER.info("Total records after duplication: 4");
            }
            
            // Log duplicated record details
            try (ResultSet rs = st.executeQuery(
                    "SELECT PK, EMISSION_TYPE, BRIDGE_PK FROM ROADS WHERE EMISSION_TYPE='BRIDGE'")) {
                if (rs.next()) {
                    LOGGER.info(String.format("Duplicated record: PK=%d, EMISSION_TYPE=%s, BRIDGE_PK=%d",
                            rs.getLong("PK"), rs.getString("EMISSION_TYPE"), rs.getLong("BRIDGE_PK")));
                }
            }
            
            // ============================================================
            // Bridge Geometry Construction and Validation
            // Constructs bridge geometry using ProfileBuilder and validates spatial containment
            // as per source_algorithms.md bridge geometry handling
            // ============================================================
            
            Bridge bridge = createBridgeFromDatabase();
            ProfileBuilder profileBuilder = new ProfileBuilder();
            profileBuilder.addBridge(bridge);
            profileBuilder.addTopographicPoint(new Coordinate(0, 0, 0.5));
            profileBuilder.addTopographicPoint(new Coordinate(50, 0, 1.2));
            profileBuilder.addTopographicPoint(new Coordinate(100, 0, 2.8));
            profileBuilder.addTopographicPoint(new Coordinate(0, 50, 0.1));
            profileBuilder.addTopographicPoint(new Coordinate(50, 50, 3.0));
            profileBuilder.addTopographicPoint(new Coordinate(100, 50, 1.5));
            profileBuilder.addTopographicPoint(new Coordinate(0, 100, 2.1));
            profileBuilder.addTopographicPoint(new Coordinate(50, 100, 0.8));
            profileBuilder.addTopographicPoint(new Coordinate(100, 100, 2.5));
            profileBuilder.finishFeeding();

            Geometry deckGeometry = bridge.getDeckGeometry();
            Bridge.GirderType girderType = bridge.getGirderType();
            Bridge.SlabType slabType = bridge.getSlabType();
            
            assertNotNull(bridge, "Bridge should be constructed successfully");
            assertNotNull(deckGeometry, "Deck geometry should be generated successfully");
            assertNotNull(girderType, "Girder type should be set");
            assertNotNull(slabType, "Slab type should be set");
            
            LOGGER.info("--- Bridge Construction Validation ---");
            LOGGER.info("Bridge PK: " + bridge.getPrimaryKey());
            LOGGER.info("Deck geometry type: " + deckGeometry.getGeometryType());
            LOGGER.info("Deck geometry: " + deckGeometry);
            LOGGER.info("Girder type: " + girderType);
            LOGGER.info("Slab type: " + slabType);
            
            
            // Insert deck geometry into temporary table for H2GIS spatial queries
            st.execute("CREATE TEMPORARY TABLE TEMP_BRIDGE_DECK (BRIDGE_PK INTEGER, THE_GEOM GEOMETRY)");
            st.execute(String.format("INSERT INTO TEMP_BRIDGE_DECK VALUES (100, ST_GeomFromText('%s'))",
                    deckGeometry.toText()));
            
            // Validate spatial containment using H2GIS spatial functions
            LOGGER.info("--- Bridge Spatial Containment Validation (H2GIS) ---");
            try (ResultSet rs = st.executeQuery(
                    "SELECT r.PK, " +
                    "ST_Intersects(r.THE_GEOM, b.THE_GEOM) as intersects, " +
                    "ST_Contains(b.THE_GEOM, r.THE_GEOM) as contains " +
                    "FROM ROADS r, TEMP_BRIDGE_DECK b " +
                    "WHERE r.EMISSION_TYPE='ROAD' AND r.PK IN (1, 2) " +
                    "ORDER BY r.PK")) {
                
                // Road 1 (Y=0) should NOT intersect with bridge deck
                assertTrue(rs.next());
                assertEquals(1, rs.getLong("PK"));
                assertFalse(rs.getBoolean("intersects"), 
                        "Road 1 (Y=0) should NOT intersect with Bridge deck (ST_Intersects)");
                assertFalse(rs.getBoolean("contains"), 
                        "Road 1 (Y=0) should NOT be contained by Bridge deck (ST_Contains)");
                LOGGER.info("Road 1: ST_Intersects=false, ST_Contains=false ✓");
                
                // Road 2 (Y=20) should intersect and be contained by bridge deck
                assertTrue(rs.next());
                assertEquals(2, rs.getLong("PK"));
                assertTrue(rs.getBoolean("intersects"), 
                        "Road 2 (Y=20) should intersect with Bridge deck (ST_Intersects)");
                assertTrue(rs.getBoolean("contains"), 
                        "Road 2 (Y=20) should be contained by Bridge deck (ST_Contains)");
                LOGGER.info("Road 2: ST_Intersects=true, ST_Contains=true ✓");
                
                assertFalse(rs.next());
            }
            LOGGER.info("--- Bridge validation completed ---");
            
            // ============================================================
            // Step 3: Emission Calculation
            // Calculates sound power levels for road traffic noise (CNOSSOS-EU) and bridge structural noise (ASJ)
            // as per source_algorithms.md Step 3: Emission Calculation
            // ============================================================
            // ============================================================
            // Step 4: LW_ROADS Table Creation
            // Creates LW_ROADS table with emission data and assigns Z coordinates based on EMISSION_TYPE
            // as per source_algorithms.md Step 4: LW_ROADS Table Creation
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("========================================");
            LOGGER.info("Step 3, 4: Emission Calculation and LW_ROADS Table Creation");
            LOGGER.info("========================================");
            
            // Create LW_ROADS table with 28 columns (PK, THE_GEOM, EMISSION_TYPE, BRIDGE_PK + 24 emission levels)
            st.execute("CREATE TABLE LW_ROADS (" +
                    "PK LONG PRIMARY KEY, " +
                    "THE_GEOM GEOMETRY, " +
                    "EMISSION_TYPE VARCHAR(20), " +
                    "HEIGHT_TYPE VARCHAR(10), " +
                    "BRIDGE_PK LONG, " +
                    // Day period (8 octave bands: 63, 125, 250, 500, 1000, 2000, 4000, 8000 Hz)
                    "LWD63 DOUBLE PRECISION, LWD125 DOUBLE PRECISION, LWD250 DOUBLE PRECISION, LWD500 DOUBLE PRECISION, " +
                    "LWD1000 DOUBLE PRECISION, LWD2000 DOUBLE PRECISION, LWD4000 DOUBLE PRECISION, LWD8000 DOUBLE PRECISION, " +
                    // Evening period
                    "LWE63 DOUBLE PRECISION, LWE125 DOUBLE PRECISION, LWE250 DOUBLE PRECISION, LWE500 DOUBLE PRECISION, " +
                    "LWE1000 DOUBLE PRECISION, LWE2000 DOUBLE PRECISION, LWE4000 DOUBLE PRECISION, LWE8000 DOUBLE PRECISION, " +
                    // Night period
                    "LWN63 DOUBLE PRECISION, LWN125 DOUBLE PRECISION, LWN250 DOUBLE PRECISION, LWN500 DOUBLE PRECISION, " +
                    "LWN1000 DOUBLE PRECISION, LWN2000 DOUBLE PRECISION, LWN4000 DOUBLE PRECISION, LWN8000 DOUBLE PRECISION)");
            
            
            // Calculate emissions for all roads and insert into LW_ROADS table
            Map<String, Integer> fieldCache = new HashMap<>();
            
            // Process roads: 1 (Regular ROAD), 2 (Bridge ROAD), 3 (Crossing ROAD), 4 (Bridge BRIDGE)
            int[] roadPks = {1, 2, 3, 4};
            
            for (int i = 0; i < roadPks.length; i++) {
                int pk = roadPks[i];
                
                try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM ROADS WHERE PK=" + pk);
                     SpatialResultSet rs = ps.executeQuery().unwrap(SpatialResultSet.class)) {
                    if (rs.next()) {
                        // Cache field indices for performance
                        fieldCache.clear();
                        ResultSetMetaData metaData = rs.getMetaData();
                        for (int j = 1; j <= metaData.getColumnCount(); j++) {
                            fieldCache.put(metaData.getColumnName(j), j);
                        }
                                                
                        // Compute emissions based on source type (returns W values)
                        List<SourceEmission> emissions = new RoadEmissionBuilder(rs)
                            .trafficAsInput()
                            .withPeriods(Arrays.asList("D", "E", "N"))
                            .build(profileBuilder);

                        double[][] lwResults = new double[3][8];
                        for (SourceEmission emission : emissions) {
                            int periodIndex;
                            switch (emission.period) {
                                case "D":
                                    periodIndex = 0;
                                    break;
                                case "E":
                                    periodIndex = 1;
                                    break;
                                case "N":
                                    periodIndex = 2;
                                    break;
                                default:
                                    continue;
                            }
                            lwResults[periodIndex] = emission.emissionInWatts;
                        }
                        
                        // Convert W to dB and build emission values SQL fragment (24 values: 8 bands × 3 periods)
                        StringBuilder emissionValues = new StringBuilder();
                        for (int period = 0; period < 3; period++) {
                            double[] periodEmissions = AcousticIndicatorsFunctions.wToDb(lwResults[period]);
                            for (int band = 0; band < 8; band++) {
                                emissionValues.append(String.format("%.2f%s", periodEmissions[band],
                                        (period == 2 && band == 7) ? "" : ", "));
                            }
                        }
                        
                        // Insert calculated emission data into LW_ROADS table
                        st.execute(String.format(
                                "INSERT INTO LW_ROADS (PK, THE_GEOM, EMISSION_TYPE, HEIGHT_TYPE, BRIDGE_PK, " +
                                "LWD63, LWD125, LWD250, LWD500, LWD1000, LWD2000, LWD4000, LWD8000, " +
                                "LWE63, LWE125, LWE250, LWE500, LWE1000, LWE2000, LWE4000, LWE8000, " +
                                "LWN63, LWN125, LWN250, LWN500, LWN1000, LWN2000, LWN4000, LWN8000) " +
                                "SELECT %d, THE_GEOM, EMISSION_TYPE, HEIGHT_TYPE, BRIDGE_PK, %s FROM ROADS WHERE PK=%d",
                                pk, emissionValues.toString(), pk));
                    }
                }
            }

            // Update Z coordinates based on EMISSION_TYPE as per LW_ROADS table convention
            // ROAD sources: Z=0.05m (above road surface), BRIDGE sources: Z=-0.05m (below deck)
            st.execute("UPDATE LW_ROADS SET THE_GEOM = ST_UPDATEZ(THE_GEOM, 0.05) WHERE EMISSION_TYPE='ROAD'");
            st.execute("UPDATE LW_ROADS SET THE_GEOM = ST_UPDATEZ(THE_GEOM, -0.05) WHERE EMISSION_TYPE='BRIDGE'");
            st.execute("UPDATE LW_ROADS SET HEIGHT_TYPE = 'RELATIVE'");
            
            
            // ============================================================
            // Step 4 Validation: LW_ROADS Table Structure and Data
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("--- LW_ROADS Table Validation ---");
            
            // Verify table structure
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) as col_count FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = 'LW_ROADS'")) {
                assertTrue(rs.next());
                int colCount = rs.getInt("col_count");
                assertEquals(29, colCount, "LW_ROADS should have 29 columns");
                LOGGER.info("Column count: " + colCount + " (PK, THE_GEOM, EMISSION_TYPE, BRIDGE_PK + 24 emission levels)");
            }
            
            // Verify we have 4 emission records (Roads 1, 2, 3, and 4)
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) as cnt FROM LW_ROADS")) {
                assertTrue(rs.next());
                int recordCount = rs.getInt("cnt");
                assertEquals(4, recordCount, "Should have 4 emission records");
                LOGGER.info("Emission record count: " + recordCount);
            }
            
            // Verify EMISSION_TYPE distribution
            try (ResultSet rs = st.executeQuery(
                    "SELECT EMISSION_TYPE, COUNT(*) as cnt FROM LW_ROADS GROUP BY EMISSION_TYPE ORDER BY EMISSION_TYPE")) {
                int roadCount = 0;
                int bridgeCount = 0;
                while (rs.next()) {
                    String emissionType = rs.getString("EMISSION_TYPE");
                    int count = rs.getInt("cnt");
                    if ("ROAD".equals(emissionType)) {
                        roadCount = count;
                    } else if ("BRIDGE".equals(emissionType)) {
                        bridgeCount = count;
                    }
                }
                LOGGER.info("EMISSION_TYPE='ROAD': " + roadCount + " records (CNOSSOS-EU methodology)");
                LOGGER.info("EMISSION_TYPE='BRIDGE': " + bridgeCount + " records (ASJ methodology)");
            }
            
            // Log emission data for all records
            LOGGER.info("--- Emission Results (All Records) ---");
            String[] bands = {"63", "125", "250", "500", "1000", "2000", "4000", "8000"};
            
            try (ResultSet rs = st.executeQuery(
                    "SELECT PK, EMISSION_TYPE, BRIDGE_PK, " +
                    "LWD63, LWD125, LWD250, LWD500, LWD1000, LWD2000, LWD4000, LWD8000, " +
                    "LWE63, LWE125, LWE250, LWE500, LWE1000, LWE2000, LWE4000, LWE8000, " +
                    "LWN63, LWN125, LWN250, LWN500, LWN1000, LWN2000, LWN4000, LWN8000 " +
                    "FROM LW_ROADS ORDER BY PK")) {
                
                while (rs.next()) {
                    long pk = rs.getLong("PK");
                    String emissionType = rs.getString("EMISSION_TYPE");
                    Long bridgePk = rs.getLong("BRIDGE_PK");
                    if (rs.wasNull()) bridgePk = null;
                    
                    LOGGER.info(String.format("Road %d (EMISSION_TYPE=%s, BRIDGE_PK=%s):", 
                            pk, emissionType, bridgePk));
                    
                    // Day period emissions
                    StringBuilder dayEmissions = new StringBuilder("  Day:     ");
                    for (String band : bands) {
                        dayEmissions.append(String.format("%s Hz=%.2f dB, ", band, rs.getDouble("LWD" + band)));
                    }
                    LOGGER.info(dayEmissions.toString());
                    
                    // Evening period emissions
                    StringBuilder eveningEmissions = new StringBuilder("  Evening: ");
                    for (String band : bands) {
                        eveningEmissions.append(String.format("%s Hz=%.2f dB, ", band, rs.getDouble("LWE" + band)));
                    }
                    LOGGER.info(eveningEmissions.toString());
                    
                    // Night period emissions
                    StringBuilder nightEmissions = new StringBuilder("  Night:   ");
                    for (String band : bands) {
                        nightEmissions.append(String.format("%s Hz=%.2f dB, ", band, rs.getDouble("LWN" + band)));
                    }
                    LOGGER.info(nightEmissions.toString());
                    LOGGER.info("");
                }
            }
            
            // ============================================================
            // Step 5: Geometry Loading & Step 6: Scene Registration
            // Loads geometries from LW_ROADS table within envelope and registers them in Scene
            // as per source_algorithms.md Step 5: Geometry Loading and Step 6: Scene Registration
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("========================================");
            LOGGER.info("Step 5 & 6: Geometry Loading and Scene Registration");
            LOGGER.info("========================================");


            // manually set Envelope
            LOGGER.info("--- Setting Receiver and Envelope ---");
            ReceiverPointInfo receiverPointInfo = new ReceiverPointInfo(1, 1, new Coordinate(50.0, 10.0, 1.5));
            Coordinate receiverCoord = receiverPointInfo.getCoordinate();
            double searchRadius = 100.0;
            Envelope envelope = new Envelope(
                    receiverCoord.x - searchRadius, receiverCoord.x + searchRadius,
                    receiverCoord.y - searchRadius, receiverCoord.y + searchRadius);

            LOGGER.info(String.format("Receiver at (%.1f, %.1f, %.1f)m",
                    receiverCoord.x, receiverCoord.y, receiverCoord.z));
            LOGGER.info(String.format("Search envelope: [%.1f, %.1f] × [%.1f, %.1f] (radius=%.1fm)",
                    envelope.getMinX(), envelope.getMaxX(), 
                    envelope.getMinY(), envelope.getMaxY(), searchRadius));

            // initialize DefaultTableLoader
            DefaultTableLoader tableLoader = new DefaultTableLoader();
            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker("NO_BUILDINGS_TABLE", "LW_ROADS", "NO_RECEIVERS_TABLE");
            noiseMapByReceiverMaker.setInputMode(
                SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_ATTENUATION
            );
            noiseMapByReceiverMaker.setMainEnvelope(envelope);
            DefaultProgressVisitor progressVisitor = new DefaultProgressVisitor(1, null);
            noiseMapByReceiverMaker.initialize(connection, progressVisitor);
            tableLoader.initialize(connection, noiseMapByReceiverMaker);

            LOGGER.info("--- Initializing Scene then Loading and Registering Sources using fetchCellSource method---");
            SceneWithEmission scene = new SceneWithEmission(profileBuilder);

            tableLoader.fetchCellSource(connection, envelope, scene, true);

            
            // ============================================================
            // Step 5 & 6 Validation
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("--- Validation: Loaded and Registered Sources ---");
            
            // Validate number of loaded sources
            int sourceCount = scene.countSources();
            assertEquals(4, sourceCount, "Should load and register 4 sources within envelope");
            LOGGER.info(String.format("Total sources in scene: %d", sourceCount));
            
            // Validate geometry types
            List<Geometry> sourceGeometries = scene.getSourceGeometries();
            for (Geometry geom : sourceGeometries) {
                assertNotNull(geom, "Geometry should not be null");
                assertFalse(geom.isEmpty(), "Geometry should not be empty");
                assertEquals("LineString", geom.getGeometryType(), "All sources should be LineString");
            }
            
            LOGGER.info("All geometries validated: LineString type");
            
            // Validate BridgeRelationship classification
            LOGGER.info("");
            LOGGER.info("--- BridgeRelationship Classification ---");
            LOGGER.info(String.format("bridgeRelationships.size() = %d", scene.bridgeRelationships.size()));
            LOGGER.info(String.format("sourcesPk.size() = %d", scene.sourcesPk.size()));
            
            int roadSourceCount = 0;
            int bridgeSourceOnBridgeCount = 0;
            int bridgeSourceUnderBridgeCount = 0;
            
            for (Long sourcePk : scene.bridgeRelationships.keySet()) {
                var bridgeProperty = scene.bridgeRelationships.get(sourcePk);
                
                String relationTypeDesc;
                if (bridgeProperty.getBridgePkOn() >= 0) {
                    bridgeSourceOnBridgeCount++;
                    relationTypeDesc = String.format("ACTUAL_SOURCE_ON_BRIDGE (bridgePkOn=%d)",
                            bridgeProperty.getBridgePkOn());
                } else if (bridgeProperty.getBridgePkAbove() >= 0) {
                    bridgeSourceUnderBridgeCount++;
                    relationTypeDesc = String.format("IMAGINARY_SOURCE_UNDER_BRIDGE (bridgePkAbove=%d)",
                            bridgeProperty.getBridgePkAbove());
                } else {
                    roadSourceCount++;
                    relationTypeDesc = "SOURCE_NOT_RELATED_TO_BRIDGE";
                }
                
                LOGGER.info(String.format("Source PK=%d: %s", sourcePk, relationTypeDesc));
            }
            
            LOGGER.info("");
            LOGGER.info(String.format("SOURCE_NOT_RELATED_TO_BRIDGE: %d sources (Road 1, Road 3)", roadSourceCount));
            LOGGER.info(String.format("ACTUAL_SOURCE_ON_BRIDGE: %d sources (Road 2 traffic noise)", bridgeSourceOnBridgeCount));
            LOGGER.info(String.format("IMAGINARY_SOURCE_UNDER_BRIDGE: %d sources (Road 2 structural noise)", bridgeSourceUnderBridgeCount));
            
            assertEquals(2, roadSourceCount, "Should have 2 sources not related to bridge");
            assertEquals(1, bridgeSourceOnBridgeCount, "Should have 1 source on bridge");
            assertEquals(1, bridgeSourceUnderBridgeCount, "Should have 1 imaginary source under bridge");
            
            // ============================================================
            // Step 7: LineString Point Sampling & Elevation Conversion
            // Samples LineString geometries into discrete point sources with absolute elevations
            // as per source_algorithms.md Step 7: LineString Point Sampling and Elevation Conversion
            // ============================================================
            LOGGER.info("");
            LOGGER.info("========================================");
            LOGGER.info("Step 7: LineString Point Sampling & Elevation Conversion");
            LOGGER.info("========================================");
            
            List<SourcePointInfo> sourceList = SourceCollector.collectSourcePoints(receiverPointInfo, scene);

            HashMap<Long, HashMap<BridgeRelationship.RelationType, Integer>> sourcePointCountMap = new HashMap<>();
            for (SourcePointInfo sourcePointInfo : sourceList) {
                long sourcePk = sourcePointInfo.getSourcePk();
                BridgeRelationship.RelationType relationType = sourcePointInfo.getBridgeRelationship().getRelationType();

                if(sourcePointCountMap.get(sourcePk) == null) {
                    HashMap<BridgeRelationship.RelationType, Integer> relationTypeCountMap = new HashMap<>();
                    sourcePointCountMap.put(sourcePk, relationTypeCountMap);
                }
                sourcePointCountMap.get(sourcePk).put(relationType, sourcePointCountMap.get(sourcePk).getOrDefault(relationType, 0) + 1);

                if (relationType == BridgeRelationship.RelationType.SOURCE_NOT_RELATED_TO_BRIDGE) {
                    double z = sourcePointInfo.getCoordinate().z;
                    
                    AtomicInteger triangleHint = new AtomicInteger(-1);
                    double profileZ = profileBuilder.getZGround(sourcePointInfo.getCoordinate(), triangleHint);

                    assertEquals(0.05, z - profileZ, 0.001,
                            String.format("Source NOT related to bridge: Z=%.3f should be 0.05m above ground Z=%.3f", z, profileZ));
                } else if (relationType == BridgeRelationship.RelationType.ACTUAL_SOURCE_ON_BRIDGE){
                    double z = sourcePointInfo.getCoordinate().z;
                    long bridgePkOn = sourcePointInfo.getBridgeRelationship().getBridgePkOn();
                    
                    double deckHeight = profileBuilder.getBridgeByPk(bridgePkOn).getDeckHeightAtPoint(sourcePointInfo.getCoordinate());
                    assertEquals(0.05, z - deckHeight, 0.001,
                            String.format("Actual Source ON bridge: Z=%.3f should be 0.05m above deck Z=%.3f", z, deckHeight));
                } else if(relationType == BridgeRelationship.RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE) {
                    double z = sourcePointInfo.getCoordinate().z;
                    long bridgePkAbove = sourcePointInfo.getBridgeRelationship().getBridgePkAbove();
                    
                    double deckHeight = profileBuilder.getBridgeByPk(bridgePkAbove).getDeckHeightAtPoint(sourcePointInfo.getCoordinate());
                    double deckThickness = profileBuilder.getBridgeByPk(bridgePkAbove).getDeckThicknessAtPoint(sourcePointInfo.getCoordinate());


                    assertEquals(-0.05, z - (deckHeight - deckThickness), 0.001,
                            String.format("Imaginary Source UNDER bridge: Z=%.3f should be -0.05m below ground Z=%.3f", z, deckHeight - deckThickness));
                } else if(relationType == BridgeRelationship.RelationType.MIRROR_SOURCE){
                    double z = sourcePointInfo.getCoordinate().z;
                    long bridgePkAbove = sourcePointInfo.getBridgeRelationship().getBridgePkAbove();
                    
                    double deckHeight = profileBuilder.getBridgeByPk(bridgePkAbove).getDeckHeightAtPoint(sourcePointInfo.getCoordinate());
                    double deckThickness = profileBuilder.getBridgeByPk(bridgePkAbove).getDeckThicknessAtPoint(sourcePointInfo.getCoordinate());
                    
                    AtomicInteger triangleHint = new AtomicInteger(-1);
                    double profileZ = profileBuilder.getZGround(sourcePointInfo.getCoordinate(), triangleHint);

                    assertEquals(0.05, z - 2 * (deckHeight - deckThickness - profileZ - 0.05), 0.001, String.format("Mirror Source ON bridge: Z=%.3f should be the mirror of the 0.05m above the ground Z=%.3f", z, profileZ));
                }
            }
            
            for (Long sourcePk : sourcePointCountMap.keySet()) {
                HashMap<BridgeRelationship.RelationType, Integer> pointCountMap = sourcePointCountMap.get(sourcePk);
                LOGGER.info(String.format("SourcePK: %d", sourcePk));

                for (BridgeRelationship.RelationType relationType : pointCountMap.keySet()) {
                    int typeCount = pointCountMap.get(relationType);
                    LOGGER.info(String.format("  %s: %d sampled points", relationType, typeCount));
                }
            }

                
        }
    }
}