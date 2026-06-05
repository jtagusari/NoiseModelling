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
import org.h2gis.utilities.TableLocation;
import org.h2gis.utilities.dbtypes.DBTypes;
import org.h2gis.utilities.dbtypes.DBUtils;
import org.locationtech.jts.geom.*;
import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.noise_planet.noisemodelling.jdbc.input.PropagationSettings;

import java.sql.*;

import static org.h2gis.utilities.GeometryTableUtilities.getGeometryColumnNames;
import static org.h2gis.utilities.GeometryTableUtilities.getSRID;
/**
 * Shared grid and input-table configuration for map makers.
 *
 * This base class manages computation envelope, grid subdivision and common
 * acoustic/geometric parameters used by receiver and Delaunay workflows.
 * @author Nicolas Fortin
 */
public abstract class GridMapMaker {
    public boolean verbose = true;
    private static final Logger LOGGER = LoggerFactory.getLogger(GridMapMaker.class);
    protected static final double MINIMAL_BUFFER_RATIO = 0.3;

    protected final BuildingTableSettings buildingTableSettings;
    protected final String sourcesTableName;
    protected final String soilTableName;
    // Digital elevation model table. (Contains points or triangles)
    protected final String demTable;
    // Bridge points table name. Contains BRIDGE_POINTS data with geometry and structural properties
    protected final String bridgePointsTableName;
    // protected final String sound_lvl_field = "DB_M";
    protected final String sound_lvl_field;
    // True if Z of sound source and receivers are relative to the ground
    protected boolean receiverHasAbsoluteZCoordinates = false;
    protected boolean sourceHasAbsoluteZCoordinates = false;

    protected final PropagationSettings propagationSettings;

    protected GeometryFactory geometryFactory;

    // Runtime-initialized attributes
    /**
     *  Side computation cell count (same on X and Y)
     */
    protected int gridDim = 0;
    protected Envelope mainEnvelope = new Envelope();

    public GridMapMaker(BuildingTableSettings buildingTableSettings, String sourcesTableName, String soilTableName, String demTable, String bridgePointsTableName, PropagationSettings propagationSettings, String sound_lvl_field) {
        this.buildingTableSettings = buildingTableSettings;
        this.sourcesTableName = sourcesTableName;
        this.soilTableName = soilTableName;
        this.demTable = demTable;
        this.bridgePointsTableName = bridgePointsTableName;
        this.propagationSettings = propagationSettings;
        this.sound_lvl_field = sound_lvl_field;
    }

    // public GridMapMaker(String buildingsTableName, String sourcesTableName) {
    //     this.buildingTableSettings = new BuildingTableSettings.Builder()
    //             .setBuildingsTableName(buildingsTableName)
    //             .build();
    //     this.sourcesTableName = sourcesTableName;
    // }

    public BuildingTableSettings getBuildingTableSettings() {
        return buildingTableSettings;
    }


    public GeometryFactory getGeometryFactory() {
        return geometryFactory;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Compute the envelope corresponding to parameters
     * @param cellIndex Cell location
     * @return Envelope of the cell
     */
    public Envelope getCellEnv(CellIndex cellIndex) {
        return  getCellEnv(mainEnvelope, cellIndex.getLatitudeIndex(),
                cellIndex.getLongitudeIndex(), getCellWidth(), getCellHeight());
    }
    /**
        * Compute the envelope corresponding to grid indices and dimensions.
     *
     * @param mainEnvelope Global envelope
     * @param cellI        I cell index
     * @param cellJ        J cell index
     * @param cellWidth    Cell width meter
     * @param cellHeight   Cell height meter
     * @return Envelope of the cell
     */
    public static Envelope getCellEnv(Envelope mainEnvelope, int cellI, int cellJ, double cellWidth,
                                      double cellHeight) {
        return new Envelope(mainEnvelope.getMinX() + cellI * cellWidth,
                mainEnvelope.getMinX() + cellI * cellWidth + cellWidth,
                mainEnvelope.getMinY() + cellHeight * cellJ,
                mainEnvelope.getMinY() + cellHeight * cellJ + cellHeight);
    }

    public double getGroundSurfaceSplitSideLength() {
        return propagationSettings.getGroundSurfaceSplitSideLength();
    }


    /**
     * true if train propagation is computed (multiple reflection between the train and a screen)
     */
    // public void setBodyBarrier(boolean bodyBarrier) {
    //     this.bodyBarrier = bodyBarrier;
    // }

    public double getCellWidth() {
        return mainEnvelope.getWidth() / gridDim;
    }

    public double getCellHeight() {
        return mainEnvelope.getHeight() / gridDim;
    }

    abstract protected Envelope getComputationEnvelope(Connection connection) throws SQLException;

    /**
     * Fetch scene attributes, compute best computation cell size.
     * @param connection Active connection
     * @throws java.sql.SQLException
     */
    public void initialize(Connection connection, ProgressVisitor progression) throws SQLException {
        if(propagationSettings.getSoundReflectionOrder() > 0 && propagationSettings.getMaximumPropagationDistance() < propagationSettings.getMaximumReflectionDistance()) {
            throw new SQLException(new IllegalArgumentException(
                    "Maximum wall seeking distance cannot be superior than maximum propagation distance"));
        }
        initializeGridState(connection);
    }

    /**
     * Resolve the geometry factory and computation envelope from the database-backed configuration.
     * Subclasses can override this method when the initialization source differs.
     */
    protected void initializeGridState(Connection connection) throws SQLException {
        geometryFactory = createGeometryFactory(connection);

        if(mainEnvelope.isNull()) {
            // Derive computation envelope once; grid dimensions are resolved from this extent.
            setMainEnvelope(getComputationEnvelope(connection));
        }
    }

    /**
     * Resolve the SRID and create the geometry factory used by grid computations.
     */
    protected GeometryFactory createGeometryFactory(Connection connection) throws SQLException {
        int srid = 0;
        DBTypes dbTypes = DBUtils.getDBType(connection.unwrap(Connection.class));
        if(!sourcesTableName.isEmpty()) {
            srid = getSRID(connection, TableLocation.parse(sourcesTableName, dbTypes));
        }
        if(srid == 0) {
            srid = getSRID(connection, TableLocation.parse(buildingTableSettings.getBuildingsTableName(), dbTypes));
        }
        return new GeometryFactory(new PrecisionModel(), srid);
    }

    /**
     * @return Side computation cell count (same on X and Y)
     */
    public int getGridDim() {
        return gridDim;
    }

    public void setGridDim(int gridDim) {
        this.gridDim = gridDim;
    }

    /**
     * This table must contain a POLYGON column, where Z values are wall bottom position relative to sea level.
     * It may also contain a height field (0-N] average building height from the ground.
     * @return Table name that contains buildings
     */
    public String getBuildingsTableName() {
        return buildingTableSettings.getBuildingsTableName();
    }

    /**
     * This table must contain a POINT or LINESTRING column
     * @return Table name containing linear and/or punctual sound source geometries.
     */
    public String getSourcesTableName() {
        return sourcesTableName;
    }

    /**
     * Extracted from NMPB 2008-2 7.3.2
     * Soil areas POLYGON, with a dimensionless coefficient G:
     *  - Law, meadow, field of cereals G=1
     *  - Undergrowth (resinous or decidious) G=1
     *  - Compacted earth, track G=0.3
     *  - Road surface G=0
     *  - Smooth concrete G=0
     * @return Table name of grounds properties
     */
    public String getSoilTableName() {
        return soilTableName;
    }

    /**
     * @return True if provided Z value are sea level (false for relative to ground level)
     */
    public boolean isReceiverHasAbsoluteZCoordinates() {
        return receiverHasAbsoluteZCoordinates;
    }

    /**
     *
     * @param receiverHasAbsoluteZCoordinates True if provided Z value are sea level (false for relative to ground level)
     */
    public void setReceiverHasAbsoluteZCoordinates(boolean receiverHasAbsoluteZCoordinates) {
        this.receiverHasAbsoluteZCoordinates = receiverHasAbsoluteZCoordinates;
    }

    /**
     * @return True if provided Z value are sea level (false for relative to ground level)
     */
    public boolean isSourceHasAbsoluteZCoordinates() {
        return sourceHasAbsoluteZCoordinates;
    }

    /**
     * @param sourceHasAbsoluteZCoordinates True if provided Z value are sea level (false for relative to ground level)
     */
    public void setSourceHasAbsoluteZCoordinates(boolean sourceHasAbsoluteZCoordinates) {
        this.sourceHasAbsoluteZCoordinates = sourceHasAbsoluteZCoordinates;
    }

    public boolean iszBuildings() {
        return buildingTableSettings.isZBuildings();
    }


    /**
     * Digital Elevation model table name. Currently only a table with POINTZ column is supported.
     * DEM points too close with buildings are not fetched.
     * @return Digital Elevation model table name
     */
    public String getDemTable() {
        return demTable;
    }


    /**
     * Bridge points table name. Table must contain BRIDGE_POINTS with POINT geometry,
     * BRIDGE_PK, structural properties (deck height, width, thickness, barrier heights),
     * and bridge type information (girder type, slab type).
     * @return Bridge points table name
     */
    public String getBridgePointsTableName() {
        return bridgePointsTableName;
    }

    /**
     * Field name of the {@link #sourcesTableName}HERTZ. Where HERTZ is a number [100-5000].
     * Without the hertz value.
     * @return Hertz field prefix
     */
    public String getSound_lvl_field() {
        return sound_lvl_field;
    }

    /**
     * @return Sound propagation stop at this distance, default to 750m.
        * Computation cell size is proportional to this value.
     */

    public double getMaximumPropagationDistance() {
        return propagationSettings.getMaximumPropagationDistance();
    }

    public double getGs() {
        return propagationSettings.getGs();
    }

    /**
     * @return Reflection and diffraction maximum search distance, default to 400m.
    */
    public double getMaximumReflectionDistance() {
        return propagationSettings.getMaximumReflectionDistance();
    }

    /**
     * @return Sound reflection order. 0 order mean 0 reflection depth.
     * 2 means propagation of rays up to 2 collision with walls.
     */
    public int getSoundReflectionOrder() {
        return propagationSettings.getSoundReflectionOrder();
    }

    /**
     * @return True if diffraction rays will be computed on vertical edges (around buildings)
     */
    public boolean isComputeHorizontalDiffraction() {
        return propagationSettings.isComputeHorizontalDiffraction();
    }

    /**
     * @return Global default wall absorption on sound reflection.
     */
    public double getWallAbsorption() {
        return buildingTableSettings.getDefaultWallAbsorption();
    }

    /**
        * @return {@link #getBuildingsTableName()} table field name for building height above the ground.
     */

    public String getHeightField() {
        return buildingTableSettings.getHeightField();
    }


    /**
     * @return The envelope of computation area.
    */
    public Envelope getMainEnvelope() {
        return mainEnvelope;
    }

    /**
     * Set computation area. Update the property subdivisionLevel and gridDim.
     * @param mainEnvelope Computation area
     */
    public void setMainEnvelope(Envelope mainEnvelope) {
        this.mainEnvelope = mainEnvelope;
        if(gridDim == 0) {
            // Split domain into 4^subdiv cells
            // Compute subdivision level using envelope and maximum propagation distance
            double greatestSideLength = mainEnvelope.maxExtent();
            int subdivisionLevel = 0;
            while(propagationSettings.getMaximumPropagationDistance() / (greatestSideLength / Math.pow(2, subdivisionLevel)) < MINIMAL_BUFFER_RATIO) {
                subdivisionLevel++;
            }
            gridDim = (int) Math.pow(2, subdivisionLevel);
            
            LOGGER.info("Cell subdivision computed:");
            LOGGER.info(String.format("  Envelope: X_range=[%.1f, %.1f], Y_range=[%.1f, %.1f], Width=%.1f, Height=%.1f",
                    mainEnvelope.getMinX(), mainEnvelope.getMaxX(),
                    mainEnvelope.getMinY(), mainEnvelope.getMaxY(),
                    mainEnvelope.getWidth(), mainEnvelope.getHeight()));
            LOGGER.info(String.format("  Cells: Number=%dx%d, Width=%.1f, Height=%.1f)", subdivisionLevel+1, subdivisionLevel+1, getCellWidth(), getCellHeight()));
        }
    }

    /**
     * @return True if diffraction of horizontal edges is computed.
     */
    public boolean isComputeVerticalDiffraction() {
        return propagationSettings.isComputeVerticalDiffraction();
    }

}
