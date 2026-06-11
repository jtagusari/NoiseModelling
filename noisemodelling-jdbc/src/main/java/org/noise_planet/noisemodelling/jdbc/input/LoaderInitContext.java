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
 * Read-only context required once at loader initialization.
 */
public interface LoaderInitContext {
    /** @return Input-mode and emission/directivity settings used to initialize the loader. */
    SceneDatabaseInputSettingsView getSceneInputSettings();
    /** @return Emission table name used when emissions are stored separately from geometry. */
    String getSourcesEmissionTableName();
    /** @return Prefix of frequency columns (for example HZ in HZ1000). */
    String getFrequencyFieldPrepend();
    /** @return Source geometry table name. */
    String getSourcesTableName();
    /** @return Whether verbose logs are enabled. */
    boolean isVerbose();
}
