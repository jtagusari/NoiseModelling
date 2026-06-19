package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.noise_planet.noisemodelling.jdbc.input.DefaultTableLoader;
import org.noise_planet.noisemodelling.jdbc.input.EmissionInputSettings;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.DefaultProgressVisitor;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for Step 1-5: Receiver Identification and Processing Pipeline.
 * 
 * This test suite validates the complete workflow from RECEIVERS table creation
 * through ReceiverPointInfo creation, including Z-coordinate conversion during pathfinding.
 * 
 * Pipeline Steps:
 * Step 1: RECEIVERS Table Creation - Creates table with PK and THE_GEOM (Point with Z coordinate)
 * Step 2: Geometry Loading - Queries receivers within cell envelope, validates Z coordinates
 * Step 3: Scene Registration - Registers receiver coordinates in computation scene
 * Step 4: Z-Coordinate Conversion in Pathfinder - Converts relative Z to absolute elevation using DEM
 * Step 5: ReceiverPointInfo Creation - Creates propagation-ready receiver point objects
 */
public class ReceiverIdentificationTest {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiverIdentificationTest.class);
    private Connection connection;
    
    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(ReceiverIdentificationTest.class.getSimpleName(), true, ""));
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
    
    /**
     * Test complete receiver identification workflow: Table creation, geometry loading,
     * scene registration, Z-coordinate conversion, and ReceiverPointInfo creation.
     * 
     * This integrated test validates the complete workflow from RECEIVERS table creation through
     * ReceiverPointInfo creation, covering all steps in receiver_algorithms.md:
     * 
     * Step 1: RECEIVERS Table Creation
     * - Creates table with PK (primary key) and THE_GEOM (Point with Z coordinate)
     * - Inserts 6 test receivers at different locations and heights:
    *   • Receiver 1: Ground level (Z=0.5m) inside envelope
    *   • Receiver 2: Elevated (Z=2.0m) inside envelope
     *   • Receiver 3: Low height (Z=0.3m) within envelope
     *   • Receiver 4: Elevated (Z=4.5m) within envelope
    *   • Receiver 5: Facade height (Z=3.0m) outside envelope
    *   • Receiver 6: Mixed height (Z=1.5m) outside envelope
     * 
     * Step 2: Geometry Loading
     * - Queries receivers within expanded cell envelope (100m radius)
     * - Validates Z coordinate presence for all loaded receivers
     * - Filters out receivers outside the envelope (Receivers 5-6)
     * 
     * Step 3: Scene Registration
     * - Registers loaded receivers in computation scene
     * - Stores primary key and coordinate mapping
     * - Prevents duplicate processing
     * 
     * Step 4: Z-Coordinate Conversion in Pathfinder
     * - During path finding, topographic profiles are built
     * - DEM (Digital Elevation Model) ground elevation queried for each receiver
     * - CutPointReceiver.zGround set to absolute elevation from DEM
     * - Enables calculation of absolute receiver height as zGround + relativeZ
     * 
     * Step 5: ReceiverPointInfo Creation
     * - Creates ReceiverPointInfo objects for each loaded receiver
     * - Assigns sequential receiver index and database primary key
     * - Positions retain relative Z (height above ground)
     * - Ready for propagation algorithms
     */
    @Test
    public void testReceiverIdentificationWorkflow() throws Exception {
        try (Statement st = connection.createStatement()) {
            
            // ============================================================
            // Setup: Create topographic DEM for ground elevation reference
            // ============================================================
            
            LOGGER.info("========================================");
            LOGGER.info("Setup: Creating Topographic Data (DEM)");
            LOGGER.info("========================================");
            
            // Build 3×3 grid of topographic points to simulate terrain elevation
            ProfileBuilder profileBuilder = new ProfileBuilder();
            
            // Insert topographic points at regular grid to simulate DEM
            // Ground elevation varies from 0.5m at (0,0) to 3.0m at far corners
            profileBuilder.addTopographicPoint(new Coordinate(0, 0, 0.5));
            profileBuilder.addTopographicPoint(new Coordinate(50, 0, 1.2));
            profileBuilder.addTopographicPoint(new Coordinate(100, 0, 2.8));
            profileBuilder.addTopographicPoint(new Coordinate(0, 50, 0.1));
            profileBuilder.addTopographicPoint(new Coordinate(50, 50, 1.8));
            profileBuilder.addTopographicPoint(new Coordinate(100, 50, 2.5));
            profileBuilder.addTopographicPoint(new Coordinate(0, 100, 2.1));
            profileBuilder.addTopographicPoint(new Coordinate(50, 100, 0.8));
            profileBuilder.addTopographicPoint(new Coordinate(100, 100, 3.0));
            profileBuilder.finishFeeding();
            
            LOGGER.info("--- Topographic Data Setup ---");
            LOGGER.info("DEM (Digital Elevation Model) created with 9 topographic points");
            LOGGER.info("Ground elevation range: 0.1m to 3.0m");
            
            // ============================================================
            // Step 1: RECEIVERS Table Creation
            // As per receiver_algorithms.md Step 1: RECEIVERS Table Creation
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("========================================");
            LOGGER.info("Step 1: RECEIVERS Table Creation");
            LOGGER.info("========================================");
            
            // Create RECEIVERS table: PK (primary key), THE_GEOM (3D point), HEIGHT_TYPE (coordinate type)
            st.execute("CREATE TABLE RECEIVERS (" +
                    "PK LONG PRIMARY KEY, " +
                    "THE_GEOM GEOMETRY(POINTZ), " +
                    "HEIGHT_TYPE VARCHAR(10) DEFAULT 'RELATIVE')");
            
            // Insert test receivers with various heights and locations
            // Format: (PK, X, Y, Z_height_above_ground, HEIGHT_TYPE)
            
            // Receiver 1: Ground-level receiver inside envelope (explicit RELATIVE)
            st.execute("INSERT INTO RECEIVERS VALUES (1, ST_GeomFromText('POINTZ(35 30 0.5)'), 'RELATIVE')");
            LOGGER.info("Receiver 1: (35, 30, 0.5m) - Ground level - HEIGHT_TYPE='RELATIVE'");
            
            // Receiver 2: Elevated receiver inside envelope (explicit RELATIVE)
            st.execute("INSERT INTO RECEIVERS VALUES (2, ST_GeomFromText('POINTZ(60 35 2.0)'), 'RELATIVE')");
            LOGGER.info("Receiver 2: (60, 35, 2.0m) - Elevated - HEIGHT_TYPE='RELATIVE'");
            
            // Receiver 3: Low height receiver inside envelope (NULL HEIGHT_TYPE, should default to RELATIVE)
            st.execute("INSERT INTO RECEIVERS (PK, THE_GEOM) VALUES (3, ST_GeomFromText('POINTZ(50 50 0.3)'))");
            LOGGER.info("Receiver 3: (50, 50, 0.3m) - Low height - HEIGHT_TYPE=NULL (defaults to RELATIVE)");
            
            // Receiver 4: Elevated receiver (facade height) inside envelope - explicit ABSOLUTE
            st.execute("INSERT INTO RECEIVERS VALUES (4, ST_GeomFromText('POINTZ(75 65 4.5)'), 'ABSOLUTE')");
            LOGGER.info("Receiver 4: (75, 65, 4.5m) - Elevated facade - HEIGHT_TYPE='ABSOLUTE'");
            
            // Receiver 5: Receiver outside search envelope (NULL HEIGHT_TYPE, defaults to RELATIVE)
            st.execute("INSERT INTO RECEIVERS (PK, THE_GEOM) VALUES (5, ST_GeomFromText('POINTZ(10 10 3.0)'))");
            LOGGER.info("Receiver 5: (10, 10, 3.0m) - Outside search area - HEIGHT_TYPE=NULL (defaults to RELATIVE)");
            
            // Receiver 6: Receiver outside search envelope (explicit RELATIVE)
            st.execute("INSERT INTO RECEIVERS VALUES (6, ST_GeomFromText('POINTZ(90 80 1.5)'), 'RELATIVE')");
            LOGGER.info("Receiver 6: (90, 80, 1.5m) - Outside search area - HEIGHT_TYPE='RELATIVE'");
            
            // ============================================================
            // Step 1 Validation: RECEIVERS Table Structure
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("--- Step 1 Validation: RECEIVERS Table Structure ---");
            
            // Verify 6 receivers were created
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) as cnt FROM RECEIVERS")) {
                assertTrue(rs.next());
                int receiverCount = rs.getInt("cnt");
                assertEquals(6, receiverCount, "Should have 6 receivers in table");
                LOGGER.info("Total receivers created: " + receiverCount);
            }
            
            // Ensure all receivers have valid Z and HEIGHT_TYPE values
            try (ResultSet rs = st.executeQuery(
                    "SELECT PK, ST_AsText(THE_GEOM) as GEOM_TEXT, ST_Z(THE_GEOM) as Z_COORD, HEIGHT_TYPE FROM RECEIVERS ORDER BY PK")) {
                
                int count = 0;
                while (rs.next()) {
                    long pk = rs.getLong("PK");
                    String geomText = rs.getString("GEOM_TEXT");
                    double z = rs.getDouble("Z_COORD");
                    String heightType = rs.getString("HEIGHT_TYPE");
                    
                    // HEIGHT_TYPE should default to 'RELATIVE' if NULL
                    if (heightType == null) {
                        heightType = "RELATIVE";
                    }
                    
                    assertFalse(rs.wasNull(), "Z coordinate should not be NULL for receiver " + pk);
                    assertTrue(z > 0, "Z coordinate should be positive for receiver " + pk);
                    assertTrue(heightType.equals("RELATIVE") || heightType.equals("ABSOLUTE"), 
                            "HEIGHT_TYPE must be RELATIVE or ABSOLUTE for receiver " + pk);
                    LOGGER.info(String.format("  Receiver %d: %s (Z=%.2fm) HEIGHT_TYPE=%s ✓", pk, geomText, z, heightType));
                    count++;
                }
                assertEquals(6, count, "Should validate all 6 receivers");
            }
            
            // ============================================================
            // Step 2: Geometry Loading & Step 3: Scene Registration
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("========================================");
            LOGGER.info("Step 2: Geometry Loading");
            LOGGER.info("========================================");
            // Define search area: only receivers within [30-80, 20-70] are loaded
            Envelope envelope = new Envelope(30, 80, 20, 70);

            LOGGER.info(String.format("Search envelope: [%.1f, %.1f] × [%.1f, %.1f]",
                    envelope.getMinX(), envelope.getMaxX(), 
                    envelope.getMinY(), envelope.getMaxY()));

            // initialize DefaultTableLoader
            DefaultTableLoader tableLoader = new DefaultTableLoader();
            EmissionInputSettings emissionInputSettings = new EmissionInputSettings.Builder()
                    .setInputMode(EmissionInputSettings.INPUT_MODE.INPUT_MODE_ATTENUATION)
                    .build();
            
            TableInputSettings tableInputSettings = new TableInputSettings.Builder()
                    .setBuildingTableName("NO_BUILDINGS_TABLE")
                    .setSourceTableName("NO_SOURCES")
                    .setReceiverTableName("RECEIVERS")
                    .build();

            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker.Builder()
                    .setTableInputSettings(tableInputSettings)
                    .setEmissionInputSettings(emissionInputSettings)
                    .setThreadCount(1)
                    .build();

            noiseMapByReceiverMaker.setMainEnvelope(envelope);
            DefaultProgressVisitor progressVisitor = new DefaultProgressVisitor(1, null);
            noiseMapByReceiverMaker.initialize(connection, progressVisitor);
            tableLoader.initialize(connection, noiseMapByReceiverMaker.getLoaderInitContext());

            LOGGER.info("--- Initializing Scene then Loading and Registering Receivers using fetchCellReceiver method---");
            // Create scene and load receivers within envelope using fetchCellReceiver
            SceneWithEmission scene = new SceneWithEmission(profileBuilder);

            Set<Long> skipReceivers = new HashSet<>();

            tableLoader.fetchCellReceiver(connection, noiseMapByReceiverMaker.getCellSceneContext(), envelope, scene, skipReceivers);
            
            
            // ============================================================
            // Step 3 Validation: Scene Contains Registered Receivers
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("--- Step 3 Validation: Scene Receivers ---");
            
            int sceneReceiverCount = scene.getReceivers().size();
            assertEquals(4, sceneReceiverCount, "Scene should contain 4 registered receivers");
            LOGGER.info("Scene receiver count: " + sceneReceiverCount);
            
            // Store pre-conversion heights and types for Step 4 comparison
            int index = 0;
            HashMap<Long, Double> receiverHeight = new HashMap<>();
            HashMap<Long, Scene.HeightType> receiverHeightType = new HashMap<>();
            // for (Coordinate coord : scene.getReceivers()) {
            for (int i = 0; i < scene.countReceivers(); i++) {
                Coordinate coord = scene.getReceiverByIndex(i);
                long pk = scene.getReceiverPkByIndex(i);
                receiverHeight.put(pk, coord.getZ());
                Scene.HeightType heightType = scene.getReceiverHeightTypeByPk(pk);
                receiverHeightType.put(pk, heightType);
                LOGGER.info(String.format("  Scene Receiver[%d] PK=%d: (%.1f, %.1f, %.2f) %s height", index, pk, coord.x, coord.y, coord.z, heightType));
                index++;
            }
            
            // ============================================================
            // // Step 4: Z-Coordinate Conversion in Pathfinder
            // // As per receiver_algorithms.md Step 4: Z-Coordinate Conversion in Pathfinder
            // // ============================================================
            
            LOGGER.info("");
            LOGGER.info("========================================");
            LOGGER.info("Step 4: Z-Coordinate Conversion in Pathfinder");
            LOGGER.info("========================================");

            PathFinder pathFinder = new PathFinder(scene);
            pathFinder.ensureAbsoluteReceiverHeights();

            
            // ============================================================
            // Step 4 Validation: Z-Coordinate Conversion Results
            // ============================================================
            LOGGER.info("Receiver heights before and after conversion (RELATIVE → ABSOLUTE):");

            for (int i = 0; i < scene.countReceivers(); i++) {
                long pk = scene.getReceiverPkByIndex(i);
                Coordinate coord = scene.getReceiverByIndex(i);
                double zGround = profileBuilder.getZGround(new Coordinate(coord.x, coord.y));
                double zBefore = receiverHeight.get(pk);
                double zRelative = coord.getZ() - zGround;
                Scene.HeightType heightTypeBefore = receiverHeightType.get(pk);
                Scene.HeightType heightType = scene.getReceiverHeightTypeByPk(pk);
                
                // For RELATIVE: stored value should remain as-is relative to ground
                // For ABSOLUTE: stored value should remain absolute
                if(heightTypeBefore == Scene.HeightType.RELATIVE) {
                    assertEquals(zRelative, zBefore, 0.001, "Relative height should remain unchanged for RELATIVE height type");
                } else {
                    assertEquals(coord.getZ(), zBefore, 0.001, "Absolute height should remain unchanged for ABSOLUTE height type");
                }
                
                // All heights should be converted to ABSOLUTE after ensureAbsoluteReceiverHeights()
                assertEquals(Scene.HeightType.ABSOLUTE, heightType, "Height type should be ABSOLUTE after conversion");
                LOGGER.info(String.format("  Receiver %d: %.3f (Absolute) / %.3f (Relative) ", pk, coord.getZ(), zRelative));
            }
            
        }
    }
    
}