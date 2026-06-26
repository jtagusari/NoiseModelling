package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.jdbc.input.EmissionInputSettings;
import org.noise_planet.noisemodelling.jdbc.input.PropagationSettings;
import org.noise_planet.noisemodelling.jdbc.output.NoiseMapWriter;
import org.noise_planet.noisemodelling.propagation.cnossos.CnossosPathExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for bridge traffic noise computation via the JDBC pipeline.
 *
 * <p>Uses the TutoBridge dataset (Seishin Bypass, Shizuoka, SRID=6676).
 * Each test loads bridge points, roads with {@code BRIDGE_PK}, DEM, buildings,
 * and receivers from GeoJSON files into H2GIS, then runs
 * {@link NoiseMapByReceiverMaker} end-to-end.
 *
 * <p>Path generation and attenuation values are validated separately in
 * {@code PathFinderBridgeTest} and {@code AttenuationComputeOutputCnossosBridgeTest}.
 * These tests focus on JDBC-layer concerns:
 * <ul>
 *   <li>Bridge point loading from the {@code BRIDGE_POINTS} table</li>
 *   <li>Road-to-bridge assignment via the {@code BRIDGE_PK} column in the source table</li>
 *   <li>{@code RAYS} output: {@code BRIDGE_RELATION_TYPE} column populated correctly</li>
 *   <li>{@code RECEIVERS_LEVEL} output: valid {@code LAEQ} written for every receiver</li>
 * </ul>
 */
public class BridgeTrafficTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeTrafficTest.class);

    private static final String ROADS_TABLE         = "ROADS";
    private static final String BUILDINGS_TABLE     = "BUILDINGS";
    private static final String RECEIVERS_TABLE     = "RECEIVERS";
    private static final String BRIDGE_POINTS_TABLE = "BRIDGE_POINTS";
    private static final String DEM_TABLE           = "DEM";
    private static final String RAYS_TABLE          = "RAYS";

    private Connection connection;

    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(
                H2GISDBFactory.createSpatialDataBase(BridgeTrafficTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    /** Load TutoBridge GeoJSON files and add BRIDGE_PK column to ROADS. */
    private void loadTutoBridgeData() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute(String.format("CALL GeoJsonRead('%s', '%s')", res("roads.geojson"),        ROADS_TABLE));
            st.execute(String.format("CALL GeoJsonRead('%s', '%s')", res("buildings.geojson"),    BUILDINGS_TABLE));
            st.execute(String.format("CALL GeoJsonRead('%s', '%s')", res("receivers.geojson"),    RECEIVERS_TABLE));
            st.execute(String.format("CALL GeoJsonRead('%s', '%s')", res("bridgepoints.geojson"), BRIDGE_POINTS_TABLE));
            st.execute(String.format("CALL GeoJsonRead('%s', '%s')", res("dem.geojson"),          DEM_TABLE));

            for (String table : new String[]{ROADS_TABLE, BUILDINGS_TABLE, RECEIVERS_TABLE, BRIDGE_POINTS_TABLE}) {
                st.execute("ALTER TABLE " + table + " ALTER COLUMN PK INT NOT NULL");
                st.execute("ALTER TABLE " + table + " ADD PRIMARY KEY(PK)");
            }

            // roads.geojson has no BRIDGE_PK column; assign all segments to bridge 1
            st.execute("ALTER TABLE " + ROADS_TABLE + " ADD COLUMN BRIDGE_PK INT");
            st.execute("UPDATE " + ROADS_TABLE + " SET BRIDGE_PK = 1");
        }
    }

    private NoiseMapByReceiverMaker buildNoiseMap() {
        TableInputSettings tableInputSettings = new TableInputSettings.Builder()
                .setBuildingTableName(BUILDINGS_TABLE)
                .setBuildingHeightFieldName("HEIGHT")
                .setSourceTableName(ROADS_TABLE)
                .setReceiverTableName(RECEIVERS_TABLE)
                .setTerrainTableName(DEM_TABLE)
                .setBridgePointTableName(BRIDGE_POINTS_TABLE)
                .build();

        EmissionInputSettings emissionInputSettings = new EmissionInputSettings.Builder()
                .setInputMode(EmissionInputSettings.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW_DEN)
                .build();

        PropagationSettings propagationSettings = new PropagationSettings.Builder()
                .setMaximumPropagationDistance(500.0)
                .setSoundReflectionOrder(0)
                .build();

        CalculationIOSettings calculationIOSettings = new CalculationIOSettings.Builder()
                .setMaximumError(0.0)
                .setExportRaysMethod(CalculationIOSettings.ExportRaysMethods.TO_RAYS_TABLE)
                .setRaysTable(RAYS_TABLE)
                .setExportAttenuationMatrix(true)
                .setExportCnossosPathWithAttenuation(true)
                .build();

        return new NoiseMapByReceiverMaker.Builder()
                .setTableInputSettings(tableInputSettings)
                .setEmissionInputSettings(emissionInputSettings)
                .setPropagationSettings(propagationSettings)
                .setCalculationIOSettings(calculationIOSettings)
                .setThreadCount(1)
                .build();
    }

    private static String res(String filename) {
        return BridgeTrafficTest.class.getResource("/TutoBridge/" + filename).getFile();
    }

    /**
     * Verifies that every receiver in the input table gets a finite LAEQ in
     * {@code RECEIVERS_LEVEL} for period D.
     *
     * <p>This exercises the full pipeline: bridge point loading → DEN traffic emission
     * → propagation → level aggregation → DB write. The critical invariant is that
     * no receiver is silently skipped (a known failure mode when bridge source
     * enumeration fails for non-first receivers).
     */
    @Test
    public void testAllReceiversGetNoiseLevels() throws Exception {
        loadTutoBridgeData();
        buildNoiseMap().run(connection, new EmptyProgressVisitor());

        Set<Long> receiverPks = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT PK FROM " + RECEIVERS_TABLE)) {
            while (rs.next()) receiverPks.add(rs.getLong(1));
        }
        assertFalse(receiverPks.isEmpty(), "No receivers in input table");

        Set<Long> receiversWithLevel = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT IDRECEIVER, LAEQ FROM "
                     + CalculationIOSettings.DEFAULT_RECEIVERS_LEVEL_TABLE_NAME
                     + " WHERE PERIOD='D'")) {
            while (rs.next()) {
                long id    = rs.getLong(1);
                double laeq = rs.getDouble(2);
                assertTrue(Double.isFinite(laeq) && laeq > 0,
                        "Invalid Laeq=" + laeq + " for receiver " + id);
                receiversWithLevel.add(id);
                LOGGER.info("receiver={} Laeq={}", id, String.format("%.1f", laeq));
            }
        }

        Set<Long> missing = new HashSet<>(receiverPks);
        missing.removeAll(receiversWithLevel);
        assertTrue(missing.isEmpty(), "Receivers missing from RECEIVERS_LEVEL: " + missing);
    }

    /**
     * Verifies that the {@code RAYS} table contains both {@code ACTUAL_SOURCE_ON_BRIDGE}
     * and {@code IMAGINARY_SOURCE_UNDER_BRIDGE} entries when all roads carry a
     * {@code BRIDGE_PK}.
     *
     * <p>The JDBC layer is responsible for reading {@code BRIDGE_PK} from the source table
     * and creating the corresponding source variants; this test confirms that classification
     * reaches the output.
     */
    @Test
    public void testRaysTableContainsBridgeRelationTypes() throws Exception {
        loadTutoBridgeData();
        buildNoiseMap().run(connection, new EmptyProgressVisitor());

        assertTrue(JDBCUtilities.tableExists(connection, RAYS_TABLE),
                RAYS_TABLE + " table not created");

        Set<String> types = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT DISTINCT BRIDGE_RELATION_TYPE FROM " + RAYS_TABLE
                     + " WHERE PERIOD='D'")) {
            while (rs.next()) types.add(rs.getString(1));
        }
        LOGGER.info("Bridge relation types present in RAYS (period D): {}", types);

        assertTrue(types.contains("ACTUAL_SOURCE_ON_BRIDGE"),
                "Expected ACTUAL_SOURCE_ON_BRIDGE in RAYS, found: " + types);
        assertTrue(types.contains("IMAGINARY_SOURCE_UNDER_BRIDGE"),
                "Expected IMAGINARY_SOURCE_UNDER_BRIDGE in RAYS, found: " + types);
    }

    /**
     * Verifies that the {@code PATH} column in every {@code RAYS} row deserializes
     * to a valid {@link CnossosPathExt} with a non-null {@code aGlobal} array.
     *
     * <p>This confirms that the attenuation computation completes for all bridge
     * source types and that the JSON serialization round-trip is intact.
     */
    @Test
    public void testRayPathJsonIsParseable() throws Exception {
        loadTutoBridgeData();
        buildNoiseMap().run(connection, new EmptyProgressVisitor());

        int rowCount = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT IDRECEIVER, BRIDGE_RELATION_TYPE, PATH FROM "
                     + RAYS_TABLE + " WHERE PERIOD='D'")) {
            while (rs.next()) {
                rowCount++;
                long   receiverId = rs.getLong(1);
                String type       = rs.getString(2);
                String json       = rs.getString(3);
                assertNotNull(json, "PATH is null for receiver=" + receiverId + " type=" + type);
                assertFalse(json.isEmpty(), "PATH is empty for receiver=" + receiverId + " type=" + type);
                CnossosPathExt path = NoiseMapWriter.jsonToPropagationPath(json);
                assertNotNull(path.aGlobal,
                        "aGlobal is null for receiver=" + receiverId + " type=" + type);
            }
        }
        assertTrue(rowCount > 0, "No rows in " + RAYS_TABLE + " for period D");
    }
}
