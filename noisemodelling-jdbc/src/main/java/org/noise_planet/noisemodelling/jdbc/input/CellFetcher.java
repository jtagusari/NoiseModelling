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
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.RowMappable;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.RowFactory;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.BridgePoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Building;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;


import java.sql.*;
import java.util.*;

import static org.h2gis.utilities.GeometryTableUtilities.getGeometryColumnNames;

/**
 *  Default implementation for initializing input propagation process data for noise map computation.
 */

public class CellFetcher{
    private Connection connection;
    private Envelope fetchEnvelope;
    private DBTypes dbType;
    private GeometryFactory geometryFactory;
    private boolean requirePk = true;
    private Map<String, RowFactory.ObjectType> tableMap;

    public CellFetcher(Connection connection, Envelope fetchEnvelope, GeometryFactory geometryFactory) throws SQLException {
        this.connection = connection;
        this.fetchEnvelope = fetchEnvelope;
        this.dbType = DBUtils.getDBType(connection.unwrap(Connection.class));
        this.geometryFactory = geometryFactory;
    }

    public CellFetcher setTableMap(Map<String, RowFactory.ObjectType> tableMap) {
        this.tableMap = tableMap;
        return this;
    }

    public CellFetcher requirePk(){
        this.requirePk = true;
        return this;
    }

    public CellFetcher noRequirePk(){
        this.requirePk = false;
        return this;
    }

    private void checkIfTableExists() throws SQLException {
        for (String tableName : tableMap.keySet()) {
            if(!JDBCUtilities.tableExists(connection, tableName)) {
                throw new SQLException("Table "+ tableName +" does not exist");
            }
        }
    }

    private String getGeomFieldName(String tableName) throws SQLException {
        List<String> geomFields = getGeometryColumnNames(connection,
                TableLocation.parse(tableName, dbType));
        if(geomFields.isEmpty()) {
            throw new SQLException("Table \""+ tableName +"\" must exist and contain a geometry field");
        }
        String geomFieldName = TableLocation.quoteIdentifier(geomFields.get(0), this.dbType);
        return geomFieldName;
    }


    public Map<String, List<RowMappable>> fetch() throws SQLException {
        checkIfTableExists();
        Map<String, List<RowMappable>> result = new HashMap<>();
        for (String tableName : this.tableMap.keySet()){
            List<RowMappable> tableData = fetchMain(tableName, tableMap.get(tableName));
            result.put(tableName, tableData);
        }
        return result;
    }

    private List<RowMappable> fetchMain(String tableName, RowFactory.ObjectType objectType) throws SQLException{
        List<RowMappable> result = new ArrayList<>();
        String geomFieldName = getGeomFieldName(tableName);
        String sqlStatement = "SELECT * FROM " +
                tableName + " WHERE " +
                geomFieldName + " && ?::geometry";
        if(this.requirePk){
            sqlStatement += " ORDER BY PK";
        }
        try (PreparedStatement st = connection.prepareStatement(sqlStatement)) {
            st.setObject(1, geometryFactory.toGeometry(fetchEnvelope));
            try (SpatialResultSet rs = st.executeQuery().unwrap(SpatialResultSet.class)) {
                while (rs.next()) {
                    RowMappable row = RowFactory.createRowMappable(objectType.name(), rs);
                    if(row != null) {
                        result.add(row);
                    }
                }
            }
        }
        return result;
    }
}
