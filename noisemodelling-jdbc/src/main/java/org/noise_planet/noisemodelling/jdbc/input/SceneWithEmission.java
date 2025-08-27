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
public class SceneWithEmission extends SceneWithAttenuation 
    implements org.noise_planet.noisemodelling.pathfinder.BridgeInformationCollector.SceneSourceAccessor {
    /** Old style DEN columns traffic period  */
    // Cache of column indexes (or presence) for emission-related fields in the
    // input SQL table. Keys are field names (e.g. "SLOPE", "LV_D1000") and
    // values typically hold the column index when available. Used to avoid
    // repeated lookups on ResultSet/SpatialResultSet.
    public Map<String, Integer> sourceEmissionFieldsCache = new HashMap<>();

    // For each source primary key store a list of PeriodEmission entries.
    // Each PeriodEmission links a period identifier (e.g. "D", "E", "N")
    // to a spectrum (array of powers for the frequencies defined in
    // profileBuilder.frequencyArray).
    public Map<Long, ArrayList<PeriodEmission>> wjSources = new HashMap<>();

    // Settings that control how input is parsed (which fields, input mode,
    // coefficient versions, frequency field prefix, etc.). Populated from the
    // caller that prepares the scene.
    public SceneDatabaseInputSettings sceneDatabaseInputSettings = new SceneDatabaseInputSettings();

    /**
     * Construct with a ProfileBuilder and input settings.
     * @param profileBuilder provides frequency array, bridges and other profiles
     * @param sceneDatabaseInputSettings controls input parsing behavior
     */
    public SceneWithEmission(ProfileBuilder profileBuilder, SceneDatabaseInputSettings sceneDatabaseInputSettings) {
        super(profileBuilder);
        this.sceneDatabaseInputSettings = sceneDatabaseInputSettings;
    }

    /** Construct with only a ProfileBuilder; settings can be set later. */
    public SceneWithEmission(ProfileBuilder profileBuilder) {
        super(profileBuilder);
    }

    /** Default constructor for frameworks that require it. */
    public SceneWithEmission() {
    }

    public void processTrafficFlowDEN(Long pk, SpatialResultSet rs) throws SQLException {
        // Source table PK, GEOM, LV_D, LV_E, LV_N ...
        // Compute LW spectra for each standard period using the DEN-style
        // columns present in the SpatialResultSet (e.g. LV_D, LV_E, LV_N).
        double[][] lw = EmissionTableGenerator.computeLw(rs, sceneDatabaseInputSettings.coefficientVersion, sourceFieldNames);
        // Add a PeriodEmission for each standard period produced by computeLw
        for (EmissionTableGenerator.STANDARD_PERIOD period : EmissionTableGenerator.STANDARD_PERIOD.values()) {
            addSourceEmission(pk, EmissionTableGenerator.STANDARD_PERIOD_VALUE[period.ordinal()], lw[period.ordinal()]);
        }
    }

    public void addBridgeVirtualSourceDEN(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException {
        
        List<Long> bridgeCandidates = getSourceBridgeCandidatePk(pk);
        if (bridgeCandidates != null) {
            long newPk = 1 + sourcesPk.stream().mapToLong(Long::longValue).max().orElse(0L);
            for (int i = 0; i < bridgeCandidates.size(); i++) {
                Bridge bridge = profileBuilder.getBridgeByPk(bridgeCandidates.get(i));
                // Compute structural emissions for this bridge candidate.
                double[][] lw = BridgeStructuralEmissionCalculator.computeStructuralLw(rs, bridge, sourceFieldNames);
                // Create a new virtual source representing the bridge element
                addSource(newPk, geom, rs);
                setSourceIsVirtualSource(newPk, true);
                setSourceBridgePk(newPk, bridge.getPrimaryKey());
                // Attach computed spectra per standard period to the new virtual source
                for (EmissionTableGenerator.STANDARD_PERIOD period : EmissionTableGenerator.STANDARD_PERIOD.values()) {
                    addSourceEmission(newPk, EmissionTableGenerator.STANDARD_PERIOD_VALUE[period.ordinal()], lw[period.ordinal()]);
                }
                newPk = newPk + 1;
            }
        }
    }


    /**
     * @param pk Source primary key
     * @param rs Emission source table IDSOURCE, PERIOD, LV, HV ..
     * @throws SQLException
     */
    public void processTrafficFlow(Long pk, ResultSet rs) throws SQLException {
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
        // array of dB values corresponding to profileBuilder.frequencyArray.
        double[] lw = AcousticIndicatorsFunctions.dBToW(
            EmissionTableGenerator.getEmissionFromTrafficTable(rs, "",
            defaultSlope,
            sceneDatabaseInputSettings.coefficientVersion, sourceEmissionFieldsCache));
        addSourceEmission(pk, period, lw);
    }


    public void addBridgeVirtualSourceEmission(Long pk, SpatialResultSet rs) throws SQLException {
        // String period = rs.getString("PERIOD");
    // This method is a placeholder for creating virtual bridge sources from
    // traffic flow tables when needed. The implementation was commented
    // out in the original code. Keep the placeholder for future use.
    }

    /**
     * @param pk Source primary key
     * @param rs Emission source table IDSOURCE, PERIOD, LV, HV ..
     * @throws SQLException
     */
    public void processEmission(Long pk, ResultSet rs) throws SQLException {
        double[] lw = new double[profileBuilder.frequencyArray.size()];
        List<Integer> frequencyArray = profileBuilder.frequencyArray;
        for (int i = 0, frequencyArraySize = frequencyArray.size(); i < frequencyArraySize; i++) {
            Integer frequency = frequencyArray.get(i);
            // Directly read per-frequency dB levels from the ResultSet using
            // the configured frequency field prefix, then convert to Watts.
            lw[i] = AcousticIndicatorsFunctions.dBToW(rs.getDouble(sceneDatabaseInputSettings.frequencyFieldPrepend +frequency));
        }
        String period = rs.getString("PERIOD");
        addSourceEmission(pk, period, lw);
    }

    @Override
    public void addSource(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException {
        super.addSource(pk, geom, rs);
        switch (Objects.requireNonNull(sceneDatabaseInputSettings.inputMode)) {
            case INPUT_MODE_TRAFFIC_FLOW_DEN:
                processTrafficFlowDEN(pk, rs);
                addBridgeVirtualSourceDEN(pk, geom, rs);
                break;
            case INPUT_MODE_LW_DEN:
                processEmissionDEN(pk, rs);
                break;
            default:
                // For input modes that don't carry emission data here, do nothing.
                // This keeps the switch forward-compatible with new enum values.
                break;
        }
    }

    private void processEmissionDEN(Long pk, SpatialResultSet rs) throws SQLException {
        List<Integer> frequencyArray = profileBuilder.frequencyArray;
        for (EmissionTableGenerator.STANDARD_PERIOD period : EmissionTableGenerator.STANDARD_PERIOD.values()) {
            double[] lw = new double[profileBuilder.frequencyArray.size()];
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
                addSourceEmission(pk, periodFieldName, lw);
            }
        }
    }

    public void addSourceEmission(Long pk, ResultSet rs) throws SQLException {
        switch (sceneDatabaseInputSettings.inputMode) {
            case INPUT_MODE_TRAFFIC_FLOW:
                processTrafficFlow(pk, rs);
                break;
            case INPUT_MODE_LW:
                processEmission(pk, rs);
                break;
            default:
                // Unknown or non-emission input modes: nothing to do.
                break;
        }
    }

    /**
     * Link a source with a period and a spectrum
     * @param sourcePrimaryKey
     * @param period
     * @param wj
     */
    public void addSourceEmission(Long sourcePrimaryKey, String period, double[] wj) {
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
    public void clearSources() {
        super.clearSources(); // This clears all sources including virtual sources
        sourceEmissionFieldsCache.clear();
        wjSources.clear(); 
    }

    public static class PeriodEmission {
        public final String period;
        public final double[] emission;

        public PeriodEmission(String period, double[] emission) {
            this.period = period;
            this.emission = emission;
        }
    }
    

    // Implementation of BridgeInformationCollector.SceneSourceAccessor interface
    @Override
    public int getSourceCount() {
        return sourcesPk.size();
    }

    @Override
    public Long getSourcePk(int index) {
        return sourcesPk.get(index);
    }
}
