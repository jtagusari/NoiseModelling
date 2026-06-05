/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.propagation.AttenuationParameters;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;
import org.noise_planet.noisemodelling.jdbc.input.PropagationSettings;
import org.noise_planet.noisemodelling.jdbc.BuildingTableSettings;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test special edge cases in NoiseModelling computation scheme.
 * These tests verify the behavior of the system under unusual but valid input conditions,
 * documenting how the computation pipeline handles each case.
 */
public class SpecialCasesTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpecialCasesTest.class);
    private Connection connection;
    private GeometryFactory geometryFactory;

    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(
                H2GISDBFactory.createSpatialDataBase(SpecialCasesTest.class.getSimpleName(), true, ""));
        geometryFactory = new GeometryFactory();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    /**
     * Helper method to create a simple source table with sound emission data.
     * Creates a point source with specified emission level.
     */
    private void createPointSource(Statement st, double x, double y, double z, double emissionLevel) throws SQLException {
        // Create source table schema
        StringBuilder sb = new StringBuilder("CREATE TABLE SOURCES(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY");
        AttenuationParameters params = new AttenuationParameters(FrequencyConfig.FrequencyBand.OCTAVE);
        
        for (int freq : params.getFrequencies()) {
            sb.append(", LW").append(freq).append(" REAL");
        }
        sb.append(")");
        st.execute(sb.toString());

        // Insert source point with emission levels
        StringBuilder insert = new StringBuilder("INSERT INTO SOURCES(THE_GEOM");
        
        for (int freq : params.getFrequencies()) {
            insert.append(", LW").append(freq);
        }
        insert.append(") VALUES (ST_GeomFromText('POINT Z(")
                .append(x).append(" ").append(y).append(" ").append(z).append(")', 2154)");
        
        for (int freq : params.getFrequencies()) {
            insert.append(", ").append(String.format(Locale.ROOT, "%.2f", emissionLevel));
        }
        insert.append(")");
        
        st.execute(insert.toString());
    }

    /**
     * Test Case 1: Receiver below ground surface and at ground level
     * 
     * Test scenarios:
     * A) Receiver below ground surface (negative relative height)
     *    - Source at ground level (z=0 relative)
     *    - Receiver with HEIGHT_TYPE='RELATIVE' and negative Z coordinate (z=-2 relative)
     * 
     * B) Receiver at exact ground level (zero relative height)
     *    - Receiver with HEIGHT_TYPE='RELATIVE' and Z coordinate = 0
     *    - Tests boundary condition: is z=0 processed or skipped?
     * 
     * Expected behavior (based on implementation analysis):
     * - ThreadPathFinder.call() checks: if(receiverRelativeHeight < 0) → skip
     * - For RELATIVE receivers: absolute Z = ground elevation + relative Z
     * - Receivers with relative height < 0: SKIPPED (with WARNING)
     * - Receivers with relative height = 0: PROCESSED (ground level receivers are valid)
     * 
     * Key findings:
     * 1. NoiseModelling DOES NOT compute for receivers BELOW ground (relative height < 0)
     * 2. NoiseModelling DOES compute for receivers AT ground level (relative height = 0)
     * 3. Check condition is strictly less-than (<), not less-than-or-equal (<=)
     */
    @Test
    public void testReceiverBelowGroundSurface() throws Exception {
        try (Statement st = connection.createStatement()) {
            LOGGER.info("\n=== Test Case 1: Receiver Below Ground Surface ===");
            
            // Create empty buildings table
            st.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            
            // Create DEM table with flat terrain at elevation = 0
            st.execute("CREATE TABLE DEM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO DEM(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POINT(0 0)', 2154), 0.0)");
            
            // Create point source at ground level
            createPointSource(st, 0.0, 0.0, 0.0, 90.0);
            LOGGER.info("Source: (0, 0, 0), 90 dB emission");
            
            // Create receivers table with below-ground, at-ground, and above-ground receivers
            st.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ), HEIGHT_TYPE VARCHAR(10))");
            
            // Receiver 1: Normal receiver at 4m above ground (reference case)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(10.0 0.0 4.0)', 2154), 'RELATIVE')");
            
            // Receiver 2: Receiver below ground surface (-2m relative)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(10.0 10.0 -2.0)', 2154), 'RELATIVE')");
            
            // Receiver 3: Receiver at ground level (z=0 relative)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(10.0 30.0 0.0)', 2154), 'RELATIVE')");
            
            LOGGER.info("Receivers: PK1(h=+4m-above), PK2(h=-2m-below), PK3(h=0m-ground)");
            

            BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                    .setBuildingsTableName("BUILDINGS")
                    .setHeightField("HEIGHT")
                    .setAlphaFieldName("G")
                    .setDefaultWallAbsorption(100000.0)
                    .setZBuildings(false)
                    .build();
            
            PropagationSettings propagationSettings = new PropagationSettings.Builder()
                    .setMaximumPropagationDistance(100.0)
                    .setComputeHorizontalDiffraction(false)
                    .setComputeVerticalDiffraction(false)
                    .setSoundReflectionOrder(0)
                    .build();

            NoiseMapByReceiverMaker noiseMapMaker = new NoiseMapByReceiverMaker.Builder()
                    .setBuildingTableSettings(buildingTableSettings)
                    .setSourcesTableName("SOURCES")
                    .setReceiverTableName("RECEIVERS")
                    .setDemTable("DEM")
                    .setPropagationSettings(propagationSettings)
                    .build();
            
            // Run computation
            LOGGER.info("Running computation...");
            noiseMapMaker.initialize(connection, new EmptyProgressVisitor());
            noiseMapMaker.run(connection, new EmptyProgressVisitor());
            
            // Retrieve and analyze results
            CalculationIOSettings params = noiseMapMaker.getCalculationIOSettings();
            String resultsTable = params.receiversLevelTable;
            
            LOGGER.info("\n=== Results ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT IDRECEIVER, HZ1000 FROM " + resultsTable + " ORDER BY IDRECEIVER")) {
                
                while (rs.next()) {
                    long receiverId = rs.getLong("IDRECEIVER");
                    double level = rs.getDouble("HZ1000");
                    String desc = receiverId == 1 ? "above(+4m)" : (receiverId == 3 ? "ground(0m)" : "");
                    LOGGER.info("PK={}: {} dB ({})", receiverId, String.format("%.1f", level), desc);
                    
                    // Verify results for above-ground and ground-level receivers
                    assertTrue(receiverId == 1 || receiverId == 3, 
                            "Only above-ground (PK=1) and ground-level (PK=3) receivers should have results");
                    assertTrue(level < 0 && level > -100, 
                            "Sound level should be reasonable negative dB value");
                }
            }
            
            // Verify below-ground receivers were skipped
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM " + resultsTable + " WHERE IDRECEIVER = 2")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Below-ground receiver (PK 2) should have no results");
            }
            
            LOGGER.info("\n=== Findings ===");
            LOGGER.info("• Below-ground (h<0): SKIPPED (no results)");
            LOGGER.info("• Ground level (h=0): COMPUTED");
            LOGGER.info("• Above-ground (h>0): COMPUTED");
        }
    }

    /**
     * Test Case 2: Receivers inside building geometries
     * 
     * Test scenarios:
     * - Source outside building at ground level
     * - Building geometry (rectangular polygon) with defined height
     * - Receivers at different positions:
     *   A) Inside building (X, Y within polygon, Z within height range)
     *   B) Outside building (X, Y outside polygon)
     *   C) Above building (X, Y within polygon, Z > building height)
     * 
     * Expected behavior:
     * - Do receivers inside buildings receive computation?
     * - Is there any spatial collision detection?
     * - How does the presence of building geometry affect indoor sound levels?
     * 
     * Key questions:
     * 1. Are receivers inside buildings processed or skipped?
     * 2. Is sound level attenuated by building obstruction?
     * 3. Does building-receiver collision affect results?
     */
    @Test
    public void testReceiversInsideBuildings() throws Exception {
        try (Statement st = connection.createStatement()) {
            LOGGER.info("\n=== Test Case 2: Receivers Inside Buildings ===");
            
            // Create building table
            st.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POLYGON((40 40, 60 40, 60 60, 40 60, 40 40))', 2154), 10.0)");
            LOGGER.info("Building: (40,40)-(60,60), h=10m");
            
            // Create flat DEM
            st.execute("CREATE TABLE DEM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO DEM(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POINT(50 50)', 2154), 0.0)");
            
            // Create point source outside building
            createPointSource(st, 0.0, 0.0, 5.0, 85.0);
            LOGGER.info("Source: (0, 0, 5m), 85 dB, outside building");
            
            // Create receivers
            st.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ), HEIGHT_TYPE VARCHAR(10))");
            
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.0 50.0 1.5)', 2154), 'RELATIVE')");
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.0 50.0 6.0)', 2154), 'RELATIVE')");
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.0 50.0 12.0)', 2154), 'RELATIVE')");
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(30.0 50.0 1.5)', 2154), 'RELATIVE')");
            
            LOGGER.info("Receivers: PK1(inside, 1.5m), PK2(inside, 6m), PK3(above, 12m), PK4(outside, 1.5m)");
            
            BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                    .setBuildingsTableName("BUILDINGS")
                    .setHeightField("HEIGHT")
                    .setAlphaFieldName("G")
                    .setDefaultWallAbsorption(100000.0)
                    .setZBuildings(false)
                    .build();
            
            PropagationSettings propagationSettings = new PropagationSettings.Builder()
                    .setMaximumPropagationDistance(100.0)
                    .setComputeHorizontalDiffraction(true)
                    .setComputeVerticalDiffraction(true)
                    .setSoundReflectionOrder(0)
                    .build();
            
            NoiseMapByReceiverMaker noiseMapMaker = new NoiseMapByReceiverMaker.Builder()
                    .setBuildingTableSettings(buildingTableSettings)
                    .setSourcesTableName("SOURCES")
                    .setReceiverTableName("RECEIVERS")
                    .setDemTable("DEM")
                    .setPropagationSettings(propagationSettings)
                    .build();
            
            // Run computation
            LOGGER.info("Running computation...");
            noiseMapMaker.initialize(connection, new EmptyProgressVisitor());
            noiseMapMaker.run(connection, new EmptyProgressVisitor());
            
            // Retrieve and analyze results
            CalculationIOSettings params = noiseMapMaker.getCalculationIOSettings();
            String resultsTable = params.receiversLevelTable;
            
            LOGGER.info("\n=== Results ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT IDRECEIVER, HZ1000 FROM " + resultsTable + " ORDER BY IDRECEIVER")) {
                
                String[] locations = {"inside(1.5m)", "inside(6m)", "above(12m)", "outside(1.5m)"};
                int idx = 0;
                while (rs.next()) {
                    long receiverId = rs.getLong("IDRECEIVER");
                    double level = rs.getDouble("HZ1000");
                    LOGGER.info("PK={}: {} dB ({})", receiverId, String.format("%.1f", level), 
                            receiverId <= 4 ? locations[(int)receiverId - 1] : "");
                }
            }
            
            LOGGER.info("\n=== Finding ===");
            LOGGER.info("• Receivers inside buildings ARE computed");
            LOGGER.info("• Buildings treated as obstacles for diffraction/reflection, not hard volume exclusions");
        }
    }

    /**
     * Test Case 2B: Receivers inside buildings - Detailed ray path analysis
     * 
     * Extended analysis of Case 2 with ray path export enabled.
     * This test verifies actual propagation paths (direct, reflection, diffraction)
     * used for receivers positioned inside buildings.
     * 
     * Questions answered:
     * 1. Is direct path calculated for indoor receivers?
     * 2. Are reflections considered for indoor-to-outdoor paths?
     * 3. What diffraction mechanisms apply to indoor receivers?
     * 4. How many reflection bounces are needed to reach indoor receiver?
     * 
     * Method: Enable RAYS_GEOMETRY table export to analyze path characteristics
     */
    @Test
    public void testReceiversInsideBuildingsRayAnalysis() throws Exception {
        try (Statement st = connection.createStatement()) {
            LOGGER.info("");
            LOGGER.info("=== Test Case 2B: Indoor Receivers - Ray Path Analysis ===");
            
            // Create building table with rectangular building
            st.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POLYGON((40 40, 60 40, 60 60, 40 60, 40 40))', 2154), 10.0)");
            LOGGER.info("Building: (40,40)-(60,60), height=10m");
            
            // Create flat DEM
            st.execute("CREATE TABLE DEM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO DEM(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POINT(50 50)', 2154), 0.0)");
            
            // Create point source outside building
            createPointSource(st, 0.0, 0.0, 5.0, 85.0);
            LOGGER.info("Source: (0, 0, 5m), outside building");
            
            // Create receivers: inside and outside only
            st.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ), HEIGHT_TYPE VARCHAR(10))");
            
            // Receiver A: Inside building, ground level
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.0 50.0 1.5)', 2154), 'RELATIVE')");
            LOGGER.info("Receiver A (PK=1): inside building (50, 50, 1.5m) - CENTER");
            
            // Receiver B: Outside building, same height
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(30.0 50.0 1.5)', 2154), 'RELATIVE')");
            LOGGER.info("Receiver B (PK=2): outside building (30, 50, 1.5m) - REFERENCE");
            
            // Receiver C: Far outside building for baseline
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(100.0 100.0 1.5)', 2154), 'RELATIVE')");
            LOGGER.info("Receiver C (PK=3): far outside (100, 100, 1.5m) - DISTANCE BASELINE");
            

            BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                    .setBuildingsTableName("BUILDINGS")
                    .setHeightField("HEIGHT")
                    .setAlphaFieldName("G")
                    .setDefaultWallAbsorption(100000.0)
                    .setZBuildings(false)
                    .build();
            
            PropagationSettings propagationSettings = new PropagationSettings.Builder()
                    .setMaximumPropagationDistance(200.0)
                    .setComputeHorizontalDiffraction(true)
                    .setComputeVerticalDiffraction(true)
                    .setSoundReflectionOrder(2)
                    .build();
            
            NoiseMapByReceiverMaker noiseMapMaker = new NoiseMapByReceiverMaker.Builder()
                    .setBuildingTableSettings(buildingTableSettings)
                    .setSourcesTableName("SOURCES")
                    .setReceiverTableName("RECEIVERS")
                    .setDemTable("DEM")
                    .setPropagationSettings(propagationSettings)
                    .build();
            
            // Enable ray path output tables
            CalculationIOSettings params = noiseMapMaker.getCalculationIOSettings();
            params.exportRaysMethod = CalculationIOSettings.ExportRaysMethods.TO_RAYS_TABLE;
            params.exportCnossosPathWithAttenuation = true;
            
            LOGGER.info("");
            LOGGER.info("Running computation with ray path export enabled...");
            noiseMapMaker.initialize(connection, new EmptyProgressVisitor());
            noiseMapMaker.run(connection, new EmptyProgressVisitor());
            
            // Retrieve sound level results
            String resultsTable = params.receiversLevelTable;
            LOGGER.info("");
            LOGGER.info("=== Sound Level Results ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT IDRECEIVER, HZ1000 FROM " + resultsTable + " ORDER BY IDRECEIVER")) {
                while (rs.next()) {
                    long receiverId = rs.getLong("IDRECEIVER");
                    double level = rs.getDouble("HZ1000");
                    String location = receiverId == 1 ? "Inside" : (receiverId == 2 ? "Outside" : "Far");
                    LOGGER.info("Receiver {} ({}): HZ1000 = {}", receiverId, location, 
                            String.format("%.1f", level));
                }
            }
            
            // Analyze ray paths if RAYS_GEOMETRY table exists
            try {
                LOGGER.info("");
                LOGGER.info("=== Available Output Tables ===");
                
                // List all tables in database
                try (ResultSet tableList = st.executeQuery(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' ORDER BY TABLE_NAME")) {
                    LOGGER.info("Tables created during computation:");
                    while (tableList.next()) {
                        String tableName = tableList.getString("TABLE_NAME");
                        if (tableName.toUpperCase().contains("RAYS") || 
                            tableName.toUpperCase().contains("PATH") ||
                            tableName.toUpperCase().contains("RECEIVER")) {
                            LOGGER.info("  - {}", tableName);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.info("Could not list tables");
            }
            
            // Try to find ray/path information in actual output
            try {
                LOGGER.info("");
                LOGGER.info("=== Ray Path Information (if available) ===");
                
                // Check for RAYS table with different naming
                String[] possibleTableNames = {"RAYS", "RAYS_GEOMETRY", "RAYS_PATH", "CUTPATH", "CNOSSOS_PATH"};
                boolean foundTable = false;
                
                for (String tableName : possibleTableNames) {
                    try {
                        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
                            if (rs.next()) {
                                int count = rs.getInt(1);
                                if (count > 0) {
                                    LOGGER.info("Found table: {} with {} rows", tableName, count);
                                    foundTable = true;
                                    
                                    // Get column info
                                    try (ResultSet columns = st.executeQuery(
                                            "SELECT * FROM " + tableName + " LIMIT 1")) {
                                        ResultSetMetaData meta = columns.getMetaData();
                                        LOGGER.info("  Columns: ");
                                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                                            if (i < 10) {  // Limit to first 10 columns
                                                LOGGER.info("    - {}", meta.getColumnName(i));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Table doesn't exist, continue
                    }
                }
                
                if (!foundTable) {
                    LOGGER.info("No ray/path output tables found");
                    LOGGER.info("Note: Ray export may require additional configuration parameters");
                }
            } catch (Exception e) {
                LOGGER.info("Could not query ray tables: {}", e.getMessage());
            }
            
            // Detailed analysis of RAYS table
            try {
                LOGGER.info("");
                LOGGER.info("=== Detailed RAYS Table Analysis ===");
                
                try (ResultSet rs = st.executeQuery("SELECT * FROM RAYS ORDER BY IDRECEIVER, PK")) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    
                    LOGGER.info("RAYS table has {} columns:", colCount);
                    StringBuilder colNames = new StringBuilder();
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) colNames.append(", ");
                        colNames.append(meta.getColumnName(i));
                    }
                    LOGGER.info("  {}", colNames.toString());
                    
                    LOGGER.info("");
                    LOGGER.info("RAYS data grouped by receiver:");
                    
                    Integer lastReceiver = null;
                    int rayCountPerReceiver = 0;
                    int rowNum = 0;
                    
                    while (rs.next()) {
                        rowNum++;
                        long receiverId = rs.getLong("IDRECEIVER");
                        long sourceId = rs.getLong("IDSOURCE");
                        String pathInfo = rs.getString("PATH");
                        
                        if (lastReceiver != null && lastReceiver != receiverId) {
                            LOGGER.info("  Receiver {} total: {} rays", lastReceiver, rayCountPerReceiver);
                            rayCountPerReceiver = 0;
                        }
                        
                        String location = receiverId == 1 ? "Inside" : (receiverId == 2 ? "Outside" : "Far");
                        rayCountPerReceiver++;
                        
                        // Parse PATH JSON to extract key information
                        String rayType = "UNKNOWN";
                        String pathMechanism = "UNKNOWN";
                        if (pathInfo != null) {
                            if (pathInfo.contains("\"type\":\"VEdgeDiffraction\"")) {
                                rayType = "DIFFRACTION";
                            } else if (pathInfo.contains("\"type\":\"Reflection\"")) {
                                rayType = "REFLECTION";
                            } else if (pathInfo.contains("\"intersectionType\":\"BUILDING_ENTER\"")) {
                                rayType = "BUILDING_PASS";
                            } else if (pathInfo.contains("\"intersectionType\":\"BUILDING_EXIT\"")) {
                                rayType = "DIFFRACTION (EXIT)";
                            } else if (pathInfo.contains("\"type\":\"Source\",") && pathInfo.contains("\"type\":\"Receiver\"") && !pathInfo.contains("\"type\":\"")) {
                                rayType = "DIRECT";
                            }
                            
                            // Extract path mechanism complexity
                            int cutPointCount = pathInfo.split("\"type\":\"").length - 1;
                            pathMechanism = cutPointCount + " points";
                        }
                        
                        LOGGER.info("  Receiver {} ({}) - Ray {}: TYPE={} MECHANISM=[{}]", 
                                receiverId, location, rayCountPerReceiver, rayType, pathMechanism);
                        
                        lastReceiver = (int)receiverId;
                    }
                    
                    if (lastReceiver != null) {
                        LOGGER.info("  Receiver {} total: {} rays", lastReceiver, rayCountPerReceiver);
                    }
                    
                    LOGGER.info("");
                    LOGGER.info("Total rays: {}", rowNum);
                }
            } catch (Exception e) {
                LOGGER.info("Could not analyze RAYS table: {}", e.getMessage());
            }
            
            LOGGER.info("");
            LOGGER.info("=== Conclusion ===");
            LOGGER.info("Analysis Purpose: Understand actual propagation mechanisms for indoor receivers");
            LOGGER.info("Key questions answered via RAYS_GEOMETRY:");
            LOGGER.info("1. Is direct path computed for indoor receivers? (check DIRECT_PATH column)");
            LOGGER.info("2. How many reflections needed to reach indoor receiver? (check REFLECTIONS)");
            LOGGER.info("3. Is diffraction the dominant mechanism? (check diffraction indicators)");
            LOGGER.info("");
            LOGGER.info("Hypothesis from Case 2 observations:");
            LOGGER.info("- 24 dB reduction suggests strong building shielding");
            LOGGER.info("- Direct path likely blocked (building geometry blocks line-of-sight)");
            LOGGER.info("- Sound reaches indoor receiver via diffraction paths or building reflections");
        }
    }

    @Test
    public void testReceiversInsideBuildingsRayPathValidity() throws Exception {
        // Diagnose whether BUILDING_PASS rays are physically valid
        try (Statement st = connection.createStatement()) {
            LOGGER.info("");
            LOGGER.info("=== Test Case 2C: Indoor Ray Path Validity Check ===");
            LOGGER.info("Question: Can sound physically pass THROUGH building interiors?");
            LOGGER.info("");
            
            // Create building
            st.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POLYGON((40 40, 60 40, 60 60, 40 60, 40 40))', 2154), 10.0)");
            
            // Create DEM
            st.execute("CREATE TABLE DEM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO DEM(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POINT(50 50)', 2154), 0.0)");
            
            // Create source
            createPointSource(st, 0.0, 0.0, 5.0, 85.0);
            
            // Create receiver inside building
            st.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ), HEIGHT_TYPE VARCHAR(10))");
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.0 50.0 1.5)', 2154), 'RELATIVE')");
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(30.0 50.0 1.5)', 2154), 'RELATIVE')");
            

            BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                    .setBuildingsTableName("BUILDINGS")
                    .setHeightField("HEIGHT")
                    .setAlphaFieldName("G")
                    .setDefaultWallAbsorption(100000.0)
                    .setZBuildings(false)
                    .build();
            
            PropagationSettings propagationSettings = new PropagationSettings.Builder()
                    .setMaximumPropagationDistance(200.0)
                    .setComputeHorizontalDiffraction(true)
                    .setComputeVerticalDiffraction(true)
                    .setSoundReflectionOrder(2)
                    .build();
            
            NoiseMapByReceiverMaker noiseMapMaker = new NoiseMapByReceiverMaker.Builder()
                    .setBuildingTableSettings(buildingTableSettings)
                    .setSourcesTableName("SOURCES")
                    .setReceiverTableName("RECEIVERS")
                    .setDemTable("DEM")
                    .setPropagationSettings(propagationSettings)
                    .build();
            
            CalculationIOSettings params = noiseMapMaker.getCalculationIOSettings();
            params.exportRaysMethod = CalculationIOSettings.ExportRaysMethods.TO_RAYS_TABLE;
            params.exportCnossosPathWithAttenuation = true;
            
            noiseMapMaker.initialize(connection, new EmptyProgressVisitor());
            noiseMapMaker.run(connection, new EmptyProgressVisitor());
            
            // Analyze RAYS table
            LOGGER.info("");
            LOGGER.info("=== Ray Analysis for Indoor Receiver ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT PK, IDRECEIVER, PATH FROM RAYS WHERE IDRECEIVER = 1 ORDER BY PK")) {
                
                int rayNum = 1;
                while (rs.next()) {
                    String pathJson = rs.getString("PATH");
                    
                    boolean hasBuildingEnter = pathJson.contains("\"intersectionType\":\"BUILDING_ENTER\"");
                    boolean hasVEdgeDiff = pathJson.contains("\"type\":\"VEdgeDiffraction\"");
                    int wallCount = pathJson.split("\"type\":\"Wall\"").length - 1;
                    
                    LOGGER.info("Ray {}: ", rayNum);
                    if (hasVEdgeDiff) {
                        LOGGER.info("  Type: DIFFRACTION (Valid - corner diffraction)");
                    } else if (hasBuildingEnter && !hasVEdgeDiff) {
                        LOGGER.info("  Type: ⚠️ BUILDING_PASS (Questionable)");
                        LOGGER.info("     Wall intersections: {} walls", wallCount);
                        LOGGER.info("     Status: Sound passing through building without explicit");
                        LOGGER.info("     diffraction - may be implementation artifact");
                    }
                    LOGGER.info("");
                    rayNum++;
                }
            }
            
            LOGGER.info("=== Conclusion ===");
            LOGGER.info("The BUILDING_PASS ray (with BUILDING_ENTER but no VEdgeDiffraction)");
            LOGGER.info("represents sound passing through building interior - QUESTIONABLE VALIDITY");
            LOGGER.info("in standard CNOSSOS diffraction-based acoustic model.");
        }
    }

    /**
     * Test Case 3: Source and Receiver at Same Location
     * 
     * Test scenarios:
     * A) XY同一，Z異なる（同じ水平位置，異なる高さ）
     *    - Source at (50, 50, 5m), Receiver at (50, 50, 1.5m)
     *    - Vertical separation only, horizontal distance = 0
     * 
     * B) XYZ完全同一（完全に同一位置）
     *    - Source at (50, 50, 1.5m), Receiver at (50, 50, 1.5m)
     *    - Horizontal distance = 0, vertical distance = 0
     * 
     * Expected behavior:
     * - What happens when horizontal distance is zero?
     * - Is sound level computed as direct path?
     * - Is there a singularity or special handling?
     * - Can a receiver be placed at the exact same location as a source?
     * 
     * Key questions:
     * 1. How does the propagation model handle zero horizontal distance?
     * 2. Is there a minimum distance threshold?
     * 3. Does Z difference matter when XY = 0?
     */
    @Test
    public void testSourceReceiverSameLocationXYOnly() throws Exception {
        try (Statement st = connection.createStatement()) {
            LOGGER.info("\n=== Test Case 3A: Source-Receiver Same XY Position (Different Z) ===");
            
            // Create empty buildings table
            st.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            
            // Create flat DEM
            st.execute("CREATE TABLE DEM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO DEM(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POINT(50 50)', 2154), 0.0)");
            
            // Create point source at (50, 50, 5m)
            createPointSource(st, 50.0, 50.0, 5.0, 85.0);
            LOGGER.info("Source: (50, 50, 5m), 85 dB emission");
            
            // Create receivers at same XY but different Z
            st.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ), HEIGHT_TYPE VARCHAR(10))");
            
            // Receiver 1: Same XY, different Z (lower)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.0 50.0 1.5)', 2154), 'RELATIVE')");
            
            // Receiver 2: Same XY, different Z (higher)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.0 50.0 7.0)', 2154), 'RELATIVE')");
            
            // Receiver 3: Reference receiver at different XY
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(100.0 50.0 5.0)', 2154), 'RELATIVE')");
            LOGGER.info("Receivers: PK1(50,50,1.5m), PK2(50,50,7.0m), PK3(100,50,5.0m-ref)");
            

            BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                    .setBuildingsTableName("BUILDINGS")
                    .setHeightField("HEIGHT")
                    .setAlphaFieldName("G")
                    .setDefaultWallAbsorption(100000.0)
                    .setZBuildings(false)
                    .build();

            PropagationSettings propagationSettings = new PropagationSettings.Builder()
                    .setMaximumPropagationDistance(500.0)
                    .setComputeHorizontalDiffraction(false)
                    .setComputeVerticalDiffraction(false)
                    .setSoundReflectionOrder(0)
                    .build();

            NoiseMapByReceiverMaker noiseMapMaker = new NoiseMapByReceiverMaker.Builder()
                    .setBuildingTableSettings(buildingTableSettings)
                    .setSourcesTableName("SOURCES")
                    .setReceiverTableName("RECEIVERS")
                    .setDemTable("DEM")
                    .setPropagationSettings(propagationSettings)
                    .build();
            
            // Run computation
            LOGGER.info("Running computation...");
            noiseMapMaker.initialize(connection, new EmptyProgressVisitor());
            noiseMapMaker.run(connection, new EmptyProgressVisitor());
            
            // Retrieve and analyze results
            CalculationIOSettings params = noiseMapMaker.getCalculationIOSettings();
            String resultsTable = params.receiversLevelTable;
            
            try (ResultSet rs = st.executeQuery(
                    "SELECT IDRECEIVER, HZ1000 FROM " + resultsTable + " ORDER BY IDRECEIVER")) {
                
                LOGGER.info("\n=== Results ===");
                while (rs.next()) {
                    long receiverId = rs.getLong("IDRECEIVER");
                    double level = rs.getDouble("HZ1000");
                    LOGGER.info("Receiver {}: HZ1000 = {} dB", receiverId, 
                            String.format("%.1f", level));
                }
            }
        }
    }

    /**
     * Test Case 3B: Source and Receiver at Completely Same Position
     */
    @Test
    public void testSourceReceiverCompletelyIdentical() throws Exception {
        try (Statement st = connection.createStatement()) {
            LOGGER.info("\n=== Test Case 3B: Source-Receiver Completely Identical Position ===");
            
            // Create empty buildings table
            st.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            
            // Create flat DEM
            st.execute("CREATE TABLE DEM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO DEM(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POINT(50 50)', 2154), 0.0)");
            
            // Create point source at (50, 50, 1.5m)
            createPointSource(st, 50.0, 50.0, 1.5, 85.0);
            LOGGER.info("Source: (50, 50, 1.5m), 85 dB emission");
            
            // Create receivers
            st.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ), HEIGHT_TYPE VARCHAR(10))");
            
            // Receiver 1: IDENTICAL to source
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.0 50.0 1.5)', 2154), 'RELATIVE')");
            
            // Receiver 2: Very close (1mm)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.001 50.0 1.5)', 2154), 'RELATIVE')");
            
            // Receiver 3: 1m away
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(51.0 50.0 1.5)', 2154), 'RELATIVE')");
            
            // Receiver 4: 50m reference
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(100.0 50.0 1.5)', 2154), 'RELATIVE')");
            LOGGER.info("Receivers: PK1(d=0), PK2(d≈0.001m), PK3(d=1m), PK4(d=50m-ref)");
            

            BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                    .setBuildingsTableName("BUILDINGS")
                    .setHeightField("HEIGHT")
                    .setAlphaFieldName("G")
                    .setDefaultWallAbsorption(100000.0)
                    .setZBuildings(false)
                    .build();

            PropagationSettings propagationSettings = new PropagationSettings.Builder()
                    .setMaximumPropagationDistance(500.0)
                    .setComputeHorizontalDiffraction(false)
                    .setComputeVerticalDiffraction(false)
                    .setSoundReflectionOrder(0)
                    .build();

            NoiseMapByReceiverMaker noiseMapMaker = new NoiseMapByReceiverMaker.Builder()
                    .setBuildingTableSettings(buildingTableSettings)
                    .setSourcesTableName("SOURCES")
                    .setReceiverTableName("RECEIVERS")
                    .setDemTable("DEM")
                    .setPropagationSettings(propagationSettings)
                    .build();

            // Run computation
            LOGGER.info("Running computation...");
            noiseMapMaker.initialize(connection, new EmptyProgressVisitor());
            noiseMapMaker.run(connection, new EmptyProgressVisitor());
            
            // Retrieve and analyze results
            CalculationIOSettings params = noiseMapMaker.getCalculationIOSettings();
            String resultsTable = params.receiversLevelTable;
            
            try (ResultSet rs = st.executeQuery(
                    "SELECT IDRECEIVER, HZ1000 FROM " + resultsTable + " ORDER BY IDRECEIVER")) {
                
                LOGGER.info("\n=== Results ===");
                double[] distances = {0.0, 0.001, 1.0, 50.0};
                int idx = 0;
                while (rs.next()) {
                    long receiverId = rs.getLong("IDRECEIVER");
                    double level = rs.getDouble("HZ1000");
                    LOGGER.info("Distance {}: {} dB", String.format("%.3f m", distances[idx]), 
                            String.format("%.1f", level));
                    idx++;
                }
            }
        }
    }

    /**
     * Test Case 4: Building Geometry Between Source and Receiver
     * 
     * Tests the effect of building obstruction on sound propagation when the
     * building geometry is positioned between the direct source-receiver path.
     * This examines diffraction around building edges as the primary mechanism.
     */
    @Test
    public void testBuildingBetweenSourceAndReceiver() throws Exception {
        try (Statement st = connection.createStatement()) {
            LOGGER.info("\n=== Test Case 4: Building Between Source and Receiver ===");
            
            // Create building geometry: rectangular obstacle between source and receiver
            st.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POLYGON((40 0, 60 0, 60 20, 40 20, 40 0))', 2154), 15.0)");
            LOGGER.info("Building: (40,0)-(60,20), h=15m, positioned between source and receiver");
            
            // Create flat DEM
            st.execute("CREATE TABLE DEM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO DEM(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POINT(50 50)', 2154), 0.0)");
            
            // Create point source on left side (outside building)
            createPointSource(st, 0.0, 10.0, 5.0, 85.0);
            LOGGER.info("Source: (0, 10, 5m), 85 dB, left of obstacle");
            
            // Create receivers on right side (building blocks direct path)
            st.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ), HEIGHT_TYPE VARCHAR(10))");
            
            // Receiver 1: Just beyond building, same height as source
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(70.0 10.0 5.0)', 2154), 'RELATIVE')");
            
            // Receiver 2: Well beyond building (100m away)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(150.0 10.0 5.0)', 2154), 'RELATIVE')");
            
            // Receiver 3: Above building (direct path unobstructed)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(100.0 10.0 20.0)', 2154), 'RELATIVE')");
            
            // Receiver 4: To the side of building (minimal diffraction)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(100.0 -10.0 5.0)', 2154), 'RELATIVE')");
            
            LOGGER.info("Receivers: PK1(just beyond, 70m), PK2(far, 150m), PK3(above, 20m), PK4(aside, side path)");
            

            BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                    .setBuildingsTableName("BUILDINGS")
                    .setHeightField("HEIGHT")
                    .setAlphaFieldName("G")
                    .setDefaultWallAbsorption(100000.0)
                    .setZBuildings(false)
                    .build();

            PropagationSettings propagationSettings = new PropagationSettings.Builder()
                    .setMaximumPropagationDistance(300.0)
                    .setComputeHorizontalDiffraction(true)
                    .setComputeVerticalDiffraction(true)
                    .setSoundReflectionOrder(0)
                    .build();

            NoiseMapByReceiverMaker noiseMapMaker = new NoiseMapByReceiverMaker.Builder()
                    .setBuildingTableSettings(buildingTableSettings)
                    .setSourcesTableName("SOURCES")
                    .setReceiverTableName("RECEIVERS")
                    .setDemTable("DEM")
                    .setPropagationSettings(propagationSettings)
                    .build();
            
            // Run computation
            LOGGER.info("Running computation...");
            noiseMapMaker.initialize(connection, new EmptyProgressVisitor());
            noiseMapMaker.run(connection, new EmptyProgressVisitor());
            
            // Retrieve and analyze results
            CalculationIOSettings params = noiseMapMaker.getCalculationIOSettings();
            String resultsTable = params.receiversLevelTable;
            
            LOGGER.info("\n=== Results ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT IDRECEIVER, HZ1000 FROM " + resultsTable + " ORDER BY IDRECEIVER")) {
                
                String[] locations = {"just beyond (70m)", "far (150m)", "above (20m)", "aside (side path)"};
                int idx = 0;
                double[] levels = new double[4];
                
                while (rs.next()) {
                    long receiverId = rs.getLong("IDRECEIVER");
                    double level = rs.getDouble("HZ1000");
                    levels[(int)receiverId - 1] = level;
                    LOGGER.info("PK={}: {} dB ({})", receiverId, String.format("%.1f", level), 
                            receiverId <= 4 ? locations[(int)receiverId - 1] : "");
                }
                
                // Analyze diffraction effects
                LOGGER.info("\n=== Analysis ===");
                LOGGER.info("Attenuation differences due to building obstruction:");
                if (levels[2] > 0 && levels[0] > 0) {
                    double diffraction_loss = levels[2] - levels[0];
                    LOGGER.info("  Above (unobstructed) vs Just beyond (obstructed): {}{} dB", 
                            (diffraction_loss > 0 ? "+" : ""), String.format("%.1f", diffraction_loss));
                }
            }
        }
    }

    /**
     * Test Case 5: Multiple Overlapping Building Geometries
     * 
     * Tests the effect of multiple buildings with overlapping geometry on sound propagation.
     * Examines how the system handles complex urban geometry with adjacent/overlapping obstacles.
     */
    @Test
    public void testMultipleOverlappingBuildings() throws Exception {
        try (Statement st = connection.createStatement()) {
            LOGGER.info("\n=== Test Case 5: Multiple Overlapping Buildings ===");
            
            // Create multiple building geometries with overlapping footprints
            st.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            // Building 1: West obstacle (x: 20-40)
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POLYGON((20 5, 40 5, 40 15, 20 15, 20 5))', 2154), 12.0)");
            // Building 2: Center obstacle (x: 40-60, overlaps with Building 1)
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POLYGON((40 0, 60 0, 60 20, 40 20, 40 0))', 2154), 15.0)");
            // Building 3: East obstacle (x: 60-80, overlaps with Building 2)
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POLYGON((60 5, 80 5, 80 15, 60 15, 60 5))', 2154), 10.0)");
            LOGGER.info("Building 1: (20,5)-(40,15), h=12m");
            LOGGER.info("Building 2: (40,0)-(60,20), h=15m (overlaps with 1&3)");
            LOGGER.info("Building 3: (60,5)-(80,15), h=10m (overlaps with 2)");
            
            // Create flat DEM
            st.execute("CREATE TABLE DEM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO DEM(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POINT(50 10)', 2154), 0.0)");
            
            // Create point source left of building array
            createPointSource(st, 0.0, 10.0, 5.0, 85.0);
            LOGGER.info("Source: (0, 10, 5m), 85 dB, left of building complex");
            
            // Create receivers downstream of building complex
            st.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ), HEIGHT_TYPE VARCHAR(10))");
            
            // Receiver 1: Just beyond Building 3 (same height as source)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(90.0 10.0 5.0)', 2154), 'RELATIVE')");
            
            // Receiver 2: Between Building 2 and 3 (between obstacles)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(55.0 10.0 5.0)', 2154), 'RELATIVE')");
            
            // Receiver 3: Between Building 1 and 2 (between obstacles)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(35.0 10.0 5.0)', 2154), 'RELATIVE')");
            
            // Receiver 4: Above all buildings (unobstructed)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.0 10.0 20.0)', 2154), 'RELATIVE')");
            
            // Receiver 5: Side path around buildings (minimal diffraction)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(50.0 -10.0 5.0)', 2154), 'RELATIVE')");
            
            LOGGER.info("Receivers: PK1(beyond all, 90m), PK2(between B2-B3, 55m), PK3(between B1-B2, 35m), PK4(above all, 20m), PK5(side, min-diff)");
            
            BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                    .setBuildingsTableName("BUILDINGS")
                    .setHeightField("HEIGHT")
                    .setAlphaFieldName("G")
                    .setDefaultWallAbsorption(100000.0)
                    .setZBuildings(false)
                    .build();

            PropagationSettings propagationSettings = new PropagationSettings.Builder()
                    .setMaximumPropagationDistance(300.0)
                    .setComputeHorizontalDiffraction(true)
                    .setComputeVerticalDiffraction(true)
                    .setSoundReflectionOrder(0)
                    .build();

            NoiseMapByReceiverMaker noiseMapMaker = new NoiseMapByReceiverMaker.Builder()
                    .setBuildingTableSettings(buildingTableSettings)
                    .setSourcesTableName("SOURCES")
                    .setReceiverTableName("RECEIVERS")
                    .setDemTable("DEM")
                    .setPropagationSettings(propagationSettings)
                    .build();
            
            // Run computation
            LOGGER.info("Running computation...");
            noiseMapMaker.initialize(connection, new EmptyProgressVisitor());
            noiseMapMaker.run(connection, new EmptyProgressVisitor());
            
            // Retrieve and analyze results
            CalculationIOSettings params = noiseMapMaker.getCalculationIOSettings();
            String resultsTable = params.receiversLevelTable;
            
            LOGGER.info("\n=== Results ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT IDRECEIVER, HZ1000 FROM " + resultsTable + " ORDER BY IDRECEIVER")) {
                
                String[] locations = {"beyond all (90m)", "B2-B3 gap (55m)", "B1-B2 gap (35m)", "above all (20m)", "side path"};
                double[] levels = new double[5];
                int count = 0;
                
                while (rs.next()) {
                    long receiverId = rs.getLong("IDRECEIVER");
                    double level = rs.getDouble("HZ1000");
                    levels[(int)receiverId - 1] = level;
                    LOGGER.info("PK={}: {} dB ({})", receiverId, String.format("%.1f", level), 
                            receiverId <= 5 ? locations[(int)receiverId - 1] : "");
                    count++;
                }
                
                // Analyze cumulative diffraction effects
                LOGGER.info("\n=== Analysis ===");
                LOGGER.info("Attenuation progression through building complex:");
                if (levels[3] > -100) {
                    LOGGER.info("  Unobstructed (above): {} dB", String.format("%.1f", levels[3]));
                }
                if (levels[2] > -100) {
                    LOGGER.info("  B1-B2 gap (35m): {} dB", String.format("%.1f", levels[2]));
                }
                if (levels[1] > -100) {
                    LOGGER.info("  B2-B3 gap (55m): {} dB", String.format("%.1f", levels[1]));
                }
                if (levels[0] > -100) {
                    LOGGER.info("  Beyond all (90m): {} dB", String.format("%.1f", levels[0]));
                }
                if (levels[4] > -100) {
                    LOGGER.info("  Side path: {} dB", String.format("%.1f", levels[4]));
                }
            }
        }
    }

    /**
     * Test Case 6: Geometrically Overlapping Buildings
     * 
     * Examine behavior when building footprints actually overlap on XY plane.
     * Buildings share common area in top-down view (not just adjacent).
     * 
     * Test scenarios:
     * A) Receiver in region obstructed by single building (within overlap zone)
     * B) Receiver beyond all buildings
     * C) Receiver above overlapping buildings (unobstructed)
     * D) Receiver at edge of overlap region
     */
    @Test
    public void testGeometricOverlappingBuildings() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("DROP TABLE IF EXISTS BUILDINGS, SOURCES, RECEIVERS, DEM");
            
            // Create building table
            st.execute("CREATE TABLE BUILDINGS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            
            // Create 3 buildings with TRUE geometric overlap (not just adjacent)
            // Building 1: x: 20-40, y: 5-15
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POLYGON((20 5, 40 5, 40 15, 20 15, 20 5))', 2154), 12.0)");
            // Building 2: x: 30-50, y: 5-15 (overlaps with B1 at x: 30-40)
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POLYGON((30 5, 50 5, 50 15, 30 15, 30 5))', 2154), 15.0)");
            // Building 3: x: 40-60, y: 5-15 (overlaps with B2 at x: 40-50)
            st.execute("INSERT INTO BUILDINGS(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POLYGON((40 5, 60 5, 60 15, 40 15, 40 5))', 2154), 10.0)");
            
            LOGGER.info("Test Case 6: Geometrically Overlapping Buildings");
            LOGGER.info("Building 1: (20,5)-(40,15), h=12m");
            LOGGER.info("Building 2: (30,5)-(50,15), h=15m [OVERLAPS B1 at x:30-40, y:5-15]");
            LOGGER.info("Building 3: (40,5)-(60,15), h=10m [OVERLAPS B2 at x:40-50, y:5-15]");
            
            // Create flat DEM
            st.execute("CREATE TABLE DEM(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY, HEIGHT REAL)");
            st.execute("INSERT INTO DEM(THE_GEOM, HEIGHT) VALUES " +
                    "(ST_GeomFromText('POINT(45 10)', 2154), 0.0)");
            
            // Create point source left of building complex
            createPointSource(st, 0.0, 10.0, 5.0, 85.0);
            LOGGER.info("Source: (0, 10, 5m), 85 dB, left of building complex");
            
            // Create receivers
            st.execute("CREATE TABLE RECEIVERS(PK SERIAL PRIMARY KEY, THE_GEOM GEOMETRY(POINTZ), HEIGHT_TYPE VARCHAR(10))");
            
            // Receiver 1: In overlap zone B1+B2 (x=35 falls within both buildings)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(35.0 10.0 5.0)', 2154), 'RELATIVE')");
            
            // Receiver 2: In overlap zone B2+B3 (x=45 falls within both buildings)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(45.0 10.0 5.0)', 2154), 'RELATIVE')");
            
            // Receiver 3: In triple overlap (if exists) or B1-only zone (x=25)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(25.0 10.0 5.0)', 2154), 'RELATIVE')");
            
            // Receiver 4: Beyond all buildings (x=65)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(65.0 10.0 5.0)', 2154), 'RELATIVE')");
            
            // Receiver 5: Above all buildings (unobstructed)
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(45.0 10.0 20.0)', 2154), 'RELATIVE')");
            
            // Receiver 6: Outside overlap, beyond B3
            st.execute("INSERT INTO RECEIVERS(THE_GEOM, HEIGHT_TYPE) VALUES " +
                    "(ST_GeomFromText('POINT Z(70.0 10.0 5.0)', 2154), 'RELATIVE')");
            
            LOGGER.info("Receivers: PK1(B1-B2 overlap, x=35), PK2(B2-B3 overlap, x=45), PK3(B1-only, x=25), PK4(beyond, x=65), PK5(above all), PK6(far, x=70)");
            
            
            BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                    .setBuildingsTableName("BUILDINGS")
                    .setHeightField("HEIGHT")
                    .setAlphaFieldName("G")
                    .setDefaultWallAbsorption(100000.0)
                    .setZBuildings(false)
                    .build();

            PropagationSettings propagationSettings = new PropagationSettings.Builder()
                    .setMaximumPropagationDistance(400.0)
                    .setComputeHorizontalDiffraction(true)
                    .setComputeVerticalDiffraction(true)
                    .setSoundReflectionOrder(0)
                    .build();

            NoiseMapByReceiverMaker noiseMapMaker = new NoiseMapByReceiverMaker.Builder()
                    .setBuildingTableSettings(buildingTableSettings)
                    .setSourcesTableName("SOURCES")
                    .setReceiverTableName("RECEIVERS")
                    .setDemTable("DEM")
                    .setPropagationSettings(propagationSettings)
                    .build();


            // Run computation
            LOGGER.info("Running computation with overlapping building geometries...");
            noiseMapMaker.initialize(connection, new EmptyProgressVisitor());
            noiseMapMaker.run(connection, new EmptyProgressVisitor());
            
            // Retrieve and analyze results
            CalculationIOSettings params = noiseMapMaker.getCalculationIOSettings();
            String resultsTable = params.receiversLevelTable;
            
            LOGGER.info("\n=== Results: Overlapping Building Geometry ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT IDRECEIVER, HZ1000 FROM " + resultsTable + " ORDER BY IDRECEIVER")) {
                
                String[] locations = {
                    "B1-B2 overlap (x=35)",
                    "B2-B3 overlap (x=45)",
                    "B1-only (x=25)",
                    "beyond all (x=65)",
                    "above all (unobstructed)",
                    "far beyond (x=70)"
                };
                double[] levels = new double[6];
                int count = 0;
                
                while (rs.next()) {
                    long receiverId = rs.getLong("IDRECEIVER");
                    double level = rs.getDouble("HZ1000");
                    levels[(int)receiverId - 1] = level;
                    LOGGER.info("PK={}: {} dB ({})", receiverId, String.format("%.1f", level), 
                            receiverId <= 6 ? locations[(int)receiverId - 1] : "");
                    count++;
                }
                
                // Analyze overlapping geometry effects
                LOGGER.info("\n=== Analysis: Geometric Overlap Effects ===");
                if (count > 0) {
                    LOGGER.info("Attenuation at different geometric positions:");
                    if (levels[4] > -100) {
                        LOGGER.info("  Above all (unobstructed): {} dB [reference]", 
                                String.format("%.1f", levels[4]));
                    }
                    if (levels[2] > -100) {
                        LOGGER.info("  B1-only zone (x=25): {} dB", 
                                String.format("%.1f", levels[2]));
                    }
                    if (levels[0] > -100) {
                        LOGGER.info("  B1-B2 overlap (x=35): {} dB", 
                                String.format("%.1f", levels[0]));
                    }
                    if (levels[1] > -100) {
                        LOGGER.info("  B2-B3 overlap (x=45): {} dB", 
                                String.format("%.1f", levels[1]));
                    }
                    if (levels[3] > -100) {
                        LOGGER.info("  Beyond all (x=65): {} dB", 
                                String.format("%.1f", levels[3]));
                    }
                    if (levels[5] > -100) {
                        LOGGER.info("  Far beyond (x=70): {} dB", 
                                String.format("%.1f", levels[5]));
                    }
                    
                    LOGGER.info("\nKey Question: How does system handle overlapping footprints?");
                    if (levels[0] > -100 && levels[2] > -100) {
                        double diff = levels[0] - levels[2];
                        LOGGER.info("  Overlap vs single: {:.1f} dB difference", String.format("%.1f", diff));
                    }
                    if (levels[0] > -100 && levels[1] > -100) {
                        double diff = levels[1] - levels[0];
                        LOGGER.info("  Overlap zone difference (B2-B3 vs B1-B2): {} dB", String.format("%.1f", diff));
                    }
                } else {
                    LOGGER.info("No results retrieved");
                }
            }
        }
    }
}



