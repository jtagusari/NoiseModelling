/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

import org.h2gis.utilities.SpatialResultSet;
import org.locationtech.jts.geom.Geometry;
import org.noise_planet.noisemodelling.jdbc.RoadEmissionBuilder;
import org.noise_planet.noisemodelling.jdbc.SourceEmissionBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.propagation.SceneWithAttenuation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import javax.xml.transform.Source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// static imports intentionally omitted; sumArray not used in this file

/**
 * Add emission information for each source in the computation scene.
 * <p>
 * This class collects and stores source emission spectra (per period) and
 * integrates emission-related processing when sources are added from a
 * database result set. The object represents input data only and is not
 * thread-safe: it must not be mutated concurrently during propagation.
 * <p>
 * Responsibilities:
 * - maintain a mapping between source primary keys and their period spectra
 * - parse different input table layouts (traffic flow, precomputed LW/DEN)
 * - create virtual bridge sources when applicable
 */
public class SceneWithEmission extends SceneWithAttenuation {
    private static final Logger LOGGER = LoggerFactory.getLogger(SceneWithEmission.class);

    // For each source primary key store a list of SourceEmission entries.
    // Each SourceEmission links a period identifier (e.g. "D", "E", "N")
    // to a spectrum (array of powers for the frequencies defined in
    // profileBuilder.getFrequencyArray()).
    private Map<Long, ArrayList<SourceEmission>> sourceEmissionsMap = new HashMap<>();

    // Settings that control how input is parsed (which fields, input mode,
    // coefficient versions, frequency field prefix, etc.). Populated from the
    // caller that prepares the scene.
    private SceneDatabaseInputSettings sceneDatabaseInputSettings = new SceneDatabaseInputSettings();

    /**
    * Construct a scene with a configured {@link ProfileBuilder} and explicit
    * {@link SceneDatabaseInputSettings}.
    *
    * @param profileBuilder provides frequency bands, bridge definitions and
    *                       other profiles needed to build source spectra
    * @param sceneDatabaseInputSettings controls how database fields are
    *                                  interpreted when reading emissions
     */
    public SceneWithEmission(ProfileBuilder profileBuilder, SceneDatabaseInputSettings sceneDatabaseInputSettings) {
        super(profileBuilder);
        this.sceneDatabaseInputSettings = sceneDatabaseInputSettings;
    }

    /**
     * Construct a scene when only a {@link ProfileBuilder} is available.
     *
     * <p>The {@link SceneDatabaseInputSettings} can be provided later via the
     * {@link #getSceneDatabaseInputSettings} accessor and modified by the
     * caller before loading sources.
     *
     * @param profileBuilder the profile builder that defines frequency bands
     */
    public SceneWithEmission(ProfileBuilder profileBuilder) {
        super(profileBuilder);
    }

    /**
     * Default no-arg constructor. Required by some frameworks/serializers.
     *
     * <p>After using this constructor callers should set a valid
     * {@link ProfileBuilder} (via mutation) before attempting to register
     * sources or compute emissions.
     */
    public SceneWithEmission() {
    }



    /**
     * Add a source to the scene using database values and register its
     * associated emission spectra where available.
     *
     * <p>This method extends {@link SceneWithAttenuation#addSourceDb(Long, Geometry, SpatialResultSet)}
     * by reading emission-related columns from the provided {@link SpatialResultSet}
     * according to the active {@link SceneDatabaseInputSettings#inputMode} and
     * storing per-period spectra in {@link #sourceEmissionsMap}.
     *
     * @param pk the primary key value read from the sources table (may be used
     *           to identify virtual/bridge sources)
     * @param geom the source geometry (can be null for some virtual sources)
     * @param rs the spatial result set positioned on the source row
     * @return a list of primary keys actually added to the scene (includes
     *         virtual sources created while processing bridges)
     * @throws SQLException forwarded when reading from the result set fails
     */
    @Override
    public void addSourceDb(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException {
        super.addSourceDb(pk, geom, rs);
        
        List<String> periods = new ArrayList<>();
        periods.add("D");
        periods.add("E");
        periods.add("N");

        List<SourceEmission> emissions;
        switch (Objects.requireNonNull(sceneDatabaseInputSettings.inputMode)) {
            case INPUT_MODE_TRAFFIC_FLOW_DEN:
               emissions = new RoadEmissionBuilder(rs)
                    .withPeriods(periods)
                    .build(profileBuilder);
                break;
            case INPUT_MODE_LW_DEN:
                emissions = new SourceEmissionBuilder(rs, profileBuilder.getFrequencyArray())
                    .withPeriods(periods)
                    .setFrequencyFieldPrepend(sceneDatabaseInputSettings.frequencyFieldPrepend)
                    .build();
                break;
            default:
                // For input modes that don't carry emission data here, do nothing.
                // This keeps the switch forward-compatible with new enum values.
                // break;
                return;
        }
        
        for (SourceEmission emission : emissions) {
            registerSourceEmission(pk, emission);
        }
    }


    /**
     * Read per-frequency emission values from an emission table row and
     * register them for the given source primary key.
     *
     * <p>The method interprets the current {@link SceneDatabaseInputSettings#inputMode}
     * to decide how to read values (traffic flow vs direct LW) and converts
     * the read dB values to Watts for internal storage.
     *
     * @param pk the source primary key to associate with the read spectra
     * @param rs a {@link ResultSet} positioned on the emission row
     * @throws SQLException if reading from the result set fails
     */
    public void registerSourceEmissionFromDb(Long pk, ResultSet rs) throws SQLException {

        List<SourceEmission> emissions;
        switch (sceneDatabaseInputSettings.inputMode) {
            case INPUT_MODE_TRAFFIC_FLOW:
                emissions = new RoadEmissionBuilder(rs)
                    .periodFromDb()
                    .build(profileBuilder);
                break;
            case INPUT_MODE_LW:
                emissions = new SourceEmissionBuilder(rs, profileBuilder.getFrequencyArray())
                    .periodFromDb()
                    .setFrequencyFieldPrepend(sceneDatabaseInputSettings.frequencyFieldPrepend)
                    .build();
                break;
            default:
                // Unknown or non-emission input modes: nothing to do.
                return;
        }
        
        for (SourceEmission emission : emissions) {
            registerSourceEmission(pk, emission);
        }
    }

    /**
     * Register a single emission spectrum for a source and period.
     *
     * @param sourcePrimaryKey source primary key
     * @param sourceEmission emission data with period and frequency spectrum
     */
    public void registerSourceEmission(Long sourcePrimaryKey,SourceEmission sourceEmission) {
        ArrayList<SourceEmission> sourceEmissions;
        if(sourceEmissionsMap.containsKey(sourcePrimaryKey)) {
            sourceEmissions = sourceEmissionsMap.get(sourcePrimaryKey);
        } else {
            sourceEmissions = new ArrayList<>();
            this.sourceEmissionsMap.put(sourcePrimaryKey, sourceEmissions);
        }
        sourceEmissions.add(sourceEmission);
        // Add period to periodSet to ensure missing periods can be filled later
        if(!sourceEmission.period.isEmpty()) {
            this.periodSet.add(sourceEmission.period);
        }
    }

    /**
     * Remove all sources and associated emission data from the scene.
     *
     * <p>This overrides {@link SceneWithAttenuation#clearSources} and also
     * clears emission-specific caches and the stored spectra map
     * ({@link #sourceEmissionsMap}).
     */
    @Override
    public void clearSources() {
        super.clearSources(); // This clears all sources including virtual sources
        sourceEmissionsMap.clear(); 
    }

    /**
     * Get the internal map of stored emission spectra.
     *
     * @return map keyed by source primary key with emission spectra per period
     */
    public Map<Long, ArrayList<SourceEmission>> getSourceEmissionsMap () {
        return this.sourceEmissionsMap;
    }

    /**
     * Get the settings that control database field interpretation.
     *
     * @return the current scene database input settings
     */
    public SceneDatabaseInputSettings getSceneDatabaseInputSettings() {
        return this.sceneDatabaseInputSettings;
    }
}
