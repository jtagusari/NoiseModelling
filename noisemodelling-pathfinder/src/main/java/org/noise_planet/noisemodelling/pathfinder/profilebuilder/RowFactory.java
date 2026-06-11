package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.HashMap;

public class RowFactory {
    public enum ObjectType {
        BridgePoint
    }

    private static final Map<String, ResultSetConstructor<? extends RowMappable>> MAP =
        Map.of(
            "BridgePoint", BridgePoint::new
        );

    public static RowMappable create(String type, ResultSet rs) throws SQLException {
        ResultSetConstructor<? extends RowMappable> ctor = MAP.get(type);
        if (ctor == null) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
        return ctor.create(rs);
    }
}