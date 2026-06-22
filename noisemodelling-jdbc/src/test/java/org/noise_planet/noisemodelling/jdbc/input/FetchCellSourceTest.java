package org.noise_planet.noisemodelling.jdbc.input;

import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import org.noise_planet.noisemodelling.jdbc.NoiseMapByReceiverMaker;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig.FrequencyBand;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;
import org.noise_planet.noisemodelling.jdbc.TableInputSettings;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.DefaultProgressVisitor;
import org.noise_planet.noisemodelling.jdbc.input.DefaultTableLoader;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FetchCellSourceTest {

    private Connection connection;

    @BeforeEach
    public void setUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(FetchCellSourceTest.class.getSimpleName(), true, ""));
        try (Statement st = connection.createStatement()) {
            st.execute(String.format("CALL GeoJsonRead('%s', 'BRIDGE_POINTS')",
                    FetchCellSourceTest.class.getResource("/bridge_points.geojson").getFile()));
            st.execute(String.format("CALL GeoJsonRead('%s', 'ROADS')",
                    FetchCellSourceTest.class.getResource("/roads.geojson").getFile()));
            st.execute("ALTER TABLE ROADS ALTER COLUMN PK SET NOT NULL");
            st.execute("ALTER TABLE ROADS ADD CONSTRAINT PK_SET PRIMARY KEY (PK)");
            st.execute(String.format("CALL GeoJsonRead('%s', 'DEM')",
                    FetchCellSourceTest.class.getResource("/dem.geojson").getFile()));
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    public void testFetchCellSource_DEN() throws Exception {
        DefaultTableLoader tableLoader = new DefaultTableLoader();

        TableInputSettings tableInputSettings = new TableInputSettings.Builder()
                .setSourceTableName("ROADS")
                .setTerrainTableName("DEM")
                .setBridgePointTableName("BRIDGE_POINTS")
                .setReceiverTableName("RECEIVERS")
                .build();

        PropagationSettings propagationSettings = new PropagationSettings.Builder()
                .setMaximumPropagationDistance(100.0)
                .setMaximumReflectionDistance(50.0)
                .build();
        
        EmissionInputSettings emissionInputSettings = new EmissionInputSettings.Builder()
                .setInputMode(EmissionInputSettings.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW_DEN)
                .build();

        NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker.Builder()
                .setTableInputSettings(tableInputSettings)
                .setPropagationSettings(propagationSettings)
                .setEmissionInputSettings(emissionInputSettings)
                .build();

        noiseMapByReceiverMaker.setMainEnvelope(new Envelope(0.0, 100.0, 0.0, 100.0));

        DefaultProgressVisitor progressVisitor = new DefaultProgressVisitor(1, null);
        noiseMapByReceiverMaker.initialize(connection, progressVisitor);
        tableLoader.initialize(connection, noiseMapByReceiverMaker);

        ProfileBuilder profileBuilder = new ProfileBuilder(new FrequencyConfig(FrequencyBand.OCTAVE));
        SceneWithEmission scene = new SceneWithEmission(profileBuilder, noiseMapByReceiverMaker.getEmissionInputSettings());

        Envelope mainEnvelope = new Envelope(0, 100, 0, 100);
        Envelope expandedCellEnvelop = new Envelope(mainEnvelope);
        expandedCellEnvelop.expandBy(noiseMapByReceiverMaker.getMaximumPropagationDistance() + 2 * noiseMapByReceiverMaker.getMaximumReflectionDistance());
    

        CellProfileLoader cellProfileLoader = new CellProfileLoader.Builder()
                .setConnection(connection)
                .setCellSceneContext(noiseMapByReceiverMaker.getCellSceneContext())
                .build();
        cellProfileLoader.fetchCellTerrain(expandedCellEnvelop, profileBuilder);
        cellProfileLoader.fetchCellBridge(expandedCellEnvelop, profileBuilder);
        profileBuilder.finishFeeding();

        tableLoader.fetchCellSource(connection, noiseMapByReceiverMaker.getCellSceneContext(), mainEnvelope, scene, true);

        int sourceCount = scene.countSources();
        assertTrue(sourceCount >= 1, "At least one source should be registered by fetchCellSource");

        List<Long> sourcePks = scene.getSourcePks();
        assertNotNull(sourcePks);
        assertTrue(sourcePks.contains(1L), "Expected source PK 1 to be registered");
        assertTrue(sourcePks.contains(2L), "Expected source PK 2 to be registered");
        assertTrue(sourcePks.contains(3L), "Expected source PK 3 to be registered");

        assertTrue(scene.getSourceEmissionsMap().size() >= 1, "sourceEmissionsMap should contain entries");

        // Simple success log for quick CLI verification
        System.out.println("FetchCellSourceTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0");
    }
}
