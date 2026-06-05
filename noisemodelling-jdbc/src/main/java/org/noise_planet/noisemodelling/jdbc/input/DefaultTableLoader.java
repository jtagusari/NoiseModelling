/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

import org.h2gis.utilities.*;
import org.h2gis.utilities.dbtypes.DBTypes;
import org.h2gis.utilities.dbtypes.DBUtils;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.locationtech.jts.io.WKTWriter;
import org.noise_planet.noisemodelling.emission.LineSource;
import org.noise_planet.noisemodelling.emission.directivity.DirectivityRecord;
import org.noise_planet.noisemodelling.emission.directivity.DirectivitySphere;
import org.noise_planet.noisemodelling.emission.directivity.DiscreteDirectivitySphere;
import org.noise_planet.noisemodelling.emission.directivity.OmnidirectionalDirection;
import org.noise_planet.noisemodelling.emission.directivity.cnossos.RailwayCnossosDirectivitySphere;
import org.noise_planet.noisemodelling.emission.railway.cnossos.RailWayCnossosParameters;
import org.noise_planet.noisemodelling.jdbc.BuildingTableSettings;
import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgePoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Building;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Wall;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig.FrequencyBand;
import org.noise_planet.noisemodelling.propagation.AttenuationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.flatbuffers.Table;

import java.sql.*;
import java.util.*;

import static org.h2gis.utilities.GeometryTableUtilities.getGeometryColumnNames;

/**
 *  Default implementation for initializing input propagation process data for noise map computation.
 */
public class DefaultTableLoader implements TableLoader {
    protected static final Logger LOGGER = LoggerFactory.getLogger(DefaultTableLoader.class);
    // Snapshot of context values captured at initialize time.
    // Keeping plain values here avoids retaining a hard reference to the context object.
    private SceneDatabaseInputSettings sceneInputSettings;
    private String sourcesTableName;
    private String sourcesEmissionTableName;
    private String frequencyFieldPrepend;
    private boolean verbose;
    // Soil areas are split by the provided size in order to reduce the propagation time
    protected double groundSurfaceSplitSideLength = 200;
    private FrequencyConfig frequencyConfig = new FrequencyConfig(FrequencyBand.OCTAVE);
    
    //  Attenuation settings to apply for each period
    public Map<String, AttenuationParameters> cnossosParametersPerPeriod = new HashMap<>();
    public AttenuationParameters defaultParameters = new AttenuationParameters();

    public static final int DEFAULT_FETCH_SIZE = 300;
    protected int fetchSize = DEFAULT_FETCH_SIZE;

    /**
     * Attenuation and other attributes relative to direction on sphere
     */
    public Map<Integer, DirectivitySphere> directionAttributes = new HashMap<>();

    /**
     * Inserts directivity attributes for noise sources for trains into the directionAttributes map.
     */
    public void insertTrainDirectivity() {
        directionAttributes.clear();
        directionAttributes.put(0, new OmnidirectionalDirection());
        int i=1;
        for(String typeSource : RailWayCnossosParameters.emissionType) {
            directionAttributes.put(i, new RailwayCnossosDirectivitySphere(new LineSource(typeSource)));
            i++;
        }
    }

    /**
        * Initializes the loader with a snapshot of context values and preloads acoustic configuration.
        *
     * @param connection   the database connection to be used for initialization.
     * @param context the table loader context associated with the computation process.
     * @throws SQLException
     */
    @Override
    public void initialize(Connection connection, LoaderInitContext context) throws SQLException {
        // Capture everything needed for createScene/fetch* calls.
        // After this point, processing does not depend on a live context reference.
        this.sceneInputSettings = new SceneDatabaseInputSettings(context.getSceneInputSettings());
        this.sourcesTableName = context.getSourcesTableName();
        this.sourcesEmissionTableName = context.getSourcesEmissionTableName();
        this.frequencyFieldPrepend = context.getFrequencyFieldPrepend();
        this.verbose = context.isVerbose();

        if(sceneInputSettings.getInputMode() == SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_GUESS) {
            guessInputMode(connection, sceneInputSettings);
        }

        if(sceneInputSettings.getInputMode() == SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW) {
            // Load expected frequencies used for computation
            // Fetch source fields
            List<String> sourceField = JDBCUtilities.getColumnNames(connection, sourcesEmissionTableName);
            List<Integer> frequencyValues = readFrequenciesFromLwTable(frequencyFieldPrepend, sourceField);
            if(frequencyValues.isEmpty()) {
                throw new SQLException("Source emission table "+ sourcesTableName+" does not contains any frequency bands");
            }
            frequencyConfig.setFrequencyArray(frequencyValues);

        } else if (sceneInputSettings.getInputMode() == SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN) {
            List<String> sourceFields = JDBCUtilities.getColumnNames(connection, sourcesTableName);
            Set<Integer> frequencySet = new HashSet<>();

            for (SourceEmission.StandardPeriod period : SourceEmission.StandardPeriod.values()) {
                String periodFieldName = SourceEmission.STANDARD_PERIOD_VALUE[period.ordinal()];
                frequencySet.addAll(readFrequenciesFromLwTable(frequencyFieldPrepend + periodFieldName, sourceFields));
            }
            frequencyConfig.setFrequencyArray(frequencySet);
        }
        defaultParameters.setFrequencies(frequencyConfig.getFrequencyArray());
        // Load atmospheric data from database
        if(!sceneInputSettings.getPeriodAtmosphericSettingsTableName().isEmpty()) {
            loadAtmosphericTableSettings(connection, sceneInputSettings.getPeriodAtmosphericSettingsTableName());
        }
        // apply expected frequency to each atmospheric data
        for(AttenuationParameters parameters : cnossosParametersPerPeriod.values()) {
            parameters.setFrequencies(frequencyConfig.getFrequencyArray());
        }
        // Load source directivity
        if(sceneInputSettings.isUseTrainDirectivity()) {
            insertTrainDirectivity();
        } else if (!sceneInputSettings.getDirectivityTableName().isEmpty()) {
            directionAttributes = fetchDirectivity(connection, sceneInputSettings.getDirectivityTableName(), 1, frequencyFieldPrepend);
            if(verbose) {
                LOGGER.info("Loaded {} directivities from the database", directionAttributes.size());
            }
        }
    }

    /**
     * Infers the input mode by inspecting available columns in source/emission tables.
     */
    private void guessInputMode(Connection connection, SceneDatabaseInputSettings inputSettings) throws SQLException {
        
        // Check fields to find appropriate expected data
        inputSettings.inputMode = SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_ATTENUATION;
        if(!inputSettings.sourcesEmissionTableName.isEmpty()) {
            List<String> sourceFields = JDBCUtilities.getColumnNames(connection, sourcesEmissionTableName);
            if(sourceFields.contains("LV_SPD")) {
                inputSettings.inputMode = SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW;
            } else {
                inputSettings.inputMode = SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW;
            }
        } else {
            List<String> sourceFields = JDBCUtilities.getColumnNames(connection, sourcesTableName);

            for (SourceEmission.StandardPeriod period : SourceEmission.StandardPeriod.values()) {
                String periodFieldName = SourceEmission.STANDARD_PERIOD_VALUE[period.ordinal()];
                List<Integer> frequencyValues = readFrequenciesFromLwTable(
                        frequencyFieldPrepend +
                                periodFieldName, sourceFields);
                if(!frequencyValues.isEmpty()) {
                    inputSettings.inputMode = SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN;
                    break;
                } else {
                    if(sourceFields.contains("LV_SPD_" + periodFieldName)) {
                        inputSettings.inputMode = SceneDatabaseInputSettings.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW_DEN;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Loads period-specific atmospheric attenuation settings from a database table.
     */
    private void loadAtmosphericTableSettings(Connection connection, String atmosphericSettingsTableName) throws SQLException {
        String query = "SELECT * FROM " + atmosphericSettingsTableName;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                // Placeholder for processing the results
                AttenuationParameters.readFromDatabase(resultSet, cnossosParametersPerPeriod);
            }
        }
    }

    /**
     * Retrieves the frequency array used within the class.
     *
     * @return a list of integers representing the frequency values in the array.
     */
    public List<Integer> getFrequencyArray() {
        return frequencyConfig.getFrequencyArray();
    }

    /**
     * Retrieves the exact frequency array used within the class.
     *
     * @return a list of doubles representing the exact frequency values in the array.
     */
    public List<Double> getExactFrequencyArray() {
        return frequencyConfig.getExactFrequencyArray();
    }

    /**
     * Retrieves the A-weighting correction array used within the class.
     * A-weighting is applied to account for the varying sensitivity of
     * human hearing to different frequencies, commonly used in acoustic measurements.
     *
     * @return a list of doubles representing the A-weighting correction values.
     */
    public List<Double> getaWeightingArray() {
        return frequencyConfig.getAWeightingArray();
    }

    /**
     * Retrieves the parameters defined for different time periods.
     *
     * @return a map where the keys represent the time periods (e.g., "D", "E", "N") as strings,
     *         and the values are instances of {@link AttenuationParameters} representing the corresponding parameters.
     */
    public Map<String, AttenuationParameters> getCnossosParametersPerPeriod() {
        return cnossosParametersPerPeriod;
    }

    public int getFetchSize() {
        return fetchSize;
    }

    public Map<Integer, DirectivitySphere> getDirectionAttributes() {
        return directionAttributes;
    }

    public void setAttenuationParameters(AttenuationParameters defaultParameters) {
        this.defaultParameters = defaultParameters;
    }

    /**
     * Extracts valid frequency bands from column names matching a prefix (e.g. HZ1000).
     */
    private static List<Integer> readFrequenciesFromLwTable(String frequencyPrepend, List<String> sourceField) throws SQLException {
        List<Integer> frequencyValues = new ArrayList<>();
        for (String fieldName : sourceField) {
            if (fieldName.toUpperCase(Locale.ROOT).startsWith(frequencyPrepend)) {
                try {
                    int freq = Integer.parseInt(fieldName.substring(frequencyPrepend.length()));
                    int index = Arrays.binarySearch(FrequencyConfig.DEFAULT_FREQUENCIES_THIRD_OCTAVE, freq);
                    if (index >= 0) {
                        frequencyValues.add(freq);
                    }
                } catch (NumberFormatException ex) {
                    // ignore
                }
            }
        }
        return frequencyValues;
    }

    /**
     * Builds a fully initialized scene for one computation cell.
     */
    @Override
    public SceneWithEmission createScene(Connection connection, CellSceneContext cellContext, CellIndex cellIndex,
                                    Set<Long> skipReceivers) throws SQLException {
        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        GeometryFactory geometryFactory = cellContext.getGeometryFactory();

        Envelope cellEnvelope = cellContext.getCellEnv(cellIndex);
        Envelope expandedCellEnvelop = new Envelope(cellEnvelope);
        double maximumPropagationDistance = cellContext.getMaximumPropagationDistance();
        double maximumReflectionDistance = cellContext.getMaximumReflectionDistance();

        // We have to fetch input data at least at this distance from the receivers in order to have continuity
        // between subdomains
        expandedCellEnvelop.expandBy(maximumPropagationDistance + 2 * maximumReflectionDistance);

        ProfileBuilder profileBuilder = new ProfileBuilder(frequencyConfig);
        // profileBuilder.setFrequencyArray(frequencyArray);
        SceneWithEmission scene = new SceneWithEmission(profileBuilder, sceneInputSettings);
        scene.setDirectionAttributes(directionAttributes);
        scene.cnossosParametersPerPeriod = cnossosParametersPerPeriod;
        scene.setAttenuationParameters(defaultParameters);
        scene.periodSet.addAll(cnossosParametersPerPeriod.keySet());


        // //////////////////////////////////////////////////////
        // feed freeFieldFinder for fast intersection query
        // optimization
        // Fetch buildings in extendedEnvelope
        fetchCellBuildings(connection, cellContext.getBuildingTableSettings(), expandedCellEnvelop,
                scene.profileBuilder, geometryFactory);

        //if we have topographic points data
        fetchCellDem(connection, cellContext, expandedCellEnvelop, scene.profileBuilder);

        // Fetch soil areas
        fetchCellSoilAreas(connection, cellContext, expandedCellEnvelop, scene.profileBuilder);

        // Fetch bridges
        fetchCellBridge(connection, cellContext, expandedCellEnvelop, scene.profileBuilder, geometryFactory);

        scene.profileBuilder.finishFeeding();

        scene.setReflexionOrder(cellContext.getSoundReflectionOrder());
        scene.setBodyBarrier(cellContext.isBodyBarrier());
        scene.maxRefDist = maximumReflectionDistance;
        scene.setMaxSrcDist(maximumPropagationDistance);
        scene.setComputeVerticalDiffraction(cellContext.isComputeVerticalDiffraction());
        scene.setComputeHorizontalDiffraction(cellContext.isComputeHorizontalDiffraction());

        // Fetch all source located in expandedCellEnvelop
        fetchCellSource(connection, cellContext, expandedCellEnvelop, scene, true);

        // Fetch receivers
        fetchCellReceiver(connection, cellContext, cellEnvelope, scene, skipReceivers);

        return scene;
    }

    /**
     * The table shall contain the following fields :
     * DIR_ID : identifier of the directivity sphere (INTEGER)
     * THETA : Horizontal angle in degree. 0° front and 90° right (0-360) (FLOAT)
     * PHI : Vertical angle in degree. 0° front and 90° top -90° bottom (-90 - 90) (FLOAT)
     * HZ63, HZ125, HZ250, HZ500, HZ1000, HZ2000, HZ4000, HZ8000 : attenuation levels in dB for each octave or third octave (FLOAT)
     * @param connection Connection
     * @param tableName Table name
     * @param defaultInterpolation Interpolation if applicable
     * @param frequencyFieldPrepend Frequency field name ex. HZ for HZ1000
     * @return
     */
    public static Map<Integer, DirectivitySphere> fetchDirectivity(Connection connection, String tableName, int defaultInterpolation, String frequencyFieldPrepend) throws SQLException {
        Map<Integer, DirectivitySphere> directionAttributes = new HashMap<>();
        List<String> fields = JDBCUtilities.getColumnNames(connection, tableName);
        // fetch provided frequencies
        List<String> frequenciesFields = new ArrayList<>();
        for(String field : fields) {
            if(field.toUpperCase(Locale.ROOT).startsWith(frequencyFieldPrepend)) {
                try {
                    double frequency = Double.parseDouble(field.substring(frequencyFieldPrepend.length()));
                    if (frequency > 0) {
                        frequenciesFields.add(field);
                    }
                } catch (NumberFormatException ex) {
                    //ignore column
                }
            }
        }
        if(frequenciesFields.isEmpty()) {
            return directionAttributes;
        }
        double[] frequencies = new double[frequenciesFields.size()];
        for(int idFrequency = 0; idFrequency < frequencies.length; idFrequency++) {
            frequencies[idFrequency] = Double.parseDouble(frequenciesFields.get(idFrequency).substring(2));
        }
        StringBuilder sb = new StringBuilder("SELECT DIR_ID, THETA, PHI");
        for(String frequency : frequenciesFields) {
            sb.append(", ");
            sb.append(frequency);
        }
        sb.append(" FROM ");
        sb.append(tableName);
        sb.append(" ORDER BY DIR_ID");
        try(Statement st = connection.createStatement()) {
            try(ResultSet rs = st.executeQuery(sb.toString())) {
                List<DirectivityRecord> rows = new ArrayList<>();
                int lastDirId = Integer.MIN_VALUE;
                while (rs.next()) {
                    int dirId = rs.getInt(1);
                    // Flush previous sphere when DIR_ID changes.
                    if(lastDirId != dirId && !rows.isEmpty()) {
                        DiscreteDirectivitySphere attributes = new DiscreteDirectivitySphere(lastDirId, frequencies);
                        attributes.setInterpolationMethod(defaultInterpolation);
                        attributes.addDirectivityRecords(rows);
                        directionAttributes.put(lastDirId, attributes);
                        rows.clear();
                    }
                    lastDirId = dirId;
                    double theta = Math.toRadians(rs.getDouble(2));
                    double phi = Math.toRadians(rs.getDouble(3));
                    double[] att = new double[frequencies.length];
                    for(int freqColumn = 0; freqColumn < frequencies.length; freqColumn++) {
                        att[freqColumn] = rs.getDouble(freqColumn + 4);
                    }
                    DirectivityRecord r = new DirectivityRecord(theta, phi, att);
                    rows.add(r);
                }
                if(!rows.isEmpty()) {
                    // Flush last pending sphere.
                    DiscreteDirectivitySphere attributes = new DiscreteDirectivitySphere(lastDirId, frequencies);
                    attributes.setInterpolationMethod(defaultInterpolation);
                    attributes.addDirectivityRecords(rows);
                    directionAttributes.put(lastDirId, attributes);
                }
            }
        }
        return directionAttributes;
    }


    /**
     * Fetches buildings data for the specified cell envelope and adds them to the profile builder.
     * @param connection     the database connection to use for querying the buildings data.
     * @param buildingTableSettings Database settings for the building table
     * @param fetchEnvelope  the envelope representing the cell to fetch buildings data for.
     * @param builder        the profile builder to which the buildings data will be added.
     * @param geometryFactory geometry factory instance with SRID set.
     * @throws SQLException  if an SQL exception occurs while fetching the buildings data.
     */
    public static void fetchCellBuildings(Connection connection, BuildingTableSettings buildingTableSettings,
                                          Envelope fetchEnvelope, ProfileBuilder builder,
                                          GeometryFactory geometryFactory) throws SQLException {
        List<Building> buildings = new LinkedList<>();
        List<Wall> walls = new LinkedList<>();
        fetchCellBuildings(connection,buildingTableSettings, fetchEnvelope, buildings, walls, geometryFactory);
        for(Building building : buildings) {
            builder.addBuilding(building);
        }
        for (Wall wall : walls) {
            builder.addWall(wall);
        }
    }

    /**
     * Fetches building data for the specified cell envelope and adds them to the provided list of buildings.
     * @param connection      the database connection to use for querying the building data.
     * @param buildingTableSettings Database settings for the building table
     * @param fetchEnvelope   the envelope representing the cell to fetch building data for.
     * @param buildings       the list to which the fetched buildings will be added.
     * @param walls Wall list to feed
     * @param geometryFactory geometry factory instance with SRID set.
     * @throws SQLException   if an SQL exception occurs while fetching the building data.
     */
    public static void fetchCellBuildings(Connection connection,
                                          BuildingTableSettings buildingTableSettings,
                                          Envelope fetchEnvelope,
                                          List<Building> buildings,
                                          List<Wall> walls,
                                          GeometryFactory geometryFactory) throws SQLException {
        Geometry envGeo = geometryFactory.toGeometry(fetchEnvelope);
        boolean fetchAlpha = JDBCUtilities.hasField(connection, buildingTableSettings.getBuildingsTableName(),
                buildingTableSettings.getAlphaFieldName());
        String additionalQuery = "";
        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        if(!buildingTableSettings.getHeightField().isEmpty()) {
            additionalQuery += ", " + TableLocation.quoteIdentifier(buildingTableSettings.getHeightField(), dbType);
        }
        if(fetchAlpha) {
            additionalQuery += ", " + buildingTableSettings.getAlphaFieldName();
        }
        String pkBuilding = "";
        final int indexPk = JDBCUtilities.getIntegerPrimaryKey(connection.unwrap(Connection.class),
                new TableLocation(buildingTableSettings.getBuildingsTableName(), dbType));
        if(indexPk > 0) {
            pkBuilding = JDBCUtilities.getColumnName(connection, buildingTableSettings.getBuildingsTableName(), indexPk);
            additionalQuery += ", " + pkBuilding;
        }
        String buildingGeomName = getGeometryColumnNames(connection,
                TableLocation.parse(buildingTableSettings.getBuildingsTableName(), dbType)).get(0);
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT " + TableLocation.quoteIdentifier(buildingGeomName) + additionalQuery + " FROM " +
                        buildingTableSettings.getBuildingsTableName() + " WHERE " +
                        TableLocation.quoteIdentifier(buildingGeomName, dbType) + " && ?::geometry")) {
            st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
            try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                int columnIndex = 0;
                if(!pkBuilding.isEmpty()) {
                    columnIndex = JDBCUtilities.getFieldIndex(rs.getMetaData(), pkBuilding);
                }
                double oldAlpha = buildingTableSettings.getDefaultWallAbsorption();
                while (rs.next()) {
                    // Clip each building geometry to the fetched envelope to keep per-cell consistency.
                    Geometry building = rs.getGeometry();
                    if(building != null) {
                        Geometry intersectedGeometry = null;
                        try {
                            intersectedGeometry = building.intersection(envGeo);
                        } catch (TopologyException ex) {
                            WKTWriter wktWriter = new WKTWriter(3);
                            LOGGER.error(String.format("Error with input buildings geometry\n%s\n%s",wktWriter.write(building),wktWriter.write(envGeo)), ex);
                        }
                        if(intersectedGeometry instanceof Polygon || intersectedGeometry instanceof MultiPolygon || intersectedGeometry instanceof LineString) {
                            if(fetchAlpha) {
                                oldAlpha = rs.getDouble(buildingTableSettings.getAlphaFieldName());
                            }

                            long pk = -1;
                            if(columnIndex != 0) {
                                pk = rs.getLong(columnIndex);
                            }
                            for(int i=0; i<intersectedGeometry.getNumGeometries(); i++) {
                                Geometry geometry = intersectedGeometry.getGeometryN(i);
                                if(geometry instanceof Polygon && !geometry.isEmpty()) {
                                    Building poly = new Building((Polygon) geometry,
                                            buildingTableSettings.getHeightField().isEmpty() ?
                                                    Double.MAX_VALUE :
                                                    rs.getDouble(buildingTableSettings.getHeightField()),
                                            oldAlpha, pk, buildingTableSettings.isZBuildings());
                                    buildings.add(poly);
                                } else if (geometry instanceof LineString) {
                                    // Convert border lines into individual wall segments.
                                    LineString lineString = (LineString) geometry;
                                    Coordinate[] coordinates = lineString.getCoordinates();
                                    for(int vertex=0; vertex < coordinates.length - 1; vertex++) {
                                        Wall wall = new Wall(new LineSegment(coordinates[vertex], coordinates[vertex+1]),
                                                -1, ProfileBuilder.IntersectionType.WALL);
                                        wall.setG(oldAlpha);
                                        wall.setPrimaryKey(pk);
                                        wall.setHeight(buildingTableSettings.getHeightField().isEmpty() ?
                                                Double.MAX_VALUE : rs.getDouble(buildingTableSettings.getHeightField()));
                                        walls.add(wall);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Fetches digital elevation model (DEM) data for the specified cell envelope and adds it to the mesh.
     * @param connection the database connection to use for querying the DEM data.
     * @param fetchEnvelope  the envelope representing the cell to fetch DEM data for.
     * @param profileBuilder the profile builder mesh to which the DEM data will be added.
     * @throws SQLException if an SQL exception occurs while fetching the DEM data.
     */
    public void fetchCellDem(Connection connection, CellSceneContext cellContext, Envelope fetchEnvelope, ProfileBuilder profileBuilder) throws SQLException {
        String demTable = cellContext.getDemTable();
        if(!demTable.isEmpty()) {
            GeometryFactory geometryFactory = cellContext.getGeometryFactory();
            DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
            List<String> geomFields = getGeometryColumnNames(connection,
                    TableLocation.parse(demTable, dbType));
            if(geomFields.isEmpty()) {
                throw new SQLException("Digital elevation model table \""+ demTable +"\" must exist and contain a POINT field");
            }
            String topoGeomName = geomFields.get(0);
            double sumZ = 0;
            int topoCount = 0;
            try (PreparedStatement st = connection.prepareStatement(
                    "SELECT " + TableLocation.quoteIdentifier(topoGeomName, dbType) + " FROM " +
                            demTable + " WHERE " +
                            TableLocation.quoteIdentifier(topoGeomName, dbType) + " && ?::geometry")) {
                st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
                try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                    while (rs.next()) {
                        Geometry pt = rs.getGeometry();
                        if(pt != null) {
                            Coordinate ptCoordinate = pt.getCoordinate();
                            profileBuilder.addTopographicPoint(ptCoordinate);
                            if(!Double.isNaN(ptCoordinate.z)) {
                                sumZ+=ptCoordinate.z;
                                topoCount+=1;
                            }
                        }
                    }
                }
                double averageZ = 0;
                if(topoCount > 0) {
                    averageZ = sumZ / topoCount;
                }
                // Add envelope corners to ensure topography continuity between neighboring cells.
                Envelope extentedEnvelope = new Envelope(fetchEnvelope);
                extentedEnvelope.expandBy(fetchEnvelope.getDiameter());
                Coordinate[] coordinates = geometryFactory.toGeometry(extentedEnvelope).getCoordinates();
                for (int i = 0; i < coordinates.length - 1; i++) {
                    Coordinate coordinate = coordinates[i];
                    profileBuilder.addTopographicPoint(new Coordinate(coordinate.x, coordinate.y, averageZ));
                }
            }
        }
    }


    /**
     * Fetches soil areas data for the specified cell envelope and adds them to the profile builder.
     * @param connection         the database connection to use for querying the soil areas data.
     * @param fetchEnvelope      the envelope representing the cell to fetch soil areas data for.
     * @param builder            the profile builder to which the soil areas data will be added.
     * @throws SQLException      if an SQL exception occurs while fetching the soil areas data.
     */
    protected void fetchCellSoilAreas(Connection connection, CellSceneContext cellContext, Envelope fetchEnvelope, ProfileBuilder builder)
            throws SQLException {
        String soilTableName = cellContext.getSoilTableName();
        if(!soilTableName.isEmpty()){
            GeometryFactory geometryFactory = cellContext.getGeometryFactory();
            DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
            double startX = Math.floor(fetchEnvelope.getMinX() / groundSurfaceSplitSideLength) * groundSurfaceSplitSideLength;
            double startY = Math.floor(fetchEnvelope.getMinY() / groundSurfaceSplitSideLength) * groundSurfaceSplitSideLength;
            String soilGeomName = getGeometryColumnNames(connection,
                    TableLocation.parse(soilTableName, dbType)).get(0);
            try (PreparedStatement st = connection.prepareStatement(
                    "SELECT " + TableLocation.quoteIdentifier(soilGeomName, dbType) + ", G FROM " +
                            soilTableName + " WHERE " +
                            TableLocation.quoteIdentifier(soilGeomName, dbType) + " && ?::geometry")) {
                st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
                try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                    while (rs.next()) {
                        Geometry mainPolygon = rs.getGeometry();
                        if(mainPolygon != null) {
                            for (int idPoly = 0; idPoly < mainPolygon.getNumGeometries(); idPoly++) {
                                Geometry poly = mainPolygon.getGeometryN(idPoly);
                                if (poly instanceof Polygon) {
                                    PreparedPolygon preparedPolygon = new PreparedPolygon((Polygon) poly);
                                    // Split large soil polygons into regular tiles to reduce intersection cost.
                                    Envelope geoEnv = poly.getEnvelopeInternal();
                                    double startXGeo = Math.max(startX, Math.floor(geoEnv.getMinX() / groundSurfaceSplitSideLength) * groundSurfaceSplitSideLength);
                                    double startYGeo = Math.max(startY, Math.floor(geoEnv.getMinY() / groundSurfaceSplitSideLength) * groundSurfaceSplitSideLength);
                                    double xCursor = startXGeo;
                                    double g = rs.getDouble("G");
                                    double maxX = Math.min(fetchEnvelope.getMaxX(), geoEnv.getMaxX());
                                    double maxY = Math.min(fetchEnvelope.getMaxY(), geoEnv.getMaxY());
                                    while (xCursor < maxX) {
                                        double yCursor = startYGeo;
                                        while (yCursor < maxY) {
                                            Envelope cellEnv = new Envelope(xCursor, xCursor + groundSurfaceSplitSideLength, yCursor, yCursor + groundSurfaceSplitSideLength);
                                            Geometry envGeom = geometryFactory.toGeometry(cellEnv);
                                            if(preparedPolygon.intersects(envGeom)) {
                                                try {
                                                    Geometry inters = poly.intersection(envGeom);
                                                    if (!inters.isEmpty() && (inters instanceof Polygon || inters instanceof MultiPolygon)) {
                                                        builder.addGroundEffect(inters, g);
                                                    }
                                                } catch (TopologyException | IllegalArgumentException ex) {
                                                    // Ignore
                                                }
                                            }
                                            yCursor += groundSurfaceSplitSideLength;
                                        }
                                        xCursor += groundSurfaceSplitSideLength;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Fetches bridge data for the specified cell envelope and adds them to the profile builder.
     * Reads BRIDGE_POINTS table, groups points by BRIDGE_PK, and creates Bridge objects
     * using Bridge.Builder pattern.
     * @param connection      the database connection to use for querying the bridge points data.
     * @param fetchEnvelope   the envelope representing the cell to fetch bridge data for.
     * @param builder         the profile builder to which the bridges will be added.
     * @param geometryFactory geometry factory instance with SRID set.
     * @throws SQLException   if an SQL exception occurs while fetching the bridge data.
     */
    public void fetchCellBridge(Connection connection, CellSceneContext cellContext, Envelope fetchEnvelope, ProfileBuilder builder,
                                   GeometryFactory geometryFactory) throws SQLException {
        
        
        String bridgePointsTableName = cellContext.getBridgePointsTableName();
        if(!bridgePointsTableName.isEmpty()) {
            DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
            List<String> geomFields = getGeometryColumnNames(connection,
                    TableLocation.parse(bridgePointsTableName, dbType));
            if(geomFields.isEmpty()) {
                throw new SQLException("Bridge points table \"" + bridgePointsTableName + "\" must exist and contain a POINT field");
            }
            String bridgeGeomName = geomFields.get(0);
            
            // Load all bridge points within the envelope
            List<BridgePoint> bridgePointsList = new ArrayList<>();
            try (PreparedStatement st = connection.prepareStatement(
                    "SELECT * FROM " + bridgePointsTableName + " WHERE " +
                            TableLocation.quoteIdentifier(bridgeGeomName, dbType) + " && ?::geometry ORDER BY PK")) {
                st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        bridgePointsList.add(new BridgePoint(rs));
                    }
                }
            }

            List<Bridge> bridges = Bridge.createBridgesFromPoints(bridgePointsList);

            for (Bridge bridge : bridges) {
                builder.addBridge(bridge);
            }
            
        }
    }

    /**
     * Fetches receivers data for the specified cell envelope and adds them to the profile builder.
     * @param connection         the database connection to use for querying the receivers data.
     * @param cellEnvelope       the envelope representing the cell to fetch receivers data for.
        * @param scene              the scene to which the receivers data will be added.
     * @param skipReceivers      set of receiver primary keys to skip (already processed in other cells).
     * @throws SQLException      if an SQL exception occurs while fetching the receivers data.
     */
    public void fetchCellReceiver(Connection connection, CellSceneContext cellContext, Envelope cellEnvelope, SceneWithEmission scene,
                                     Set<Long> skipReceivers) throws SQLException {
        String receiverTableName = cellContext.getReceiverTableName();
        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        String receiverGeomName = GeometryTableUtilities.getGeometryColumnNames(connection,
                TableLocation.parse(receiverTableName)).get(0);
        String receiverPkName = "PK";
        String receiverHeightTypeName = "HEIGHT_TYPE";

        GeometryFactory geometryFactory = cellContext.getGeometryFactory();

        // check if primary key exists
        int intPk = JDBCUtilities.getIntegerPrimaryKey(connection.unwrap(Connection.class), TableLocation.parse(receiverTableName, dbType));
        
        if(intPk < 1) {
            throw new SQLException(String.format("Table %s missing primary key for receiver identification", receiverTableName));
        }

        // get dbType specific name
        receiverGeomName = TableLocation.quoteIdentifier(receiverGeomName, dbType);
        receiverPkName = TableLocation.quoteIdentifier(JDBCUtilities.getColumnName(connection, receiverTableName, intPk), dbType);

        String pkSelectPart = "SELECT " + receiverGeomName + ", " + receiverPkName;

        // check if height type field (optional) exists
        String actualHeightTypeName = null;
        for (String col : JDBCUtilities.getColumnNames(connection, receiverTableName)) {
            if (col.equalsIgnoreCase(receiverHeightTypeName)) {
                actualHeightTypeName = col;
                break;
            }
        }
        if(actualHeightTypeName != null) {
            receiverHeightTypeName = TableLocation.quoteIdentifier(actualHeightTypeName, dbType);
            pkSelectPart += ", " + receiverHeightTypeName;
        }

        try (PreparedStatement st = connection.prepareStatement(
                pkSelectPart + " FROM " + receiverTableName + " WHERE " +
                        TableLocation.quoteIdentifier(receiverGeomName, dbType) + " && ?::geometry")) {
            st.setObject(1, geometryFactory.toGeometry(cellEnvelope));
            try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                while (rs.next()) {
                    long receiverPk = rs.getLong(receiverPkName);
                    if(receiverPk < 0) {
                        throw new IllegalArgumentException("The table " + receiverTableName +
                                " must have a primary key field named PK of type INTEGER");
                    }
                    if(skipReceivers.contains(receiverPk)) {
                        continue;
                    } else {
                        skipReceivers.add(receiverPk);
                    }
                    Geometry pt = rs.getGeometry();

                    if(pt == null || pt.isEmpty()) {
                        throw new IllegalArgumentException("The table " + receiverTableName +
                                " contain at least one receiver without geometry.");
                    }
                    // check z value
                    if(pt.getCoordinate().getZ() == Coordinate.NULL_ORDINATE) {
                        throw new IllegalArgumentException("The table " + receiverTableName +
                                " contain at least one receiver without Z ordinate." +
                                " You must specify X,Y,Z for each receiver");
                    }

                    Scene.HeightType heightType = Scene.HeightType.RELATIVE;
                    try {
                        heightType = Scene.HeightType.fromString(rs.getString(receiverHeightTypeName));
                    } catch (SQLException ex) {
                        // ignore, use default
                    }
                    
                    scene.addReceiver(receiverPk, pt.getCoordinate(), heightType);
                }
            }
        }
    }

    /**
     * Fetch source geometries and power
     * @param connection Active connection
     * @param fetchEnvelope Fetch envelope
     * @param scene (Out) Propagation process input data
     * @throws SQLException
     */
    public void fetchCellSource(Connection connection, CellSceneContext cellContext, Envelope fetchEnvelope, SceneWithEmission scene, boolean doIntersection)
            throws SQLException {
        String sourcesTableName = cellContext.getSourcesTableName();
        GeometryFactory geometryFactory = cellContext.getGeometryFactory();
        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        TableLocation sourceTableIdentifier = TableLocation.parse(sourcesTableName, dbType);
        List<String> geomFields = getGeometryColumnNames(connection, sourceTableIdentifier);
        if (geomFields.isEmpty()) {
            throw new SQLException(String.format("The table %s does not exists or does not contain a geometry field", sourceTableIdentifier));
        }
        String sourceGeomName = geomFields.get(0);
        Geometry domainConstraint = geometryFactory.toGeometry(fetchEnvelope);
        Tuple<String, Integer> primaryKey = JDBCUtilities.getIntegerPrimaryKeyNameAndIndex(
                connection.unwrap(Connection.class), new TableLocation(sourcesTableName, dbType));
        if (primaryKey == null) {
            throw new IllegalArgumentException(String.format("Source table %s does not contain a primary key", sourceTableIdentifier));
        }
        int pkIndex = primaryKey.second();
        try (PreparedStatement st = connection.prepareStatement("SELECT * FROM " + sourcesTableName + " WHERE "
                + TableLocation.quoteIdentifier(sourceGeomName) + " && ?::geometry")) {
            st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
            st.setFetchSize(fetchSize);
            boolean autoCommit = connection.getAutoCommit();
            if (autoCommit) {
                connection.setAutoCommit(false);
            }
            st.setFetchDirection(ResultSet.FETCH_FORWARD);
            try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                while (rs.next()) {
                    Geometry geo = rs.getGeometry();
                    if (geo != null) {
                        if (doIntersection) {
                            geo = domainConstraint.intersection(geo);
                        }
                        if (!geo.isEmpty()) {
                            Coordinate[] coordinates = geo.getCoordinates();
                            for (Coordinate coordinate : coordinates) {
                                // check z value
                                if (coordinate.getZ() == Coordinate.NULL_ORDINATE) {
                                    throw new IllegalArgumentException("The table " + sourcesTableName +
                                            " contain at least one source without Z ordinate." +
                                            " You must specify X,Y,Z for each source");
                                }
                            }
                            scene.addSourceDb(rs.getLong(pkIndex), geo, rs);
                        }
                    }
                }
            } finally {
                if (autoCommit) {
                    connection.setAutoCommit(true);
                }
            }
        }
        // Fetch emission records for sources falling in the same envelope.
        String emissionTableName = scene.getSceneDatabaseInputSettings().getSourcesEmissionTableName();
        if (!emissionTableName.isEmpty()) {
            try (PreparedStatement st = connection.prepareStatement("SELECT E.* FROM " + sourcesTableName +
                    " S INNER JOIN "+emissionTableName+" E ON S."+primaryKey.first()+" = E." +
                scene.getSceneDatabaseInputSettings().getSourceEmissionPrimaryKeyField()+" WHERE S."
                    + TableLocation.quoteIdentifier(sourceGeomName) + " && ?::geometry")) {
                st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
                st.setFetchSize(fetchSize);
                boolean autoCommit = connection.getAutoCommit();
                if (autoCommit) {
                    connection.setAutoCommit(false);
                }
                st.setFetchDirection(ResultSet.FETCH_FORWARD);
                try (ResultSet rs = st.executeQuery()) {
                    while (rs.next()) {
                        scene.registerSourceEmissionFromDb(rs.getLong(scene.getSceneDatabaseInputSettings().getSourceEmissionPrimaryKeyField()), rs);
                    }
                } finally {
                    if (autoCommit) {
                        connection.setAutoCommit(true);
                    }
                }
            }

        }
    }
}
