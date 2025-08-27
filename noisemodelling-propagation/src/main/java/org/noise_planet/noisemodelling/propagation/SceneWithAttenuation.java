package org.noise_planet.noisemodelling.propagation;

import org.h2gis.utilities.JDBCUtilities;
import org.h2gis.utilities.SpatialResultSet;
import org.locationtech.jts.geom.Geometry;
import org.noise_planet.noisemodelling.emission.directivity.DirectivitySphere;
import org.noise_planet.noisemodelling.emission.directivity.OmnidirectionalDirection;
import org.noise_planet.noisemodelling.pathfinder.path.Scene;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

import java.sql.SQLException;
import java.util.*;

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
     * Whether a source is located on a bridge (per source primary key).
     */
    public Map<Long, Boolean> sourceIsOnBridge = new HashMap<>();

    /**
     * Whether a source is a virtual source used to model structural
     * noise transmission (per source primary key).
     */
    public Map<Long, Boolean> sourceIsVirtualSource = new HashMap<>();

    /**
     * If a source is linked to a bridge, this map points to the bridge
     * primary key (per source primary key).
     */
    public Map<Long, Long> sourceBridgePk = new HashMap<>();

    /**
     * Candidate bridge indices for a given source primary key. When an
     * explicit bridge primary key is not provided in the input table,
     * the scene can search profileBuilder bridges to find intersecting
     * bridge geometries and populate this list.
     */
    public Map<Long, List<Long>> sourceBridgeCandidatePk = new HashMap<>();

    /**
     * Cached input table column names -> 1-based column index. Filled
     * on first call to {@link #addSource(Long, Geometry, SpatialResultSet)}.
     */
    public Map<String, Integer> sourceFieldNames = new HashMap<>();

    /**
     * Default attenuation parameters used when per-period parameters are
     * not specified. The default frequencies are initialized in the
     * constructor from the profileBuilder frequencyArray.
     */
    public AttenuationParameters defaultCnossosParameters = new AttenuationParameters();

    /**
     * Per-period attenuation settings (keyed by period identifier). When
     * present, propagation uses these parameters instead of
     * {@link #defaultCnossosParameters}.
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
        defaultCnossosParameters.setFrequencies(profileBuilder.frequencyArray);
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
     * Retrieves whether the noise source at the specified index is on a bridge.
     * @param srcIndex the index of the source
     * @return true if the source is on a bridge, false otherwise, or null if not specified
     */
    public Boolean getSourceIsOnBridge(int srcIndex) {
        return sourceIsOnBridge.get(sourcesPk.get(srcIndex));
    }

    /**
     * Retrieves whether the noise source at the specified index is a virtual source for structural noise transmission.
     * Virtual sources are used for calculating structural noise transmission through bridge structures.
     * @param srcIndex the index of the source
     * @return true if the source is a virtual source, false otherwise, or null if not specified
     */
    public Boolean getSourceIsVirtualSource(int srcIndex) {
        return sourceIsVirtualSource.get(sourcesPk.get(srcIndex));
    }

    /**
     * Retrieves whether the noise source at the specified index is a virtual source for structural noise transmission.
     * Virtual sources are used for calculating structural noise transmission through bridge structures.
     * @param sourcePk the primary key of the source
     * @return true if the source is a virtual source, false otherwise, or null if not specified
     */
    public Boolean getSourceIsVirtualSource(long sourcePk) {
        return sourceIsVirtualSource.get(sourcePk);
    }
    

    /**
     * Retrieves whether the noise source at the specified index is a virtual source for structural noise transmission.
     * Virtual sources are used for calculating structural noise transmission through bridge structures.
     * @param sourcePk the primary key of the source
     * @param isVirtual whether the source is a virtual source
     * @return true if the source is a virtual source, false otherwise, or null if not specified
     */
    public void setSourceIsVirtualSource(long sourcePk, boolean isVirtual) {
        sourceIsVirtualSource.put(sourcePk, isVirtual);
    }
    /**
     * Retrieves the bridge primary key for the noise source at the specified index.
     * @param srcIndex the index of the source
     * @return the bridge primary key, or null if not specified
     */
    public Long getSourceBridgePk(int srcIndex) {
        return sourceBridgePk.get(sourcesPk.get(srcIndex));
    }

    /**
     * Retrieves the bridge primary key for the noise source at the specified index.
     * @param sourcePk the primary key of the source
     * @return the bridge primary key, or null if not specified
     */
    public Long getSourceBridgePk(long sourcePk) {
        return sourceBridgePk.get(sourcePk);
    }


    /**
     * Retrieves the bridge primary key for the noise source at the specified index.
     * @param sourcePk the primary key of the source
     * @param bridgePk the primary key of the bridge
     * @return the bridge primary key, or null if not specified
     */
    public void setSourceBridgePk(long sourcePk, long bridgePk) {
        sourceBridgePk.put(sourcePk, bridgePk);
    }

    
    /**
     * Retrieves the bridge primary key for the noise source at the specified index.
     * @param srcIndex the index of the source
     * @return the bridge primary key, or null if not specified
     */
    public List<Long> getSourceBridgeCandidatePk(int srcIndex) {
        return sourceBridgeCandidatePk.get(sourcesPk.get(srcIndex));
    }

    /**
     * Retrieves the bridge primary key for the noise source at the specified index.
     * @param sourcePk the primary key of the source
     * @return the bridge primary key, or null if not specified
     */
    public List<Long> getSourceBridgeCandidatePk(long sourcePk) {
        return sourceBridgeCandidatePk.get(sourcePk);
    }

    /**
     * Add geometry with additional attributes
     * @param pk Unique source identifier
     * @param geom Source geometry
     * @param gs Additional attributes
     */

    public void addSource(Long pk, Geometry geom, Double gs) {
        addSource(pk, geom);
        sourceGs.put(pk, gs);
    }

    /**
     * Sets the direction attributes for the receiver.
     * @param directionAttributes
     */
    public void setDirectionAttributes(Map<Integer, DirectivitySphere> directionAttributes) {
        this.directionAttributes = directionAttributes;
        // Check if the directivities contain all required frequencies
        directionAttributes.forEach((integer, directivitySphere) -> {
            profileBuilder.frequencyArray.forEach(frequency->{
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
    public void addSource(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException {
        if(sourceFieldNames.isEmpty()) {
            List<String> fieldNames = JDBCUtilities.getColumnNames(rs.getMetaData());
            for(int idField = 0; idField < fieldNames.size(); idField++) {
                sourceFieldNames.put(fieldNames.get(idField).toUpperCase(Locale.ROOT), idField + 1);
            }
        }
        float yaw = 0;
        float pitch = 0;
        float roll = 0;
        boolean hasOrientation = false;
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
            addSource(pk, geom, new Orientation(yaw, pitch, roll));
        } else {
            addSource(pk, geom);
        }

        int gsField = JDBCUtilities.getFieldIndex(rs.getMetaData(), GS_DATABASE_FIELD);
        if(sourceFieldNames.containsKey(GS_DATABASE_FIELD)) {
            sourceGs.put(pk, rs.getDouble(gsField));
        }

        // Read bridge-related attributes
        int isOnBridgeField = JDBCUtilities.getFieldIndex(rs.getMetaData(), IS_ON_BRIDGE_DATABASE_FIELD);
        if(sourceFieldNames.containsKey(IS_ON_BRIDGE_DATABASE_FIELD)) {
            sourceIsOnBridge.put(pk, rs.getBoolean(isOnBridgeField));
        } else {
            // Default value is false for all sources when field is not present
            sourceIsOnBridge.put(pk, false);
        }

        int isVirtualSourceField = JDBCUtilities.getFieldIndex(rs.getMetaData(), IS_VIRTUAL_SOURCE_DATABASE_FIELD);
        if(sourceFieldNames.containsKey(IS_VIRTUAL_SOURCE_DATABASE_FIELD)) {
            sourceIsVirtualSource.put(pk, rs.getBoolean(isVirtualSourceField));
        } else {
            // Default value is false for all sources when field is not present
            sourceIsVirtualSource.put(pk, false);
        }

        int bridgePkField = JDBCUtilities.getFieldIndex(rs.getMetaData(), BRIDGE_PK_DATABASE_FIELD);
        if(sourceFieldNames.containsKey(BRIDGE_PK_DATABASE_FIELD)) {
            sourceBridgePk.put(pk, rs.getLong(bridgePkField));
        }

        List<Long> bridgeCandidatePk = new ArrayList<>();
        if (sourceFieldNames.containsKey(BRIDGE_PK_DATABASE_FIELD)) {
            bridgeCandidatePk.add(rs.getLong(bridgePkField));
        } else if (sourceIsOnBridge.get(pk) && !sourceIsVirtualSource.get(pk)) {
            List<Bridge> bridges = profileBuilder.getBridges();
            for (int i = 0; i < bridges.size(); i++) {
                Bridge bridge = bridges.get(i);
                if (bridge.getGeometry() != null && bridge.intersects(geom)) {
                    bridgeCandidatePk.add(bridge.getPrimaryKey());
                }
            }
        }
        sourceBridgeCandidatePk.put(pk, bridgeCandidatePk);
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
        int directivityIdentifier = sourceEmissionAttenuation.get(sourcesPk.get(srcIndex));
        if (directionAttributes.containsKey(directivityIdentifier)) {
            return directionAttributes.get(directivityIdentifier).getAttenuationArray(frequencies, phi, theta);
        } else {
            // This direction identifier has not been found
            return new double[frequencies.length];
        }
    }

    @Override
    public void clearSources() {
        super.clearSources();
        sourceEmissionAttenuation.clear();
        sourceFieldNames.clear();
        sourceGs.clear();
        sourceIsOnBridge.clear();
        sourceIsVirtualSource.clear();
        sourceBridgePk.clear();
        sourceBridgeCandidatePk.clear();
        directionAttributes.clear();
    }
}
