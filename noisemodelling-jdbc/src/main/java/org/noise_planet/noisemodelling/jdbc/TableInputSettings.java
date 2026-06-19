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
 * Names of database tables and columns that provide scene geometry and acoustic input data:
 * buildings, sound sources, receivers, terrain, ground absorption, bridges, and atmospheric settings.
 * Use {@link Builder} to construct instances.
 */
public class TableInputSettings {
    /** Building polygon table name. */
    private final String buildingTableName;
    /** Column name for building height above local ground. */
    private final String buildingHeightField;
    /** Column name for wall absorption coefficient. */
    private final String buildingAlphaField;
    /** Fallback wall absorption value used when the per-feature column is absent. */
    private final double buildingDefaultAlpha;
    /** When {@code true}, building polygon Z is absolute altitude (sea level to top of wall). */
    private final boolean buildingGeometryZ;

    /** Sound source geometry table name. */
    private final String sourceTableName;
    /** Column name prefix for source power level (e.g. {@code DB_M} → columns {@code DB_M63}, …). */
    private final String sourceLevelFieldName;
    /** When {@code true}, source Z is absolute altitude; otherwise relative to the ground surface. */
    private final boolean sourceHasAbsoluteZCoordinates;
    /** Receiver point table name. */
    private final String receiverTableName;
    /** When {@code true}, receiver Z is absolute altitude; otherwise relative to the ground surface. */
    private final boolean receiverHasAbsoluteZCoordinates;
    /** Ground-absorption polygon table name. Empty string disables ground loading. */
    private final String groundTableName;
    /** Digital elevation model point table name. Empty string disables terrain loading. */
    private final String terrainTableName;
    /** Bridge point table name. Empty string disables bridge loading. */
    private final String bridgePointsTableName;
    /** Table containing period-specific atmospheric parameters. Empty string disables atmospheric loading. */
    private final String periodAtmosphericSettingsTableName;

    public TableInputSettings(String buildingTableName, String buildingHeightField, String buildingAlphaField, double buildingDefaultAlpha, boolean buildingGeometryZ, String sourceTableName, String sourceLevelFieldName, boolean sourceHasAbsoluteZCoordinates, String receiverTableName, boolean receiverHasAbsoluteZCoordinates, String groundTableName, String terrainTableName, String bridgePointsTableName, String periodAtmosphericSettingsTableName) {
        this.buildingTableName = buildingTableName;
        this.buildingHeightField = buildingHeightField;
        this.buildingAlphaField = buildingAlphaField;
        this.buildingDefaultAlpha = buildingDefaultAlpha;
        this.buildingGeometryZ = buildingGeometryZ;
        this.sourceTableName = sourceTableName;
        this.sourceLevelFieldName = sourceLevelFieldName;
        this.sourceHasAbsoluteZCoordinates = sourceHasAbsoluteZCoordinates;
        this.receiverTableName = receiverTableName;
        this.receiverHasAbsoluteZCoordinates = receiverHasAbsoluteZCoordinates;
        this.groundTableName = groundTableName;
        this.terrainTableName = terrainTableName;
        this.bridgePointsTableName = bridgePointsTableName;
        this.periodAtmosphericSettingsTableName = periodAtmosphericSettingsTableName;
    }

    public static class Builder {
        private String buildingTableName = "";
        private String buildingHeightField = "HEIGHT";
        private String buildingAlphaField = "G";
        private double buildingDefaultAlpha = 100000;
        private boolean buildingGeometryZ = false;
        private String sourceTableName;
        private String sourceLevelFieldName = "DB_M";
        private boolean sourceHasAbsoluteZCoordinates = false;
        private String receiverTableName;
        private boolean receiverHasAbsoluteZCoordinates = false;
        private String groundTableName = "";
        private String terrainTableName = "";
        private String bridgePointsTableName = "";
        private String periodAtmosphericSettingsTableName = "";

        public Builder inheritTableInputSettings(TableInputSettings tableInputSettings){
            this.buildingTableName = tableInputSettings.getBuildingTableName();
            this.buildingHeightField = tableInputSettings.getBuildingHeightFieldName();
            this.buildingAlphaField = tableInputSettings.getBuildingAlphaField();
            this.buildingDefaultAlpha = tableInputSettings.getBuildingDefaultAlpha();
            this.buildingGeometryZ = tableInputSettings.useBuildingGeometryZ();
            this.sourceTableName = tableInputSettings.getSourceTableName();
            this.sourceLevelFieldName = tableInputSettings.getSourceLevelFieldName();
            this.sourceHasAbsoluteZCoordinates = tableInputSettings.isSourceHasAbsoluteZCoordinates();
            this.receiverTableName = tableInputSettings.getReceiverTableName();
            this.receiverHasAbsoluteZCoordinates = tableInputSettings.isReceiverHasAbsoluteZCoordinates();
            this.groundTableName = tableInputSettings.getGroundTableName();
            this.terrainTableName = tableInputSettings.getTerrainTableName();
            this.bridgePointsTableName = tableInputSettings.getBridgePointTableName();
            this.periodAtmosphericSettingsTableName = tableInputSettings.getPeriodAtmosphericSettingsTableName();
            return this;
        }

        public Builder setBuildingTableName(String buildingTableName) {
            this.buildingTableName = buildingTableName;
            return this;
        }

        public Builder setBuildingHeightFieldName(String buildingHeightField) {
            this.buildingHeightField = buildingHeightField;
            return this;
        }

        public Builder setBuildingAlphaFieldName(String buildingAlphaField) {
            this.buildingAlphaField = buildingAlphaField;
            return this;
        }

        public Builder setBuildingDefaultAlpha(double buildingDefaultAlpha) {
            this.buildingDefaultAlpha = buildingDefaultAlpha;
            return this;
        }

        public Builder setZBuildings(boolean buildingGeometryZ) {
            this.buildingGeometryZ = buildingGeometryZ;
            return this;
        }

        public Builder setSourceTableName(String sourceTableName) {
            this.sourceTableName = sourceTableName;
            return this;
        }

        public Builder setSourceLevelFieldName(String sourceLevelFieldName) {
            this.sourceLevelFieldName = sourceLevelFieldName;
            return this;
        }

        public Builder setSourceHasAbsoluteZCoordinates(boolean sourceHasAbsoluteZCoordinates) {
            this.sourceHasAbsoluteZCoordinates = sourceHasAbsoluteZCoordinates;
            return this;
        }

        public Builder setReceiverTableName(String receiverTableName) {
            this.receiverTableName = receiverTableName;
            return this;
        }

        public Builder setReceiverHasAbsoluteZCoordinates(boolean receiverHasAbsoluteZCoordinates) {
            this.receiverHasAbsoluteZCoordinates = receiverHasAbsoluteZCoordinates;
            return this;
        }

        public Builder setGroundTableName(String groundTableName) {
            this.groundTableName = groundTableName;
            return this;
        }

        public Builder setTerrainTableName(String terrainTableName) {
            this.terrainTableName = terrainTableName;
            return this;
        }

        public Builder setBridgePointsTableName(String bridgePointsTableName) {
            this.bridgePointsTableName = bridgePointsTableName;
            return this;
        }

        public Builder setPeriodAtmosphericSettingsTableName(String periodAtmosphericSettingsTableName) {
            this.periodAtmosphericSettingsTableName = periodAtmosphericSettingsTableName;
            return this;
        }

        public TableInputSettings build() {
            TableInputSettings settings = new TableInputSettings(buildingTableName, buildingHeightField, buildingAlphaField, buildingDefaultAlpha, buildingGeometryZ, sourceTableName, sourceLevelFieldName, sourceHasAbsoluteZCoordinates, receiverTableName, receiverHasAbsoluteZCoordinates, groundTableName, terrainTableName, bridgePointsTableName, periodAtmosphericSettingsTableName);
            return settings;
        }
    }


    public String getBuildingTableName() {
        return buildingTableName;
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
        * @return {@link #buildingTableName} field name for building height above ground.
     */
    public String getBuildingHeightFieldName() {
        return buildingHeightField;
    }

    public String getSourceTableName() {
        return sourceTableName;
    }

    public String getSourceLevelFieldName() {
        return sourceLevelFieldName;
    }

    public boolean isSourceHasAbsoluteZCoordinates() {
        return sourceHasAbsoluteZCoordinates;
    }

    public String getReceiverTableName() {
        return receiverTableName;
    }

    public boolean isReceiverHasAbsoluteZCoordinates() {
        return receiverHasAbsoluteZCoordinates;
    }

    public String getGroundTableName() {
        return groundTableName;
    }

    public String getTerrainTableName() {
        return terrainTableName;
    }

    public String getBridgePointTableName() {
        return bridgePointsTableName;
    }

    public String getPeriodAtmosphericSettingsTableName() {
        return periodAtmosphericSettingsTableName;
    }

}