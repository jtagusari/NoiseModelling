package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
public interface ResultSetConstructor<T> {
    T create(ResultSet rs) throws SQLException;
}