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
import org.noise_planet.noisemodelling.emission.LineSource;
import org.noise_planet.noisemodelling.emission.directivity.DirectivityRecord;
import org.noise_planet.noisemodelling.emission.directivity.DirectivitySphere;
import org.noise_planet.noisemodelling.emission.directivity.DiscreteDirectivitySphere;
import org.noise_planet.noisemodelling.emission.directivity.OmnidirectionalDirection;
import org.noise_planet.noisemodelling.emission.directivity.cnossos.RailwayCnossosDirectivitySphere;
import org.noise_planet.noisemodelling.emission.railway.cnossos.RailWayCnossosParameters;
import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig.FrequencyBand;
import org.noise_planet.noisemodelling.propagation.AttenuationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
    private EmissionInputSettings emissionInputSettings;
    private String sourceTableName;
    private String sourcesEmissionTableName;
    private String frequencyFieldPrepend;
    private String periodAtmosphericSettingsTableName = "";
    private boolean verbose;
    /** Side length used to subdivide large soil polygons; passed to {@link CellProfileLoader}. */
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
        this.emissionInputSettings = new EmissionInputSettings(context.getEmissionInputSettings());
        this.sourceTableName = context.getSourceTableName();
        this.sourcesEmissionTableName = context.getSourcesEmissionTableName();
        this.frequencyFieldPrepend = context.getFrequencyFieldPrepend();
        this.periodAtmosphericSettingsTableName = context.getTableInputSettings().getPeriodAtmosphericSettingsTableName();
        this.verbose = context.isVerbose();

        if(emissionInputSettings.getInputMode() == EmissionInputSettings.INPUT_MODE.INPUT_MODE_GUESS) {
            guessInputMode(connection, emissionInputSettings);
        }

        if(emissionInputSettings.getInputMode() == EmissionInputSettings.INPUT_MODE.INPUT_MODE_LW) {
            // Load expected frequencies used for computation
            // Fetch source fields
            List<String> sourceField = JDBCUtilities.getColumnNames(connection, sourcesEmissionTableName);
            List<Integer> frequencyValues = readFrequenciesFromLwTable(frequencyFieldPrepend, sourceField);
            if(frequencyValues.isEmpty()) {
                throw new SQLException("Source emission table "+ sourceTableName+" does not contains any frequency bands");
            }
            frequencyConfig.setFrequencyArray(frequencyValues);

        } else if (emissionInputSettings.getInputMode() == EmissionInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN) {
            List<String> sourceFields = JDBCUtilities.getColumnNames(connection, sourceTableName);
            Set<Integer> frequencySet = new HashSet<>();

            for (SourceEmission.StandardPeriod period : SourceEmission.StandardPeriod.values()) {
                String periodFieldName = SourceEmission.STANDARD_PERIOD_VALUE[period.ordinal()];
                frequencySet.addAll(readFrequenciesFromLwTable(frequencyFieldPrepend + periodFieldName, sourceFields));
            }
            frequencyConfig.setFrequencyArray(frequencySet);
        }
        defaultParameters.setFrequencies(frequencyConfig.getFrequencyArray());
        // Load atmospheric data from database
        if(!periodAtmosphericSettingsTableName.isEmpty()) {
            loadPeriodAtmosphericSettings(connection, periodAtmosphericSettingsTableName);
        }
        // apply expected frequency to each atmospheric data
        for(AttenuationParameters parameters : cnossosParametersPerPeriod.values()) {
            parameters.setFrequencies(frequencyConfig.getFrequencyArray());
        }
        // Load source directivity
        if(emissionInputSettings.isUseTrainDirectivity()) {
            insertTrainDirectivity();
        } else if (!emissionInputSettings.getDirectivityTableName().isEmpty()) {
            directionAttributes = fetchDirectivity(connection, emissionInputSettings.getDirectivityTableName(), 1, frequencyFieldPrepend);
            if(verbose) {
                LOGGER.info("Loaded {} directivities from the database", directionAttributes.size());
            }
        }
    }

    /**
     * Infers the input mode by inspecting available columns in source/emission tables.
     */
    private void guessInputMode(Connection connection, EmissionInputSettings inputSettings) throws SQLException {

        // Check fields to find appropriate expected data
        inputSettings.inputMode = EmissionInputSettings.INPUT_MODE.INPUT_MODE_ATTENUATION;
        if(!inputSettings.sourcesEmissionTableName.isEmpty()) {
            List<String> sourceFields = JDBCUtilities.getColumnNames(connection, sourcesEmissionTableName);
            if(sourceFields.contains("LV_SPD")) {
                inputSettings.inputMode = EmissionInputSettings.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW;
            } else {
                inputSettings.inputMode = EmissionInputSettings.INPUT_MODE.INPUT_MODE_LW;
            }
        } else {
            List<String> sourceFields = JDBCUtilities.getColumnNames(connection, sourceTableName);

            for (SourceEmission.StandardPeriod period : SourceEmission.StandardPeriod.values()) {
                String periodFieldName = SourceEmission.STANDARD_PERIOD_VALUE[period.ordinal()];
                List<Integer> frequencyValues = readFrequenciesFromLwTable(
                        frequencyFieldPrepend +
                                periodFieldName, sourceFields);
                if(!frequencyValues.isEmpty()) {
                    inputSettings.inputMode = EmissionInputSettings.INPUT_MODE.INPUT_MODE_LW_DEN;
                    break;
                } else {
                    if(sourceFields.contains("LV_SPD_" + periodFieldName)) {
                        inputSettings.inputMode = EmissionInputSettings.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW_DEN;
                        break;
                    }
                }
            }
        }
    }

    /**
     * Loads period-specific atmospheric attenuation settings from a database table.
     */
    private void loadPeriodAtmosphericSettings(Connection connection, String periodAtmosphericSettingsTableName) throws SQLException {
        String query = "SELECT * FROM " + periodAtmosphericSettingsTableName;
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

        Envelope cellEnvelope = cellContext.getCellEnv(cellIndex);

        Envelope expandedCellEnvelop = new Envelope(cellEnvelope);
        double maximumPropagationDistance = cellContext.getMaximumPropagationDistance();
        double maximumReflectionDistance = cellContext.getMaximumReflectionDistance();
        expandedCellEnvelop.expandBy(maximumPropagationDistance + 2 * maximumReflectionDistance);

        ProfileBuilder profileBuilder = new ProfileBuilder(frequencyConfig);

        CellProfileLoader cellProfileLoader = new CellProfileLoader.Builder()
                .setConnection(connection)
                .setCellSceneContext(cellContext)
                .build();
        
        cellProfileLoader.fetchCellBuilding(expandedCellEnvelop, profileBuilder);
        cellProfileLoader.fetchCellTerrain(expandedCellEnvelop, profileBuilder);
        cellProfileLoader.fetchCellGround(expandedCellEnvelop, profileBuilder);
        cellProfileLoader.fetchCellBridge(expandedCellEnvelop, profileBuilder);
        
        profileBuilder.finishFeeding();
        
        SceneWithEmission scene = new SceneWithEmission(profileBuilder, emissionInputSettings);
        scene.setDirectionAttributes(directionAttributes);
        scene.setAttenuationParameters(defaultParameters);
        scene.setCnossosParametersPerPeriod(cnossosParametersPerPeriod);
        scene.addPeriods(cnossosParametersPerPeriod.keySet());
        scene.setReflexionOrder(cellContext.getSoundReflectionOrder());
        scene.setBodyBarrier(cellContext.isBodyBarrier());
        scene.setMaxRefDist(maximumReflectionDistance);
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
        String sourceTableName = cellContext.getSourceTableName();
        GeometryFactory geometryFactory = cellContext.getGeometryFactory();
        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        TableLocation sourceTableIdentifier = TableLocation.parse(sourceTableName, dbType);
        List<String> geomFields = getGeometryColumnNames(connection, sourceTableIdentifier);
        if (geomFields.isEmpty()) {
            throw new SQLException(String.format("The table %s does not exists or does not contain a geometry field", sourceTableIdentifier));
        }
        String sourceGeomName = geomFields.get(0);
        Geometry domainConstraint = geometryFactory.toGeometry(fetchEnvelope);
        Tuple<String, Integer> primaryKey = JDBCUtilities.getIntegerPrimaryKeyNameAndIndex(
                connection.unwrap(Connection.class), new TableLocation(sourceTableName, dbType));
        if (primaryKey == null) {
            throw new IllegalArgumentException(String.format("Source table %s does not contain a primary key", sourceTableIdentifier));
        }
        int pkIndex = primaryKey.second();
        try (PreparedStatement st = connection.prepareStatement("SELECT * FROM " + sourceTableName + " WHERE "
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
                                    throw new IllegalArgumentException("The table " + sourceTableName +
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
        String emissionTableName = scene.getEmissionInputSettings().getSourcesEmissionTableName();
        if (!emissionTableName.isEmpty()) {
            try (PreparedStatement st = connection.prepareStatement("SELECT E.* FROM " + sourceTableName +
                    " S INNER JOIN "+emissionTableName+" E ON S."+primaryKey.first()+" = E." +
                scene.getEmissionInputSettings().getSourceEmissionPrimaryKeyField()+" WHERE S."
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
                        scene.registerSourceEmissionFromDb(rs.getLong(scene.getEmissionInputSettings().getSourceEmissionPrimaryKeyField()), rs);
                    }
                } finally {
                    if (autoCommit) {
                        connection.setAutoCommit(true);
                    }
                }
            }

        }
    }

    public void putCnossosParametersPerPeriod(String key, AttenuationParameters cnossosParametersPerPeriod) {
        this.cnossosParametersPerPeriod.put(key, cnossosParametersPerPeriod);
    }
}
