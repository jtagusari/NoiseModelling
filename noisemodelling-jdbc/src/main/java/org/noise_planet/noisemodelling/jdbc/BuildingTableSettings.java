/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc;

/**
 * Database settings for building table and associated acoustic properties.
 */
public class BuildingTableSettings {
    /** Building geometry table name. */
    public String buildingsTableName;
    /** Building height field name (height above local ground). */
    public String heightField = "HEIGHT";
    /** Wall absorption coefficient field name. */
    public String alphaFieldName = "G";
    /** Global default wall absorption value used when no per-feature value is available. */
    public double defaultWallAbsorption = 100000;
    /**
     * If true, use Z values from building polygons.
     * In this mode, Z is interpreted as altitude (sea level to top of wall).
     */
    public boolean zBuildings = false;

    public BuildingTableSettings() {
    }


    /**
        * @return Building absorption coefficient column name.
     */

    public String getAlphaFieldName() {
        return alphaFieldName;
    }

    /**
        * @param alphaFieldName Building absorption coefficient column name.
     */

    public void setAlphaFieldName(String alphaFieldName) {
        this.alphaFieldName = alphaFieldName;
    }


    /**
        * @return {@link #buildingsTableName} field name for building height above ground.
     */
    public String getHeightField() {
        return heightField;
    }

    /**
        * @param heightField {@link #buildingsTableName} field name for building height above ground.
     */
    public void setHeightField(String heightField) {
        this.heightField = heightField;
    }
}