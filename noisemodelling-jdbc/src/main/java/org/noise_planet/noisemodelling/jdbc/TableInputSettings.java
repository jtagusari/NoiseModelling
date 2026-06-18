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
    private final String buildingTableName;
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
    private final String sourceLevelFieldName;
    private final boolean sourceHasAbsoluteZCoordinates;
    private final String receiverTableName;
    private final boolean receiverHasAbsoluteZCoordinates;
    private final String groundTableName;
    private final String terrainTableName;
    private final String bridgePointsTableName;
    private final String atmosphericSettingsTableName;


    public TableInputSettings(String buildingTableName, String buildingHeightField, String buildingAlphaField, double buildingDefaultAlpha, boolean buildingGeometryZ, String sourcesTableName, String sourceLevelFieldName, boolean sourceHasAbsoluteZCoordinates, String receiverTableName, boolean receiverHasAbsoluteZCoordinates, String groundTableName, String terrainTableName, String bridgePointsTableName, String atmosphericSettingsTableName) {
        this.buildingTableName = buildingTableName;
        this.buildingHeightField = buildingHeightField;
        this.buildingAlphaField = buildingAlphaField;
        this.buildingDefaultAlpha = buildingDefaultAlpha;
        this.buildingGeometryZ = buildingGeometryZ;
        this.sourcesTableName = sourcesTableName;
        this.sourceLevelFieldName = sourceLevelFieldName;
        this.sourceHasAbsoluteZCoordinates = sourceHasAbsoluteZCoordinates;
        this.receiverTableName = receiverTableName;
        this.receiverHasAbsoluteZCoordinates = receiverHasAbsoluteZCoordinates;
        this.groundTableName = groundTableName;
        this.terrainTableName = terrainTableName;
        this.bridgePointsTableName = bridgePointsTableName;
        this.atmosphericSettingsTableName = atmosphericSettingsTableName;
    }

    public static class Builder {
        private String buildingTableName;
        private String buildingHeightField = "HEIGHT";
        private String buildingAlphaField = "G";
        private double buildingDefaultAlpha = 100000;
        private boolean buildingGeometryZ = false;
        private String sourcesTableName;
        private String sourceLevelFieldName = "DB_M";
        private boolean sourceHasAbsoluteZCoordinates = false;
        private String receiverTableName;
        private boolean receiverHasAbsoluteZCoordinates = false;
        private String groundTableName = "";
        private String terrainTableName = "";
        private String bridgePointsTableName = "";
        private String atmosphericSettingsTableName = "";

        public Builder inheritTableInputSettings(TableInputSettings tableInputSettings){
            this.buildingTableName = tableInputSettings.getBuildingTableName();
            this.buildingHeightField = tableInputSettings.getBuildingHeightFieldName();
            this.buildingAlphaField = tableInputSettings.getBuildingAlphaField();
            this.buildingDefaultAlpha = tableInputSettings.getBuildingDefaultAlpha();
            this.buildingGeometryZ = tableInputSettings.useBuildingGeometryZ();
            this.sourcesTableName = tableInputSettings.getSourceTableName();
            this.sourceLevelFieldName = tableInputSettings.getSourceLevelFieldName();
            this.sourceHasAbsoluteZCoordinates = tableInputSettings.isSourceHasAbsoluteZCoordinates();
            this.receiverTableName = tableInputSettings.getReceiverTableName();
            this.receiverHasAbsoluteZCoordinates = tableInputSettings.isReceiverHasAbsoluteZCoordinates();
            this.groundTableName = tableInputSettings.getGroundTableName();
            this.terrainTableName = tableInputSettings.getTerrainTableName();
            this.bridgePointsTableName = tableInputSettings.getBridgePointTableName();
            this.atmosphericSettingsTableName = tableInputSettings.getAtmosphericSettingsTableName();
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

        public Builder setSourceTableName(String sourcesTableName) {
            this.sourcesTableName = sourcesTableName;
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

        public Builder setAtmosphericSettingsTableName(String atmosphericSettingsTableName) {
            this.atmosphericSettingsTableName = atmosphericSettingsTableName;
            return this;
        }

        public TableInputSettings build() {
            TableInputSettings settings = new TableInputSettings(buildingTableName, buildingHeightField, buildingAlphaField, buildingDefaultAlpha, buildingGeometryZ, sourcesTableName, sourceLevelFieldName, sourceHasAbsoluteZCoordinates, receiverTableName, receiverHasAbsoluteZCoordinates, groundTableName, terrainTableName, bridgePointsTableName, atmosphericSettingsTableName);
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
        return sourcesTableName;
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

    public String getAtmosphericSettingsTableName() {
        return atmosphericSettingsTableName;
    }
}