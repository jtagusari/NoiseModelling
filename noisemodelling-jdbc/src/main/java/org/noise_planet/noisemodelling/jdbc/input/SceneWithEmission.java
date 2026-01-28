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
import org.noise_planet.noisemodelling.jdbc.BridgeStructuralEmissionCalculator;
import org.noise_planet.noisemodelling.jdbc.EmissionTableGenerator;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;
import org.noise_planet.noisemodelling.propagation.SceneWithAttenuation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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
    /** Old style DEN columns traffic period  */
    // Cache of column indexes (or presence) for emission-related fields in the
    // input SQL table. Keys are field names (e.g. "SLOPE", "LV_D1000") and
    // values typically hold the column index when available. Used to avoid
    // repeated lookups on ResultSet/SpatialResultSet.
    private Map<String, Integer> sourceEmissionFieldsCache = new HashMap<>();

    // For each source primary key store a list of PeriodEmission entries.
    // Each PeriodEmission links a period identifier (e.g. "D", "E", "N")
    // to a spectrum (array of powers for the frequencies defined in
    // profileBuilder.getFrequencyArray()).
    private Map<Long, ArrayList<PeriodEmission>> wjSources = new HashMap<>();

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

    private void registerDENValuesUsingTrafficFlow(long pk, SpatialResultSet rs) throws SQLException {
        // Source table PK, GEOM, LV_D, LV_E, LV_N ...
        // Compute LW spectra for each standard period using the DEN-style
        // columns present in the SpatialResultSet (e.g. LV_D, LV_E, LV_N).
        double[][] lw = EmissionTableGenerator.computeLw(rs, sceneDatabaseInputSettings.coefficientVersion, sourceFieldNames);
        // Add a PeriodEmission for each standard period produced by computeLw
        for (EmissionTableGenerator.STANDARD_PERIOD period : EmissionTableGenerator.STANDARD_PERIOD.values()) {
            registerSourceEmission(pk, EmissionTableGenerator.STANDARD_PERIOD_VALUE[period.ordinal()], lw[period.ordinal()]);
        }
    }

    private void registerBridgeStructuralDENValues(Long pk, SpatialResultSet rs, Bridge bridge) throws SQLException {
        
        double[][] lw = BridgeStructuralEmissionCalculator.computeStructuralLw(rs, bridge, sourceFieldNames);
        // Attach computed spectra per standard period to the new virtual source
        for (EmissionTableGenerator.STANDARD_PERIOD period : EmissionTableGenerator.STANDARD_PERIOD.values()) {
            registerSourceEmission(pk, EmissionTableGenerator.STANDARD_PERIOD_VALUE[period.ordinal()], lw[period.ordinal()]);
        }
    }


    /**
     * @param pk Source primary key
     * @param rs Emission source table IDSOURCE, PERIOD, LV, HV ..
     * @throws SQLException
     */
    private void registerPeriodValueUsingTrafficFlow(Long pk, ResultSet rs) throws SQLException {
        String period = rs.getString("PERIOD");
        // Use geometry as default slope (if field slope is not provided
        double defaultSlope = 0;
        if(!sourceEmissionFieldsCache.containsKey("SLOPE")) {
            int sourceIndex = sourcesPk.indexOf(pk);
            if(sourceIndex >= 0) {
                defaultSlope = EmissionTableGenerator.getSlope(sourceGeometries.get(sourceIndex));
            }
        }
        // Read traffic table values and convert from dB to W for the frequency
        // bands. EmissionTableGenerator.getEmissionFromTrafficTable returns an
        // array of dB values corresponding to profileBuilder.getFrequencyArray().
        double[] lw = AcousticIndicatorsFunctions.dBToW(
            EmissionTableGenerator.getEmissionFromTrafficTable(rs, "",
            defaultSlope,
            sceneDatabaseInputSettings.coefficientVersion, sourceEmissionFieldsCache));
        registerSourceEmission(pk, period, lw);
    }


    private void registerBridgeStructuralPeriodValues(Long pk, ResultSet rs, Bridge bridge) throws SQLException {
        String period = rs.getString("PERIOD");
        // Read traffic table values and convert from dB to W for the frequency
        // bands. EmissionTableGenerator.getEmissionFromTrafficTable returns an
        // array of dB values corresponding to profileBuilder.getFrequencyArray().
        double[] lw = AcousticIndicatorsFunctions.dBToW(
            BridgeStructuralEmissionCalculator.getStructuralEmissionFromTrafficTable(rs, "", bridge,sourceEmissionFieldsCache));
        registerSourceEmission(pk, period, lw);
    }

    /**
     * @param pk Source primary key
     * @param rs Emission source table IDSOURCE, PERIOD, LV, HV ..
     * @throws SQLException
     */
    public void registerPeriodValueUsingEmission(Long pk, ResultSet rs) throws SQLException {
        double[] lw = new double[profileBuilder.getFrequencyArray().size()];
        List<Integer> frequencyArray = profileBuilder.getFrequencyArray();
        for (int i = 0, frequencyArraySize = frequencyArray.size(); i < frequencyArraySize; i++) {
            Integer frequency = frequencyArray.get(i);
            // Directly read per-frequency dB levels from the ResultSet using
            // the configured frequency field prefix, then convert to Watts.
            lw[i] = AcousticIndicatorsFunctions.dBToW(rs.getDouble(sceneDatabaseInputSettings.frequencyFieldPrepend +frequency));
        }
        String period = rs.getString("PERIOD");
        registerSourceEmission(pk, period, lw);
    }


    /**
     * Add a source to the scene using database values and register its
     * associated emission spectra where available.
     *
     * <p>This method extends {@link SceneWithAttenuation#addSourceDb(Long, Geometry, SpatialResultSet)}
     * by reading emission-related columns from the provided {@link SpatialResultSet}
     * according to the active {@link SceneDatabaseInputSettings#inputMode} and
     * storing per-period spectra in {@link #wjSources}.
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
        
        SourceBridgeProperty sourceBridgeProperty = super.getSourceBridgePropertyByPk(pk);
        // If sourceBridgeProperty is null, create a default instance (source not related to bridge)
        if (sourceBridgeProperty == null) {
            sourceBridgeProperty = new SourceBridgeProperty();
        }
        switch (Objects.requireNonNull(sceneDatabaseInputSettings.inputMode)) {
            case INPUT_MODE_TRAFFIC_FLOW_DEN:
                if (sourceBridgeProperty.getSourceType() == SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE) {
                    Bridge bridge = profileBuilder.getBridgeByPk(sourceBridgeProperty.getBridgePkOn());
                    registerBridgeStructuralDENValues(pk, rs, bridge);
                } else {
                    registerDENValuesUsingTrafficFlow(pk, rs);
                }
                break;
            case INPUT_MODE_LW_DEN:
                registerDENValuesUsingEmission(pk, rs);
                break;
            default:
                // For input modes that don't carry emission data here, do nothing.
                // This keeps the switch forward-compatible with new enum values.
                break;
        }
        return;
    }

    private void registerDENValuesUsingEmission(Long pk, SpatialResultSet rs) throws SQLException {
        List<Integer> frequencyArray = profileBuilder.getFrequencyArray();
        for (EmissionTableGenerator.STANDARD_PERIOD period : EmissionTableGenerator.STANDARD_PERIOD.values()) {
            double[] lw = new double[profileBuilder.getFrequencyArray().size()];
            boolean missingField = false;
            String periodFieldName = EmissionTableGenerator.STANDARD_PERIOD_VALUE[period.ordinal()];
            for (int i = 0, frequencyArraySize = frequencyArray.size(); i < frequencyArraySize; i++) {
                Integer frequency = frequencyArray.get(i);
                final String tableFieldName = sceneDatabaseInputSettings.frequencyFieldPrepend + periodFieldName + frequency;
                if(sourceFieldNames.containsKey(tableFieldName)) {
                    // If the table has the expected column, read dB and convert
                    // to W for the frequency band.
                    lw[i] = AcousticIndicatorsFunctions.dBToW(
                            rs.getDouble(tableFieldName));
                } else {
                    missingField = true;
                    break;
                }
            }
            if(!missingField) {
                registerSourceEmission(pk, periodFieldName, lw);
            }
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
    public void registerSourceEmission(Long pk, ResultSet rs) throws SQLException {
        SourceBridgeProperty sourceBridgeProperty = super.getSourceBridgePropertyByPk(pk);
        // If sourceBridgeProperty is null, create a default instance (source not related to bridge)
        if (sourceBridgeProperty == null) {
            sourceBridgeProperty = new SourceBridgeProperty();
        }
        switch (sceneDatabaseInputSettings.inputMode) {
            case INPUT_MODE_TRAFFIC_FLOW:
                if (sourceBridgeProperty.getSourceType() == SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE) {
                    Bridge bridge = profileBuilder.getBridgeByPk(sourceBridgeProperty.getBridgePkOn());
                    registerBridgeStructuralPeriodValues(pk, rs, bridge);
                } else {
                    registerPeriodValueUsingTrafficFlow(pk, rs);
                }
                break;
            case INPUT_MODE_LW:
                registerPeriodValueUsingEmission(pk, rs);
                break;
            default:
                // Unknown or non-emission input modes: nothing to do.
                break;
        }
    }

    /**
     * Register a precomputed emission spectrum for a source for a given period.
     *
     * <p>The provided spectrum array must match the frequency band ordering
     * defined by the {@link ProfileBuilder} used to construct the scene.
     * The method stores the spectrum (in Watts) and records the period in the
     * internal period set when the period string is not empty.
     *
     * @param sourcePrimaryKey the primary key identifying the source
     * @param period a period identifier (for example "D", "E", "N" or an
     *               empty string for period-less spectra)
     * @param wj spectrum of emitted powers (Watts) per frequency band
     */
    public void registerSourceEmission(Long sourcePrimaryKey, String period, double[] wj) {
        ArrayList<PeriodEmission> sourceEmissions;
        if(wjSources.containsKey(sourcePrimaryKey)) {
            sourceEmissions = wjSources.get(sourcePrimaryKey);
        } else {
            sourceEmissions = new ArrayList<>();
            wjSources.put(sourcePrimaryKey, sourceEmissions);
        }
        sourceEmissions.add(new PeriodEmission(period, wj));
        if(!period.isEmpty()) {
            periodSet.add(period);
        }
    }

    @Override
    /**
     * Remove all sources and associated emission data from the scene.
     *
     * <p>This overrides {@link SceneWithAttenuation#clearSources} and also
     * clears emission-specific caches and the stored spectra map
     * ({@link #wjSources}).
     */
    public void clearSources() {
        super.clearSources(); // This clears all sources including virtual sources
        sourceEmissionFieldsCache.clear();
        wjSources.clear(); 
    }

    public Map<Long, ArrayList<PeriodEmission>> getWjSources () {
        /**
         * Get the internal map of stored emission spectra.
         *
         * <p>Return value is the live internal map used by the scene. Callers
         * should treat it as read-only to avoid corrupting internal state.
         *
         * @return map keyed by source primary key with a list of
         *         {@link PeriodEmission} entries for each registered period
         */
        return this.wjSources;
    }

    public SceneDatabaseInputSettings getSceneDatabaseInputSettings() {
        /**
         * Access the {@link SceneDatabaseInputSettings} that controls how
         * database fields are interpreted when reading emission data.
         *
         * @return the settings instance used by this scene
         */
        return this.sceneDatabaseInputSettings;
    }

    public static class PeriodEmission {
        /**
         * Represents the emitted spectrum for a single period.
         *
         * <p>The {@code emission} array contains power values (Watts) for
         * the frequency bands defined by the {@link ProfileBuilder} used to
         * build the scene. The {@code period} field is a short identifier
         * such as "D", "E" or "N" and may be empty when the spectrum is
         * not period-specific.
         */
        public final String period;
        /** Power spectrum (Watts) per frequency band in the profile builder. */
        public final double[] emission;

        /**
         * Create a new PeriodEmission.
         *
         * @param period the period identifier (may be empty)
         * @param emission the spectrum array (Watts) matching profile frequencies
         */
        public PeriodEmission(String period, double[] emission) {
            this.period = period;
            this.emission = emission;
        }
    }
    
}
