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
public class TableInputSettings {
    /** Building geometry table name. */
    private final String buildingsTableName;
    /** Building height field name (height above local ground). */
    private final String buildingHeightField;
    /** Wall absorption coefficient field name. */
    private final String buildingAlphaField;
    /** Global default wall absorption value used when no per-feature value is available. */
    private final double buildingDefaultAlpha;
    /**
     * If true, use Z values from building polygons.
     * In this mode, Z is interpreted as altitude (sea level to top of wall).
     */
    private final boolean buildingGeometryZ;

    private final String sourcesTableName;
    private final String receiverTableName;
    private final String soilTableName;
    private final String demTableName;
    private final String bridgePointsTableName;


    public TableInputSettings(String buildingsTableName, String buildingHeightField, String buildingAlphaField, double buildingDefaultAlpha, boolean buildingGeometryZ, String sourcesTableName, String receiverTableName, String soilTableName, String demTableName, String bridgePointsTableName, String atmosphericSettingsTableName) {
        this.buildingsTableName = buildingsTableName;
        this.buildingHeightField = buildingHeightField;
        this.buildingAlphaField = buildingAlphaField;
        this.buildingDefaultAlpha = buildingDefaultAlpha;
        this.buildingGeometryZ = buildingGeometryZ;
        this.sourcesTableName = sourcesTableName;
        this.receiverTableName = receiverTableName;
        this.soilTableName = soilTableName;
        this.demTableName = demTableName;
        this.bridgePointsTableName = bridgePointsTableName;
    }

    public static class Builder {
        private String buildingsTableName;
        private String buildingHeightField = "HEIGHT";
        private String buildingAlphaField = "G";
        private double buildingDefaultAlpha = 100000;
        private boolean buildingGeometryZ = false;
        private String sourcesTableName;
        private String receiverTableName;
        private String soilTableName = "";
        private String demTableName = "";
        private String bridgePointsTableName = "";
        private String atmosphericSettingsTableName = "";

        public Builder setBuildingsTableName(String buildingsTableName) {
            this.buildingsTableName = buildingsTableName;
            return this;
        }

        public Builder setHeightField(String buildingHeightField) {
            this.buildingHeightField = buildingHeightField;
            return this;
        }

        public Builder setAlphaFieldName(String buildingAlphaField) {
            this.buildingAlphaField = buildingAlphaField;
            return this;
        }

        public Builder setDefaultWallAbsorption(double buildingDefaultAlpha) {
            this.buildingDefaultAlpha = buildingDefaultAlpha;
            return this;
        }

        public Builder setZBuildings(boolean buildingGeometryZ) {
            this.buildingGeometryZ = buildingGeometryZ;
            return this;
        }

        public Builder setSourcesTableName(String sourcesTableName) {
            this.sourcesTableName = sourcesTableName;
            return this;
        }

        public Builder setReceiverTableName(String receiverTableName) {
            this.receiverTableName = receiverTableName;
            return this;
        }

        public Builder setSoilTableName(String soilTableName) {
            this.soilTableName = soilTableName;
            return this;
        }

        public Builder setDemTableName(String demTableName) {
            this.demTableName = demTableName;
            return this;
        }

        public Builder setBridgePointsTableName(String bridgePointsTableName) {
            this.bridgePointsTableName = bridgePointsTableName;
            return this;
        }

        public Builder setAtmosphericSettingsTableName(String atmosphericSettingsTableName) {
            this.atmosphericSettingsTableName = atmosphericSettingsTableName;
            return this;
        }

        public TableInputSettings build() {
            TableInputSettings settings = new TableInputSettings(buildingsTableName, buildingHeightField, buildingAlphaField, buildingDefaultAlpha, buildingGeometryZ, sourcesTableName, receiverTableName, soilTableName, demTableName, bridgePointsTableName, atmosphericSettingsTableName);
            return settings;
        }
    }


    public String getBuildingsTableName() {
        return buildingsTableName;
    }


    public boolean useBuildingGeometryZ() {
        return buildingGeometryZ;
    }

    public double getBuildingDefaultAlpha() {
        return buildingDefaultAlpha;
    }

    /**
        * @return Building absorption coefficient column name.
     */

    public String getBuildingAlphaField() {
        return buildingAlphaField;
    }
    
    /**
        * @return {@link #buildingsTableName} field name for building height above ground.
     */
    public String getBuildingHeightField() {
        return buildingHeightField;
    }

    public String getSourcesTableName() {
        return sourcesTableName;
    }

    public String getReceiverTableName() {
        return receiverTableName;
    }

    public String getSoilTableName() {
        return soilTableName;
    }

    public String getDemTableName() {
        return demTableName;
    }

    public String getBridgePointsTableName() {
        return bridgePointsTableName;
    }
}