package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.ReceiverPointInfo;
import org.noise_planet.noisemodelling.pathfinder.SourceCollector;
import org.noise_planet.noisemodelling.pathfinder.SourcePointInfo;
import org.noise_planet.noisemodelling.pathfinder.path.BridgeRelationship;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.jdbc.input.CellProfileLoader;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgePoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig.FrequencyBand;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.jdbc.input.DefaultTableLoader;
import org.noise_planet.noisemodelling.jdbc.input.EmissionInputSettings;
import org.noise_planet.noisemodelling.jdbc.input.PropagationSettings;
import org.noise_planet.noisemodelling.jdbc.input.SourceEmission;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.DefaultProgressVisitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
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
     * Loads test data from GeoJSON files into database tables.
     * 
     * Loads BRIDGE_POINTS table from bridge_points.geojson:
     * - Bridge 100: 3 CENTER points (X=0, 50, 100) at Y=20 with absolute deck height 10.0m
    * - Bridge 200: 1 CENTER point far from calculation region ([0,100] x [0,100])
     * 
     * Loads ROADS table from roads.geojson:
     * - Road 1: Regular road (BRIDGE_PK=NULL) at Y=0, moderate speed
     * - Road 2: Highway on bridge (BRIDGE_PK=100) at Y=20, high speed
     * - Road 3: Crossing road (BRIDGE_PK=NULL), all equal speeds
     * - Road 4: Remote road (BRIDGE_PK=NULL) far from calculation region ([0,100] × [0,100])
     * 
     * Loads DEM table from dem.geojson:
    * - 10 topographic points: 9 in 3x3 grid covering [0,100] × [0,100] with Z values [0.1, 3.0]m
    * - 1 remote point far from calculation region ([0,100] × [0,100])
     * 
     * @throws Exception if GeoJSON files cannot be read or loaded
     */
    private void loadTestDataFromGeoJson() throws Exception {
        try (Statement st = connection.createStatement()) {
            // Load BRIDGE_POINTS table from GeoJSON
            // Bridge 100: 3 CENTER points used for deck geometry generation
            st.execute(String.format("CALL GeoJsonRead('%s', 'BRIDGE_POINTS')", 
                SourceIdentificationTest.class.getResource("/bridge_points.geojson").getFile()));
            
            // Load ROADS table from GeoJSON
            // Contains 4 roads with Day/Evening/Night traffic data and speed profiles
            st.execute(String.format("CALL GeoJsonRead('%s', 'ROADS')", 
                SourceIdentificationTest.class.getResource("/roads.geojson").getFile()));
            // Set PK column to NOT NULL before adding PRIMARY KEY constraint
            st.execute("ALTER TABLE ROADS ALTER COLUMN PK SET NOT NULL");
            st.execute("ALTER TABLE ROADS ADD CONSTRAINT PK_SET PRIMARY KEY (PK)");
            
            // Load DEM table from GeoJSON
            // Contains 10 topographic points (9 in 3x3 grid + 1 remote) for ground elevation modeling
            st.execute(String.format("CALL GeoJsonRead('%s', 'DEM')", 
                SourceIdentificationTest.class.getResource("/dem.geojson").getFile()));
        }
    }
    
    @Test
    public void testGeometryPreparation() throws Exception{
        
        // Load test data from GeoJSON files
        loadTestDataFromGeoJson();

        LOGGER.info("========================================");
        LOGGER.info("testGeometryPreparation: Step 1-2 Validation");
        LOGGER.info("========================================");
        
        try (Statement st = connection.createStatement()) {
            // ============================================================
            // Step 1: ROADS Table Creation and Validation
            // Validates user-provided ROADS table with traffic and geometry data.
            // Source: source_algorithms.md Step 1 - ROADS Table Creation
            // 
            // Required fields: THE_GEOM (LineString), PK, LV_D/HGV_D (traffic flow),
            //                  LV_SPD_D/HGV_SPD_D (speed), optional: BRIDGE_PK
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("========== Step 1: ROADS Table Creation and Validation ==========");
            LOGGER.info("Input data format: Standard Format (Traffic Flow + Speed per period)");
            
            // Verify initial data (4 roads including one far from calculation region)
            int roadsCount = 0;
            int roadsOnBridgeCount = 0;
            int roadRegularCount = 0;
            
            try (ResultSet rs = st.executeQuery("SELECT * FROM ROADS ORDER BY PK")) {
                
                // Road 1: Regular road (not on bridge)
                assertTrue(rs.next());
                roadsCount++;
                roadRegularCount++;
                assertEquals(1, rs.getLong("PK"));
                assertEquals(600.0, rs.getDouble("LV_D"), 0.001);
                rs.getLong("BRIDGE_PK");
                assertTrue(rs.wasNull(), "Road 1 should not be on a bridge");
                LOGGER.info(String.format("  Road 1 (PK=1): LV_D=600 vehicles/h, BRIDGE_PK=NULL (regular road)"));
                
                // Road 2: Highway on bridge
                assertTrue(rs.next());
                roadsCount++;
                roadsOnBridgeCount++;
                assertEquals(2, rs.getLong("PK"));
                assertEquals(1200.0, rs.getDouble("LV_D"), 0.001);
                assertEquals(100, rs.getLong("BRIDGE_PK"), "Road 2 should be on bridge 100");
                LOGGER.info(String.format("  Road 2 (PK=2): LV_D=1200 vehicles/h, BRIDGE_PK=100 (on-bridge)"));
                
                // Road 3: Crossing road (not on bridge)
                assertTrue(rs.next());
                roadsCount++;
                roadRegularCount++;
                assertEquals(3, rs.getLong("PK"));
                assertEquals(600.0, rs.getDouble("LV_D"), 0.001);
                rs.getLong("BRIDGE_PK");
                assertTrue(rs.wasNull(), "Road 3 should not be on a bridge");
                LOGGER.info(String.format("  Road 3 (PK=3): LV_D=600 vehicles/h, BRIDGE_PK=NULL (regular road)"));

                // Road 4: Road far from calculation region [0,100] × [0,100]
                assertTrue(rs.next());
                roadsCount++;
                roadRegularCount++;
                assertEquals(4, rs.getLong("PK"));
                assertEquals(800.0, rs.getDouble("LV_D"), 0.001);
                rs.getLong("BRIDGE_PK");
                assertTrue(rs.wasNull(), "Road 4 should not be on a bridge");
                
                // Verify Road 4 is far from [0,100] × [0,100] region
                Geometry road4Geom = (Geometry) rs.getObject("THE_GEOM");
                Coordinate[] road4Coords = road4Geom.getCoordinates();
                assertTrue(road4Coords.length > 0);
                for (Coordinate coord : road4Coords) {
                    assertTrue(coord.x >= 500 || coord.y >= 500, 
                        "Road 4 coordinates should be far from [0,100] region");
                }
                LOGGER.info(String.format("  Road 4 (PK=4): LV_D=800 vehicles/h, BRIDGE_PK=NULL (remote, outside [0,100]×[0,100])"));
                
                assertFalse(rs.next(), "Should have exactly 4 roads");
            }
            
            LOGGER.info("");
            LOGGER.info("Step 1 Validation Summary:");
            LOGGER.info(String.format("  ✓ Total roads in ROADS table: %d", roadsCount));
            LOGGER.info(String.format("  ✓ Regular roads (BRIDGE_PK=NULL): %d (PKs: 1, 3, 4)", roadRegularCount));
            LOGGER.info(String.format("  ✓ On-bridge roads (BRIDGE_PK specified): %d (PKs: 2)", roadsOnBridgeCount));
            LOGGER.info("  ✓ All traffic fields (LV_D, LV_SPD_D, etc.) populated");

            // Verify Bridge table has 3 CENTER points for Bridge 100
            LOGGER.info("");
            LOGGER.info("Verifying BRIDGE_POINTS table structure (Bridge 100):");
            try (ResultSet rs = st.executeQuery(
                    "SELECT POSITION, COUNT(*) as cnt FROM BRIDGE_POINTS WHERE BRIDGE_PK=100 GROUP BY POSITION")) {
                
                assertTrue(rs.next());
                assertEquals("CENTER", rs.getString("POSITION"));
                assertEquals(3, rs.getInt("cnt"), "Should have exactly 3 CENTER points for Bridge 100");
                LOGGER.info("  ✓ Bridge 100: 3 CENTER points at X=0, 50, 100; Y=20");
                
                // No LEFT or RIGHT points (they will be generated by BridgeGeometryBuilder)
                assertFalse(rs.next(), "Should only have CENTER points for Bridge 100");
            }
    
            // ============================================================
            // Step 2: Cell Selection and Scene Context Preparation
            // Loads geometry data (DEM, bridges) into ProfileBuilder with expanded envelope.
            // Source: source_algorithms.md Step 2
            // 
            // Step 2a: Cell Envelope Computation
            // Step 2b: ProfileBuilder Creation
            // Step 2c: Geometry Data Loading (DEM, Bridges)
            // Step 2d: ProfileBuilder Finalization (Critical - builds spatial indices)
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("========== Step 2: Scene Context Preparation ==========");
            
            // Step 2a: Load Bridge Points and create Bridge object
            LOGGER.info("");
            LOGGER.info("Step 2a: Bridge Geometry Construction");
            LOGGER.info("  Loading bridge points from BRIDGE_POINTS table -> Bridge object...");
            
            List<BridgePoint> bridgePointsList = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT * FROM BRIDGE_POINTS WHERE BRIDGE_PK=100 ORDER BY PK")) {
                while (rs.next()) {
                    bridgePointsList.add(new BridgePoint(rs));
                }
            }
            assertEquals(3, bridgePointsList.size(), "Should have loaded 3 CENTER bridge points");
            LOGGER.info(String.format("  ✓ Loaded %d bridge points from BRIDGE_POINTS table", bridgePointsList.size()));
            
            // Create Bridge object with alpha values (absorption coefficients)
            List<Double> alphas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                alphas.add(0.5); // Default alpha values for 8 frequency bands
            }
            Bridge bridge = new Bridge.Builder(bridgePointsList).withAlphas(alphas).build();
            LOGGER.info("  ✓ Bridge object constructed with geometry and acoustic properties");
            
            // Step 2b: ProfileBuilder Creation and Initialization
            LOGGER.info("");
            LOGGER.info("Step 2b: ProfileBuilder Initialization");
            ProfileBuilder profileBuilder = new ProfileBuilder();
            profileBuilder.addBridge(bridge);
            LOGGER.info("  ✓ ProfileBuilder created and Bridge 100 added");
            
            // Step 2c: Load DEM (topographic points) into ProfileBuilder
            LOGGER.info("");
            LOGGER.info("Step 2c: Loading Geometry Data (DEM) into ProfileBuilder");
            LOGGER.info("  Loading topographic points from DEM table...");
            
            int demPointCount = 0;
            try (ResultSet rs = st.executeQuery("SELECT THE_GEOM FROM DEM")) {
                while (rs.next()) {
                    Geometry geom = (Geometry) rs.getObject("THE_GEOM");
                    if (geom != null) {
                        profileBuilder.addTopographicPoint(geom.getCoordinate());
                        demPointCount++;
                    }
                }
            }
            LOGGER.info(String.format("  ✓ Loaded %d topographic points from DEM table", demPointCount));
            
            // Step 2d: ProfileBuilder Finalization (Critical step)
            // Builds spatial indices (STRtree) for geometry data.
            // After finishFeeding(), no new geometry can be added.
            LOGGER.info("");
            LOGGER.info("Step 2d: ProfileBuilder Finalization (Critical)");
            LOGGER.info("  Building spatial indices (STRtree) for efficient geometry queries...");
            profileBuilder.finishFeeding();
            LOGGER.info("  ✓ ProfileBuilder finalized - spatial indices built");

            // ============================================================
            // Bridge Geometry Validation
            // Validates that Bridge geometry (deck) was generated correctly
            // from the loaded bridge points
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("========== Bridge Geometry Generation Validation ==========");
            
            Geometry deckGeometry = bridge.getDeckGeometry();
            Bridge.GirderType girderType = bridge.getGirderType();
            Bridge.SlabType slabType = bridge.getSlabType();
            
            assertNotNull(bridge, "Bridge should be constructed successfully");
            assertNotNull(deckGeometry, "Deck geometry should be generated successfully");
            assertNotNull(girderType, "Girder type should be set");
            assertNotNull(slabType, "Slab type should be set");
            
            LOGGER.info("Bridge 100 Properties:");
            LOGGER.info(String.format("  ✓ Bridge PK: %d", bridge.getPrimaryKey()));
            LOGGER.info(String.format("  ✓ Deck geometry type: %s", deckGeometry.getGeometryType()));
            LOGGER.info(String.format("  ✓ Deck geometry: %s", deckGeometry));
            LOGGER.info(String.format("  ✓ Girder type: %s (affects reflection modeling)", girderType));
            LOGGER.info(String.format("  ✓ Slab type: %s (affects reflection modeling)", slabType));
            
            // ============================================================
            // Bridge Spatial Containment Validation
            // Uses H2GIS spatial functions to verify road-bridge relationships
            // ST_Intersects: checks if geometries overlap
            // ST_Contains: checks if one geometry completely contains another
            // ============================================================
            
            LOGGER.info("");
            LOGGER.info("========== Bridge Spatial Containment Validation (H2GIS) ==========");
            LOGGER.info("Validating road-bridge spatial relationships using H2GIS functions:");
            
            // Insert deck geometry into temporary table for H2GIS spatial queries
            st.execute("CREATE TEMPORARY TABLE TEMP_BRIDGE_DECK (BRIDGE_PK INTEGER, THE_GEOM GEOMETRY)");
            st.execute(String.format("INSERT INTO TEMP_BRIDGE_DECK VALUES (100, ST_GeomFromText('%s'))",
                    deckGeometry.toText()));
            
            LOGGER.info("  Deck geometry inserted into TEMP_BRIDGE_DECK table");
            LOGGER.info("");
            LOGGER.info("Checking spatial relationships:");
            
            try (ResultSet rs = st.executeQuery(
                    "SELECT r.PK, " +
                    "ST_Intersects(r.THE_GEOM, b.THE_GEOM) as intersects, " +
                    "ST_Contains(b.THE_GEOM, r.THE_GEOM) as contains " +
                    "FROM ROADS r, TEMP_BRIDGE_DECK b " +
                    "WHERE r.PK IN (1, 2) " +
                    "ORDER BY r.PK")) {
                
                // Road 1 (Y=0) should NOT intersect with bridge deck
                assertTrue(rs.next());
                assertEquals(1, rs.getLong("PK"));
                boolean road1Intersects = rs.getBoolean("intersects");
                boolean road1Contains = rs.getBoolean("contains");
                
                assertFalse(road1Intersects, 
                        "Road 1 (Y=0) should NOT intersect with Bridge deck (ST_Intersects)");
                assertFalse(road1Contains, 
                        "Road 1 (Y=0) should NOT be contained by Bridge deck (ST_Contains)");
                LOGGER.info(String.format("  Road 1 (PK=1, Y=0):"));
                LOGGER.info(String.format("    ├─ ST_Intersects(Road, Deck) = %s ✓", road1Intersects));
                LOGGER.info(String.format("    └─ ST_Contains(Deck, Road) = %s ✓", road1Contains));
                LOGGER.info("    └─ Result: Road NOT related to bridge (as expected)");
                
                // Road 2 (Y=20) should intersect and be contained by bridge deck
                assertTrue(rs.next());
                assertEquals(2, rs.getLong("PK"));
                boolean road2Intersects = rs.getBoolean("intersects");
                boolean road2Contains = rs.getBoolean("contains");
                
                assertTrue(road2Intersects, 
                        "Road 2 (Y=20) should intersect with Bridge deck (ST_Intersects)");
                assertTrue(road2Contains, 
                        "Road 2 (Y=20) should be contained by Bridge deck (ST_Contains)");
                LOGGER.info(String.format("  Road 2 (PK=2, Y=20):"));
                LOGGER.info(String.format("    ├─ ST_Intersects(Road, Deck) = %s ✓", road2Intersects));
                LOGGER.info(String.format("    └─ ST_Contains(Deck, Road) = %s ✓", road2Contains));
                LOGGER.info("    └─ Result: Road is ON bridge (as expected)");
                
                assertFalse(rs.next());
            }
            
            LOGGER.info("");
            LOGGER.info("========== Test Complete - All validations passed ==========");
            LOGGER.info("  ✓ Step 1: ROADS table structure validated");
            LOGGER.info("  ✓ Step 2a: Bridge geometry constructed from bridge points");
            LOGGER.info("  ✓ Step 2c: DEM topographic points loaded into ProfileBuilder");
            LOGGER.info("  ✓ Step 2d: ProfileBuilder finalized with spatial indices");
            LOGGER.info("  ✓ Bridge-road relationships verified by H2GIS spatial functions");
            LOGGER.info("========================================");
        } catch (Exception e) {
            fail("Exception during geometry preparation test: " + e.getMessage());
        }
    }

    @Test
    public void testCellFetch() throws Exception{
        // Load test data from GeoJSON files
        loadTestDataFromGeoJson();

        LOGGER.info("========================================");
        LOGGER.info("testCellFetch: Source Identification Test");
        LOGGER.info("========================================");

        DefaultTableLoader tableLoader = new DefaultTableLoader();
        TableInputSettings tableInputSettings = new TableInputSettings.Builder()
                .setBuildingTableName("NO_BUILDINGS") // No buildings for this test
                .setBuildingHeightFieldName("HEIGHT")
                .setSourceTableName("ROADS")
                .setReceiverTableName("RECEIVERS")
                .setTerrainTableName("DEM")
                .setBridgePointsTableName("BRIDGE_POINTS")
                .build();
        EmissionInputSettings emissionInputSettings = new EmissionInputSettings.Builder()
                .setInputMode(EmissionInputSettings.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW_DEN)
                .build();
        
        PropagationSettings propagationSettings = new PropagationSettings.Builder()
                .setMaximumPropagationDistance(100.0)
                .setMaximumReflectionDistance(50.0)
                .build();

        NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker.Builder()
                .setTableInputSettings(tableInputSettings)
                .setEmissionInputSettings(emissionInputSettings)
                .setPropagationSettings(propagationSettings)
                .build();


        noiseMapByReceiverMaker.setMainEnvelope(new Envelope(0.0, 100.0, 0.0, 100.0));

        DefaultProgressVisitor progressVisitor = new DefaultProgressVisitor(1, null);
        noiseMapByReceiverMaker.initialize(connection, progressVisitor);
        tableLoader.initialize(connection, noiseMapByReceiverMaker.getLoaderInitContext());
        
        Envelope mainEnvelope = new Envelope(0, 100, 0, 100);
        LOGGER.info("Main envelope (calculation region): [0,100] × [0,100]");
        
        // Step 2a: Cell Envelope Computation
        // Expands cell envelope by propagation distance + 2 × reflection distance
        // for continuity between subdomains (see source_algorithms.md Step 2)
        Envelope expandedCellEnvelop = new Envelope(mainEnvelope);
        double maximumPropagationDistance = noiseMapByReceiverMaker.getMaximumPropagationDistance();
        double maximumReflectionDistance = noiseMapByReceiverMaker.getMaximumReflectionDistance();
        expandedCellEnvelop.expandBy(maximumPropagationDistance + 2 * maximumReflectionDistance);
        
        LOGGER.info(String.format("Cell expansion: propDistance=%.1f, reflDistance=%.1f", 
            maximumPropagationDistance, maximumReflectionDistance));
        LOGGER.info(String.format("Expanded envelope: [%.0f,%.0f] × [%.0f,%.0f]",
            expandedCellEnvelop.getMinX(), expandedCellEnvelop.getMaxX(),
            expandedCellEnvelop.getMinY(), expandedCellEnvelop.getMaxY()));

        GeometryFactory geometryFactory = noiseMapByReceiverMaker.getGeometryFactory();

        // Step 2b-2c: ProfileBuilder Creation and Initialization
        // Creates ProfileBuilder and SceneWithEmission before loading geometry
        // (see source_algorithms.md Step 2: "ProfileBuilder is initialized before any geometry data loading")
        ProfileBuilder profileBuilder = new ProfileBuilder(new FrequencyConfig(FrequencyBand.OCTAVE));
        SceneWithEmission scene = new SceneWithEmission(profileBuilder, noiseMapByReceiverMaker.getEmissionInputSettings());
        LOGGER.info("Step 2b: ProfileBuilder and Scene initialized");

        // Step 2c: Geometry Data Loading
        // Loads buildings, DEM, soil areas, and bridges into ProfileBuilder
        // using expanded envelope (see source_algorithms.md Step 2)
        LOGGER.info("Step 2c: Loading geometry context into ProfileBuilder...");
        LOGGER.info("  (NOTE: No buildings scene used - 'NO_BUILDINGS' parameter passed to NoiseMapByReceiverMaker)");
        
        // Note: Buildings would be loaded here with fetchCellBuilding() if available
        // Skipped in this test: tableLoader.fetchCellBuilding(connection, expandedCellEnvelop, scene.profileBuilder, geometryFactory);
        
        // Note: Soil areas would be loaded here with fetchCellGround() if available
        // Skipped in this test: tableLoader.fetchCellGround(connection, expandedCellEnvelop, scene.profileBuilder);
        CellProfileLoader cellProfileLoader = new CellProfileLoader.Builder()
                .setConnection(connection)
                .setCellSceneContext(noiseMapByReceiverMaker.getCellSceneContext())
                .build();
        
        cellProfileLoader.fetchCellTerrain(expandedCellEnvelop, scene.profileBuilder);
        LOGGER.info("  ✓ DEM (topographic points) loaded");

        cellProfileLoader.fetchCellBridge(expandedCellEnvelop, scene.profileBuilder);
        LOGGER.info("  ✓ Bridge geometry (BRIDGE_POINTS table) loaded");

        // Step 2d: ProfileBuilder Finalization (Critical step - MUST be done BEFORE Step 3)
        // Builds spatial indices (STRtree) for geometry data
        // After finishFeeding(), no new geometry can be added
        // (see source_algorithms.md Step 2: "ProfileBuilder must be finalized before source loading")
        LOGGER.info("");
        LOGGER.info("Step 2d: Finalizing ProfileBuilder (building spatial indices)...");
        LOGGER.info("  CRITICAL: This step must be completed BEFORE source loading (Step 3)");
        scene.profileBuilder.finishFeeding();
        LOGGER.info("  ✓ ProfileBuilder finalized - spatial indices ready");
        LOGGER.info("  ✓ No new geometry can be added to ProfileBuilder after finalization");

        // Step 3: Source Loading, Emission Calculation, and Scene Registration
        // Registers source geometries and emission spectra into Scene
        // (see source_algorithms.md Step 3: "fetchCellSource() ... registers geometry and emissions")
        LOGGER.info("");
        LOGGER.info("Step 3: Loading sources and registering emissions...");
        LOGGER.info("  (ProfileBuilder finalization is now complete - proceeds to source loading)");
        tableLoader.fetchCellSource(connection, noiseMapByReceiverMaker.getCellSceneContext(), mainEnvelope, scene, true);
        LOGGER.info("  ✓ Source geometries (LineStrings) registered in Scene");
        LOGGER.info("  ✓ Source emissions (spectra) registered via sourcePk lookup");

        // === Validation: ProfileBuilder State ===
        LOGGER.info("");
        LOGGER.info("Validating ProfileBuilder state after Step 2...");
        assertNotNull(profileBuilder, "ProfileBuilder should be initialized");
        assertTrue(profileBuilder.hasBridges(), "ProfileBuilder should contain bridge data");
        assertEquals(1, profileBuilder.getBridges().size(), "Only Bridge 100 should be in the cell context");
        assertNotNull(profileBuilder.getBridgeByPk(100), "Bridge 100 should be loaded in the cell context");
        assertNull(profileBuilder.getBridgeByPk(200), "Bridge 200 should be outside the cell context");
        LOGGER.info("  ✓ Bridge 100 loaded (Bridge 200 excluded - outside expanded envelope)");

        // === Validation: Scene and Source Loading (Step 3 validation) ===
        LOGGER.info("");
        LOGGER.info("Validating Scene and source registration (Step 3)...");
        assertNotNull(scene, "Scene should be initialized");
        int sourceCount = scene.countSources();
        assertEquals(3, sourceCount, "Only roads inside the main envelope should be loaded as sources");
        LOGGER.info("  ✓ Total sources registered in Scene: " + sourceCount + " (Road 4 excluded - outside main envelope)");

        List<Long> sourcePks = scene.getSourcePks();
        assertTrue(sourcePks.contains(1L), "Road 1 should be loaded as a source");
        assertTrue(sourcePks.contains(2L), "Road 2 should be loaded as a source");
        assertTrue(sourcePks.contains(3L), "Road 3 should be loaded as a source");
        assertFalse(sourcePks.contains(4L), "Road 4 should be outside the main envelope");
        LOGGER.info("  ✓ Source PKs registered: " + sourcePks);

        Geometry envelopeGeometry = geometryFactory.toGeometry(mainEnvelope);
        for (Geometry sourceGeometry : scene.getSourceGeometries()) {
            assertNotNull(sourceGeometry, "Source geometry should not be null");
            assertFalse(sourceGeometry.isEmpty(), "Source geometry should not be empty");
            assertEquals("LineString", sourceGeometry.getGeometryType(), "Source geometry should be a LineString");
            assertTrue(envelopeGeometry.intersects(sourceGeometry),
                "Source geometry should intersect the main envelope");
        }
        LOGGER.info("  ✓ All source geometries are valid LineStrings intersecting main envelope");

        // === Step 3b: Source Height Type and Bridge Relationship ===
        // Each source is classified by its bridge relationship and height type
        // (see source_algorithms.md Step 3: "registers geometry and metadata in the Scene")
        LOGGER.info("");
        LOGGER.info("Step 3b: Source height type and bridge relationship classification...");
        
        int roadSourceCount = 0;
        int bridgeSourceOnBridgeCount = 0;
        int bridgeSourceUnderBridgeCount = 0;
        for (Long sourcePk : sourcePks) {
            Scene.HeightType heightType = scene.getSourceHeightTypeByPk(sourcePk);
            assertEquals(Scene.HeightType.RELATIVE, heightType,
                "Source height type should default to RELATIVE");

            BridgeRelationship bridgeRelationship = scene.getBridgeRelationshipByPk(sourcePk);
            assertNotNull(bridgeRelationship, "Bridge relationship should be registered for each source");

            switch (bridgeRelationship.getRelationType()) {
                case SOURCE_NOT_RELATED_TO_BRIDGE:
                    roadSourceCount++;
                    LOGGER.info(String.format("  SourcePK %d: SOURCE_NOT_RELATED_TO_BRIDGE (Road)", sourcePk));
                    break;
                case ACTUAL_SOURCE_ON_BRIDGE:
                    bridgeSourceOnBridgeCount++;
                    assertEquals(100L, bridgeRelationship.getBridgePkOn(),
                        "Bridge source should reference Bridge 100");
                    LOGGER.info(String.format("  SourcePK %d: ACTUAL_SOURCE_ON_BRIDGE (Bridge PK=100)", sourcePk));
                    break;
                case IMAGINARY_SOURCE_UNDER_BRIDGE:
                    bridgeSourceUnderBridgeCount++;
                    LOGGER.info(String.format("  SourcePK %d: IMAGINARY_SOURCE_UNDER_BRIDGE", sourcePk));
                    break;
                default:
                    fail("Unexpected bridge relationship type: " + bridgeRelationship.getRelationType());
            }
        }

        assertEquals(2, roadSourceCount, "Road 1 and Road 3 should not be related to bridge");
        assertEquals(1, bridgeSourceOnBridgeCount, "Road 2 should be classified as on-bridge source");
        assertEquals(0, bridgeSourceUnderBridgeCount, "No imaginary under-bridge sources expected for ROADS input");
        LOGGER.info(String.format("  ✓ Classification complete: %d road sources, %d on-bridge sources, %d under-bridge sources",
            roadSourceCount, bridgeSourceOnBridgeCount, bridgeSourceUnderBridgeCount));

        double zAtCenter = profileBuilder.getZGround(new Coordinate(50.0, 50.0), new AtomicInteger(-1));
        assertEquals(3.0, zAtCenter, 0.001, "DEM should provide expected Z at center point");
        LOGGER.info(String.format("  ✓ DEM validation: Z at center (50, 50) = %.1f m", zAtCenter));

        LOGGER.info("Step 3c: Source emissions...");
        Map<Long, ArrayList<SourceEmission>> sourceEmissionsMap = scene.getSourceEmissionsMap();

        for (Long sourcePk : sourcePks) {
            assertTrue(sourceEmissionsMap.containsKey(sourcePk), "Source emissions should be registered for each source");
            ArrayList<SourceEmission> emissionsList = sourceEmissionsMap.get(sourcePk);
            assertNotNull(emissionsList, "Emissions list should not be null for source PK " + sourcePk);
            assertFalse(emissionsList.isEmpty(), "Emissions list should not be empty for source PK " + sourcePk);
            String emissionProperty = emissionsList.size() + " Emissions: ";
            for (SourceEmission emission : emissionsList) {
                emissionProperty += String.format("%s %s; ", emission.getEmissionType().name(), emission.getPeriod());
            }
            LOGGER.info(String.format("  Source PK %d: %s", sourcePk, emissionProperty));
        }

        // === Step 4: LineString Point Sampling and Elevation Conversion ===
        // Samples Scene-registered LineString geometries into discrete point sources
        // with absolute elevations for propagation calculations
        // (see source_algorithms.md Step 4: "samples Scene-registered LineString geometries
        // into discrete point sources with absolute elevations")
        LOGGER.info("");
        LOGGER.info("Step 4: LineString point sampling and elevation conversion...");
        
        ReceiverPointInfo receiverPointInfo = new ReceiverPointInfo(1, 1, new Coordinate(50.0, 10.0, 1.5));
        LOGGER.info(String.format("  Receiver position: X=%.1f, Y=%.1f, Z=%.1f m",
            receiverPointInfo.getCoordinate().x, receiverPointInfo.getCoordinate().y, receiverPointInfo.getCoordinate().z));
        
        // Collect sampled source points based on receiver location
        // Sampling density adapts to receiver-source distance (see source_algorithms.md Step 4, Segment Size Determination)
        List<SourcePointInfo> sourceList = SourceCollector.collectSourcePoints(receiverPointInfo, scene);
        LOGGER.info(String.format("  ✓ Total sampled source points: %d", sourceList.size()));

        HashMap<Long, HashMap<BridgeRelationship.RelationType, Integer>> sourcePointCountMap = new HashMap<>();
        int sourceNotRelatedToBridgeCount = 0;
        int sourceOnBridgeCount = 0;
        int sourceUnderBridgeCount = 0;
        int mirrorSourceCount = 0;
        
        for (SourcePointInfo sourcePointInfo : sourceList) {
            long sourcePk = sourcePointInfo.getSourcePk();
            BridgeRelationship.RelationType relationType = sourcePointInfo.getBridgeRelationship().getRelationType();

            if(!sourcePointCountMap.containsKey(sourcePk)) {
                sourcePointCountMap.put(sourcePk, new HashMap<>());
            }
            int currentCount = sourcePointCountMap.get(sourcePk).getOrDefault(relationType, 0);
            sourcePointCountMap.get(sourcePk).put(relationType, currentCount + 1);

            // Step 4: Height Elevation Validation
            // Validates that sampled points have correct absolute elevations based on their bridge relationship
            // (see source_algorithms.md Step 4: "calculateAbsoluteElevation() to convert relative Z")
            if (relationType == BridgeRelationship.RelationType.SOURCE_NOT_RELATED_TO_BRIDGE) {
                sourceNotRelatedToBridgeCount++;
                double z = sourcePointInfo.getCoordinate().z;
                double profileZ = profileBuilder.getZGround(sourcePointInfo.getCoordinate(), new AtomicInteger(-1));
                assertEquals(0.05, z - profileZ, 0.001,
                        String.format("Source NOT related to bridge: Z=%.3f should be 0.05m above ground Z=%.3f", z, profileZ));
                
            } else if (relationType == BridgeRelationship.RelationType.ACTUAL_SOURCE_ON_BRIDGE) {
                sourceOnBridgeCount++;
                double z = sourcePointInfo.getCoordinate().z;
                long bridgePkOn = sourcePointInfo.getBridgeRelationship().getBridgePkOn();
                double deckHeight = profileBuilder.getBridgeByPk(bridgePkOn).getDeckHeightAtPoint(sourcePointInfo.getCoordinate());
                assertEquals(0.05, z - deckHeight, 0.001,
                        String.format("Actual Source ON bridge: Z=%.3f should be 0.05m above deck Z=%.3f", z, deckHeight));
                
            } else if(relationType == BridgeRelationship.RelationType.IMAGINARY_SOURCE_UNDER_BRIDGE) {
                sourceUnderBridgeCount++;
                double z = sourcePointInfo.getCoordinate().z;
                long bridgePkAbove = sourcePointInfo.getBridgeRelationship().getBridgePkAbove();
                double deckHeight = profileBuilder.getBridgeByPk(bridgePkAbove).getDeckHeightAtPoint(sourcePointInfo.getCoordinate());
                double deckThickness = profileBuilder.getBridgeByPk(bridgePkAbove).getDeckThicknessAtPoint(sourcePointInfo.getCoordinate());

                assertEquals(-0.05, z - (deckHeight - deckThickness), 0.001,
                        String.format("Imaginary Source UNDER bridge: Z=%.3f should be -0.05m below ground Z=%.3f", z, deckHeight - deckThickness));
                
            } else if(relationType == BridgeRelationship.RelationType.MIRROR_SOURCE) {
                mirrorSourceCount++;
                double z = sourcePointInfo.getCoordinate().z;
                long bridgePkAbove = sourcePointInfo.getBridgeRelationship().getBridgePkAbove();
                double deckHeight = profileBuilder.getBridgeByPk(bridgePkAbove).getDeckHeightAtPoint(sourcePointInfo.getCoordinate());
                double deckThickness = profileBuilder.getBridgeByPk(bridgePkAbove).getDeckThicknessAtPoint(sourcePointInfo.getCoordinate());
                
                AtomicInteger triangleHint = new AtomicInteger(-1);
                double profileZ = profileBuilder.getZGround(sourcePointInfo.getCoordinate(), triangleHint);

                assertEquals(0.05, z - 2 * (deckHeight - deckThickness - profileZ - 0.05), 0.001, 
                    String.format("Mirror Source: Z=%.3f should reflect correctly", z));
            }
        }
        
        LOGGER.info("");
        LOGGER.info("Step 4: Elevation conversion validation complete");
        LOGGER.info(String.format("  ✓ SOURCE_NOT_RELATED_TO_BRIDGE: %d points (absolute Z = DEM + 0.05m)", sourceNotRelatedToBridgeCount));
        LOGGER.info(String.format("  ✓ ACTUAL_SOURCE_ON_BRIDGE: %d points (absolute Z = deck + 0.05m)", sourceOnBridgeCount));
        LOGGER.info(String.format("  ✓ IMAGINARY_SOURCE_UNDER_BRIDGE: %d points (absolute Z = (deck - thickness) - 0.05m)", sourceUnderBridgeCount));
        LOGGER.info(String.format("  ✓ MIRROR_SOURCE: %d points (reflection from bridge underside)", mirrorSourceCount));
        
        // === Final Summary ===
        LOGGER.info("");
        LOGGER.info("Final Summary: Sampling and classification by source");
        for (Long sourcePk : sourcePointCountMap.keySet()) {
            HashMap<BridgeRelationship.RelationType, Integer> pointCountMap = sourcePointCountMap.get(sourcePk);
            LOGGER.info(String.format("SourcePK %d (Road %d):", sourcePk, sourcePk));

            for (BridgeRelationship.RelationType relationType : pointCountMap.keySet()) {
                int typeCount = pointCountMap.get(relationType);
                LOGGER.info(String.format("  └─ %s: %d sampled points", relationType, typeCount));
            }
        }
        
        LOGGER.info("");
        LOGGER.info("========================================");
        LOGGER.info("Test Complete - All steps validated");
        LOGGER.info("========================================");
    }

}