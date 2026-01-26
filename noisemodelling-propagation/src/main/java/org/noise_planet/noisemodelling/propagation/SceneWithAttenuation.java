package org.noise_planet.noisemodelling.propagation;

import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.SpatialResultSet;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.noise_planet.noisemodelling.emission.directivity.DirectivitySphere;
import org.noise_planet.noisemodelling.emission.directivity.OmnidirectionalDirection;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.FrequencyConfig;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

import java.sql.SQLException;
import java.util.*;

// removed unused import

/**
 * SceneWithAttenuation extends the geometric Scene used by the
 * ProfileBuilder by adding attenuation-related attributes and source
 * emission parameters.
 * <p>
 * The class stores per-source orientation/directivity identifiers,
 * ground factor (gs), bridge-related flags and attenuation parameters
 * used during propagation calculations. It acts as input/metadata for
 * propagation visitors and is not intended to perform propagation itself.
 */
public class SceneWithAttenuation extends Scene {
    public static final double DEFAULT_GS = 0.0;
    private static final Logger LOGGER = LoggerFactory.getLogger(SceneWithAttenuation.class);

    /**
     * Map of directivity identifier -> DirectivitySphere.
     * A DirectivitySphere contains attenuation patterns for various
     * directions and frequencies. The identifier is looked up from
     * sourceEmissionAttenuation for each source.
     */
    public Map<Integer, DirectivitySphere> directionAttributes = new HashMap<>();

    /**
     * For each source primary key, stores the integer identifier that
     * references an entry in {@link #directionAttributes}.
     */
    public Map<Long, Integer> sourceEmissionAttenuation = new HashMap<>();

    /**
     * Ground factor (gs) per source primary key. Typical values come from
     * the input table or are set to {@link #DEFAULT_GS} when not present.
     */
    public Map<Long, Double> sourceGs = new HashMap<>();

    /**
     * Cached input table column names -> 1-based column index. Filled
     * on first call to {@link #addSource(Long, Geometry, SpatialResultSet)}.
     */
    public Map<String, Integer> sourceFieldNames = new HashMap<>();


    private AttenuationParameters attenuationParameters = new AttenuationParameters(FrequencyConfig.FrequencyBand.OCTAVE);
    public void setAttenuationParameters(AttenuationParameters attenuationParameters) {
        this.attenuationParameters = attenuationParameters;
    }

    public AttenuationParameters getAttenuationParameters() {
        return attenuationParameters;
    }

    /**
     * Per-period attenuation settings (keyed by period identifier). When
     * present, propagation uses these parameters instead of
     * {@link #DEFAULT_CNOSSOS_PARAMETERS}.
     */
    public Map<String, AttenuationParameters> cnossosParametersPerPeriod = new HashMap<>();

    /**
     * Known set of all periods encountered in the scene. Used to ensure
     * that propagation outputs a consistent set of periods even if a
     * particular period contains no sources.
     */
    public Set<String> periodSet = new HashSet<>();

    public SceneWithAttenuation(ProfileBuilder profileBuilder) {
        super(profileBuilder);
        attenuationParameters.setFrequencies(profileBuilder.getFrequencyArray());
    }

    public SceneWithAttenuation() {
    }

    /**
     * Retrieves the ground speed of the noise source at the specified index.
     * @param srcIndex
     * @return the ground speed of the noise source at the specified index.
     */
    public double getSourceGs(int srcIndex){
        return sourceGs.get(sourcesPk.get(srcIndex));
    }

    /**
     * Add a source geometry and associate a ground factor (gs) with it.
     * This is a convenience wrapper around {@link #addSource(Long, Geometry)}
     * which also records the provided {@code gs} value in
     * {@link #sourceGs}.
     *
     * @param pk Unique source identifier (may be used as a candidate key by the scene)
     * @param geom Source geometry
     * @param gs Ground factor (gs) to associate with the registered source; may be null
     * @return the registered source primary key (as returned by {@code addSource})
     */
    public long addSource(Long pk, Geometry geom, HeightType heightType, Double gs) {
        long returnedPk = addSource(pk, geom, heightType);
        sourceGs.put(returnedPk, gs);
        return returnedPk;
    }


    /**
     * Sets the direction attributes for the receiver.
     * @param directionAttributes
     */
    public void setDirectionAttributes(Map<Integer, DirectivitySphere> directionAttributes) {
        this.directionAttributes = directionAttributes;
        // Check if the directivities contain all required frequencies
        directionAttributes.forEach((integer, directivitySphere) -> {
            profileBuilder.getFrequencyArray().forEach(frequency->{
                if(!directivitySphere.coverFrequency(frequency)) {
                    throw new IllegalArgumentException(
                            String.format(Locale.ROOT,
                                    "The provided DirectivitySphere does not handle %d Hertz", frequency));
                }
            });
        });
    }

    /**
     * Add geometry with additional attributes
     * @param pk Unique source identifier
     * @param geom Source geometry
     * @param rs Additional attributes fetched from database
     */
    public Long addSourceDb(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException {
        return doAddSourceDb(pk, geom, (java.sql.ResultSet) rs);
    }

    /**
     * Test-friendly overload accepting a plain {@link java.sql.ResultSet}.
     * <p>
     * This duplicates the behavior of the SpatialResultSet overload but makes
     * it easier to unit-test addSourceDb without requiring a full H2GIS
     * SpatialResultSet instance.
     */
    public Long addSourceDb(Long pk, Geometry geom, java.sql.ResultSet rs) throws SQLException {
        return doAddSourceDb(pk, geom, rs);
    }

    /**
     * Common implementation for both SpatialResultSet and plain ResultSet overloads.
    *
    * The method reads the ResultSet metadata on first use to build a map of
    * input column names to 1-based indexes. It attempts to extract optional
    * orientation (yaw/pitch/roll), directivity identifier, ground factor (gs)
    * and bridge-related attributes. When BRIDGE_PK is present, SOURCE_TYPE field
    * determines the appropriate SourceBridgeProperty:
    * - SOURCE_TYPE='ROAD': ACTUAL_SOURCE_ON_BRIDGE (road traffic noise)
    * - SOURCE_TYPE='BRIDGE': IMAGINARY_SOURCE_UNDER_BRIDGE (structural noise)
    *
    * If the registered PK differs from the provided candidate PK the method
    * will emit an info-level log entry describing the candidate and the
    * actual registered key. This helps detect when the scene assigns or
    * rewrites identifiers during ingestion.
     */
    private Long doAddSourceDb(Long pk, Geometry geom, java.sql.ResultSet rs) throws SQLException {
        if(sourceFieldNames.isEmpty()) {
            List<String> fieldNames = JDBCUtilities.getColumnNames(rs.getMetaData());
            for(int idField = 0; idField < fieldNames.size(); idField++) {
                sourceFieldNames.put(fieldNames.get(idField).toUpperCase(Locale.ROOT), idField + 1);
            }
        }
        // List<Long> returnedPks = new ArrayList<>();
        long returnedPk;
        float yaw = 0;
        float pitch = 0;
        float roll = 0;
        boolean hasOrientation = false;
        Orientation orientation = null;
        if(sourceFieldNames.containsKey(YAW_DATABASE_FIELD)) {
            yaw = rs.getFloat(sourceFieldNames.get(YAW_DATABASE_FIELD));
            hasOrientation = true;
        }
        if(sourceFieldNames.containsKey(PITCH_DATABASE_FIELD)) {
            pitch = rs.getFloat(sourceFieldNames.get(PITCH_DATABASE_FIELD));
            hasOrientation = true;
        }
        if(sourceFieldNames.containsKey(ROLL_DATABASE_FIELD)) {
            roll = rs.getFloat(sourceFieldNames.get(ROLL_DATABASE_FIELD));
            hasOrientation = true;
        }
        int directivityField = JDBCUtilities.getFieldIndex(rs.getMetaData(), DIRECTIVITY_DATABASE_FIELD);
        if(sourceFieldNames.containsKey(DIRECTIVITY_DATABASE_FIELD)) {
            sourceEmissionAttenuation.put(pk, rs.getInt(directivityField));
        }

        if(hasOrientation) {
            orientation =  new Orientation(yaw, pitch, roll);
        }

        // Read HEIGHT_TYPE field (default to RELATIVE if not present)
        Scene.HeightType heightType = Scene.HeightType.RELATIVE;
        if(sourceFieldNames.containsKey(HEIGHT_TYPE_DATABASE_FIELD)) {
            String heightTypeStr = rs.getString(sourceFieldNames.get(HEIGHT_TYPE_DATABASE_FIELD));
            if (heightTypeStr != null && !heightTypeStr.trim().isEmpty()) {
                try {
                    heightType = Scene.HeightType.valueOf(heightTypeStr.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Invalid HEIGHT_TYPE value '{}' for source pk={}, defaulting to RELATIVE", heightTypeStr, pk);
                    heightType = Scene.HeightType.RELATIVE;
                }
            }
        }

        int gsField = JDBCUtilities.getFieldIndex(rs.getMetaData(), GS_DATABASE_FIELD);
        if(sourceFieldNames.containsKey(GS_DATABASE_FIELD)) {
            sourceGs.put(pk, rs.getDouble(gsField));
        }

        if (!profileBuilder.hasBridges()) {
            returnedPk = addSource(pk, geom, heightType, orientation, null);
            // returnedPks.add(addSource(pk, geom, heightType, orientation, null));
            // Log when the registered key differs from the candidate key
            // for (Long registered : returnedPks) {
            //     if (!Objects.equals(pk, registered)) {
            //         LOGGER.info("Registered source key differs from candidate: candidate={}, registered={}", pk, registered);
            //     }
            // }
            // return returnedPks;
            return returnedPk;
        }

        long bridgePk = -1;
        if(sourceFieldNames.containsKey(BRIDGE_PK_DATABASE_FIELD)) {
            int bridgePkCol = JDBCUtilities.getFieldIndex(rs.getMetaData(), BRIDGE_PK_DATABASE_FIELD);
            bridgePk = rs.getLong(bridgePkCol);
            // SQL NULLの場合はwasNull()で-1にする
            if (rs.wasNull()) {
                bridgePk = -1;
            }
        }
        if(bridgePk == -1){
            SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(SourceBridgeProperty.SourceType.SOURCE_NOT_RELATED_TO_BRIDGE, -1L, -1L);
            returnedPk = addSource(pk, geom, heightType, orientation, sourceBridgeProperty);
            // returnedPks.add(returnedPk);
            // return returnedPks;
            return returnedPk;
        }

        // Read SOURCE_TYPE field to determine the appropriate SourceBridgeProperty.sourceType
        String sourceType = null;
        if(sourceFieldNames.containsKey(SOURCE_TYPE_DATABASE_FIELD)) {
            sourceType = rs.getString(JDBCUtilities.getFieldIndex(rs.getMetaData(), SOURCE_TYPE_DATABASE_FIELD));
        }

        if(sourceType.equals("ROAD")){
            SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, bridgePk, -1L);
            returnedPk = addSource(pk, geom, heightType, orientation, sourceBridgeProperty);
            // returnedPks.add(returnedPk);
            // return returnedPks;
            return returnedPk;
        } else if(sourceType.equals("BRIDGE")){
            SourceBridgeProperty sourceBridgeProperty = new SourceBridgeProperty(SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, -1L, bridgePk);
            returnedPk = addSource(pk, geom, heightType, orientation, sourceBridgeProperty);
            // returnedPks.add(returnedPk);
            // return returnedPks;
            return returnedPk;
        } else {
            throw new IllegalArgumentException(
                "Unknown SOURCE_TYPE value for bridge source. " +
                "Expected 'ROAD' or 'BRIDGE', got '" + sourceType + "'. " +
                "sourcePk=" + pk + ", bridgePk=" + bridgePk + ".");
        }

    }


    /**
     * Checks if the noise source at the specified index is omnidirectional.
     * @param srcIndex Source index in the list sourceGeometries
     * @return True if the source is omnidirectional and so does not have orientation dependant attenuation, false otherwise.
     */
    public boolean isOmnidirectional(int srcIndex) {
        if (srcIndex < 0 || !(srcIndex < sourcesPk.size())) {
            return true;
        }
        long sourcePk = sourcesPk.get(srcIndex);
        if(!sourceEmissionAttenuation.containsKey(sourcePk)) {
            return true;
        }
        return directionAttributes.get(sourceEmissionAttenuation.get(sourcePk)) instanceof OmnidirectionalDirection;
    }

    /**
     *
     * @param srcIndex Source index in the list sourceGeometries
     * @param frequencies Frequency in Hertz
     * @param phi (0 2π) 0 is front
     * @param theta (-π/2 π/2) 0 is horizontal π is top
     * @return
     */
    public double[] getSourceAttenuation(int srcIndex, double[] frequencies, double phi, double theta) {
        Long sourcePk = sourcesPk.get(srcIndex);
        if (sourcePk == null || !sourceEmissionAttenuation.containsKey(sourcePk)) {
            return new double[frequencies.length];
        }
        Integer directivityIdentifier = sourceEmissionAttenuation.get(sourcePk);
        if (directivityIdentifier == null || !directionAttributes.containsKey(directivityIdentifier)) {
            return new double[frequencies.length];
        }
        return directionAttributes.get(directivityIdentifier).getAttenuationArray(frequencies, phi, theta);
    }
    
    
    @Override
    public SourceBridgeProperty getSourceBridgePropertyByPk(long pk) {
        return super.getSourceBridgePropertyByPk(pk);
    }

    @Override
    public List<SourceBridgeProperty> getSourceBridgeProperties() {
        return super.getSourceBridgeProperties();
    }

    @Override
    public void clearSources() {
        super.clearSources();
        sourceEmissionAttenuation.clear();
        sourceFieldNames.clear();
        sourceGs.clear();
        directionAttributes.clear();
    }
}
