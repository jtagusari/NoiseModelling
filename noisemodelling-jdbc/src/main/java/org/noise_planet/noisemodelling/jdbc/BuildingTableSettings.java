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
    private final String buildingsTableName;
    /** Building height field name (height above local ground). */
    private final String heightField;
    /** Wall absorption coefficient field name. */
    private final String alphaFieldName;
    /** Global default wall absorption value used when no per-feature value is available. */
    private final double defaultWallAbsorption;
    /**
     * If true, use Z values from building polygons.
     * In this mode, Z is interpreted as altitude (sea level to top of wall).
     */
    private final boolean zBuildings;

    public BuildingTableSettings(String buildingsTableName, String heightField, String alphaFieldName, double defaultWallAbsorption, boolean zBuildings) {
        this.buildingsTableName = buildingsTableName;
        this.heightField = heightField;
        this.alphaFieldName = alphaFieldName;
        this.defaultWallAbsorption = defaultWallAbsorption;
        this.zBuildings = zBuildings;
    }

    public static class Builder {
        private String buildingsTableName;
        private String heightField = "HEIGHT";
        private String alphaFieldName = "G";
        private double defaultWallAbsorption = 100000;
        private boolean zBuildings = false;

        public Builder setBuildingsTableName(String buildingsTableName) {
            this.buildingsTableName = buildingsTableName;
            return this;
        }

        public Builder setHeightField(String heightField) {
            this.heightField = heightField;
            return this;
        }

        public Builder setAlphaFieldName(String alphaFieldName) {
            this.alphaFieldName = alphaFieldName;
            return this;
        }

        public Builder setDefaultWallAbsorption(double defaultWallAbsorption) {
            this.defaultWallAbsorption = defaultWallAbsorption;
            return this;
        }

        public Builder setZBuildings(boolean zBuildings) {
            this.zBuildings = zBuildings;
            return this;
        }

        public BuildingTableSettings build() {
            BuildingTableSettings settings = new BuildingTableSettings(buildingsTableName, heightField, alphaFieldName, defaultWallAbsorption, zBuildings);
            return settings;
        }
    }


    public String getBuildingsTableName() {
        return buildingsTableName;
    }


    public boolean useBuildingGeometryZ() {
        return zBuildings;
    }

    public double getBuildingDefaultAlpha() {
        return defaultWallAbsorption;
    }

    /**
        * @return Building absorption coefficient column name.
     */

    public String getBuildingAlphaField() {
        return alphaFieldName;
    }

    /**
        * @param alphaFieldName Building absorption coefficient column name.
     */

    // public void setAlphaFieldName(String alphaFieldName) {
    //     this.alphaFieldName = alphaFieldName;
    // }


    /**
        * @return {@link #buildingsTableName} field name for building height above ground.
     */
    public String getBuildingHeightField() {
        return heightField;
    }

    /**
        * @param heightField {@link #buildingsTableName} field name for building height above ground.
     */
    // public void setHeightField(String heightField) {
    //     this.heightField = heightField;
    // }
}