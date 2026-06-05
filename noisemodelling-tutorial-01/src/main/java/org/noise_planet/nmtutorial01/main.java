package org.noise_planet.nmtutorial01;

import org.h2.value.ValueBoolean;
import org.h2gis.functions.io.geojson.GeoJsonRead;
import org.h2gis.functions.io.shp.SHPWrite;
import org.h2gis.utilities.GeometryTableUtilities;
import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.TableLocation;
import org.h2gis.utilities.dbtypes.DBTypes;
import org.h2gis.utilities.dbtypes.DBUtils;
import org.noise_planet.noisemodelling.jdbc.NoiseMapByReceiverMaker;
import org.noise_planet.noisemodelling.jdbc.utils.IsoSurface;
import org.noise_planet.noisemodelling.jdbc.input.PropagationSettings;
import org.noise_planet.noisemodelling.jdbc.input.SceneDatabaseInputSettings;
import org.noise_planet.noisemodelling.jdbc.CalculationIOSettings;
import org.noise_planet.noisemodelling.jdbc.BuildingTableSettings;
import org.noise_planet.noisemodelling.jdbc.DelaunayReceiversMaker;
import org.noise_planet.noisemodelling.pathfinder.delaunay.LayerDelaunayError;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.RootProgressVisitor;

import org.noise_planet.noisemodelling.propagation.AttenuationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

class Main {
    public final static int MAX_OUTPUT_PROPAGATION_PATHS = 50000;

    public static NoiseMapByReceiverMaker mainWithConnection(Connection connection, String workingDir)  throws SQLException, IOException, LayerDelaunayError {

        if(!new File(workingDir).exists()) {
            new File(workingDir).mkdir();
        }

        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));

        TableLocation tableLwRoads = TableLocation.parse("LW_ROADS", dbType);
        TableLocation tableBuildings = TableLocation.parse("BUILDINGS", dbType);
        TableLocation tableDemLorient = TableLocation.parse("DEM", dbType);
        String heightField = dbType.equals(DBTypes.POSTGIS) ? "height"  : "HEIGHT";

        // Init output logger
        Logger logger = LoggerFactory.getLogger(Main.class);

        Statement sql = connection.createStatement();

        // Import BUILDINGS

        logger.info("Import buildings");

        GeoJsonRead.importTable(connection, Main.class.getResource("buildings.geojson").getFile(), tableBuildings.toString(),
                ValueBoolean.TRUE);

        // Import noise source

        logger.info("Import noise source");

        GeoJsonRead.importTable(connection, Main.class.getResource("lw_roads.geojson").getFile(), tableLwRoads.toString(),
                ValueBoolean.TRUE);
        // Set primary key
        sql.execute("ALTER TABLE "+tableLwRoads+" ALTER COLUMN PK SET NOT NULL");
        sql.execute("ALTER TABLE "+tableLwRoads+" ADD PRIMARY KEY (PK)");

        // Import BUILDINGS

        logger.info("Generate receivers grid for noise map rendering");

        BuildingTableSettings buildingTableSettings = new BuildingTableSettings.Builder()
                .setBuildingsTableName(tableBuildings.toString())
                .setHeightField(heightField)
                .setAlphaFieldName("G")
                .setDefaultWallAbsorption(100000)
                .setZBuildings(false)
                .build();

        DelaunayReceiversMaker noiseMap = new DelaunayReceiversMaker.Builder()
                .setBuildingTableSettings(buildingTableSettings)
                .setSourcesTableName(tableLwRoads.toString())
                .build();

        noiseMap.setGridDim(1);
        noiseMap.setMaximumArea(0);
        noiseMap.setIsoSurfaceInBuildings(false);
        sql.execute("DROP TABLE IF EXISTS RECEIVERS;");
        sql.execute("DROP TABLE IF EXISTS TRIANGLES;");

        noiseMap.run(connection, "RECEIVERS", "TRIANGLES");

        // Import MNT

        logger.info("Import digital elevation model");

        GeoJsonRead.importTable(connection, Main.class.getResource("dem_lorient.geojson").getFile(),
                tableDemLorient.toString(),
                ValueBoolean.TRUE);


        RootProgressVisitor progressLogger = new RootProgressVisitor(1, true, 1);

        String atmosphericSettingsTableName = "ATMOSPHERIC_SETTINGS";

        sql.execute("DROP TABLE IF EXISTS " + atmosphericSettingsTableName + ";");

        AttenuationParameters defaultParameters = new AttenuationParameters();
        defaultParameters.setTemperature(20);
        defaultParameters.writeToDatabase(connection, atmosphericSettingsTableName, "D");
        defaultParameters.setTemperature(16);
        defaultParameters.writeToDatabase(connection, atmosphericSettingsTableName, "E");
        defaultParameters.setTemperature(10);
        defaultParameters.writeToDatabase(connection, atmosphericSettingsTableName, "N");

        PropagationSettings propagationSettings = new PropagationSettings.Builder()
                .setMaximumPropagationDistance(100.0)
                .setMaximumReflectionDistance(100.0)
                .setGs(0)
                .setGroundSurfaceSplitSideLength(200)
                .setSoundReflectionOrder(0)
                .setBodyBarrier(false)
                .setComputeHorizontalDiffraction(false)
                .setComputeVerticalDiffraction(true)
                .build();
        
        CalculationIOSettings calculationIOSettings = new CalculationIOSettings.Builder()
                .setMaximumError(3.0)
                .setExportReceiverPosition(true)
                .build();
        
        SceneDatabaseInputSettings sceneDatabaseInputSettings = new SceneDatabaseInputSettings.Builder()
                .setFrequencyFieldPrepend("LW")
                .setPeriodAtmosphericSettingsTableName(atmosphericSettingsTableName)
                .build();

        NoiseMapByReceiverMaker noiseMapByReceiverMaker = new NoiseMapByReceiverMaker.Builder()
                .setBuildingTableSettings(buildingTableSettings)
                .setPropagationSettings(propagationSettings)
                .setSceneDatabaseInputSettings(sceneDatabaseInputSettings)
                .setCalculationIOSettings(calculationIOSettings)
                .setSourcesTableName(tableLwRoads.toString())
                .setDemTable(tableDemLorient.toString())
                .setReceiverTableName("RECEIVERS")
                .setGridDim(1)
                .build();

        noiseMapByReceiverMaker.run(connection, progressLogger);

        logger.info("Create iso contours");
        int srid = GeometryTableUtilities.getSRID(connection, TableLocation.parse("LW_ROADS", DBTypes.H2GIS));
        List<Double> isoLevels = IsoSurface.NF31_133_ISO; // default values
        IsoSurface isoSurface = new IsoSurface(isoLevels, srid);
        isoSurface.setSmoothCoefficient(0.5);
        isoSurface.setPointTable(TableLocation.parse(noiseMapByReceiverMaker.getCalculationIOSettings().receiversLevelTable, dbType).toString());
        isoSurface.createTable(connection, "IDRECEIVER");
        logger.info("Export iso contours");

        SHPWrite.exportTable(connection, Paths.get(workingDir, isoSurface.getOutputTable()+".shp").toString(),
                isoSurface.getOutputTable(), ValueBoolean.TRUE);

        SHPWrite.exportTable(connection, Paths.get(workingDir, noiseMapByReceiverMaker.getSourcesTableName()+".shp").toString(),
                noiseMapByReceiverMaker.getSourcesTableName(), ValueBoolean.TRUE);

        SHPWrite.exportTable(connection, Paths.get(workingDir, noiseMapByReceiverMaker.getCalculationIOSettings().getReceiversLevelTable()+".shp").toString(),
                noiseMapByReceiverMaker.getCalculationIOSettings().getReceiversLevelTable(), ValueBoolean.TRUE);

        return noiseMapByReceiverMaker;
    }

    public static void main(String[] args) throws SQLException, IOException, LayerDelaunayError {
        // Init output logger
        Logger logger = LoggerFactory.getLogger(Main.class);

        // Read working directory argument
        String workingDir = "target";
        if (args.length > 0) {
            workingDir = args[0];
        }
        File workingDirPath = new File(workingDir).getAbsoluteFile();
        if(!workingDirPath.exists()) {
            if(!workingDirPath.mkdirs()) {
                logger.error("Cannot create working directory {}", workingDir);
                return;
            }
        }

        logger.info("Working directory is {}", workingDirPath.getAbsolutePath());

        // Create spatial database named to current time
        DateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault());

        // Open connection to database
        String dbName = Paths.get(workingDir,  "db_" + df.format(new Date())).toFile().toURI().toString();
        Connection connection = JDBCUtilities.wrapConnection(DbUtilities.createSpatialDataBase(dbName, true));
        mainWithConnection(connection, workingDir);
    }

}