/**
 * NoiseModelling is an open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by the DECIDE team from the Lab-STICC (CNRS) and by the Mixt Research Unit in Environmental Acoustics (Université Gustave Eiffel).
 * <http://noise-planet.org/noisemodelling.html>
 *
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 *
 * Contact: contact@noise-planet.org
 *
 */

/**
 * @Author Pierre Aumond, Université Gustave Eiffel
 */

package org.noise_planet.noisemodelling.wps.NoiseModelling

import geoserver.GeoServer
import geoserver.catalog.Store
import groovy.sql.Sql
import org.geotools.jdbc.JDBCDataStore
import org.h2gis.utilities.GeometryTableUtilities
import org.h2gis.utilities.JDBCUtilities
import org.h2gis.utilities.SpatialResultSet
import org.h2gis.utilities.TableLocation
import org.h2gis.utilities.wrapper.ConnectionWrapper
import org.locationtech.jts.geom.Geometry
import org.noise_planet.noisemodelling.jdbc.BridgeStructuralEmissionCalculator
import org.noise_planet.noisemodelling.jdbc.EmissionTableGenerator
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgePoint
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

title = 'Compute road emission noise map from road table.'
description = '&#10145;&#65039; Compute Road Emission Noise Map from Day Evening Night traffic flow rate and speed estimates (specific format, see input details). </br>' +
              '<hr>' +
              '&#x2705; The output table is called: <b>LW_ROADS </b> '

inputs = [
        tableRoads: [
                name       : 'Roads table name',
                title      : 'Roads table name',
                description: "<b>Name of the Roads table.</b>  </br>  " +
                        "<br>  This function recognize the following columns (* mandatory) : </br><ul>" +
                        "<li><b> PK </b>* : an identifier. It shall be a primary key (INTEGER, PRIMARY KEY)</li>" +
                        "<li><b> LV_D </b><b>LV_E </b><b>LV_N </b> : Hourly average light vehicle count (6-18h)(18-22h)(22-6h) (DOUBLE)</li>" +
                        "<li><b> MV_D </b><b>MV_E </b><b>MV_N </b> : Hourly average medium heavy vehicles, delivery vans > 3.5 tons,  buses, touring cars, etc. with two axles and twin tyre mounting on rear axle count (6-18h)(18-22h)(22-6h) (DOUBLE)</li>" +
                        "<li><b> HGV_D </b><b> HGV_E </b><b> HGV_N </b> :  Hourly average heavy duty vehicles, touring cars, buses, with three or more axles (6-18h)(18-22h)(22-6h) (DOUBLE)</li>" +
                        "<li><b> WAV_D </b><b> WAV_E </b><b> WAV_N </b> :  Hourly average mopeds, tricycles or quads &le; 50 cc count (6-18h)(18-22h)(22-6h) (DOUBLE)</li>" +
                        "<li><b> WBV_D </b><b> WBV_E </b><b> WBV_N </b> :  Hourly average motorcycles, tricycles or quads > 50 cc count (6-18h)(18-22h)(22-6h) (DOUBLE)</li>" +
                        "<li><b> LV_SPD_D </b><b> LV_SPD_E </b><b>LV_SPD_N </b> :  Hourly average light vehicle speed (6-18h)(18-22h)(22-6h) (DOUBLE)</li>" +
                        "<li><b> MV_SPD_D </b><b> MV_SPD_E </b><b>MV_SPD_N </b> :  Hourly average medium heavy vehicles speed (6-18h)(18-22h)(22-6h) (DOUBLE)</li>" +
                        "<li><b> HGV_SPD_D </b><b> HGV_SPD_E </b><b> HGV_SPD_N </b> :  Hourly average heavy duty vehicles speed (6-18h)(18-22h)(22-6h) (DOUBLE)</li>" +
                        "<li><b> WAV_SPD_D </b><b> WAV_SPD_E </b><b> WAV_SPD_N </b> :  Hourly average mopeds, tricycles or quads &le; 50 cc speed (6-18h)(18-22h)(22-6h) (DOUBLE)</li>" +
                        "<li><b> WBV_SPD_D </b><b> WBV_SPD_E </b><b> WBV_SPD_N </b> :  Hourly average motorcycles, tricycles or quads > 50 cc speed (6-18h)(18-22h)(22-6h) (DOUBLE)</li>" +
                        "<li><b> PVMT </b> :  CNOSSOS road pavement identifier (ex: NL05)(default NL08) (VARCHAR)</li>" +
                        "<li><b> TS_STUD </b> : A limited period Ts (in months) over the year where a average proportion pm of light vehicles are equipped with studded tyres (0-12) (DOUBLE)</li>" +
                        "<li><b> PM_STUD </b> : Average proportion of vehicles equipped with studded tyres during TS_STUD period (0-1) (DOUBLE)</li>" +
                        "<li><b> JUNC_DIST </b> : Distance to junction in meters (DOUBLE)</li>" +
                        "<li><b> JUNC_TYPE </b> : Type of junction (k=0 none, k = 1 for a crossing with traffic lights ; k = 2 for a roundabout) (INTEGER)</li>" +
                        "<li><b> SLOPE </b> : Slope (in %) of the road section. If the field is not filled in, the LINESTRING z-values will be used to calculate the slope and the traffic direction (way field) will be force to 3 (bidirectional). (DOUBLE)</li>" +
                        "<li><b> WAY </b> : Define the way of the road section. 1 = one way road section and the traffic goes in the same way that the slope definition you have used, 2 = one way road section and the traffic goes in the inverse way that the slope definition you have used, 3 = bi-directional traffic flow, the flow is split into two components and correct half for uphill and half for downhill (INTEGER)</li>" +
                        "<li><b> HEIGHT_TYPE </b> : Source height interpretation type. 'ABSOLUTE' (default when geometry has Z coordinate) = height value is absolute elevation in the same coordinate system as DEM, 'RELATIVE' (default when geometry has no Z coordinate) = height value is relative to ground elevation (VARCHAR)</li>" +
                        "<li><b> BRIDGE_PK </b> : Primary key of the bridge on which the road is located (optional, for bridge structural noise calculation) (INTEGER)</li>" +
                        "</ul></br><b> This table can be generated from the WPS Block 'Import_OSM'. </b>.",
                type       : String.class
        ],
        tableBridgePoints: [
                name       : 'Bridge points table name (optional)',
                title      : 'Bridge points table name',
                description: "<b>Name of the Bridge Points table (optional).</b> Required for bridge structural noise calculation (SOURCE_TYPE='BRIDGE'). </br>" +
                        "<br>This table should contain bridge geometry and structure information: </br><ul>" +
                        "<li><b> PK </b>* : Point identifier (INTEGER, PRIMARY KEY)</li>" +
                        "<li><b> BRIDGE_PK </b>* : Bridge identifier linking to ROADS.BRIDGE_PK (INTEGER)</li>" +
                        "<li><b> THE_GEOM </b>* : Point geometry (POINT with Z coordinate)</li>" +
                        "<li><b> GIRDER_TYPE </b> : Bridge girder type (STEEL_BOX, STEEL_PLATE, CONCRETE_BOX, CONCRETE_PLATE, CONCRETE_HOLLOW_SLAB) (VARCHAR)</li>" +
                        "<li><b> SLAB_TYPE </b> : Bridge slab type (STEEL, CONCRETE) (VARCHAR)</li>" +
                        "<li><b> ABSOLUTE_DECK_HEIGHT </b> : Absolute elevation of bridge deck (DOUBLE)</li>" +
                        "<li><b> DECK_THICKNESS </b> : Deck thickness in meters (DOUBLE)</li>" +
                        "</ul>",
                type       : String.class,
                min        : 0, max: 1
        ],
        coefficientVersion: [
                name       : 'Coefficient version',
                title      : 'Coefficient version',
                description: '&#127783; Cnossos coefficient version  (1 = 2015, 2 = 2020) </br> </br>' +
                        '&#128736; Default value: <b>2</b>',
                min        : 0, max: 1,
                type       : Double.class
        ]
]

outputs = [
        result: [
                name       : 'Result output string',
                title      : 'Result output string',
                description: 'This type of result does not allow the blocks to be linked together.',
                type       : String.class
        ]
]
// Open Connection to Geoserver
static Connection openGeoserverDataStoreConnection(String dbName) {
    if (dbName == null || dbName.isEmpty()) {
        dbName = new GeoServer().catalog.getStoreNames().get(0)
    }
    Store store = new GeoServer().catalog.getStore(dbName)
    JDBCDataStore jdbcDataStore = (JDBCDataStore) store.getDataStoreInfo().getDataStore(null)
    return jdbcDataStore.getDataSource().getConnection()
}

// run the script
def run(input) {

    // Get name of the database
    // by default an embedded h2gis database is created
    // Advanced user can replace this database for a postGis or h2Gis server database.
    String dbName = "h2gisdb"

    // Open connection
    openGeoserverDataStoreConnection(dbName).withCloseable {
        Connection connection ->
            return [result: exec(connection, input)]
    }
}

// main function of the script
def exec(Connection connection, input) {

    int coefficientVersion = 2
    if (input.containsKey('coefficientVersion')) {
        coefficientVersion = Integer.parseInt(input['confHumidity'] as String)
    }

    //Need to change the ConnectionWrapper to WpsConnectionWrapper to work under postGIS database
    connection = new ConnectionWrapper(connection)

    // output string, the information given back to the user
    String resultString = null

    // Create a logger to display messages in the geoserver logs and in the command prompt.
    Logger logger = LoggerFactory.getLogger("org.noise_planet.noisemodelling")

    // print to command window
    logger.info('Start : Road Emission from DEN')
    logger.info("inputs {}", input) // log inputs of the run


    // -------------------
    // Get every inputs
    // -------------------

    String sources_table_name = input['tableRoads']
    // do it case-insensitive
    sources_table_name = sources_table_name.toUpperCase()
    
    // Check if srid are in metric projection.
    int sridSources = GeometryTableUtilities.getSRID(connection, TableLocation.parse(sources_table_name))
    if (sridSources == 3785 || sridSources == 4326) throw new IllegalArgumentException("Error : Please use a metric projection for "+sources_table_name+".")
    if (sridSources == 0) throw new IllegalArgumentException("Error : The table "+sources_table_name+" does not have an associated SRID.")

    //Get the geometry field of the source table
    TableLocation sourceTableIdentifier = TableLocation.parse(sources_table_name)
    List<String> geomFields = GeometryTableUtilities.getGeometryColumnNames(connection, sourceTableIdentifier)
    if (geomFields.isEmpty()) {
        throw new SQLException(String.format("The table %s does not exists or does not contain a geometry field", sourceTableIdentifier))
    }

    //Get the primary key field of the source table
    int pkIndex = JDBCUtilities.getIntegerPrimaryKey(connection, TableLocation.parse( sources_table_name))
    if (pkIndex < 1) {
        throw new IllegalArgumentException(String.format("Source table %s does not contain a primary key", sourceTableIdentifier))
    }

    // Optional: Bridge points table for structural noise calculation
    // Default to BRIDGE_POINTS if not specified
    String bridge_points_table_name = ""
    if (input['tableBridgePoints']) {
        bridge_points_table_name = input['tableBridgePoints']
    } else {
        // Try to use default BRIDGE_POINTS table if it exists
        bridge_points_table_name = "BRIDGE_POINTS"
    }
    
    // do it case-insensitive
    bridge_points_table_name = bridge_points_table_name.toUpperCase()
    
    // Check if the table exists
    boolean bridgePointsTableExists = JDBCUtilities.tableExists(connection, bridge_points_table_name)
    
    if (!bridgePointsTableExists) {
        if (input['tableBridgePoints']) {
            // User explicitly specified a table that doesn't exist
            throw new IllegalArgumentException("Error : The table "+bridge_points_table_name+" does not exist.")
        } else {
            // Default table doesn't exist, check if any road record has BRIDGE_PK
            logger.info("Default BRIDGE_POINTS table not found")
            
            // Create a sql connection to check for BRIDGE_PK usage
            Sql sql = new Sql(connection)
            
            // Check if BRIDGE_PK column exists and is used
            boolean hasBridgePkColumn = JDBCUtilities.hasField(connection, sources_table_name, "BRIDGE_PK")
            
            if (hasBridgePkColumn) {
                def bridgePkCount = sql.firstRow("SELECT COUNT(*) as cnt FROM " + sources_table_name + " WHERE BRIDGE_PK IS NOT NULL")
                int numRecordsWithBridgePk = bridgePkCount.cnt as Integer
                
                if (numRecordsWithBridgePk > 0) {
                    throw new IllegalArgumentException(
                        "Error: BRIDGE_POINTS table does not exist, but " + numRecordsWithBridgePk + 
                        " record(s) in " + sources_table_name + " have BRIDGE_PK values set. " +
                        "Bridge structural noise calculation requires a valid BRIDGE_POINTS table. " +
                        "Please provide a BRIDGE_POINTS table or remove BRIDGE_PK values from the roads table.")
                }
            }
            
            logger.info("No BRIDGE_PK values found in roads table, skipping bridge structural noise calculation")
            bridge_points_table_name = ""
        }
    } else {
        // Table exists, perform SRID validation
        logger.info("Using bridge points table: " + bridge_points_table_name)
        // Check if srid are in metric projection and are all the same.
        int sridBridgePoints = GeometryTableUtilities.getSRID(connection, TableLocation.parse(bridge_points_table_name))
        if (sridBridgePoints == 3785 || sridBridgePoints == 4326) throw new IllegalArgumentException("Error : Please use a metric projection for "+bridge_points_table_name+".")
        if (sridBridgePoints == 0) throw new IllegalArgumentException("Error : The table "+bridge_points_table_name+" does not have an associated SRID.")
        if (sridBridgePoints != sridSources) throw new IllegalArgumentException("Error : The SRID of table "+sources_table_name+" and "+bridge_points_table_name+" are not the same.")
    }

    // -------------------
    // Load Bridge Information (if provided)
    // -------------------
    Map<Long, Bridge> bridgeMap = new HashMap<>()
    
    if (!bridge_points_table_name.isEmpty()) {
        logger.info('Loading bridge information from ' + bridge_points_table_name)
        
        // Create a sql connection
        Sql sql = new Sql(connection)
        
        // Load bridge points from database
        List<BridgePoint> bridgePoints = new ArrayList<>()
        
        sql.eachRow("SELECT * FROM " + bridge_points_table_name) { row ->
            BridgePoint bridgePoint = new BridgePoint()
            
            // Set primary keys
            bridgePoint.setPrimaryKey(row.PK as Long)
            bridgePoint.setBridgePrimaryKey(row.BRIDGE_PK as Long)
            
            // Set geometry
            Geometry geom = row.THE_GEOM as Geometry
            if (geom != null && !geom.isEmpty()) {
                bridgePoint.setCoordinate(geom.getCoordinate())
            }
            
            // Set bridge structure type
            if (row.hasProperty('GIRDER_TYPE') && row.GIRDER_TYPE != null) {
                try {
                    bridgePoint.setGirderType(Bridge.GirderType.valueOf(row.GIRDER_TYPE as String))
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown girder type: " + row.GIRDER_TYPE + ", using default")
                }
            }
            
            if (row.hasProperty('SLAB_TYPE') && row.SLAB_TYPE != null) {
                try {
                    bridgePoint.setSlabType(Bridge.SlabType.valueOf(row.SLAB_TYPE as String))
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown slab type: " + row.SLAB_TYPE + ", using default")
                }
            }
            
            // Set height information
            if (row.hasProperty('ABSOLUTE_DECK_HEIGHT') && row.ABSOLUTE_DECK_HEIGHT != null) {
                bridgePoint.setAbsoluteDeckHeight(row.ABSOLUTE_DECK_HEIGHT as Double)
            }
            
            if (row.hasProperty('DECK_THICKNESS') && row.DECK_THICKNESS != null) {
                bridgePoint.setDeckThickness(row.DECK_THICKNESS as Double)
            }
            
            bridgePoints.add(bridgePoint)
        }
        
        logger.info('Loaded ' + bridgePoints.size() + ' bridge points')
        
        // Create bridges from bridge points
        if (!bridgePoints.isEmpty()) {
            List<Bridge> bridges = Bridge.createBridgesFromPoints(bridgePoints)
            logger.info('Created ' + bridges.size() + ' bridge structures')
            
            // Create map for easy lookup by bridge PK
            for (Bridge bridge : bridges) {
                bridgeMap.put(bridge.getPrimaryKey(), bridge)
            }
        }
    }

    // -------------------
    // Step 2: Bridge Record Duplication and Classification
    // Duplicate bridge road records to enable dual emission calculation:
    // SOURCE_TYPE='ROAD' for traffic noise, SOURCE_TYPE='BRIDGE' for structural noise
    // -------------------
    logger.info('Step 2: Checking for bridge records and duplicating if necessary...')
    
    // Create a sql connection to interact with the database in SQL
    Sql sql = new Sql(connection)
    
    // Check if BRIDGE_PK column exists in the source table
    boolean hasBridgePkColumn = false
    try {
        sql.firstRow("SELECT BRIDGE_PK FROM " + sources_table_name + " LIMIT 1")
        hasBridgePkColumn = true
        logger.info('BRIDGE_PK column found in ' + sources_table_name)
    } catch (SQLException e) {
        logger.info('BRIDGE_PK column not found in ' + sources_table_name + ', skipping bridge duplication')
    }
    
    if (hasBridgePkColumn) {
        // Check if SOURCE_TYPE column already exists - if so, throw exception
        boolean hasSourceTypeColumn = false
        try {
            sql.firstRow("SELECT SOURCE_TYPE FROM " + sources_table_name + " LIMIT 1")
            hasSourceTypeColumn = true
        } catch (SQLException e) {
            // Column doesn't exist, which is expected
            hasSourceTypeColumn = false
        }
        
        if (hasSourceTypeColumn) {
            throw new IllegalArgumentException("Error: SOURCE_TYPE column already exists in " + sources_table_name + 
                ". This script expects to create this column. Please remove the SOURCE_TYPE column or use a different source table.")
        }
        
        // Add SOURCE_TYPE column (now we know it doesn't exist)
        sql.execute("ALTER TABLE " + sources_table_name + " ADD COLUMN SOURCE_TYPE VARCHAR(20)")
        logger.info('Added SOURCE_TYPE column to ' + sources_table_name)
        
        // Set all existing records to 'ROAD'
        sql.execute("UPDATE " + sources_table_name + " SET SOURCE_TYPE = 'ROAD'")
        logger.info('Set SOURCE_TYPE to ROAD for existing records')
        
        // Count bridge records
        def bridgeCount = sql.firstRow("SELECT COUNT(*) as cnt FROM " + sources_table_name + " WHERE BRIDGE_PK IS NOT NULL AND SOURCE_TYPE = 'ROAD'")
        int numBridgeRecords = bridgeCount.cnt as Integer
        
        if (numBridgeRecords > 0) {
            logger.info('Found ' + numBridgeRecords + ' bridge records to duplicate')
            
            // Validate that each bridge road is contained within the bridge footprint
            if (!bridgeMap.isEmpty()) {
                logger.info('Validating bridge road geometries...')
                sql.eachRow("SELECT PK, BRIDGE_PK, THE_GEOM FROM " + sources_table_name + " WHERE BRIDGE_PK IS NOT NULL AND SOURCE_TYPE = 'ROAD'") { row ->
                    Long roadPk = row.PK as Long
                    Long bridgePkValue = row.BRIDGE_PK as Long
                    Geometry roadGeometry = row.THE_GEOM as Geometry
                    
                    // Check if bridge exists in bridgeMap
                    Bridge bridge = bridgeMap.get(bridgePkValue)
                    if (bridge == null) {
                        throw new IllegalArgumentException(
                            "Bridge with PK=" + bridgePkValue + " not found in bridge points table " + bridge_points_table_name + ". " +
                            "Road record PK=" + roadPk + " references a non-existent bridge.")
                    }
                    
                    // Get bridge footprint geometry
                    Geometry bridgeFootprint = bridge.getFootprintGeometry()
                    if (bridgeFootprint == null) {
                        throw new IllegalArgumentException(
                            "Bridge with PK=" + bridgePkValue + " has no footprint geometry. " +
                            "Cannot validate road geometry containment for road PK=" + roadPk)
                    }
                    
                    // Check if road geometry is contained within bridge footprint
                    if (!bridgeFootprint.contains(roadGeometry)) {
                        throw new IllegalArgumentException(
                            "Road geometry is not fully contained within bridge footprint. " +
                            "bridgePk=" + bridgePkValue + ", roadPk=" + roadPk + ". " +
                            "Roads with BRIDGE_PK must be completely within the bridge geometry.")
                    }
                }
                logger.info('All bridge road geometries validated successfully')
            } else {
                logger.warn('Bridge points table not loaded, skipping bridge geometry validation. ' +
                    'Bridge records will be duplicated but structural noise calculation may fail.')
            }
            
            // Get the maximum PK value for generating new PKs
            def maxPkRow = sql.firstRow("SELECT MAX(PK) as maxpk FROM " + sources_table_name)
            int maxPk = (maxPkRow.maxpk ?: 0) as Integer
            
            // Get all column names except PK
            def columnNames = JDBCUtilities.getColumnNames(connection.getMetaData(), sources_table_name)
            def columnsExceptPk = columnNames.findAll { it.toUpperCase() != 'PK' }
            def columnList = columnsExceptPk.join(', ')
            
            // Duplicate bridge records with SOURCE_TYPE='BRIDGE'
            String duplicateQuery = "INSERT INTO " + sources_table_name + " (PK, " + columnList + ") " +
                    "SELECT (ROW_NUMBER() OVER () + " + maxPk + ") as new_pk, " + columnList + " " +
                    "FROM " + sources_table_name + " " +
                    "WHERE BRIDGE_PK IS NOT NULL AND SOURCE_TYPE = 'ROAD'"
            
            sql.execute(duplicateQuery)
            
            // Update the duplicated records to have SOURCE_TYPE='BRIDGE' and HEIGHT_TYPE='RELATIVE'
            sql.execute("UPDATE " + sources_table_name + " SET SOURCE_TYPE = 'BRIDGE' WHERE PK > " + maxPk)
            
            // Set HEIGHT_TYPE to 'RELATIVE' for bridge structural sources (if column exists)
            boolean hasHeightTypeColumn = JDBCUtilities.hasField(connection, sources_table_name, "HEIGHT_TYPE")
            if (hasHeightTypeColumn) {
                sql.execute("UPDATE " + sources_table_name + " SET HEIGHT_TYPE = 'RELATIVE' WHERE PK > " + maxPk)
                logger.info('Set HEIGHT_TYPE=RELATIVE for duplicated bridge records')
            }
            
            logger.info('Duplicated ' + numBridgeRecords + ' bridge records with SOURCE_TYPE=BRIDGE')
        } else {
            logger.info('No bridge records found to duplicate')
        }
    }

    // -------------------
    // Init table LW_ROADS
    // -------------------

    // drop table LW_ROADS if exists and the create and prepare the table
    sql.execute("drop table if exists LW_ROADS;")
    sql.execute("create table LW_ROADS (pk integer, the_geom Geometry, SOURCE_TYPE varchar(20) DEFAULT 'ROAD', BRIDGE_PK integer, HEIGHT_TYPE varchar(10), " +
            "HZD63 double precision, HZD125 double precision, HZD250 double precision, HZD500 double precision, HZD1000 double precision, HZD2000 double precision, HZD4000 double precision, HZD8000 double precision," +
            "HZE63 double precision, HZE125 double precision, HZE250 double precision, HZE500 double precision, HZE1000 double precision, HZE2000 double precision, HZE4000 double precision, HZE8000 double precision," +
            "HZN63 double precision, HZN125 double precision, HZN250 double precision, HZN500 double precision, HZN1000 double precision, HZN2000 double precision, HZN4000 double precision, HZN8000 double precision);")

    def qry = 'INSERT INTO LW_ROADS(pk,the_geom,SOURCE_TYPE,BRIDGE_PK, ' +
            'HZD63, HZD125, HZD250, HZD500, HZD1000,HZD2000, HZD4000, HZD8000,' +
            'HZE63, HZE125, HZE250, HZE500, HZE1000,HZE2000, HZE4000, HZE8000,' +
            'HZN63, HZN125, HZN250, HZN500, HZN1000,HZN2000, HZN4000, HZN8000) ' +
            'VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);'


    // --------------------------------------
    // Start calculation and fill the table
    // --------------------------------------

    EmissionTableGenerator noiseEmissionMaker = new EmissionTableGenerator()


    // Get size of the table (number of road segments
    PreparedStatement st = connection.prepareStatement("SELECT COUNT(*) AS total FROM " + sources_table_name)
    ResultSet rs1 = st.executeQuery().unwrap(ResultSet.class)
    int nbRoads = 0
    while (rs1.next()) {
        nbRoads = rs1.getInt("total")
        logger.info('The table Roads has ' + nbRoads + ' road segments.')
    }

    int k = 0
    sql.withBatch(100, qry) { ps ->
        st = connection.prepareStatement("SELECT * FROM " + sources_table_name)
        SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)

        Map<String, Integer> sourceFieldsCache = new HashMap<>()
        while (rs.next()) {
            k++
            //logger.info(rs)
            Geometry geo = rs.getGeometry()
            
            // Get SOURCE_TYPE and BRIDGE_PK from the record
            String sourceType = 'ROAD'
            Integer bridgePk = null
            try {
                sourceType = rs.getString('SOURCE_TYPE') ?: 'ROAD'
                bridgePk = rs.getObject('BRIDGE_PK') as Integer
            } catch (SQLException e) {
                // Columns may not exist, use defaults
            }

            // Compute emission sound level for each road segment
            double[][] results
            
            // Route 1: CNOSSOS-EU road traffic noise (SOURCE_TYPE='ROAD')
            // Route 2: ASJ 2023 bridge structural noise (SOURCE_TYPE='BRIDGE')
            if (sourceType == 'BRIDGE' && bridgePk != null) {
                // Route 2: Bridge structural noise calculation
                Bridge bridge = bridgeMap.get(bridgePk as Long)
                if (bridge == null) {
                    throw new IllegalArgumentException("Bridge with PK=" + bridgePk + " not found in bridge map. " +
                        "Road record PK=" + rs.getLong(pkIndex) + " references a non-existent bridge.")
                }
                
                // Use BridgeStructuralEmissionCalculator for structural noise
                results = BridgeStructuralEmissionCalculator.computeStructuralLw(rs, bridge, sourceFieldsCache)
            } else {
                // Route 1: Standard road traffic noise calculation
                results = EmissionTableGenerator.computeLw(rs, coefficientVersion, sourceFieldsCache)
            }
            
            def lday = AcousticIndicatorsFunctions.wToDb(results[0])
            def levening = AcousticIndicatorsFunctions.wToDb(results[1])
            def lnight = AcousticIndicatorsFunctions.wToDb(results[2])
            // fill the LW_ROADS table
            ps.addBatch(rs.getLong(pkIndex) as Integer, geo as Geometry, sourceType as String, bridgePk,
                    lday[0] as Double, lday[1] as Double, lday[2] as Double,
                    lday[3] as Double, lday[4] as Double, lday[5] as Double,
                    lday[6] as Double, lday[7] as Double,
                    levening[0] as Double, levening[1] as Double, levening[2] as Double,
                    levening[3] as Double, levening[4] as Double, levening[5] as Double,
                    levening[6] as Double, levening[7] as Double,
                    lnight[0] as Double, lnight[1] as Double, lnight[2] as Double,
                    lnight[3] as Double, lnight[4] as Double, lnight[5] as Double,
                    lnight[6] as Double, lnight[7] as Double)
        }
    }

    // Add Z dimension to the road segments
    // ROAD sources: z=0.05m (standard road surface height)
    // BRIDGE sources: z=-0.05m (below deck for structural vibration sources)
    sql.execute("UPDATE LW_ROADS SET THE_GEOM = ST_UPDATEZ(The_geom,0.05) WHERE SOURCE_TYPE = 'ROAD';")
    sql.execute("UPDATE LW_ROADS SET THE_GEOM = ST_UPDATEZ(The_geom,-0.05) WHERE SOURCE_TYPE = 'BRIDGE';")
    
    // Set HEIGHT_TYPE to RELATIVE since we've assigned fixed relative heights
    sql.execute("UPDATE LW_ROADS SET HEIGHT_TYPE = 'RELATIVE';")
    logger.info('Set HEIGHT_TYPE=RELATIVE for all LW_ROADS records')

    // Add primary key to the road table
    sql.execute("ALTER TABLE LW_ROADS ALTER COLUMN PK INT NOT NULL;")
    sql.execute("ALTER TABLE LW_ROADS ADD PRIMARY KEY (PK);  ")

    resultString = "Calculation Done ! The table LW_ROADS has been created."

    // print to command window
    logger.info('\nResult : ' + resultString)
    logger.info('End : LW_ROADS from Emission')

    // print to WPS Builder
    return resultString

}


