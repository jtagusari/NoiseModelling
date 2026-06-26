/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.noise_planet.noisemodelling.jdbc.TableInputSettings;
import org.noise_planet.noisemodelling.jdbc.ComputationSettings;
import org.noise_planet.noisemodelling.jdbc.utils.CellIndex;

/**
 * Read-only context required when creating a scene for a computation cell.
 */
public interface CellSceneContext {
    /** @return Geometry factory configured with the computation SRID. */
    GeometryFactory getGeometryFactory();
    /** @return Envelope of a computation cell. */
    Envelope getCellEnv(CellIndex cellIndex);
    /** @return Maximum source-receiver propagation distance. */
    double getMaximumPropagationDistance();
    /** @return Maximum wall/corner search distance for reflections/diffractions. */
    double getMaximumReflectionDistance();
    /** @return Building table and associated field settings. */
    TableInputSettings getTableInputSettings();
    ComputationSettings getComputationSettings();
    /** @return Maximum reflection order to compute. */
    int getSoundReflectionOrder();
    /** @return Whether body-barrier mode is enabled. */
    boolean isBodyBarrier();
    /** @return Whether vertical diffraction is enabled. */
    boolean isComputeVerticalDiffraction();
    /** @return Whether horizontal diffraction is enabled. */
    boolean isComputeHorizontalDiffraction();
    /** @return DEM table name. */
    String getTerrainTableName();
    /** @return Soil table name. */
    String getGroundTableName();
    /** @return Bridge points table name. */
    String getBridgePointTableName();
    /** @return Receiver table name. */
    String getReceiverTableName();
    /** @return Source geometry table name. */
    String getSourceTableName();
    /** @return Input-mode and emission/directivity settings used to build scenes. */
    EmissionInputSettings getEmissionInputSettings();
}
