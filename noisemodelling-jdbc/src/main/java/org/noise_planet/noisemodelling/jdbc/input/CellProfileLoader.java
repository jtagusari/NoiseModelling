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
import org.noise_planet.noisemodelling.jdbc.TableInputSettings;
import org.noise_planet.noisemodelling.jdbc.ComputationSettings;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgePoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Building;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Wall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

import static org.h2gis.utilities.GeometryTableUtilities.getGeometryColumnNames;

/**
 * Loads scene environment geometry (buildings, terrain, soil areas, bridges) from database
 * tables into a {@link ProfileBuilder} for a given computation cell.
 */
public class CellProfileLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(CellProfileLoader.class);

    private final Connection connection;
    private final String buildingTableName;
    private final String buildingAlphaFieldName;
    private final double buildingDefaultAlpha;
    private final String buildingHeightFieldName;
    private final boolean buildingGeometryZ;
    /** Side length used to subdivide large soil polygons before intersection, in metres. */
    private final double groundSurfaceSplitSideLength;
    private final String terrainTableName;
    private final String groundTableName;
    private final String bridgePointTableName;
    private final GeometryFactory geometryFactory;

    public CellProfileLoader(Connection connection, String buildingTableName, String buildingAlphaFieldName, double buildingDefaultAlpha, String buildingHeightFieldName, boolean buildingGeometryZ, double groundSurfaceSplitSideLength, String terrainTableName, String groundTableName, String bridgePointTableName, GeometryFactory geometryFactory) {
        this.connection = connection;
        this.buildingTableName = buildingTableName;
        this.buildingAlphaFieldName = buildingAlphaFieldName;
        this.buildingDefaultAlpha = buildingDefaultAlpha;
        this.buildingHeightFieldName = buildingHeightFieldName;
        this.buildingGeometryZ = buildingGeometryZ;
        this.groundSurfaceSplitSideLength = groundSurfaceSplitSideLength;
        this.terrainTableName = terrainTableName;
        this.groundTableName = groundTableName;
        this.bridgePointTableName = bridgePointTableName;
        this.geometryFactory = geometryFactory;
    }

    public static class Builder{
        private Connection connection;
        private TableInputSettings tableInputSettings = new TableInputSettings.Builder().build();
        private ComputationSettings computationSettings = new ComputationSettings.Builder().build();
        private String buildingTableName = tableInputSettings.getBuildingTableName();
        private String buildingAlphaFieldName = tableInputSettings.getBuildingAlphaField();
        private double buildingDefaultAlpha = tableInputSettings.getBuildingDefaultAlpha();
        private String buildingHeightFieldName = tableInputSettings.getBuildingHeightFieldName();
        private boolean buildingGeometryZ = tableInputSettings.useBuildingGeometryZ();
        private double groundSurfaceSplitSideLength = computationSettings.getGroundSurfaceSplitSideLength();
        private String terrainTableName = tableInputSettings.getTerrainTableName();
        private String groundTableName = tableInputSettings.getGroundTableName();
        private String bridgePointTableName = tableInputSettings.getBridgePointTableName();
        private GeometryFactory geometryFactory;

        public Builder setConnection(Connection connection) {
            this.connection = connection;
            return this;
        }

        public Builder setCellSceneContext(CellSceneContext cellSceneContext) {
            TableInputSettings tableInputSettingsFromContext = cellSceneContext.getTableInputSettings();
            ComputationSettings computationSettingsFromContext = cellSceneContext.getComputationSettings();
            this.buildingTableName = tableInputSettingsFromContext.getBuildingTableName();
            this.buildingAlphaFieldName = tableInputSettingsFromContext.getBuildingAlphaField();
            this.buildingDefaultAlpha = tableInputSettingsFromContext.getBuildingDefaultAlpha();
            this.buildingHeightFieldName = tableInputSettingsFromContext.getBuildingHeightFieldName();
            this.buildingGeometryZ = tableInputSettingsFromContext.useBuildingGeometryZ();
            this.groundSurfaceSplitSideLength = computationSettingsFromContext.getGroundSurfaceSplitSideLength();
            this.terrainTableName = tableInputSettingsFromContext.getTerrainTableName();
            this.groundTableName = tableInputSettingsFromContext.getGroundTableName();
            this.bridgePointTableName = tableInputSettingsFromContext.getBridgePointTableName();
            this.geometryFactory = cellSceneContext.getGeometryFactory();
            return this;
        }

        public Builder setTableInputSettings(TableInputSettings tableInputSettings) {
            this.buildingTableName = tableInputSettings.getBuildingTableName();
            this.buildingAlphaFieldName = tableInputSettings.getBuildingAlphaField();
            this.buildingDefaultAlpha = tableInputSettings.getBuildingDefaultAlpha();
            this.buildingHeightFieldName = tableInputSettings.getBuildingHeightFieldName();
            this.buildingGeometryZ = tableInputSettings.useBuildingGeometryZ();
            this.terrainTableName = tableInputSettings.getTerrainTableName();
            this.groundTableName = tableInputSettings.getGroundTableName();
            this.bridgePointTableName = tableInputSettings.getBridgePointTableName();
            return this;
        }

        public Builder setBuildingTableName(String buildingTableName) {
            this.buildingTableName = buildingTableName;
            return this;
        }

        public Builder setBuildingAlphaFieldName(String buildingAlphaFieldName) {
            this.buildingAlphaFieldName = buildingAlphaFieldName;
            return this;
        }

        public Builder setBuildingDefaultAlpha(double buildingDefaultAlpha) {
            this.buildingDefaultAlpha = buildingDefaultAlpha;
            return this;
        }

        public Builder setBuildingHeightFieldName(String buildingHeightFieldName) {
            this.buildingHeightFieldName = buildingHeightFieldName;
            return this;
        }

        public Builder setBuildingGeometryZ(boolean buildingGeometryZ) {
            this.buildingGeometryZ = buildingGeometryZ;
            return this;
        }

        public Builder setGroundSurfaceSplitSideLength(double groundSurfaceSplitSideLength) {
            this.groundSurfaceSplitSideLength = groundSurfaceSplitSideLength;
            return this;
        }

        public Builder setTerrainTableName(String terrainTableName) {
            this.terrainTableName = terrainTableName;
            return this;
        }

        public Builder setGroundTableName(String groundTableName) {
            this.groundTableName = groundTableName;
            return this;
        }

        public Builder setBridgePointTableName(String bridgePointTableName) {
            this.bridgePointTableName = bridgePointTableName;
            return this;
        }

        public Builder setGeometryFactory(GeometryFactory geometryFactory) {
            this.geometryFactory = geometryFactory;
            return this;
        }

        public CellProfileLoader build() {
            if (connection == null) {
                throw new IllegalStateException("Connection must be set");
            }

            if (geometryFactory == null) {
                throw new IllegalStateException("GeometryFactory must be set");
            }

            return new CellProfileLoader(connection, buildingTableName, buildingAlphaFieldName, buildingDefaultAlpha, buildingHeightFieldName, buildingGeometryZ, groundSurfaceSplitSideLength, terrainTableName, groundTableName, bridgePointTableName, geometryFactory);
        }
    }

    /**
     * Fetches buildings for the cell envelope and adds them to the profile builder.
     */
    public void fetchCellBuilding(Envelope fetchEnvelope, ProfileBuilder builder) throws SQLException {
        List<Building> buildings = new LinkedList<>();
        List<Wall> walls = new LinkedList<>();
        fetchCellBuilding(fetchEnvelope, buildings, walls);
        for (Building building : buildings) {
            builder.addBuilding(building);
        }
        for (Wall wall : walls) {
            builder.addWall(wall);
        }
    }

    /**
     * Fetches building data for the cell envelope into the provided lists.
     *
     * @param connection         database connection
     * @param tableInputSettings table and field configuration
     * @param fetchEnvelope      spatial filter
     * @param buildings          list to populate with fetched buildings
     * @param walls              list to populate with fetched wall segments
     * @param geometryFactory    geometry factory with the computation SRID
     */
    public void fetchCellBuilding(Envelope fetchEnvelope, List<Building> buildings, List<Wall> walls) throws SQLException {
        if (buildingTableName == null || buildingTableName.isEmpty()) {
            return;
        }
        Geometry envGeo = geometryFactory.toGeometry(fetchEnvelope);

        boolean fetchAlpha = JDBCUtilities.hasField(connection, buildingTableName, buildingAlphaFieldName);
        String additionalQuery = "";
        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        if (!buildingHeightFieldName.isEmpty()) {
            additionalQuery += ", " + TableLocation.quoteIdentifier(buildingHeightFieldName, dbType);
        }
        if (fetchAlpha) {
            additionalQuery += ", " + buildingAlphaFieldName;
        }
        String pkBuilding = "";
        final int indexPk = JDBCUtilities.getIntegerPrimaryKey(connection.unwrap(Connection.class),
                new TableLocation(buildingTableName, dbType));
        if (indexPk > 0) {
            pkBuilding = JDBCUtilities.getColumnName(connection, buildingTableName, indexPk);
            additionalQuery += ", " + pkBuilding;
        }
        String buildingGeomName = getGeometryColumnNames(connection,
                TableLocation.parse(buildingTableName, dbType)).get(0);
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT " + TableLocation.quoteIdentifier(buildingGeomName) + additionalQuery + " FROM " +
                        buildingTableName + " WHERE " +
                        TableLocation.quoteIdentifier(buildingGeomName, dbType) + " && ?::geometry")) {
            st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
            try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                int columnIndex = pkBuilding.isEmpty() ? 0 :
                        JDBCUtilities.getFieldIndex(rs.getMetaData(), pkBuilding);
                while (rs.next()) {
                    Geometry building = rs.getGeometry();
                    if (building == null) continue;
                    Geometry intersected = tryIntersect(building, envGeo);
                    if (!(intersected instanceof Polygon || intersected instanceof MultiPolygon
                            || intersected instanceof LineString)) continue;
                    double alpha = fetchAlpha ? rs.getDouble(buildingAlphaFieldName) : buildingDefaultAlpha;
                    long pk = columnIndex != 0 ? rs.getLong(columnIndex) : -1;
                    double height = buildingHeightFieldName.isEmpty() ?
                            Double.MAX_VALUE : rs.getDouble(buildingHeightFieldName);
                    addBuildingGeometryParts(intersected, alpha, pk, height, buildingGeometryZ, buildings, walls);
                }
            }
        }
    }

    private static Geometry tryIntersect(Geometry building, Geometry envGeo) {
        try {
            return building.intersection(envGeo);
        } catch (TopologyException ex) {
            WKTWriter wktWriter = new WKTWriter(3);
            LOGGER.error(String.format("Error with input buildings geometry\n%s\n%s",
                    wktWriter.write(building), wktWriter.write(envGeo)), ex);
            return null;
        }
    }

    private static void addBuildingGeometryParts(Geometry intersected, double alpha, long pk, double height,
                                                 boolean geometryZ, List<Building> buildings, List<Wall> walls) {
        for (int i = 0; i < intersected.getNumGeometries(); i++) {
            Geometry geometry = intersected.getGeometryN(i);
            if (geometry instanceof Polygon && !geometry.isEmpty()) {
                buildings.add(new Building((Polygon) geometry, height, alpha, pk, geometryZ));
            } else if (geometry instanceof LineString) {
                addWallSegments((LineString) geometry, alpha, pk, height, walls);
            }
        }
    }

    private static void addWallSegments(LineString lineString, double alpha, long pk, double height,
                                        List<Wall> walls) {
        Coordinate[] coordinates = lineString.getCoordinates();
        for (int vertex = 0; vertex < coordinates.length - 1; vertex++) {
            Wall wall = new Wall(new LineSegment(coordinates[vertex], coordinates[vertex + 1]),
                    -1, ProfileBuilder.IntersectionType.WALL);
            wall.setG(alpha);
            wall.setPrimaryKey(pk);
            wall.setHeight(height);
            walls.add(wall);
        }
    }

    /**
     * Fetches digital elevation model (DEM) points for the cell envelope and adds them to the profile builder.
     */
    public void fetchCellTerrain(Envelope fetchEnvelope, ProfileBuilder profileBuilder) throws SQLException {
        if (terrainTableName.isEmpty()) return;

        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        List<String> geomFields = getGeometryColumnNames(connection, TableLocation.parse(terrainTableName, dbType));
        if (geomFields.isEmpty()) {
            throw new SQLException("Digital elevation model table \"" + terrainTableName + "\" must exist and contain a POINT field");
        }
        String topoGeomName = geomFields.get(0);
        double sumZ = 0;
        int topoCount = 0;
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT " + TableLocation.quoteIdentifier(topoGeomName, dbType) + " FROM " +
                        terrainTableName + " WHERE " +
                        TableLocation.quoteIdentifier(topoGeomName, dbType) + " && ?::geometry")) {
            st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
            try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                while (rs.next()) {
                    Geometry pt = rs.getGeometry();
                    if (pt == null) continue;
                    Coordinate ptCoordinate = pt.getCoordinate();
                    profileBuilder.addTopographicPoint(ptCoordinate);
                    if (!Double.isNaN(ptCoordinate.z)) {
                        sumZ += ptCoordinate.z;
                        topoCount += 1;
                    }
                }
            }
            double averageZ = topoCount > 0 ? sumZ / topoCount : 0;
            addAverageZCorners(fetchEnvelope, averageZ, geometryFactory, profileBuilder);
        }
    }

    private static void addAverageZCorners(Envelope fetchEnvelope, double averageZ,
                                           GeometryFactory geometryFactory, ProfileBuilder profileBuilder) {
        Envelope extendedEnvelope = new Envelope(fetchEnvelope);
        extendedEnvelope.expandBy(fetchEnvelope.getDiameter());
        Coordinate[] coordinates = geometryFactory.toGeometry(extendedEnvelope).getCoordinates();
        for (int i = 0; i < coordinates.length - 1; i++) {
            Coordinate coordinate = coordinates[i];
            profileBuilder.addTopographicPoint(new Coordinate(coordinate.x, coordinate.y, averageZ));
        }
    }

    /**
     * Fetches soil areas for the cell envelope and adds them to the profile builder.
     * Large polygons are subdivided into tiles of {@link #groundSurfaceSplitSideLength} to reduce
     * per-cell memory usage.
     */
    public void fetchCellGround(Envelope fetchEnvelope, ProfileBuilder builder) throws SQLException {
        if (groundTableName.isEmpty()) return;
        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        double startX = Math.floor(fetchEnvelope.getMinX() / groundSurfaceSplitSideLength) * groundSurfaceSplitSideLength;
        double startY = Math.floor(fetchEnvelope.getMinY() / groundSurfaceSplitSideLength) * groundSurfaceSplitSideLength;
        String soilGeomName = getGeometryColumnNames(connection,
                TableLocation.parse(groundTableName, dbType)).get(0);
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT " + TableLocation.quoteIdentifier(soilGeomName, dbType) + ", G FROM " +
                        groundTableName + " WHERE " +
                        TableLocation.quoteIdentifier(soilGeomName, dbType) + " && ?::geometry")) {
            st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
            try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                while (rs.next()) {
                    Geometry mainPolygon = rs.getGeometry();
                    if (mainPolygon == null) continue;
                    double g = rs.getDouble("G");
                    addSoilPolygon(mainPolygon, g, fetchEnvelope, startX, startY, geometryFactory, builder);
                }
            }
        }
    }

    private void addSoilPolygon(Geometry mainPolygon, double g, Envelope fetchEnvelope,
                                double startX, double startY, GeometryFactory geometryFactory,
                                ProfileBuilder builder) {
        for (int idPoly = 0; idPoly < mainPolygon.getNumGeometries(); idPoly++) {
            Geometry poly = mainPolygon.getGeometryN(idPoly);
            if (!(poly instanceof Polygon)) continue;
            Envelope geoEnv = poly.getEnvelopeInternal();
            double startXGeo = Math.max(startX,
                    Math.floor(geoEnv.getMinX() / groundSurfaceSplitSideLength) * groundSurfaceSplitSideLength);
            double startYGeo = Math.max(startY,
                    Math.floor(geoEnv.getMinY() / groundSurfaceSplitSideLength) * groundSurfaceSplitSideLength);
            double maxX = Math.min(fetchEnvelope.getMaxX(), geoEnv.getMaxX());
            double maxY = Math.min(fetchEnvelope.getMaxY(), geoEnv.getMaxY());
            addTiledPolygon((Polygon) poly, g, startXGeo, startYGeo, maxX, maxY, geometryFactory, builder);
        }
    }

    private void addTiledPolygon(Polygon poly, double g, double startX, double startY,
                                 double maxX, double maxY, GeometryFactory geometryFactory,
                                 ProfileBuilder builder) {
        PreparedPolygon preparedPolygon = new PreparedPolygon(poly);
        double xCursor = startX;
        while (xCursor < maxX) {
            double yCursor = startY;
            while (yCursor < maxY) {
                Geometry tileGeom = geometryFactory.toGeometry(
                        new Envelope(xCursor, xCursor + groundSurfaceSplitSideLength,
                                yCursor, yCursor + groundSurfaceSplitSideLength));
                if (preparedPolygon.intersects(tileGeom)) {
                    addIntersectedTile(poly, tileGeom, g, builder);
                }
                yCursor += groundSurfaceSplitSideLength;
            }
            xCursor += groundSurfaceSplitSideLength;
        }
    }

    private static void addIntersectedTile(Polygon poly, Geometry tileGeom, double g, ProfileBuilder builder) {
        try {
            Geometry inters = poly.intersection(tileGeom);
            if (!inters.isEmpty() && (inters instanceof Polygon || inters instanceof MultiPolygon)) {
                builder.addGroundEffect(inters, g);
            }
        } catch (TopologyException | IllegalArgumentException ex) {
            // Ignore invalid geometry fragments
        }
    }

    /**
     * Fetches bridge geometry for the cell envelope and adds it to the profile builder.
     */
    public void fetchCellBridge(Envelope fetchEnvelope, ProfileBuilder builder) throws SQLException {
        if (bridgePointTableName.isEmpty()) return;
        DBTypes dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        List<String> geomFields = getGeometryColumnNames(connection,
                TableLocation.parse(bridgePointTableName, dbType));
        if (geomFields.isEmpty()) {
            throw new SQLException("Bridge points table \"" + bridgePointTableName + "\" must exist and contain a POINT field");
        }
        String bridgeGeomName = geomFields.get(0);
        List<BridgePoint> bridgePointsList = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT * FROM " + bridgePointTableName + " WHERE " +
                        TableLocation.quoteIdentifier(bridgeGeomName, dbType) + " && ?::geometry ORDER BY PK")) {
            st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    bridgePointsList.add(new BridgePoint(rs));
                }
            }
        }
        Bridge.createBridgesFromPoints(bridgePointsList).forEach(builder::addBridge);
    }
}
