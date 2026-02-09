package org.noise_planet.noisemodelling.jdbc;

import org.h2gis.utilities.JDBCUtilities;
import org.noise_planet.noisemodelling.jdbc.input.SourceEmission;
import org.noise_planet.noisemodelling.jdbc.input.SourceEmission.EmissionType;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions.dBToW;

/**
 * Builder class for creating emission spectra from pre-computed sound power data.
 * <p>
 * This builder reads frequency-band values from database columns and produces
 * per-period {@link SourceEmission} instances.
 */
public class SourceEmissionBuilder {
    protected final List<Integer> frequencyBands;
    protected static final Logger LOGGER = LoggerFactory.getLogger(SourceEmissionBuilder.class);
    protected final Map<String, Integer> sourceFieldsCache;
    protected final ResultSet rs;
    protected List<String> periods = new ArrayList<>();
    protected static final String emissionTypeStr = "EMISSION_TYPE";
    protected static final String periodStr = "PERIOD";
    protected String frequencyFieldPrepend = "HZ";
    protected boolean periodFromDb = false;

    /**
     * Constructs a SourceEmissionBuilder from a database ResultSet.
     * <p>
     * Automatically caches all column names and positions for efficient field access.
     *
     * @param rs the ResultSet positioned at the row to process
     * @throws SQLException if the ResultSet is invalid or missing required 'PK' field
     */
    public SourceEmissionBuilder(ResultSet rs, List<Integer> frequencyBands) throws SQLException{        
        this.rs = rs;
        this.frequencyBands = frequencyBands;
        this.sourceFieldsCache = new HashMap<String, Integer>();
        int fieldId = 1;
        for (String fieldName : JDBCUtilities.getColumnNames(rs.getMetaData())) {
            sourceFieldsCache.put(fieldName.toUpperCase(), fieldId++);
        }
    }

    /**
     * Configures the builder to read the time period from a PERIOD column in the database.
     * <p>
     * When enabled, expects a 'PERIOD' field (e.g., "D", "E", "N") in the ResultSet.
     *
     * @return this builder instance for method chaining
     */
    public SourceEmissionBuilder periodFromDb(){
        this.periodFromDb = true;
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
    public SourceEmissionBuilder setFrequencyFieldPrepend(String frequencyFieldPrepend){
        this.frequencyFieldPrepend = frequencyFieldPrepend;
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
    public SourceEmissionBuilder withPeriods(List<String> periods){
        this.periods = periods;
        this.periodFromDb = false;
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
    public SourceEmissionBuilder addPeriod(String period){
        this.periods.add(period);
        this.periodFromDb = false;
        return this;
    }

    /**
     * Safely retrieves a double value from the ResultSet.
     *
     * @param fieldName the column name to read
     * @param defaultValue value to return if column doesn't exist
     * @return the column value or default if column not found
     * @throws SQLException if database access fails
     */
    protected double getDouble(String fieldName, double defaultValue) throws SQLException {
        if(sourceFieldsCache.containsKey(fieldName)) {
            return rs.getDouble(sourceFieldsCache.get(fieldName));
        }
        return defaultValue;
    }

    /**
     * Safely retrieves an integer value from the ResultSet.
     *
     * @param fieldName the column name to read
     * @param defaultValue value to return if column doesn't exist
     * @return the column value or default if column not found
     * @throws SQLException if database access fails
     */
    protected int getInt(String fieldName, int defaultValue) throws SQLException {
        if(sourceFieldsCache.containsKey(fieldName)) {
            return rs.getInt(sourceFieldsCache.get(fieldName));
        }
        return defaultValue;
    }

    /**
     * Safely retrieves a long value from the ResultSet.
     *
     * @param fieldName the column name to read
     * @param defaultValue value to return if column doesn't exist
     * @return the column value or default if column not found
     * @throws SQLException if database access fails
     */
    protected long getLong(String fieldName, long defaultValue) throws SQLException {
        if(sourceFieldsCache.containsKey(fieldName)) {
            return rs.getLong(sourceFieldsCache.get(fieldName));
        }
        return defaultValue;
    }

    /**
     * Safely retrieves a string value from the ResultSet.
     *
     * @param fieldName the column name to read
     * @param defaultValue value to return if column doesn't exist
     * @return the column value or default if column not found
     * @throws SQLException if database access fails
     */
    protected String getString(String fieldName, String defaultValue) throws SQLException {
        if(sourceFieldsCache.containsKey(fieldName)) {
            return rs.getString(sourceFieldsCache.get(fieldName));
        }
        return defaultValue;
    }

    /**
     * Builds the list of SourceEmission objects based on current configuration.
     * <p>
     * Reads pre-computed emission values from the ResultSet for the configured periods.
     *
     * @return list of SourceEmission objects (one per period)
     * @throws SQLException if database access fails or required fields are missing
     */
    public List<SourceEmission> build() throws SQLException {
        return buildUsingEmission();
    }

    /**
     * Builds emissions by reading pre-computed sound power levels from database.
     * <p>
     * Expects frequency-specific columns (e.g., HZ63, HZ125) containing dB values.
     * Does not support multiple emission types per source.
     * <p>
     * Automatically filters out periods for which the required frequency columns
     * do not exist in the result set.
     *
     * @return list of SourceEmission objects with pre-computed spectra
     * @throws SQLException if multiple emission types found or database access fails
     */
    protected List<SourceEmission> buildUsingEmission() throws SQLException {
        
        EmissionType emissionType = EmissionType.fromString(getString(emissionTypeStr, ""));

        List<SourceEmission> sourceEmissions = new ArrayList<>();

        if(this.periodFromDb){
            String period = getString(periodStr, "");
            SourceEmission emission = buildUsingEmissionMain(emissionType, period);
            if (emission != null) {
                sourceEmissions.add(emission);
            }
        } else {
            for (String period : periods) {
                // buildUsingEmissionMain will return null if required fields are missing
                SourceEmission emission = buildUsingEmissionMain(emissionType, period);
                if (emission != null) {
                    sourceEmissions.add(emission);
                }
            }
        }

        return sourceEmissions;
    }

    /**
     * Core method to read pre-computed emission spectrum for a single period.
     * <p>
     * Reads frequency-specific dB values from database columns and converts
     * them to Watts. Column names are constructed as: prefix + period + frequency
     * (e.g., "HZD63" for Day period at 63 Hz).
     * <p>
     * Returns null if all required frequency field is missing for this period.
     *
     * @param emissionType type of emission (ROAD or BRIDGE)
     * @param period time period identifier
     * @return SourceEmission object with spectrum in Watts, or null if required fields are missing
     * @throws SQLException if database access fails
     */
    protected SourceEmission buildUsingEmissionMain(SourceEmission.EmissionType emissionType, String period) throws SQLException {

        String periodAppend = period;
        if(this.periodFromDb) {
            periodAppend = "";
        }
        boolean anyFieldExists = false;
        double[] emissionInWatts = new double[frequencyBands.size()];
        for (int idFreq = 0; idFreq < frequencyBands.size(); idFreq++) {
            String lwField = this.frequencyFieldPrepend + periodAppend + frequencyBands.get(idFreq);
            Double lw = getDouble(lwField, -99);
            if(lw >= -90) {
                emissionInWatts[idFreq] = dBToW(lw);
                anyFieldExists = true;
            } else {
                emissionInWatts[idFreq] = 0;
            }
        }
        if(anyFieldExists){
            return new SourceEmission(emissionType, period, emissionInWatts);
        } else {
            // skip
            return null;
        }
    }
}
