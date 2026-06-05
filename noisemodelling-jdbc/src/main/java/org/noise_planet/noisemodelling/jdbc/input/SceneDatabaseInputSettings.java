/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

import org.h2gis.api.ProgressVisitor;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Map;

/**
 * Configuration describing how scene/emission data must be read from database tables.
 */
public class SceneDatabaseInputSettings implements SceneDatabaseInputSettingsView {
    public enum INPUT_MODE {
        /** Auto-detect the input mode from available source/emission columns. */
        INPUT_MODE_GUESS,
        /** Read DEN traffic-flow fields directly from the source geometry table. */
        INPUT_MODE_TRAFFIC_FLOW_DEN,
        /** Read DEN emission levels directly from the source geometry table. */
        INPUT_MODE_LW_DEN,
        /** Read per-period traffic-flow fields from the emission table. */
        INPUT_MODE_TRAFFIC_FLOW,
        /** Read per-period emission levels from the emission table. */
        INPUT_MODE_LW,
        /** Compute attenuation only (no source emission lookup). */
        INPUT_MODE_ATTENUATION }

    INPUT_MODE inputMode = INPUT_MODE.INPUT_MODE_GUESS;
    String sourcesEmissionTableName = "";
    String sourceEmissionPrimaryKeyField = "IDSOURCE";

    String directivityTableName = "";
    boolean useTrainDirectivity = false;

    /**
     * Read {@link org.noise_planet.noisemodelling.propagation.AttenuationParameters} values from this table
     */
    String periodAtmosphericSettingsTableName = "";
    /** Cnossos coefficient version (1 = 2015, 2 = 2020). */
    int coefficientVersion = 2;
    public String frequencyFieldPrepend = "HZ";

    public SceneDatabaseInputSettings() {

    }

    public SceneDatabaseInputSettings(INPUT_MODE inputMode, String sourcesEmissionTableName) {
        this.inputMode = inputMode;
        this.sourcesEmissionTableName = sourcesEmissionTableName;
    }

    public SceneDatabaseInputSettings(SceneDatabaseInputSettings other) {
        this.inputMode = other.inputMode;
        this.sourcesEmissionTableName = other.sourcesEmissionTableName;
        this.sourceEmissionPrimaryKeyField = other.sourceEmissionPrimaryKeyField;
        this.directivityTableName = other.directivityTableName;
        this.useTrainDirectivity = other.useTrainDirectivity;
        this.periodAtmosphericSettingsTableName = other.periodAtmosphericSettingsTableName;
        this.coefficientVersion = other.coefficientVersion;
        this.frequencyFieldPrepend = other.frequencyFieldPrepend;
    }

    public SceneDatabaseInputSettings(SceneDatabaseInputSettingsView other) {
        this.inputMode = other.getInputMode();
        this.sourcesEmissionTableName = other.getSourcesEmissionTableName();
        this.sourceEmissionPrimaryKeyField = other.getSourceEmissionPrimaryKeyField();
        this.directivityTableName = other.getDirectivityTableName();
        this.useTrainDirectivity = other.isUseTrainDirectivity();
        this.periodAtmosphericSettingsTableName = other.getPeriodAtmosphericSettingsTableName();
        this.coefficientVersion = other.getCoefficientVersion();
        this.frequencyFieldPrepend = other.getFrequencyFieldPrepend();
    }

    public static class Builder {
        private INPUT_MODE inputMode = INPUT_MODE.INPUT_MODE_GUESS;
        private String sourcesEmissionTableName = "";
        private String sourceEmissionPrimaryKeyField = "IDSOURCE";
        private String directivityTableName = "";
        private boolean useTrainDirectivity = false;
        private String periodAtmosphericSettingsTableName = "";
        private int coefficientVersion = 2;
        private String frequencyFieldPrepend = "HZ";

        public Builder setInputMode(INPUT_MODE inputMode) {
            this.inputMode = inputMode;
            return this;
        }

        public Builder setSourcesEmissionTableName(String sourcesEmissionTableName) {
            this.sourcesEmissionTableName = sourcesEmissionTableName;
            return this;
        }

        public Builder setSourceEmissionPrimaryKeyField(String sourceEmissionPrimaryKeyField) {
            this.sourceEmissionPrimaryKeyField = sourceEmissionPrimaryKeyField;
            return this;
        }
        public Builder setDirectivityTableName(String directivityTableName) {
            this.directivityTableName = directivityTableName;
            return this;
        }
        public Builder setUseTrainDirectivity(boolean useTrainDirectivity) {
            this.useTrainDirectivity = useTrainDirectivity;
            return this;
        }
        public Builder setPeriodAtmosphericSettingsTableName(String periodAtmosphericSettingsTableName) {
            this.periodAtmosphericSettingsTableName = periodAtmosphericSettingsTableName;
            return this;
        }
        public Builder setCoefficientVersion(int coefficientVersion) {
            this.coefficientVersion = coefficientVersion;
            return this;
        }

        public Builder setFrequencyFieldPrepend(String frequencyFieldPrepend) {
            this.frequencyFieldPrepend = frequencyFieldPrepend;
            return this;
        }

        public SceneDatabaseInputSettings build() {
            SceneDatabaseInputSettings settings = new SceneDatabaseInputSettings();
            settings.inputMode = this.inputMode;
            settings.sourcesEmissionTableName = this.sourcesEmissionTableName;
            settings.sourceEmissionPrimaryKeyField = this.sourceEmissionPrimaryKeyField;
            settings.directivityTableName = this.directivityTableName;
            settings.useTrainDirectivity = this.useTrainDirectivity;
            settings.periodAtmosphericSettingsTableName = this.periodAtmosphericSettingsTableName;
            settings.coefficientVersion = this.coefficientVersion;
            settings.frequencyFieldPrepend = this.frequencyFieldPrepend;
            return settings;
        }

    }

    public SceneDatabaseInputSettings copy() {
        return new SceneDatabaseInputSettings(this);
    }

    public String getDirectivityTableName() {
        return directivityTableName;
    }

    public void setDirectivityTableName(String directivityTableName) {
        this.directivityTableName = directivityTableName;
    }

    public boolean isUseTrainDirectivity() {
        return useTrainDirectivity;
    }

    public void setUseTrainDirectivity(boolean useTrainDirectivity) {
        this.useTrainDirectivity = useTrainDirectivity;
    }

    public int getCoefficientVersion() {
        return coefficientVersion;
    }

    public SceneDatabaseInputSettings setCoefficientVersion(int coefficientVersion) {
        this.coefficientVersion = coefficientVersion;
        return this;
    }

    public INPUT_MODE getInputMode() {
        return inputMode;
    }

    public void setInputMode(INPUT_MODE inputMode) {
        this.inputMode = inputMode;
    }

    public void setInputMode(String inputMode) {
        this.inputMode = INPUT_MODE.valueOf(inputMode);
    }

    public String getSourcesEmissionTableName() {
        return sourcesEmissionTableName;
    }

    public void setSourcesEmissionTableName(String sourcesEmissionTableName) {
        this.sourcesEmissionTableName = sourcesEmissionTableName;
    }

    public String getSourceEmissionPrimaryKeyField() {
        return sourceEmissionPrimaryKeyField;
    }

    public void setSourceEmissionPrimaryKeyField(String sourceEmissionPrimaryKeyField) {
        this.sourceEmissionPrimaryKeyField = sourceEmissionPrimaryKeyField;
    }

    /**
     * Gets the name of the table that contains the period-specific atmospheric settings.
     *
     * @return The table name storing the period atmospheric settings used for computations.
     */
    public String getPeriodAtmosphericSettingsTableName() {
        return periodAtmosphericSettingsTableName;
    }

    /**
     * Sets the name of the table that contains the period-specific atmospheric settings.
     *
     * @param periodAtmosphericSettingsTableName The table name storing the period atmospheric settings
     *                                           to be used for computations.
     *                                           See {@link org.noise_planet.noisemodelling.propagation.AttenuationParameters#readFromDatabase(ResultSet, Map)}
     */
    public void setPeriodAtmosphericSettingsTableName(String periodAtmosphericSettingsTableName) {
        this.periodAtmosphericSettingsTableName = periodAtmosphericSettingsTableName;
    }

    public String getFrequencyFieldPrepend() {
        return frequencyFieldPrepend;
    }

    public void setFrequencyFieldPrepend(String frequencyFieldPrepend) {
        this.frequencyFieldPrepend = frequencyFieldPrepend;
    }
}