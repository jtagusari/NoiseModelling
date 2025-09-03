/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder;

import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * BridgeSourceProcessor handles bridge-related source processing for PathFinder.
 * This class manages the generation of additional sources (virtual and mirror sources)
 * when point sources are converted from line sources during acoustic calculation.
 * 
 * Primary Responsibilities:
 * - Process bridge adjustments for individual point sources during PathFinder processing
 * - Generate virtual sources at bridge bottom for structural noise transmission
 * - Generate mirror image sources for bridge reflections
 * - Apply appropriate acoustic level adjustments for additional sources
 * 
 * Design Goals:
 * - Separate bridge processing logic from PathFinder main class
 * - Process each point source individually for maximum acoustic accuracy
 * - Handle bridge information lookup efficiently
 * - Support integration with existing PathFinder workflow
 */
public class BridgeSourceProcessor {
    
    /** Profile builder containing bridge information */
    private final ProfileBuilder profileBuilder;
    
    /** Cache for bridge information by source primary key */
    private final Map<Long, BridgeInfo> bridgeInfoCache;
    
    /** Acoustic level tables for bridge processing */
    private final Map<Long, BridgeAcousticLevels> bridgeAcousticCache;
    
    /** Virtual source levels by source primary key */
    private final Map<Long, double[]> virtualSourceLevelsBySource;
    
    /**
     * Bridge information for a specific source
     */
    public static class BridgeInfo {
        public final boolean isOnBridge;
        public final boolean isVirtualSource;
        public final Long bridgePk;
        
        public BridgeInfo(boolean isOnBridge, boolean isVirtualSource, Long bridgePk) {
            this.isOnBridge = isOnBridge;
            this.isVirtualSource = isVirtualSource;
            this.bridgePk = bridgePk;
        }
    }
    
    /**
     * Pre-calculated acoustic levels for bridge processing.
     * Contains level adjustments for virtual sources and mirror sources.
     */
    public static class BridgeAcousticLevels {
        /** Level adjustments for virtual sources (dB) - one value per frequency band */
        public final double[] virtualSourceAdjustments;
        
        /** Level adjustments for mirror sources (dB) - one value per frequency band */
        public final double[] mirrorSourceAdjustments;
        
        /** Traffic flow used for calculations (veh/h) */
        public final double trafficFlow;
        
        /** Speed used for calculations (km/h) */
        public final double speed;
        
        /** Bridge material */
        public final String material;
        
        /** Bridge deck thickness (m) */
        public final double deckThickness;
        
        public BridgeAcousticLevels(double[] virtualSourceAdjustments, double[] mirrorSourceAdjustments,
                                   double trafficFlow, double speed, String material, double deckThickness) {
            this.virtualSourceAdjustments = virtualSourceAdjustments.clone();
            this.mirrorSourceAdjustments = mirrorSourceAdjustments.clone();
            this.trafficFlow = trafficFlow;
            this.speed = speed;
            this.material = material;
            this.deckThickness = deckThickness;
        }
    }
    
    /**
     * Additional source information for bridge processing
     */
    public static class AdditionalSource {
        public final Coordinate position;
        public final double li;
        public final Orientation orientation;
        public final SourceType type;
        public final double levelAdjustment; // dB adjustment from original source
        
        public enum SourceType {
            VIRTUAL_BRIDGE_BOTTOM,  // Virtual source at bridge bottom
            MIRROR_IMAGE,           // Mirror image source
            HEIGHT_ADJUSTED         // Original source with adjusted height
        }
        
        public AdditionalSource(Coordinate position, double li, Orientation orientation, 
                              SourceType type, double levelAdjustment) {
            this.position = position;
            this.li = li;
            this.orientation = orientation;
            this.type = type;
            this.levelAdjustment = levelAdjustment;
        }
    }
    
    /**
     * Constructor
     * @param profileBuilder Profile builder containing bridge data
     * @param bridgeInfoCache Cache for bridge information by source PK
     */
    public BridgeSourceProcessor(ProfileBuilder profileBuilder, Map<Long, BridgeInfo> bridgeInfoCache) {
        this.profileBuilder = profileBuilder;
        this.bridgeInfoCache = bridgeInfoCache;
        this.bridgeAcousticCache = new java.util.HashMap<>();
        this.virtualSourceLevelsBySource = new java.util.HashMap<>();
    }
    
    /**
     * Constructor with pre-calculated acoustic levels
     * @param profileBuilder Profile builder containing bridge data
     * @param bridgeInfoCache Cache for bridge information by source PK
     * @param bridgeAcousticCache Pre-calculated acoustic levels for bridge processing
     */
    public BridgeSourceProcessor(ProfileBuilder profileBuilder, Map<Long, BridgeInfo> bridgeInfoCache,
                               Map<Long, BridgeAcousticLevels> bridgeAcousticCache) {
        this.profileBuilder = profileBuilder;
        this.bridgeInfoCache = bridgeInfoCache;
        this.bridgeAcousticCache = bridgeAcousticCache != null ? bridgeAcousticCache : new java.util.HashMap<>();
        this.virtualSourceLevelsBySource = new java.util.HashMap<>();
    }
    
    /**
     * Constructor with pre-calculated acoustic levels and virtual source levels by source
     * @param profileBuilder Profile builder containing bridge data
     * @param bridgeInfoCache Cache for bridge information by source PK
     * @param bridgeAcousticCache Pre-calculated acoustic levels for bridge processing
     * @param virtualSourceLevelsBySource Virtual source levels indexed by source primary key
     */
    public BridgeSourceProcessor(ProfileBuilder profileBuilder, Map<Long, BridgeInfo> bridgeInfoCache,
                               Map<Long, BridgeAcousticLevels> bridgeAcousticCache,
                               Map<Long, double[]> virtualSourceLevelsBySource) {
        this.profileBuilder = profileBuilder;
        this.bridgeInfoCache = bridgeInfoCache;
        this.bridgeAcousticCache = bridgeAcousticCache != null ? bridgeAcousticCache : new java.util.HashMap<>();
        this.virtualSourceLevelsBySource = virtualSourceLevelsBySource != null ? virtualSourceLevelsBySource : new java.util.HashMap<>();
    }
    
    /**
     * Process bridge adjustments for a point source.
     * This method generates additional sources based on bridge configuration.
     * 
     * @param sourcePk Source primary key
     * @param originalPosition Original point source position
     * @param li Length factor from line source subdivision
     * @param orientation Source orientation
     * @return List of additional sources to be added (empty if no bridge processing needed)
     */
    public List<AdditionalSource> processBridgeAdjustments(long sourcePk, Coordinate originalPosition, 
                                                         double li, Orientation orientation) {
        List<AdditionalSource> additionalSources = new ArrayList<>();
        
        // Check if bridge processing is needed
        if (profileBuilder == null || profileBuilder.getBridgeCount() == 0) {
            return additionalSources;
        }
        
        BridgeInfo bridgeInfo = bridgeInfoCache.get(sourcePk);
        if (bridgeInfo == null) {
            return additionalSources; // No bridge information for this source
        }
        
        // Find the target bridge
        Bridge targetBridge = findTargetBridge(originalPosition, bridgeInfo);
        if (targetBridge == null) {
            return additionalSources; // No applicable bridge found
        }
        
        // Process different types of bridge sources
        processHeightAdjustment(originalPosition, targetBridge, li, orientation, additionalSources);
        processVirtualSources(originalPosition, targetBridge, li, orientation, additionalSources, bridgeInfo);
        processMirrorSources(originalPosition, targetBridge, li, orientation, additionalSources);
        
        return additionalSources;
    }
    
    /**
     * Find the target bridge for a source position based on bridge information.
     * 
     * @param position Source position
     * @param bridgeInfo Bridge information for the source
     * @return Target bridge or null if not found
     */
    private Bridge findTargetBridge(Coordinate position, BridgeInfo bridgeInfo) {
        Bridge targetBridge = null;
        
        if (bridgeInfo.isOnBridge) {
            // Find bridge containing this source position (no overlaps allowed)
            for (int i = 0; i < profileBuilder.getBridgeCount(); i++) {
                Bridge bridge = profileBuilder.getBridge(i);
                if (bridge.isPointWithinBridgeFootprint(position)) {
                    if (targetBridge == null) {
                        targetBridge = bridge;
                    } else {
                        // Multiple bridges overlap - skip processing
                        return null;
                    }
                }
            }
        } else if (bridgeInfo.bridgePk != null) {
            // Find specific bridge by primary key
            for (int i = 0; i < profileBuilder.getBridgeCount(); i++) {
                Bridge bridge = profileBuilder.getBridge(i);
                if (bridge.getPrimaryKey() == bridgeInfo.bridgePk && 
                    bridge.isPointWithinBridgeFootprint(position)) {
                    targetBridge = bridge;
                    break;
                }
            }
        }
        
        return targetBridge;
    }
    
    /**
     * Process height adjustment for sources on bridges.
     * Adjusts the original source height to bridge deck level.
     * 
     * @param originalPosition Original source position
     * @param bridge Target bridge
     * @param li Length factor
     * @param orientation Source orientation
     * @param additionalSources List to add additional sources to
     */
    private void processHeightAdjustment(Coordinate originalPosition, Bridge bridge, double li, 
                                       Orientation orientation, List<AdditionalSource> additionalSources) {
        double deckHeight = bridge.getDeckHeightAtPoint(originalPosition);
        if (!Double.isNaN(deckHeight)) {
            // Create height-adjusted source
            double adjustedHeight = deckHeight + originalPosition.z; // Add original height offset
            Coordinate adjustedPosition = new Coordinate(originalPosition.x, originalPosition.y, adjustedHeight);
            
            // No level adjustment needed for height adjustment
            additionalSources.add(new AdditionalSource(adjustedPosition, li, orientation, 
                                                     AdditionalSource.SourceType.HEIGHT_ADJUSTED, 0.0));
        }
    }
    
    /**
     * Process virtual sources at bridge bottom for structural noise transmission.
     * 
     * @param originalPosition Original source position
     * @param bridge Target bridge
     * @param li Length factor
     * @param orientation Source orientation
     * @param additionalSources List to add additional sources to
     * @param bridgeInfo Bridge information for the source
     */
    private void processVirtualSources(Coordinate originalPosition, Bridge bridge, double li, 
                                     Orientation orientation, List<AdditionalSource> additionalSources,
                                     BridgeInfo bridgeInfo) {
        // Only generate virtual sources for sources that are on bridges
        if (!bridgeInfo.isOnBridge && bridgeInfo.bridgePk == null) {
            return;
        }
        
        double deckHeight = bridge.getDeckHeightAtPoint(originalPosition);
        if (!Double.isNaN(deckHeight)) {
            // Get actual bridge properties
            double deckThickness = bridge.getDeckThicknessAtPoint(originalPosition);
            if (Double.isNaN(deckThickness)) {
                deckThickness = 0.5; // Default thickness in meters
            }
            
            // Create virtual source at bridge bottom
            double bottomHeight = deckHeight - deckThickness;
            Coordinate virtualPosition = new Coordinate(originalPosition.x, originalPosition.y, bottomHeight);
            
            // Try to use pre-calculated acoustic levels first
            double levelAdjustment = 0.0;
            BridgeAcousticLevels acousticLevels = bridgeAcousticCache.get(
                bridgeInfo.bridgePk != null ? bridgeInfo.bridgePk : -1L);
            
            if (acousticLevels != null && acousticLevels.virtualSourceAdjustments.length > 0) {
                // Use pre-calculated level adjustment (average across frequency bands for now)
                double sum = 0.0;
                for (double adj : acousticLevels.virtualSourceAdjustments) {
                    sum += adj;
                }
                levelAdjustment = sum / acousticLevels.virtualSourceAdjustments.length;
            } else {
                // Fallback to dynamic calculation if no pre-calculated levels available
                double trafficFlow = getTrafficFlowForSource(bridgeInfo);
                double speed = getSpeedForSource(bridgeInfo);
                String material = getBridgeMaterial(bridge);
                levelAdjustment = calculateVirtualSourceLevelAdjustment(trafficFlow, speed, deckThickness, material);
            }
            
            additionalSources.add(new AdditionalSource(virtualPosition, li, orientation, 
                                                     AdditionalSource.SourceType.VIRTUAL_BRIDGE_BOTTOM, levelAdjustment));
        }
    }
    
    /**
     * Process mirror image sources for bridge reflections.
     * 
     * @param originalPosition Original source position
     * @param bridge Target bridge
     * @param li Length factor
     * @param orientation Source orientation
     * @param additionalSources List to add additional sources to
     */
    private void processMirrorSources(Coordinate originalPosition, Bridge bridge, double li, 
                                    Orientation orientation, List<AdditionalSource> additionalSources) {
        double deckHeight = bridge.getDeckHeightAtPoint(originalPosition);
        if (!Double.isNaN(deckHeight)) {
            double deckThickness = bridge.getDeckThicknessAtPoint(originalPosition);
            if (Double.isNaN(deckThickness)) {
                deckThickness = 0.5; // Default thickness
            }
            double bottomHeight = deckHeight - deckThickness;
            
            // Only create mirror sources if bridge bottom is higher than source
            if (bottomHeight > originalPosition.z) {
                // Calculate mirror position
                double distanceToBottom = bottomHeight - originalPosition.z;
                double mirrorHeight = bottomHeight + distanceToBottom;
                
                Coordinate mirrorPosition = new Coordinate(originalPosition.x, originalPosition.y, mirrorHeight);
                
                // Try to use pre-calculated acoustic levels first
                double levelAdjustment = 0.0;
                BridgeAcousticLevels acousticLevels = bridgeAcousticCache.get(
                    findBridgePkForPosition(originalPosition));
                
                if (acousticLevels != null && acousticLevels.mirrorSourceAdjustments.length > 0) {
                    // Use pre-calculated level adjustment (average across frequency bands for now)
                    double sum = 0.0;
                    for (double adj : acousticLevels.mirrorSourceAdjustments) {
                        sum += adj;
                    }
                    levelAdjustment = sum / acousticLevels.mirrorSourceAdjustments.length;
                } else {
                    // Fallback to dynamic calculation if no pre-calculated levels available
                    String bridgeMaterial = getBridgeMaterial(bridge);
                    double reflectionCoefficient = getBridgeReflectionCoefficient(bridge);
                    levelAdjustment = calculateMirrorSourceLevelAdjustment(1000.0, reflectionCoefficient, bridgeMaterial);
                }
                
                additionalSources.add(new AdditionalSource(mirrorPosition, li, orientation, 
                                                         AdditionalSource.SourceType.MIRROR_IMAGE, levelAdjustment));
            }
        }
    }
    
    /**
     * Calculate level adjustment for virtual sources at bridge bottom.
     * This is a simplified version that provides a single dB adjustment value.
     * 
     * @param trafficFlow Traffic flow in vehicles per hour
     * @param speed Vehicle speed in km/h
     * @param thickness Bridge deck thickness in meters
     * @param material Bridge deck material
     * @return Level adjustment in dB (negative value for reduction)
     */
    private double calculateVirtualSourceLevelAdjustment(double trafficFlow, double speed, 
                                                       double thickness, String material) {
        // Simplified structural transmission calculation
        // Based on typical values for concrete bridge decks
        double baseReduction = 15.0; // Base reduction for structural transmission
        double thicknessReduction = Math.max(0, (thickness - 0.2) * 5.0); // Additional reduction for thickness
        double materialFactor = "concrete".equalsIgnoreCase(material) ? 1.0 : 0.8; // Material adjustment
        
        return -(baseReduction + thicknessReduction) * materialFactor;
    }
    
    /**
     * Calculate level adjustment for mirror image sources.
     * This is a simplified version that provides a single dB adjustment value.
     * 
     * @param frequency Frequency in Hz
     * @param reflectionCoefficient Reflection coefficient (0.0 to 1.0)
     * @param material Surface material
     * @return Level adjustment in dB (negative value for reduction)
     */
    private double calculateMirrorSourceLevelAdjustment(double frequency, double reflectionCoefficient, 
                                                      String material) {
        // Simplified reflection calculation
        // Convert reflection coefficient to dB
        if (reflectionCoefficient <= 0) {
            return -100.0; // Essentially no reflection
        }
        
        double reflectionLoss = 20.0 * Math.log10(reflectionCoefficient);
        
        // Add frequency-dependent absorption for concrete
        double absorptionLoss = 0.0;
        if ("concrete".equalsIgnoreCase(material)) {
            if (frequency < 500) {
                absorptionLoss = 1.0;
            } else if (frequency < 2000) {
                absorptionLoss = 2.0;
            } else {
                absorptionLoss = 3.0;
            }
        }
        
        return reflectionLoss - absorptionLoss;
    }
    
    /**
     * Get bridge material from bridge properties.
     * 
     * @param bridge Bridge instance
     * @return Bridge material (default: "concrete")
     */
    private String getBridgeMaterial(Bridge bridge) {
        // TODO: Implement bridge material retrieval from bridge properties
        // For now, return default concrete material
        // In future implementation, this should access bridge.getMaterial() or similar
        return "concrete";
    }
    
    /**
     * Get traffic flow for a source from bridge information.
     * 
     * @param bridgeInfo Bridge information
     * @return Traffic flow in vehicles per hour (default: 1000)
     */
    private double getTrafficFlowForSource(BridgeInfo bridgeInfo) {
        // TODO: Implement traffic flow retrieval from source data
        // This would require access to the original source emission data
        // For now, return reasonable default value
        // In future implementation, this should access source traffic data
        return 1000.0; // Default traffic flow
    }
    
    /**
     * Get speed for a source from bridge information.
     * 
     * @param bridgeInfo Bridge information
     * @return Speed in km/h (default: 50)
     */
    private double getSpeedForSource(BridgeInfo bridgeInfo) {
        // TODO: Implement speed retrieval from source data
        // This would require access to the original source emission data
        // For now, return reasonable default value
        // In future implementation, this should access source speed data
        return 50.0; // Default speed
    }
    
    /**
     * Get bridge reflection coefficient from bridge properties.
     * 
     * @param bridge Bridge instance
     * @return Reflection coefficient (default: 0.8 for concrete)
     */
    private double getBridgeReflectionCoefficient(Bridge bridge) {
        // TODO: Implement reflection coefficient retrieval from bridge properties
        // For now, return default concrete reflection coefficient
        // In future implementation, this should access bridge.getReflectionCoefficient() or similar
        return 0.8; // Default concrete reflection coefficient
    }
    
    /**
     * Find bridge primary key for a given position.
     * This method searches through available bridges to find the one containing the position.
     * 
     * @param position Position to search for
     * @return Bridge primary key, or -1 if not found
     */
    private long findBridgePkForPosition(Coordinate position) {
        if (profileBuilder == null) {
            return -1L;
        }
        
        for (int i = 0; i < profileBuilder.getBridgeCount(); i++) {
            Bridge bridge = profileBuilder.getBridge(i);
            if (bridge.isPointWithinBridgeFootprint(position)) {
                return bridge.getPrimaryKey();
            }
        }
        
        return -1L;
    }
    
    /**
     * Get virtual source levels for a specific source.
     * This method returns pre-calculated virtual source levels for the given source.
     * 
     * @param sourcePk Source primary key
     * @return Virtual source levels by frequency band, or null if not available
     */
    public double[] getVirtualSourceLevels(long sourcePk) {
        return virtualSourceLevelsBySource.get(sourcePk);
    }
    
    /**
     * Check if virtual source levels are available for a specific source.
     * 
     * @param sourcePk Source primary key
     * @return true if virtual source levels are available
     */
    public boolean hasVirtualSourceLevels(long sourcePk) {
        return virtualSourceLevelsBySource.containsKey(sourcePk);
    }
}
