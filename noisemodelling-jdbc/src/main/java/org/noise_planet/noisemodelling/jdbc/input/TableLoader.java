/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * Strategy interface used to prepare per-cell input scenes for propagation.
 *
 * Lifecycle:
 * 1) initialize once with a read-only context snapshot.
 * 2) createScene for each populated computation cell.
 */
public interface TableLoader {

    /**
     * Called only once when the settings are set.
     * @param connection             the database connection to be used for initialization.
    * @param context context exposing data required at loader initialization.
     * @throws SQLException if an SQL exception occurs while initializing the propagation process data factory.
     */
    void initialize(Connection connection, LoaderInitContext context) throws SQLException;

    /**
     * Called on each sub-domain in order to create cell input data.
     *
     * @param connection          Active connection
     * @param cellContext         Context exposing data required for this cell scene creation.
     * @param cellIndex           Active cell covering the computation
     * @param skipReceivers Do not process the receivers primary keys in this set and once included add the new receivers primary in it
     * @return Scene to feed the data
     */
    SceneWithEmission createScene(Connection connection, CellSceneContext cellContext, CellIndex cellIndex,
                                  Set<Long> skipReceivers) throws SQLException;
}