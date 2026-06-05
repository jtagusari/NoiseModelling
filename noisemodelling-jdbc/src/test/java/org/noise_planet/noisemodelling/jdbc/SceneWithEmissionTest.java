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
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.noise_planet.noisemodelling.jdbc.input.PropagationSettings;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;
import org.noise_planet.noisemodelling.jdbc.output.AttenuationOutputMultiThread;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunayError;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.WallAbsorption;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig.FrequencyBand;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.noise_planet.noisemodelling.propagation.AttenuationParameters;
import org.noise_planet.noisemodelling.propagation.ReceiverNoiseLevel;
import org.noise_planet.noisemodelling.jdbc.input.SourceEmission;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.*;

/**
 * Test class evaluation and testing attenuation values.
 */
public class SceneWithEmissionTest {
    private static final double HUMIDITY = 70;
    private static final double TEMPERATURE = 10;
    private List<Long> testIgnoreNonSignificantSourcesParam(Connection connection, double maxError) throws SQLException, IOException {
        return testIgnoreNonSignificantSourcesParam(connection, maxError, "BUILDINGS",
                "LW_ROADS", "RECEIVERS", "");
    }

    private List<Long> testIgnoreNonSignificantSourcesParam(
            Connection connection, double maxError, String buildingsTableName, String sourcesTableName,
            String receiverTableName, String sourcesEmissionTableName) throws SQLException, IOException {

        // Init NoiseModelling
        PropagationSettings propagationSettings = new PropagationSettings.Builder()
                .setMaximumPropagationDistance(5000.0)
                .setSoundReflectionOrder(1)
                .setComputeHorizontalDiffraction(true)
                .setComputeVerticalDiffraction(true)
                .build();
        
        SceneDatabaseInputSettings sceneDatabaseInputSettings = new SceneDatabaseInputSettings.Builder()
                .setSourcesEmissionTableName(sourcesEmissionTableName)
                .build();

        CalculationIOSettings calculationIOSettings = new CalculationIOSettings.Builder()
                .setMaximumError(maxError)
                .setMergeSources(false)
                .build();
        
        BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                .setBuildingsTableName(buildingsTableName)
                .setHeightField("HEIGHT")
                .build();

        NoiseMapByReceiverMaker noiseMap = new NoiseMapByReceiverMaker.Builder()
                .setPropagationSettings(propagationSettings)
                .setSceneDatabaseInputSettings(sceneDatabaseInputSettings)
                .setBuildingTableSettings(buildingTableSettings)
                .setCalculationIOSettings(calculationIOSettings)
                .setSourcesTableName(sourcesTableName)
                .setReceiverTableName(receiverTableName)
                .setThreadCount(1)
                .setGridDim(1)
                .build();


        noiseMap.run(connection, new EmptyProgressVisitor());

        Statement st = connection.createStatement();
        List<Long> sourcePks = new LinkedList<>();
        try(ResultSet rs = st.executeQuery("SELECT DISTINCT IDSOURCE FROM " + noiseMap.getCalculationIOSettings().getReceiversLevelTable())) {
            while (rs.next()) {
                sourcePks.add(rs.getLong(1));
            }
        }
        return sourcePks;
    }

    static public void assertInferiorThan(double expected, double actual) {
        assertTrue(expected < actual, String.format(Locale.ROOT, "Expected %f < %f", expected, actual));
    }

    /**
     * Test optimisation feature {@link CalculationIOSettings#setMaximumError(double)}
     * This feature is disabled and all sound sources are computed
     */
    @Test
    public void testIgnoreNonSignificantSources() throws Exception {
        final double maxError = 0.5;
        try (Connection connection =
                     JDBCUtilities.wrapConnection(
                             H2GISDBFactory.createSpatialDataBase(
                                     "testReceiverOverBuilding", true, ""))) {
            try (Statement st = connection.createStatement()) {
                st.execute(Utils.getRunScriptRes("scenario_skip_far_source.sql"));



                List<Long> allSourcesPk = testIgnoreNonSignificantSourcesParam(connection, 0.);
                List<Long> ignoreFarSourcesPk = testIgnoreNonSignificantSourcesParam(connection, maxError);
                assertEquals(2, allSourcesPk.size());
                assertEquals(1, ignoreFarSourcesPk.size());
            }
        }
    }

    private static Map<String, Double> fetchReceiverLevel(Connection connection) throws SQLException {
        Map<String, Double> allSourcesReceiverLevel = new HashMap<>();
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT PERIOD, LEQ FROM RECEIVERS_LEVEL")) {
                // Sum contribution of all sources
                while (rs.next()) {
                    allSourcesReceiverLevel.merge(rs.getString("PERIOD"), dBToW(rs.getDouble("LEQ")), Double::sum);
                }
            }
        }
        return allSourcesReceiverLevel;
    }

    /**
     * Test optimisation feature {@link CalculationIOSettings#setMaximumError(double)}
     * This feature is disabled and all sound sources are computed
     */
    @Test
    public void testIgnoreNonSignificantSources2() throws Exception {
        final double maxError = 3.0;

        try (Connection connection =
                     JDBCUtilities.wrapConnection(
                             H2GISDBFactory.createSpatialDataBase(
                                     "testIgnoreNonSignificantSources2", true, ""))) {
            try (Statement st = connection.createStatement()) {
                st.execute(Utils.getRunScriptRes("skip_far_source2.sql"));
                List<Long> allSourcesPk = testIgnoreNonSignificantSourcesParam(connection, 0.,
                        "BUILDINGS", "SOURCES_GEOM",
                        "RECEIVERS", "SOURCES_EMISSION");
                Map<String, Double> allSourcesReceiverLevel = fetchReceiverLevel(connection);
                List<Long> ignoreFarSourcesPk = testIgnoreNonSignificantSourcesParam(connection, maxError,
                        "BUILDINGS", "SOURCES_GEOM",
                        "RECEIVERS", "SOURCES_EMISSION");

                Map<String, Double> someSourcesReceiverLevel = fetchReceiverLevel(connection);
                // The noise level error should be in the expected range
                for (Map.Entry<String, Double> entry : allSourcesReceiverLevel.entrySet()) {
                    String period = entry.getKey();
                    double levelAllSources = wToDb(entry.getValue());
                    assertTrue(someSourcesReceiverLevel.containsKey(period));
                    double levelLimitedSources = wToDb(someSourcesReceiverLevel.get(period));
                    assertTrue(Math.abs(levelAllSources - levelLimitedSources) < maxError);
                }
                // Some sources should be skipped or maxDbError not doing its job
                assertNotEquals( allSourcesPk.size(), ignoreFarSourcesPk.size());
            }
        }
    }

    /**
     * Check if Li coefficient computation and line source subdivision are correctly done
     */
    @Test
    public void testSourceLines()  throws ParseException {

        // First Compute the scene with only point sources at 1m each
        GeometryFactory factory = new GeometryFactory();
        WKTReader wktReader = new WKTReader(factory);
        LineString geomSource = (LineString)wktReader.read("LINESTRING (51 40.5 0.05, 51 55.5 0.05)");
        FrequencyConfig frequencyConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.OCTAVE);

        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder(frequencyConfig);

        builder.addGroundEffect(factory.toGeometry(new Envelope(0, 50, -250, 250)), 0.9);
        builder.addGroundEffect(factory.toGeometry(new Envelope(50, 150, -250, 250)), 0.5);
        builder.addGroundEffect(factory.toGeometry(new Envelope(150, 225, -250, 250)), 0.2);

        builder.finishFeeding();

        double[] roadLvl = AcousticIndicatorsFunctions.dBToW(new double[]{25.65, 38.15, 54.35, 60.35, 74.65, 66.75, 59.25, 53.95});

        SceneWithEmission scene = new SceneWithEmission(builder);
        scene.addReceiver(0L, new Coordinate(50, 50, 0.05));
        scene.addReceiver(1L, new Coordinate(48, 50, 4));
        scene.addReceiver(2L, new Coordinate(44, 50, 4));
        scene.addReceiver(3L, new Coordinate(40, 50, 4));
        scene.addReceiver(4L, new Coordinate(20, 50, 4));
        scene.addReceiver(5L, new Coordinate(0, 50, 4));

        List<Coordinate> srcPtsRef = new ArrayList<>();
        PathFinder.splitLineStringIntoPoints(geomSource, 1.0, srcPtsRef);
        for (long i = 0; i < srcPtsRef.size(); i++) {
            Coordinate srcPtRef = srcPtsRef.get((int) i);
            scene.addSource(i, factory.createPoint(srcPtRef), null);
            SourceEmission emission = new SourceEmission("", roadLvl);
            scene.registerSourceEmission(i, emission);
        }

        scene.setComputeHorizontalDiffraction(true);
        scene.setComputeVerticalDiffraction(true);
        scene.setMaxSrcDist(2000);

        AttenuationParameters attData = new AttenuationParameters(FrequencyConfig.FrequencyBand.OCTAVE);
        attData.setHumidity(70);
        attData.setTemperature(10);

        AttenuationOutputMultiThread propDataOut = new AttenuationOutputMultiThread(scene);

        PathFinder computeRays = new PathFinder(scene);
        computeRays.ensureAbsoluteReceiverHeights();
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();
        computeRays.run(propDataOut);


        // Second compute the same scene but with a line source
        scene.clearSources();
        scene.addSource(1L, geomSource, null);
        SourceEmission emission = new SourceEmission("", roadLvl);
        scene.registerSourceEmission(1L, emission);

        AttenuationOutputMultiThread propDataOutTest = new AttenuationOutputMultiThread(scene);
        computeRays.ensureAbsoluteReceiverHeights();
        computeRays.run(propDataOutTest);

        List<ReceiverNoiseLevel> levelsPerReceiver = new ArrayList<>(propDataOut.resultsCache.receiverLevels);
        List<ReceiverNoiseLevel> levelsPerReceiverLines = new ArrayList<>(propDataOutTest.resultsCache.receiverLevels);

        assertEquals(6, levelsPerReceiver.size());
        assertEquals(6, levelsPerReceiverLines.size());

        for(int i = 0; i < levelsPerReceiver.size(); i++) {
            assertArrayEquals(levelsPerReceiver.get(i).getLevels(), levelsPerReceiverLines.get(i).getLevels(), 0.2);
        }
    }



    /**
     * Test of convergence of power at receiver when increasing the reflection order
     * Event at 100 order of reflection then final noise level should not be
     * superior to 3.0 decibels compared to direct power
     */
    @Test
    public void testReflexionConvergence() {

        //Profile building
        FrequencyConfig frequencyConfig = new FrequencyConfig(FrequencyConfig.FrequencyBand.ONE_THIRD_OCTAVE);
        List<Double> alphaWall = new ArrayList<>(frequencyConfig.getFrequencyArray().size());
        for(int frequency : frequencyConfig.getFrequencyArray()) {
            alphaWall.add(WallAbsorption.getWallAlpha(100000, frequency));
        }

        ProfileBuilder profileBuilder = new ProfileBuilder(frequencyConfig)
                .addWall(new Coordinate[]{
                        new Coordinate(6, 0, 4),
                        new Coordinate(-5, 12, 4),
                }, 8, alphaWall, 0)
                .addWall(new Coordinate[]{
                        new Coordinate(14, 4, 4),
                        new Coordinate(3, 16, 4),
                }, 8, alphaWall, 1);
        profileBuilder.setzBuildings(true);
        profileBuilder.finishFeeding();


        double[] sourcePower = new double[profileBuilder.getFrequencyArray().size()];
        Arrays.fill(sourcePower,  AcousticIndicatorsFunctions.dBToW(70.0));

        //Propagation data building
        SceneWithEmission scene = new SceneWithEmission(profileBuilder);
        GeometryFactory gf = new GeometryFactory();
        scene.addSource(1L, gf.createPoint(new Coordinate(8, 5.5, 0.1)), null);
        scene.addReceiver(0L, new Coordinate(4.5, 8, 1.6));
        scene.setDefaultGroundAttenuation(0.5);
        SourceEmission emission = new SourceEmission("", sourcePower);
        scene.registerSourceEmission(1L, emission);

        scene.setMaxSrcDist(100*800);
        scene.maxRefDist = 100*800;
        //Propagation process path data building
        AttenuationParameters attData = new AttenuationParameters(frequencyConfig);
        attData.setHumidity(HUMIDITY);
        attData.setTemperature(TEMPERATURE);
        // Set the attenuation parameters for empty period (no period-specific settings)
        scene.cnossosParametersPerPeriod.put("", attData);

        double firstPowerAtReceiver = 0;
        for(int i = 0; i < 100; i++) {

            //Out and computation settings
            AttenuationOutputMultiThread propDataOut = new AttenuationOutputMultiThread(scene);
            scene.setReflexionOrder(i);
            PathFinder computeRays = new PathFinder(scene);
            computeRays.setThreadCount(1);
            computeRays.ensureAbsoluteReceiverHeights();

            //Run computation
            computeRays.run(propDataOut);

            //Actual values
            // number of propagation paths between two walls = reflectionOrder * 2 + 1
            assertEquals(i * 2 + 1, propDataOut.cnossosPathCount.get());

            double globalPowerAtReceiver = AcousticIndicatorsFunctions.sumDbArray(propDataOut.resultsCache.receiverLevels.pop().getLevels());
            if(i == 0) {
                firstPowerAtReceiver = globalPowerAtReceiver;
            } else {
                assertEquals(firstPowerAtReceiver, globalPowerAtReceiver, 3.0);
            }
        }
    }


    /**
     * Test reported issue with receiver over building
     */
    @Test
    public void testReceiverOverBuilding() throws LayerDelaunayError, ParseException {

        GeometryFactory factory = new GeometryFactory();
        //Scene dimension
        FrequencyConfig frequencyConfig = new FrequencyConfig(FrequencyBand.OCTAVE);

        WKTReader wktReader = new WKTReader();
        //Create obstruction test object
        ProfileBuilder builder = new ProfileBuilder(frequencyConfig);

        builder.addGroundEffect(factory.toGeometry(new Envelope(0, 50, -250, 250)), 0.9);
        builder.addGroundEffect(factory.toGeometry(new Envelope(50, 150, -250, 250)), 0.5);
        builder.addGroundEffect(factory.toGeometry(new Envelope(150, 225, -250, 250)), 0.2);

        builder.addBuilding(wktReader.read("POLYGON ((-111 -35, -111 82, 70 82, 70 285, 282 285, 282 -35, -111 -35))"), 10, -1);

        builder.finishFeeding();

        double[] roadLvl = AcousticIndicatorsFunctions.dBToW(new double[]{25.65, 38.15, 54.35, 60.35, 74.65, 66.75, 59.25, 53.95});

        SceneWithEmission scene = new SceneWithEmission(builder);
        scene.addReceiver(0L, new Coordinate(162, 80, 150));
        scene.addSource(1L, factory.createPoint(new Coordinate(-150, 200, 1)), null);
        scene.setComputeHorizontalDiffraction(true);
        scene.setComputeVerticalDiffraction(true);
        SourceEmission emission = new SourceEmission("", roadLvl);
        scene.registerSourceEmission(1L, emission);

        scene.setMaxSrcDist(2000);
        
        AttenuationParameters attData = new AttenuationParameters(FrequencyConfig.FrequencyBand.OCTAVE);
        attData.setHumidity(70);
        attData.setTemperature(10);
        scene.setAttenuationParameters(attData);


        
        PathFinder computeRays = new PathFinder(scene);
        computeRays.setThreadCount(1);
        computeRays.ensureAbsoluteReceiverHeights();
        AttenuationOutputMultiThread outputMultiThread = new AttenuationOutputMultiThread(scene);
        computeRays.run(outputMultiThread);

        assertEquals(1, outputMultiThread.resultsCache.queueSize.get());

        assertEquals(14.6, AcousticIndicatorsFunctions.wToDb(sumArray(roadLvl.length,
                AcousticIndicatorsFunctions.dBToW(outputMultiThread.resultsCache.receiverLevels.pop().getLevels()))),
                0.1);
    }

}

