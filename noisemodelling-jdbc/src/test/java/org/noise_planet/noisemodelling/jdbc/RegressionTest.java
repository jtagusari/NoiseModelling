package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.api.EmptyProgressVisitor;
import org.h2gis.functions.factory.H2GISDBFactory;
import org.h2gis.utilities.JDBCUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noise_planet.noisemodelling.jdbc.input.PropagationSettings;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.jdbc.Utils.getRunScriptRes;

public class RegressionTest {

    private Connection connection;

    @BeforeEach
    public void tearUp() throws Exception {
        connection = JDBCUtilities.wrapConnection(H2GISDBFactory.createSpatialDataBase(NoiseMapByReceiverMakerTest.class.getSimpleName(), true, ""));
    }

    @AfterEach
    public void tearDown() throws Exception {
        if(connection != null) {
            connection.close();
        }
    }

    /**
     * Got reflection index out of bound exception in this scenario in the past (source-&gt;reflection-&gt;h diffraction-&gt;receiver)
     */
    @Test
    public void testScenarioOutOfBoundException() throws Exception {
        try(Statement st = connection.createStatement()) {
            st.execute(getRunScriptRes("regression_nan/lw_roads.sql"));

            TableInputSettings tableInputSettings = new TableInputSettings.Builder()
                    .setBuildingTableName("BUILDINGS")
                    .setBuildingHeightFieldName("HEIGHT")
                    .setSourceTableName("LW_ROADS")
                    .setReceiverTableName("RECEIVERS")
                    .build();

            PropagationSettings propagationSettings = new PropagationSettings.Builder()
                    .setMaximumPropagationDistance(500.0)
                    .setSoundReflectionOrder(1)
                    .setComputeHorizontalDiffraction(true)
                    .setComputeVerticalDiffraction(true)
                    .build();
            
            SceneDatabaseInputSettings sceneDatabaseInputSettings = new SceneDatabaseInputSettings.Builder()
                    .setInputMode(SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN)
                    .setFrequencyFieldPrepend("LW")
                    .build();
            
            CalculationIOSettings calculationIOSettings = new CalculationIOSettings.Builder()
                    .setExportReceiverPosition(true)
                    .build();

            NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker.Builder()
                    .setTableInputSettings(tableInputSettings)
                    .setPropagationSettings(propagationSettings)
                    .setSceneDatabaseInputSettings(sceneDatabaseInputSettings)
                    .setCalculationIOSettings(calculationIOSettings)
                    .setThreadCount(1)
                    .build();



            noiseMapByReceiverMaker.run(connection, new EmptyProgressVisitor());

            try(ResultSet rs = st.executeQuery("SELECT LEQ FROM " + noiseMapByReceiverMaker.getCalculationIOSettings().getReceiversLevelTable() + " WHERE PERIOD='DEN'")) {
                assertTrue(rs.next());
                assertEquals(36.77, rs.getDouble(1), 0.1);
                assertFalse(rs.next());
            }
        }
    }


}
