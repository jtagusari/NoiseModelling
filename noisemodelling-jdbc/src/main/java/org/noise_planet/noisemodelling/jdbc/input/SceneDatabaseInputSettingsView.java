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
 * Read-only view of scene/emission input settings.
 */
public interface SceneDatabaseInputSettingsView {
    SceneDatabaseInputSettings.INPUT_MODE getInputMode();
    String getSourcesEmissionTableName();
    String getSourceEmissionPrimaryKeyField();
    String getDirectivityTableName();
    boolean isUseTrainDirectivity();
    String getPeriodAtmosphericSettingsTableName();
    int getCoefficientVersion();
    String getFrequencyFieldPrepend();
}