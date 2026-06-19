/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

/**
 * Configuration describing how emission data must be read from database tables:
 * input mode, emission/directivity table references, and frequency field naming.
 * Use {@link Builder} to construct instances.
 */
public class EmissionInputSettings implements EmissionInputSettingsView {
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

    /** How emission data is located in the database; resolved from {@code INPUT_MODE_GUESS} at initialization. */
    INPUT_MODE inputMode = INPUT_MODE.INPUT_MODE_GUESS;
    /** Separate emission table name. Empty string means emission data is in the source geometry table. */
    String sourcesEmissionTableName = "";
    /** Primary-key column in the emission table used to join with the source geometry table. */
    String sourceEmissionPrimaryKeyField = "IDSOURCE";
    /** Source directivity table name. Empty string means omnidirectional sources. */
    String directivityTableName = "";
    /** When {@code true}, use built-in CNOSSOS railway directivity spheres instead of a custom table. */
    boolean useTrainDirectivity = false;
    /** Prefix used to identify frequency band columns (e.g. {@code HZ} → columns {@code HZ63}, {@code HZ125}, …). */
    public String frequencyFieldPrepend = "HZ";

    public EmissionInputSettings() {
    }

    public EmissionInputSettings(INPUT_MODE inputMode, String sourcesEmissionTableName) {
        this.inputMode = inputMode;
        this.sourcesEmissionTableName = sourcesEmissionTableName;
    }

    public EmissionInputSettings(EmissionInputSettings other) {
        this.inputMode = other.inputMode;
        this.sourcesEmissionTableName = other.sourcesEmissionTableName;
        this.sourceEmissionPrimaryKeyField = other.sourceEmissionPrimaryKeyField;
        this.directivityTableName = other.directivityTableName;
        this.useTrainDirectivity = other.useTrainDirectivity;
        this.frequencyFieldPrepend = other.frequencyFieldPrepend;
    }

    public EmissionInputSettings(EmissionInputSettingsView other) {
        this.inputMode = other.getInputMode();
        this.sourcesEmissionTableName = other.getSourcesEmissionTableName();
        this.sourceEmissionPrimaryKeyField = other.getSourceEmissionPrimaryKeyField();
        this.directivityTableName = other.getDirectivityTableName();
        this.useTrainDirectivity = other.isUseTrainDirectivity();
        this.frequencyFieldPrepend = other.getFrequencyFieldPrepend();
    }

    public static class Builder {
        private INPUT_MODE inputMode = INPUT_MODE.INPUT_MODE_GUESS;
        private String sourcesEmissionTableName = "";
        private String sourceEmissionPrimaryKeyField = "IDSOURCE";
        private String directivityTableName = "";
        private boolean useTrainDirectivity = false;
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

        public Builder setFrequencyFieldPrepend(String frequencyFieldPrepend) {
            this.frequencyFieldPrepend = frequencyFieldPrepend;
            return this;
        }

        public EmissionInputSettings build() {
            EmissionInputSettings settings = new EmissionInputSettings();
            settings.inputMode = this.inputMode;
            settings.sourcesEmissionTableName = this.sourcesEmissionTableName;
            settings.sourceEmissionPrimaryKeyField = this.sourceEmissionPrimaryKeyField;
            settings.directivityTableName = this.directivityTableName;
            settings.useTrainDirectivity = this.useTrainDirectivity;
            settings.frequencyFieldPrepend = this.frequencyFieldPrepend;
            return settings;
        }
    }

    public EmissionInputSettings copy() {
        return new EmissionInputSettings(this);
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

    public String getFrequencyFieldPrepend() {
        return frequencyFieldPrepend;
    }

    public void setFrequencyFieldPrepend(String frequencyFieldPrepend) {
        this.frequencyFieldPrepend = frequencyFieldPrepend;
    }
}
