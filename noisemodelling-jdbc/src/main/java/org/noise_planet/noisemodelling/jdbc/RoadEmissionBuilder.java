package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.utilities.SpatialResultSet;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.noise_planet.noisemodelling.emission.road.asj.RoadAsj;
import org.noise_planet.noisemodelling.emission.road.asj.RoadAsjParameters;
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossos;
import org.noise_planet.noisemodelling.emission.road.cnossos.RoadCnossosParameters;
import org.noise_planet.noisemodelling.emission.utils.Utils;
import org.noise_planet.noisemodelling.jdbc.input.SourceEmission;
import org.noise_planet.noisemodelling.jdbc.input.SourceEmission.EmissionType;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.dBToW;

/**
 * Builder class for creating road emission spectra from traffic or pre-computed emission data.
 * <p>
 * This builder supports two input modes:
 * <ul>
 *   <li>Traffic-based: computes emissions from vehicle counts and speeds using CNOSSOS or ASJ models</li>
 *   <li>Emission-based: reads pre-computed sound power levels directly from database</li>
 * </ul>
 * <p>
 * The builder can process multiple time periods (e.g., Day, Evening, Night) and different
 * emission types (road surface, bridge structural noise).
 */
public class RoadEmissionBuilder extends SourceEmissionBuilder {
    private static final List<Integer> frequencyBands = buildFrequencyBands();
    private static Logger LOGGER = LoggerFactory.getLogger(RoadEmissionBuilder.class);

    private static final String periodSep = "_";
    private int coefficientVersion = 2;
    private static final String pkStr = "PK";
    private static final String sourceEmissionPrimaryKeyField = "IDSOURCE";
    private static final String bridgePkStr = "BRIDGE_PK";
    private final long pk;
    private boolean trafficAsInput = true;

    /**
     * Constructs a RoadEmissionBuilder from a database ResultSet.
     * <p>
     * Automatically caches all column names and positions for efficient field access.
     * Requires a 'PK' column to identify the source.
     *
     * @param rs the ResultSet positioned at the row to process
     * @throws SQLException if the ResultSet is invalid or missing required 'PK' field
     */
    public RoadEmissionBuilder(ResultSet rs) throws SQLException{        
        super(rs, frequencyBands);
        if(!sourceFieldsCache.containsKey(pkStr) && !sourceFieldsCache.containsKey(sourceEmissionPrimaryKeyField)) {
            throw new SQLException("Field " + pkStr + " or " + sourceEmissionPrimaryKeyField + " is required in ResultSet.");
        }
        this.pk = getLong(pkStr, -1);
    }

    /**
     * Configures the builder to compute emissions from traffic flow data.
     * <p>
     * Traffic data includes vehicle counts, speeds, road surface type, etc.
     * Emissions are computed using CNOSSOS-EU or ASJ models.
     *
     * @return this builder instance for method chaining
     */
    public RoadEmissionBuilder trafficAsInput(){
        this.trafficAsInput = true;
        return this;
    }

    /**
     * Configures the builder to read pre-computed emission values from database.
     * <p>
     * Expects frequency-specific sound power level columns (e.g., HZ63, HZ125, etc.)
     * in the input table.
     *
     * @return this builder instance for method chaining
     */
    public RoadEmissionBuilder emissionAsInput(){
        this.trafficAsInput = false;
        return this;
    }

    @Override
    public RoadEmissionBuilder periodFromDb(){
        super.periodFromDb();
        return this;
    }

    /**
     * Sets the column name prefix for frequency-specific emission fields.
     * <p>
     * For example, with prefix "HZ", expects columns: HZ63, HZ125, HZ250, etc.
     * With prefix "LW", expects: LW63, LW125, LW250, etc.
     *
     * @param frequencyFieldPrepend the prefix string for frequency columns
     * @return this builder instance for method chaining
     */
    @Override
    public RoadEmissionBuilder setFrequencyFieldPrepend(String frequencyFieldPrepend){
        super.setFrequencyFieldPrepend(frequencyFieldPrepend);
        return this;
    }

    /**
     * Sets the list of time periods to process (e.g., ["D", "E", "N"]).
     * <p>
     * Overrides any previously configured periods and disables period reading from database.
     *
     * @param periods list of period identifiers
     * @return this builder instance for method chaining
     */
    @Override
    public RoadEmissionBuilder withPeriods(List<String> periods){
        super.withPeriods(periods);
        return this;
    }

    /**
     * Adds a single time period to the list of periods to process.
     * <p>
     * Can be called multiple times to build a custom period list.
     *
     * @param period the period identifier to add (e.g., "D", "E", "N")
     * @return this builder instance for method chaining
     */
    @Override
    public RoadEmissionBuilder addPeriod(String period){
        super.addPeriod(period);
        return this;
    }

    /**
     * Sets the CNOSSOS-EU coefficient version for emission calculations.
     * <p>
     * Different versions may have updated emission coefficients.
     *
     * @param coefficientVersion the coefficient version number (e.g., 2)
     * @return this builder instance for method chaining
     */
    public RoadEmissionBuilder withCoefficientVersion(int coefficientVersion){
        this.coefficientVersion = coefficientVersion;
        return this;
    }

    /**
     * Determines emission types by reading EMISSION_TYPE and BRIDGE_PK fields.
     * <p>
     * Returns a list of emission types to process:
     * <ul>
     *   <li>If EMISSION_TYPE exists: uses that value (validates BRIDGE_PK if type is BRIDGE)</li>
     *   <li>If EMISSION_TYPE missing: defaults to ROAD, adds BRIDGE if BRIDGE_PK exists</li>
     * </ul>
     *
     * @return list of emission types to compute
     * @throws SQLException if BRIDGE_PK is null when EMISSION_TYPE is BRIDGE
     */
    private List<SourceEmission.EmissionType> getEmissionTypeWithBridgePkField() throws SQLException {
        List<SourceEmission.EmissionType> emissionTypes = new ArrayList<>();

        if(sourceFieldsCache.containsKey(emissionTypeStr)) {
            emissionTypes.add(SourceEmission.EmissionType.fromString(rs.getString(sourceFieldsCache.get(emissionTypeStr))));
            
            if(emissionTypes.get(0) == EmissionType.BRIDGE){
                rs.getLong(sourceFieldsCache.get(bridgePkStr));
                if(rs.wasNull()) {
                    throw new SQLException(bridgePkStr + " is null for source pk=" + this.pk + " with EMISSION_TYPE = BRIDGE");
                }
            }
        } else {
            emissionTypes.add(SourceEmission.EmissionType.ROAD);
            long bridgePk = rs.getLong(sourceFieldsCache.get(bridgePkStr));
            if(!rs.wasNull()) {
                LOGGER.debug("Emission from bridge (pk = " + bridgePk + ") is added for source pk=" + this.pk);
                emissionTypes.add(SourceEmission.EmissionType.BRIDGE);
            }
        }
        return emissionTypes;
    }

    /**
     * Builds the list of SourceEmission objects based on current configuration.
     * <p>
     * Automatically selects traffic-based or emission-based processing.
     * ProfileBuilder is not required for emission-based input.
     *
     * @return list of SourceEmission objects (one per period and emission type)
     * @throws SQLException if database access fails or required fields are missing
     */
    public List<SourceEmission> build() throws SQLException {
        if(trafficAsInput){
            return buildUsingTraffic();
        } else {
            return buildUsingEmission();
        }
    }

    /**
     * Builds the list of SourceEmission objects with a ProfileBuilder.
     * <p>
     * ProfileBuilder is required for traffic-based input (provides frequency bands,
     * bridge definitions). For emission-based input, ProfileBuilder is optional.
     *
     * @param profileBuilder provides frequency configuration and bridge data
     * @return list of SourceEmission objects (one per period and emission type)
     * @throws SQLException if database access fails or required fields are missing
     */
    public List<SourceEmission> build(ProfileBuilder profileBuilder) throws SQLException {
        if(trafficAsInput){
            return buildUsingTraffic(profileBuilder);
        } else {
            LOGGER.debug("ProfileBuilder is not required to build emission when using emission as input.");
            return buildUsingEmission();
        }
    }

    /**
     * Builds emissions from traffic data without an explicit ProfileBuilder.
     * <p>
     * Creates a default ProfileBuilder if no EMISSION_TYPE field exists.
     * Throws exception if EMISSION_TYPE field is present (requires ProfileBuilder
     * for bridge emission calculations).
     *
     * @return list of SourceEmission objects
     * @throws SQLException if EMISSION_TYPE field exists or database access fails
     */
    public List<SourceEmission> buildUsingTraffic() throws SQLException {
        if(sourceFieldsCache.containsKey(emissionTypeStr)){
            throw new SQLException("ProfileBuilder is required to compute emission with EMISSION_TYPE field.");
        } else {
            LOGGER.debug("No " + emissionTypeStr + " field found. Default emission type ROAD is used.");
            return buildUsingTraffic(new ProfileBuilder());
        }
    }

    /**
     * Builds emissions from traffic data using the provided ProfileBuilder.
     * <p>
     * Process:
     * <ol>
     *   <li>Determines emission types (ROAD, BRIDGE, or both)</li>
     *   <li>Reads periods from database or uses configured period list</li>
     *   <li>Computes emissions using CNOSSOS-EU (road) or ASJ (bridge) models</li>
     * </ol>
     *
     * @param profileBuilder provides frequency bands and bridge definitions
     * @return list of SourceEmission objects (one per period and emission type)
     * @throws SQLException if required fields are missing or database access fails
     */
    public List<SourceEmission> buildUsingTraffic(ProfileBuilder profileBuilder) throws SQLException {

        List<SourceEmission.EmissionType> emissionTypes;
        if(sourceFieldsCache.containsKey(emissionTypeStr)){
            if(sourceFieldsCache.containsKey(bridgePkStr)){
                emissionTypes = getEmissionTypeWithBridgePkField();
            } else {
                throw new SQLException("Field " + bridgePkStr + " is required when " + emissionTypeStr + " field is present.");
            }
        } else {
            if(sourceFieldsCache.containsKey(bridgePkStr)){
                emissionTypes = getEmissionTypeWithBridgePkField();
            } else {
                LOGGER.debug("No " + emissionTypeStr + " field found. Default emission type ROAD is used.");
                emissionTypes = Arrays.asList(SourceEmission.EmissionType.ROAD);
            }
        }

        List<SourceEmission> sourceEmissions = new ArrayList<>();

        for (SourceEmission.EmissionType emissionType : emissionTypes) {
            if(periodFromDb){
                String period = getString(periodStr, "");
                sourceEmissions.add(buildUsingTrafficMain(emissionType, period, profileBuilder));
            } else {
                for (String period : periods) {
                    sourceEmissions.add(buildUsingTrafficMain(emissionType, period, profileBuilder));                
                }
            }
        }

        return sourceEmissions;
    }

    /**
     * Core method to compute emission spectrum for a single period and emission type.
     * <p>
     * Reads traffic parameters (vehicle counts, speeds, road surface, slope, etc.)
     * and evaluates emission models:
     * <ul>
     *   <li>ROAD: uses CNOSSOS-EU model</li>
     *   <li>BRIDGE: uses ASJ model for bridge structural noise</li>
     * </ul>
     *
     * @param emissionType type of emission to compute (ROAD or BRIDGE)
     * @param period time period identifier (e.g., "D", "E", "N")
     * @param profileBuilder provides frequency bands and bridge metadata
     * @return SourceEmission object with computed spectrum in Watts
     * @throws SQLException if database access fails or computation error occurs
     */
    private SourceEmission buildUsingTrafficMain(SourceEmission.EmissionType emissionType, String period, ProfileBuilder profileBuilder) throws SQLException {
        String periodAppend = periodSep + period;
        if(this.periodFromDb) {
            periodAppend = "";
        }
        // Set default values
        double tv = getDouble("TV"+periodAppend, 0); // old format "total vehicles"
        double hv = getDouble("HV"+periodAppend, 0); // old format "heavy vehicles"
        double lv_speed = getDouble("LV_SPD"+periodAppend, 0);
        double mv_speed = getDouble("MV_SPD"+periodAppend, 0);
        double hgv_speed = getDouble("HGV_SPD"+periodAppend, 0);
        double wav_speed = getDouble("WAV_SPD"+periodAppend, 0);
        double wbv_speed = getDouble("WBV_SPD"+periodAppend, 0);
        double hv_speed = getDouble("HV_SPD"+periodAppend, 0); // old format "heavy vehicles speed"
        double lvPerHour = getDouble("LV"+periodAppend, 0);
        double mvPerHour = getDouble("MV"+periodAppend, 0);
        double hgvPerHour = getDouble("HGV"+periodAppend, 0);
        double wavPerHour = getDouble("WAV"+periodAppend, 0);
        double wbvPerHour = getDouble("WBV"+periodAppend, 0);
        double temperature = getDouble("TEMP"+periodAppend, 20.0);
        String roadSurface = getString("PVMT", "NL08");
        double tsStud = getDouble("TS_STUD", 0);
        double pmStud = getDouble("PM_STUD", 0);
        double junctionDistance = getDouble("JUNC_DIST", 100);
        int junctionType = getInt("JUNC_TYPE", 2);
        int way = getInt("WAY", 3); // default value 2-way road
        double slope;
        if(sourceFieldsCache.containsKey("SLOPE")) {
            LOGGER.warn("Using SLOPE field from attribute table. To compute slope from 3D geometry, please remove SLOPE field from attribute table.");
            slope = getDouble("SLOPE", 0);
        } else {
            slope = getSlope();
            way = 3;
        }

        // Handle legacy format: convert total vehicles (TV) to light vehicles (LV)
        if(tv > 0) {
            lvPerHour = tv - (hv + mvPerHour + hgvPerHour + wavPerHour + wbvPerHour);
        }
        // Handle legacy format: convert heavy vehicles (HV) to HGV
        if(hv > 0) {
            hgvPerHour = hv;
        }

        // Compute emission spectrum for each frequency band
        double[] lvl = new double[frequencyBands.size()];

        for (int idFreq = 0; idFreq < frequencyBands.size(); idFreq++) {
            int freq = frequencyBands.get(idFreq);
            if(emissionType == EmissionType.ROAD) {
                RoadCnossosParameters rsParametersCnossos = new RoadCnossosParameters(lv_speed, mv_speed, hgv_speed, wav_speed,
                        wbv_speed, lvPerHour, mvPerHour, hgvPerHour, wavPerHour, wbvPerHour, freq, temperature,
                        roadSurface, tsStud, pmStud, junctionDistance, junctionType);
                rsParametersCnossos.setSlopePercentage(slope);
                rsParametersCnossos.setWay(way);
                rsParametersCnossos.setFileVersion(coefficientVersion);
                try {
                    lvl[idFreq] = RoadCnossos.evaluate(rsParametersCnossos);
                } catch (IOException ex) {
                    throw new SQLException(ex);
                }
            } else if(emissionType == EmissionType.BRIDGE) {
                long bridgePk = getLong(bridgePkStr, -1);
                RoadAsjParameters asjParams = new RoadAsjParameters(
                    lv_speed, mv_speed, hgv_speed, wav_speed, wbv_speed,
                    lvPerHour, mvPerHour, hgvPerHour, wavPerHour, wbvPerHour,
                    freq, temperature, roadSurface
                );
                Bridge bridge = profileBuilder.getBridgeByPk(bridgePk);
                asjParams.setHasBridge(true);
                asjParams.setBridgeGirderType(bridge.getGirderType().name());
                asjParams.setBridgeSlabType(bridge.getSlabType().name());
                try {
                    lvl[idFreq] = RoadAsj.evaluateBridgeVirtualSource(asjParams);
                } catch (IOException ex) {
                    throw new SQLException(ex);
                }
            }
        }
        return new SourceEmission(emissionType, period, dBToW(lvl));
    }

    /**
     * Computes road slope from 3D geometry.
     * <p>
     * Calculates slope percentage from the first two coordinates' Z difference
     * divided by the geometry length. Returns 0 if geometry is 2D or invalid.
     *
     * @return slope as a percentage (e.g., 5.0 for 5%)
     */
    private double getSlope() {
        double slope = 0;
        try {
            Geometry g = null;
            // Try to cast to SpatialResultSet safely
            if(this.rs instanceof SpatialResultSet) {
                SpatialResultSet spatialRs = (SpatialResultSet) this.rs;
                g = spatialRs.getGeometry();
            }
            
            if(g != null && !g.isEmpty()) {
                Coordinate[] c = g.getCoordinates();
                if(c.length >= 2) {
                    double z0 = c[0].z;
                    double z1 = c[1].z;
                    if(!Double.isNaN(z0) && !Double.isNaN(z1)) {
                        slope = Utils.computeSlope(z0, z1, g.getLength());
                    }
                }
            }
        } catch (SQLException ex) {
            // ignore
        }
        return slope;
    }

    private static List<Integer> buildFrequencyBands() {
        List<Integer> bands = new ArrayList<>(FrequencyConfig.DEFAULT_FREQUENCIES_OCTAVE.length);
        for (int freq : FrequencyConfig.DEFAULT_FREQUENCIES_OCTAVE) {
            bands.add(freq);
        }
        return bands;
    }
}
