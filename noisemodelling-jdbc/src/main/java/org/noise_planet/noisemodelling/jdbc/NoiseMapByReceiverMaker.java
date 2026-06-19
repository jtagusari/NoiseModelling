/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.api.ProgressVisitor;
import org.h2gis.utilities.*;
import org.h2gis.utilities.dbtypes.DBTypes;
import org.h2gis.utilities.dbtypes.DBUtils;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.io.WKTWriter;
import org.noise_planet.noisemodelling.jdbc.input.CellSceneContext;
import org.noise_planet.noisemodelling.jdbc.input.LoaderInitContext;
import org.noise_planet.noisemodelling.jdbc.input.EmissionInputSettings;
import org.noise_planet.noisemodelling.jdbc.input.EmissionInputSettingsView;
import org.noise_planet.noisemodelling.jdbc.input.PropagationSettings;
import org.noise_planet.noisemodelling.jdbc.input.SceneWithEmission;
import org.noise_planet.noisemodelling.jdbc.input.TableLoader;
import org.noise_planet.noisemodelling.jdbc.output.DefaultCutPlaneProcessing;
import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;
import org.noise_planet.noisemodelling.jdbc.input.DefaultTableLoader;
import org.noise_planet.noisemodelling.pathfinder.CutPlaneVisitorFactory;
import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.utils.profiler.ProfilerThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Computes sound propagation for a set of receivers stored in database tables.
 *
 * This class orchestrates the full workflow:
 * initialize data access and geometry context, build per-cell scenes through a TableLoader,
 * run ray/path computation, then delegate output handling.
 * @author Nicolas Fortin
 */
public class NoiseMapByReceiverMaker extends GridMapMaker implements LoaderInitContext, CellSceneContext {
    /** Tell table writer thread to empty current stacks then stop waiting for new data */
    public AtomicBoolean exitWhenDone = new AtomicBoolean(false);
    /** If true, all processing are aborted and all threads will be shutdown */
    public AtomicBoolean aborted = new AtomicBoolean(false);
    private static final Logger LOGGER = LoggerFactory.getLogger(NoiseMapByReceiverMaker.class);

    private final TableLoader tableLoader;
    private final CalculationIOSettings calculationIOSettings;
    private final IComputeRaysOutFactory computeRaysOutFactory;
    private final int threadCount;
    private ProfilerThread profilerThread;
    private final EmissionInputSettings emissionInputSettings;

    public NoiseMapByReceiverMaker(TableInputSettings tableInputSettings, EmissionInputSettings emissionInputSettings, PropagationSettings propagationSettings, ComputationSettings computationSettings, CalculationIOSettings calculationIOSettings, IComputeRaysOutFactory computeRaysOutFactory, int threadCount, TableLoader tableLoader) {
        super(tableInputSettings, propagationSettings, computationSettings);
        
        this.emissionInputSettings = emissionInputSettings;

        this.calculationIOSettings = calculationIOSettings;
        this.computeRaysOutFactory = computeRaysOutFactory;

        this.threadCount = threadCount;
        this.tableLoader = tableLoader;
    }

    /**
     * Fluent builder used to create a NoiseMapByReceiverMaker with optional table settings.
     */
    public static class Builder{
        private TableInputSettings tableInputSettings;
        private EmissionInputSettings emissionInputSettings = new EmissionInputSettings();
        private PropagationSettings propagationSettings = new PropagationSettings.Builder().build();
        private ComputationSettings computationSettings = new ComputationSettings.Builder().build();
        private CalculationIOSettings calculationIOSettings = new CalculationIOSettings.Builder().build();
        private int threadCount = 0;
        private IComputeRaysOutFactory computeRaysOutFactory;
        private TableLoader tableLoader;
        private AtomicBoolean exitWhenDone = new AtomicBoolean(false);
        private AtomicBoolean aborted = new AtomicBoolean(false);

        public Builder() {}

        public Builder setTableInputSettings(TableInputSettings tableInputSettings) {
            this.tableInputSettings = tableInputSettings;
            return this;
        }

        public Builder setPropagationSettings(PropagationSettings propagationSettings) {
            this.propagationSettings = propagationSettings;
            return this;
        }

        public Builder setComputationSettings(ComputationSettings computationSettings) {
            this.computationSettings = computationSettings;
            return this;
        }


        public Builder setEmissionInputSettings(EmissionInputSettings emissionInputSettings) {
            this.emissionInputSettings = emissionInputSettings;
            return this;
        }

        public Builder setCalculationIOSettings(CalculationIOSettings calculationIOSettings) {
            this.calculationIOSettings = calculationIOSettings;
            return this;
        }

        public Builder setThreadCount(int threadCount) {
            this.threadCount = threadCount;
            return this;
        }

        public Builder setComputeRaysOutFactory(IComputeRaysOutFactory computeRaysOutFactory) {
            this.computeRaysOutFactory = computeRaysOutFactory;
            return this;
        }

        public Builder setTableLoader(TableLoader tableLoader) {
            this.tableLoader = tableLoader;
            return this;
        }


        public NoiseMapByReceiverMaker build() {
            if(tableInputSettings == null) {
                throw new IllegalStateException("Either buildings table name or table input settings must be provided");
            }
            if(tableInputSettings.getSourceTableName() == null || tableInputSettings.getReceiverTableName() == null) {
                throw new IllegalStateException("Table names for sources and receivers must be provided");
            }

            if(computeRaysOutFactory == null) {
                computeRaysOutFactory = new DefaultCutPlaneProcessing(calculationIOSettings, exitWhenDone, aborted);
            }

            if(tableLoader == null) {
                tableLoader = new DefaultTableLoader();
            }
            return new NoiseMapByReceiverMaker(tableInputSettings, emissionInputSettings, propagationSettings, computationSettings, calculationIOSettings, computeRaysOutFactory, threadCount, tableLoader);
        }
    }

    /**
     * @return Settings of the database (expected tables names; fields, global settings of the computation)
     */
    public CalculationIOSettings getCalculationIOSettings() {
        return calculationIOSettings;
    }


    /**
     * This table must contain a source identifier column named IDSOURCE, a **PERIOD** VARCHAR field,
     * and emission spectrum in dB(A).
     * Spectrum column name must be LW{@link #sound_lvl_field}. Where HERTZ is a number
     * @return Source emission table name*
     */
    public String getSourcesEmissionTableName() {
        return emissionInputSettings.getSourcesEmissionTableName();
    }


    public EmissionInputSettings.INPUT_MODE getInputMode() {
        return emissionInputSettings.getInputMode();
    }

    public String getSourceEmissionPrimaryKeyField() {
        return emissionInputSettings.getSourceEmissionPrimaryKeyField();
    }


    public String getFrequencyFieldPrepend() {
        return emissionInputSettings.getFrequencyFieldPrepend();
    }


    public EmissionInputSettingsView getEmissionInputSettings() {
        return emissionInputSettings.copy();
    }


    /**
     * true if train propagation is computed (multiple reflection between the train and a screen)
     */
    public boolean isBodyBarrier() {
        return propagationSettings.isBodyBarrier();
    }

    /**
     * Computation stacks and timing are collected by this class in order
     * to profile the execution of the simulation
     * @return Instance of ProfilerThread or null
     */
    public ProfilerThread getProfilerThread() {
        return profilerThread;
    }

    /**
     * @return Receiver table name
     */
    public String getReceiverTableName() {
        return tableInputSettings.getReceiverTableName();
    }

    public String getReceiversLevelTableName(){
        return calculationIOSettings.getReceiversLevelTable();
    }


    /**
     * @return Object that generate scene for each sub-cell using database data
     */
    public TableLoader getPropagationProcessDataFactory() {
        return tableLoader;
    }

    public int getThreadCount() {
        return threadCount;
    }


    /**
     * Initialisation of data structures needed for sound propagation.
     * @param connection JDBC Connection
     * @param cellIndex Computation area index
     * @param skipReceivers Do not process the receivers primary keys in this set and once included add the new receivers primary in it
     * @return Data input for cell evaluation
     * @throws SQLException
     */
    public SceneWithEmission requestCellScene(Connection connection, CellIndex cellIndex,
                                         Set<Long> skipReceivers) throws SQLException, IOException {

        Envelope cellEnvelope = getCellEnv(cellIndex);

        if(verbose) {
            int ij = cellIndex.getLatitudeIndex() * gridDim + cellIndex.getLongitudeIndex() + 1;
            WKTWriter roundWKTWriter = new WKTWriter();
            roundWKTWriter.setPrecisionModel(new PrecisionModel(1.0));
            LOGGER.info(String.format("Begin processing of cell %d/%d Compute domain", ij, gridDim * gridDim));
            LOGGER.info(String.format("  X_range=[%.1f, %.1f], Y_range=[%.1f, %.1f]",
                    mainEnvelope.getMinX(), mainEnvelope.getMaxX(),
                    mainEnvelope.getMinY(), mainEnvelope.getMaxY()));
        }

        // Delegate all cell data extraction/assembly to the configured TableLoader.
        return tableLoader.createScene(connection, getCellSceneContext(), cellIndex, skipReceivers);
    }

    /**
     * @return Read-only context used by table loaders during one-time initialization.
     */
    public LoaderInitContext getLoaderInitContext() {
        return this;
    }

    /**
     * @return Read-only context used by table loaders for per-cell scene creation.
     */
    public CellSceneContext getCellSceneContext() {
        return this;
    }

    /**
     * Retrieves the computation envelope based on data stored in the database tables.
     * @param connection the database connection.
     * @return the computation envelope containing the bounding box of the data stored in the specified tables.
     * @throws SQLException
     */
    @Override
    protected Envelope getComputationEnvelope(Connection connection) throws SQLException {
        DBTypes dbTypes = DBUtils.getDBType(connection);
        Envelope envelopeInternal = GeometryTableUtilities.getEnvelope(connection, TableLocation.parse(tableInputSettings.getReceiverTableName(), dbTypes)).getEnvelopeInternal();
        envelopeInternal.expandBy(propagationSettings.getMaximumPropagationDistance());
        return envelopeInternal;
    }

    /**
     * Fetch all receivers and compute cells that contains receivers
     * @param connection
     * @return Cell index with number of receivers
     * @throws SQLException
     */
    public Map<CellIndex, Integer> searchPopulatedCells(Connection connection) throws SQLException {
        if(mainEnvelope == null) {
            throw new IllegalStateException("Call initialize before calling searchPopulatedCells");
        }
        Map<CellIndex, Integer> cellIndices = new HashMap<>();
        List<String> geometryFields = GeometryTableUtilities.getGeometryColumnNames(connection, TableLocation.parse(tableInputSettings.getReceiverTableName()));
        String geometryField;
        if(geometryFields.isEmpty()) {
            throw new SQLException("The table "+tableInputSettings.getReceiverTableName()+" does not contain a Geometry field, then the extent " +
                    "cannot be computed");
        }
        LOGGER.info("Collect all receivers in order to localize populated cells");
        geometryField = geometryFields.get(0);
        ResultSet rs = connection.createStatement().executeQuery("SELECT " + geometryField + " FROM " + tableInputSettings.getReceiverTableName());
        // Build an RTree index of cell envelopes to quickly map each receiver to one/many cells.
        STRtree rtree = new STRtree();
        for(int i = 0; i < gridDim; i++) {
            for(int j = 0; j < gridDim; j++) {
                Envelope refEnv = getCellEnv(mainEnvelope, i,
                        j, getCellWidth(), getCellHeight());
                rtree.insert(refEnv, new CellIndex(j, i));
            }
        }
        // Assign each receiver coordinate to intersecting cells and count receivers per cell.
        try (SpatialResultSet srs = rs.unwrap(SpatialResultSet.class)) {
            while (srs.next()) {
                Geometry pt = srs.getGeometry();
                if(pt != null && !pt.isEmpty()) {
                    Coordinate ptCoord = pt.getCoordinate();
                    List queryResult = rtree.query(new Envelope(ptCoord));
                    for(Object o : queryResult) {
                        if(o instanceof CellIndex) {
                            cellIndices.merge((CellIndex) o, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        return cellIndices;
    }

    /**
     * Launch sound propagation
     * @param connection JDBC Connection
     * @param cellIndex Computation area index
     * @param progression Progression info
     * @param skipReceivers Do not process the receivers primary keys in this set and once included add the new receivers primary in it
     * @return Output data instance for this cell
     * @throws SQLException Sql exception instance
     */
    public CutPlaneVisitorFactory evaluateCell(Connection connection, CellIndex cellIndex,
                                        ProgressVisitor progression, Set<Long> skipReceivers) throws SQLException, IOException {
        SceneWithEmission scene = requestCellScene(connection, cellIndex, skipReceivers);

        if(verbose) {
            LOGGER.info(String.format("  Number of receivers: %d", scene.receivers.size()));
            LOGGER.info(String.format("  Number of sound sources: %d", scene.countSources()));
            LOGGER.info(String.format("  Number of buildings: %d", scene.profileBuilder.getBuildingCount()));
        }

        CutPlaneVisitorFactory computeRaysOut = computeRaysOutFactory.createCutPlaneVisitorFactory(scene);

        PathFinder computeRays = new PathFinder(scene, progression);

        if(profilerThread != null) {
            computeRays.setProfilerThread(profilerThread);
        }

        if(threadCount > 0) {
            computeRays.setThreadCount(threadCount);
        }

        // Receivers are normalized to absolute heights for consistent propagation geometry.
        computeRays.ensureAbsoluteReceiverHeights();

        if(!tableInputSettings.isSourceHasAbsoluteZCoordinates()) {
            // Convert relative source heights to absolute heights only when required by inputs.
            computeRays.makeSourceRelativeZToAbsolute();
        }

        computeRays.run(computeRaysOut);

        return computeRaysOut;
    }

    /**
     * @return Class used to load tables for input data
     */
    public TableLoader getTableLoader() {
        return tableLoader;
    }

    /**
     * Initializes the noise map computation process.
     * @param connection Active connection
     * @param progression
     * @throws SQLException
     */
    @Override
    public void initialize(Connection connection, ProgressVisitor progression) throws SQLException {
        super.initialize(connection, progression);
        // Initialize collaborators after base geometry/grid initialization is complete.
        tableLoader.initialize(connection, getLoaderInitContext());
        computeRaysOutFactory.initialize(connection, this);
    }

    /**
     * Run NoiseModelling with provided parameters, return when computation is done
     */
    public void run(Connection connection, ProgressVisitor progressLogger) throws SQLException {
        initialize(connection, progressLogger);

        // Set of already processed receivers
        Set<Long> receivers = new HashSet<>();

        // Process only cells that actually contain receivers.
        Map<CellIndex, Integer> cells = searchPopulatedCells(connection);
        ProgressVisitor progressVisitor = progressLogger.subProcess(cells.size());

        try {
            computeRaysOutFactory.start(progressVisitor);
            for (CellIndex cellIndex : new TreeSet<>(cells.keySet())) {
                // Run ray propagation
                try {
                    evaluateCell(connection, cellIndex, progressVisitor, receivers);
                } catch (IOException ex) {
                    throw new SQLException(ex);
                }
            }
        } finally {
            computeRaysOutFactory.stop();
            progressLogger.endOfProgress();
        }
    }

    /**
     * A factory interface for creating objects that compute rays out for noise map computation.
     */
    public interface IComputeRaysOutFactory {

        /**
         * Called only once when the settings are set.
         * @param connection             the database connection to be used for initialization.
         * @param noiseMapByReceiverMaker the noise map by receiver maker object associated with the computation process.
         * @throws SQLException if an SQL exception occurs while initializing the propagation process data factory.
         */
        void initialize(Connection connection, NoiseMapByReceiverMaker noiseMapByReceiverMaker) throws SQLException;

        /**
         * Called before the first sub cell is being computed
         * @param progressLogger Main progression information, this method will not update the progression
         * @throws SQLException
         */
        void start(ProgressVisitor progressLogger) throws SQLException;

        /**
         * Called when all sub-cells have been processed
         * @throws SQLException
         */
        void stop() throws SQLException;

        /**
         * Creates an object that computes paths out for noise map computation.
         * @param cellData the scene data for the current computation cell
         * @return an object that computes paths out for noise map computation.
         */
        CutPlaneVisitorFactory createCutPlaneVisitorFactory(SceneWithEmission cellData);
    }

    public PropagationSettings getPropagationSettings() {
        return propagationSettings;
    }

}
