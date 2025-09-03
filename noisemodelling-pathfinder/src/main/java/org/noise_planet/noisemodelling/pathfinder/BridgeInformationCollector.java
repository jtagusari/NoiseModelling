/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for collecting bridge information from scene data.
 * This class is responsible for extracting bridge-related information from sources
 * and preparing it in a format suitable for bridge processing during acoustic calculations.
 * 
 * Design Principles:
 * - Single Responsibility: Focus only on bridge information collection
 * - Separation of Concerns: Bridge processing logic separated from emission calculations
 * - Loose Coupling: Uses generic interface for scene access
 */
public class BridgeInformationCollector {
    
    /**
     * Interface for accessing scene source information.
     * This allows the collector to work with any scene implementation
     * without tight coupling to specific classes.
     */
    public interface SceneSourceAccessor {
        int getSourceCount();
        Long getSourcePk(int index);
        Boolean getSourceIsOnBridge(int index);
        Boolean getSourceIsVirtualSource(int index);
        Long getSourceBridgePk(int index);
    }
    
    /**
     * Collect bridge information for all sources in the scene.
     * This method scans all sources and extracts bridge-related metadata
     * that will be used during PathFinder's acoustic calculation process.
     * 
     * @param sceneAccessor Interface for accessing scene source data
     * @return Map containing bridge information for each source primary key that has bridge data
     */
    public static Map<Long, BridgeSourceProcessor.BridgeInfo> collectBridgeInformation(SceneSourceAccessor sceneAccessor) {
        Map<Long, BridgeSourceProcessor.BridgeInfo> bridgeInfoMap = new HashMap<>();
        
        // Process each source in the scene
        for (int i = 0; i < sceneAccessor.getSourceCount(); i++) {
            Long sourcePk = sceneAccessor.getSourcePk(i);
            
            // Extract bridge-related attributes
            Boolean isOnBridge = sceneAccessor.getSourceIsOnBridge(i);
            Boolean isVirtualSource = sceneAccessor.getSourceIsVirtualSource(i);
            Long bridgePk = sceneAccessor.getSourceBridgePk(i);
            
            // Only create bridge info if there's relevant bridge data
            if (Boolean.TRUE.equals(isOnBridge) || Boolean.TRUE.equals(isVirtualSource) || bridgePk != null) {
                BridgeSourceProcessor.BridgeInfo bridgeInfo = new BridgeSourceProcessor.BridgeInfo(
                    Boolean.TRUE.equals(isOnBridge), 
                    Boolean.TRUE.equals(isVirtualSource), 
                    bridgePk
                );
                
                bridgeInfoMap.put(sourcePk, bridgeInfo);
            }
        }
        
        return bridgeInfoMap;
    }
    
    /**
     * Check if the scene contains any sources with bridge information.
     * This is useful for determining whether bridge processing is needed
     * before performing the full collection operation.
     * 
     * @param sceneAccessor Interface for accessing scene source data
     * @return true if at least one source has bridge information, false otherwise
     */
    public static boolean hasBridgeInformation(SceneSourceAccessor sceneAccessor) {
        for (int i = 0; i < sceneAccessor.getSourceCount(); i++) {
            Boolean isOnBridge = sceneAccessor.getSourceIsOnBridge(i);
            Boolean isVirtualSource = sceneAccessor.getSourceIsVirtualSource(i);
            Long bridgePk = sceneAccessor.getSourceBridgePk(i);
            
            if (Boolean.TRUE.equals(isOnBridge) || Boolean.TRUE.equals(isVirtualSource) || bridgePk != null) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Count the number of sources that have bridge information.
     * 
     * @param sceneAccessor Interface for accessing scene source data
     * @return Number of sources with bridge information
     */
    public static int countSourcesWithBridgeInfo(SceneSourceAccessor sceneAccessor) {
        int count = 0;
        for (int i = 0; i < sceneAccessor.getSourceCount(); i++) {
            Boolean isOnBridge = sceneAccessor.getSourceIsOnBridge(i);
            Boolean isVirtualSource = sceneAccessor.getSourceIsVirtualSource(i);
            Long bridgePk = sceneAccessor.getSourceBridgePk(i);
            
            if (Boolean.TRUE.equals(isOnBridge) || Boolean.TRUE.equals(isVirtualSource) || bridgePk != null) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Collect bridge information for specific source primary keys only.
     * This method is useful when you need bridge information for a subset of sources.
     * 
     * @param sceneAccessor Interface for accessing scene source data
     * @param sourcePks Source primary keys to collect information for
     * @return Map containing bridge information for requested sources
     */
    public static Map<Long, BridgeSourceProcessor.BridgeInfo> collectBridgeInformationForSources(
            SceneSourceAccessor sceneAccessor, java.util.Set<Long> sourcePks) {
        Map<Long, BridgeSourceProcessor.BridgeInfo> bridgeInfoMap = new HashMap<>();
        
        for (int i = 0; i < sceneAccessor.getSourceCount(); i++) {
            Long sourcePk = sceneAccessor.getSourcePk(i);
            
            // Only process requested sources
            if (!sourcePks.contains(sourcePk)) {
                continue;
            }
            
            Boolean isOnBridge = sceneAccessor.getSourceIsOnBridge(i);
            Boolean isVirtualSource = sceneAccessor.getSourceIsVirtualSource(i);
            Long bridgePk = sceneAccessor.getSourceBridgePk(i);
            
            if (Boolean.TRUE.equals(isOnBridge) || Boolean.TRUE.equals(isVirtualSource) || bridgePk != null) {
                BridgeSourceProcessor.BridgeInfo bridgeInfo = new BridgeSourceProcessor.BridgeInfo(
                    Boolean.TRUE.equals(isOnBridge), 
                    Boolean.TRUE.equals(isVirtualSource), 
                    bridgePk
                );
                
                bridgeInfoMap.put(sourcePk, bridgeInfo);
            }
        }
        
        return bridgeInfoMap;
    }
}
