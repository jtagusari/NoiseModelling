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

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for storing original source data needed for virtual source creation.
 * This class provides thread-safe caching of traffic and emission data to support
 * dynamic virtual source generation during acoustic propagation calculations.
 */
public class SourceDataCache {
    
    /** Thread-safe cache for original source data */
    private final Map<Long, OriginalSourceInfo> cache = new ConcurrentHashMap<>();
    
    /** Field names mapping for efficient data extraction */
    private final Map<String, Integer> sourceFieldNames;
    
    /**
     * Constructor
     * 
     * @param sourceFieldNames Field names mapping for data extraction
     */
    public SourceDataCache(Map<String, Integer> sourceFieldNames) {
        this.sourceFieldNames = sourceFieldNames;
    }
    
    /**
     * Store original source data from database result set.
     * 
     * @param sourcePk Source primary key
     * @param geometry Source geometry
     * @param resultSet Database result set containing source data
     * @throws SQLException If database access fails
     */
    public void storeSourceData(Long sourcePk, Geometry geometry, SpatialResultSet resultSet) throws SQLException {
        try {
            Map<String, Double> trafficData = extractTrafficData(resultSet);
            
            OriginalSourceInfo sourceInfo = new OriginalSourceInfo(trafficData, geometry);
            cache.put(sourcePk, sourceInfo);
            
        } catch (Exception e) {
            // Non-critical operation - log warning but don't fail
            System.err.println("Warning: Could not cache source data for " + sourcePk + ": " + e.getMessage());
        }
    }
    
    /**
     * Retrieve cached source data.
     * 
     * @param sourcePk Source primary key
     * @return Cached source information or null if not found
     */
    public OriginalSourceInfo getSourceData(Long sourcePk) {
        return cache.get(sourcePk);
    }
    
    /**
     * Check if source data is cached.
     * 
     * @param sourcePk Source primary key
     * @return true if data is cached, false otherwise
     */
    public boolean hasCachedData(Long sourcePk) {
        return cache.containsKey(sourcePk);
    }
    
    /**
     * Get the number of cached sources.
     * 
     * @return Number of cached sources
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Clear all cached data.
     */
    public void clear() {
        cache.clear();
    }
    
    /**
     * Extract traffic data for different periods and vehicle types.
     * 
     * @param resultSet Database result set
     * @return Map of traffic data by field name
     * @throws SQLException If database access fails
     */
    private Map<String, Double> extractTrafficData(SpatialResultSet resultSet) throws SQLException {
        Map<String, Double> trafficData = new HashMap<>();
        
        // Extract traffic data for different periods (Day, Evening, Night)
        String[] vehicleTypes = {"LV", "MV", "HGV", "WAV", "WBV"};
        String[] periods = {"_D", "_E", "_N"};
        
        for (String vehicleType : vehicleTypes) {
            for (String period : periods) {
                // Traffic count fields
                String countField = vehicleType + period;
                if (sourceFieldNames.containsKey(countField)) {
                    trafficData.put(countField, resultSet.getDouble(countField));
                } else {
                    trafficData.put(countField, 0.0); 
                }
                
                // Speed fields
                String speedField = vehicleType + "_SPD" + period;
                if (sourceFieldNames.containsKey(speedField)) {
                    trafficData.put(speedField, resultSet.getDouble(speedField));
                } else {
                    trafficData.put(speedField, Double.NaN);
                }
            }
        }
        
        return trafficData;
    }
    
    
    /**
     * Data class for storing original source information.
     */
    public static class OriginalSourceInfo {
        public final Map<String, Double> trafficData;
        public final Geometry geometry;
        
        public OriginalSourceInfo(Map<String, Double> trafficData, Geometry geometry) {
            this.trafficData = new HashMap<>(trafficData); // Defensive copy
            this.geometry = geometry;
        }
        
        /**
         * Get traffic count for a specific vehicle type and period.
         *
         * @param vehicleType Vehicle type (LV, MV, HGV, WAV, WBV)
         * @param period Time period (D, E, N)
         * @return Traffic count or null if not available
         */
        public Double getTrafficCount(String vehicleType, String period) {
            return trafficData.get(vehicleType + "_" + period);
        }
        
        /**
         * Get speed for a specific vehicle type and period.
         *
         * @param vehicleType Vehicle type (LV, MV, HGV, WAV, WBV)
         * @return Speed or null if not available
         */
        public Double getSpeed(String vehicleType, String period) {
            return trafficData.get(vehicleType + "_SPD" + "_" + period);
        }
        
    }
}
