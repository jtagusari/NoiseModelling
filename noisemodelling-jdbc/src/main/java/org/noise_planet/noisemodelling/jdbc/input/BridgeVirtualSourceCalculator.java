// /**
//  * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
//  * <p>
//  * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
//  * <p>
//  * Official webpage : http://noise-planet.org/noisemodelling.html
//  * Contact: contact@noise-planet.org
//  */
package org.noise_planet.noisemodelling.jdbc.input;

// import org.locationtech.jts.geom.Geometry;
// import org.noise_planet.noisemodelling.pathfinder.BridgeSourceProcessor;
// import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
// import org.noise_planet.noisemodelling.pathfinder.utils.AcousticIndicatorsFunctions;

// import java.util.*;

// /**
//  * Calculator for bridge virtual sources and structural noise transmission.
//  * This class handles all bridge-related calculations separately from SceneWithEmission
//  * to maintain better code organization and separation of concerns.
//  */
public class BridgeVirtualSourceCalculator {
    
//     private final ProfileBuilder profileBuilder;
    
//     /**
//      * Constructor
//      * 
//      * @param profileBuilder Profile builder containing bridge information
//      * @param sourceEmissionFieldsCache Source emission fields cache (unused but kept for API compatibility)
//      */
//     public BridgeVirtualSourceCalculator(ProfileBuilder profileBuilder, 
//                                        Map<String, Integer> sourceEmissionFieldsCache) {
//         this.profileBuilder = profileBuilder;
//         // sourceEmissionFieldsCache is not used in this implementation but kept for API compatibility
//     }
    
//     /**
//      * Add virtual sources to the scene for structural noise transmission calculation.
//      * This method creates actual virtual sources in the scene based on bridge information
//      * and adds them with proper emission levels calculated using structural methodology.
//      * 
//      * @param sceneWithEmission The scene to add virtual sources to
//      * @param bridgeInfoCache Bridge information cache
//      * @return Number of virtual sources added to the scene
//      */
//     public int addVirtualSourcesToScene(SceneWithEmission sceneWithEmission,
//                                       Map<Long, BridgeSourceProcessor.BridgeInfo> bridgeInfoCache) {
//         int virtualSourcesAdded = 0;
        
//         if (profileBuilder == null || profileBuilder.getBridgeCount() == 0) {
//             return virtualSourcesAdded; // No bridges to process
//         }
        
//         // Get frequency array from profile builder
//         List<Integer> frequencyArray = profileBuilder.getFrequencyArray();
//         if (frequencyArray == null || frequencyArray.isEmpty()) {
//             return virtualSourcesAdded; // No frequency data available
//         }
        
//         // Process each source with bridge information
//         for (int i = 0; i < sceneWithEmission.getSourceCount(); i++) {
//             Long originalSourcePk = sceneWithEmission.getSourcePkById(i);
//             BridgeSourceProcessor.BridgeInfo bridgeInfo = bridgeInfoCache.get(originalSourcePk);
            
//             // Only process sources that are on bridges (not already virtual sources)
//             if (bridgeInfo != null && bridgeInfo.isOnBridge && !bridgeInfo.isVirtualSource && bridgeInfo.bridgePk != null) {
//                 // Find the corresponding bridge
//                 org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge bridge = findBridgeByPk(bridgeInfo.bridgePk);
//                 if (bridge != null) {
//                     // Create virtual source for structural transmission
//                     Long virtualSourcePk = createVirtualSource(sceneWithEmission, originalSourcePk, bridge, bridgeInfo);
//                     if (virtualSourcePk != null) {
//                         virtualSourcesAdded++;
//                     }
//                 }
//             }
//         }
        
//         return virtualSourcesAdded;
//     }
    
//     /**
//      * Find bridge by primary key.
//      * 
//      * @param bridgePk Bridge primary key
//      * @return Bridge instance or null if not found
//      */
//     private org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge findBridgeByPk(Long bridgePk) {
//         if (profileBuilder == null || bridgePk == null) {
//             return null;
//         }
        
//         for (int i = 0; i < profileBuilder.getBridgeCount(); i++) {
//             org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge bridge = profileBuilder.getBridge(i);
//             if (bridge.getPrimaryKey() == bridgePk.longValue()) {
//                 return bridge;
//             }
//         }
        
//         return null;
//     }
    
//     /**
//      * Create a virtual source for structural noise transmission based on an original source.
//      * The virtual source inherits all attributes from the original source except for the primary key
//      * and has isVirtualSource set to true.
//      * 
//      * @param sceneWithEmission The scene to add the virtual source to
//      * @param originalSourcePk Primary key of the original source
//      * @param bridge Bridge associated with the virtual source
//      * @param bridgeInfo Bridge information for the original source
//      * @return Primary key of the created virtual source, or null if creation failed
//      */
//     private Long createVirtualSource(SceneWithEmission sceneWithEmission,
//                                    Long originalSourcePk, 
//                                    org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge bridge,
//                                    BridgeSourceProcessor.BridgeInfo bridgeInfo) {
//         try {
//             // Find the original source index
//             int originalSourceIndex = sceneWithEmission.getSourcePks().indexOf(originalSourcePk);
//             if (originalSourceIndex < 0) {
//                 return null; // Original source not found
//             }

//             // Get original source geometry and create virtual source geometry
//             Geometry originalGeometry = sceneWithEmission.getSourceGeometryByIndex(originalSourceIndex);
//             Geometry virtualGeometry = originalGeometry.copy();

//             if (virtualGeometry == null) {
//                 return null; // Failed to create virtual geometry
//             }

//             // Prepare attributes by copying from original source (defensive)
//             Double gs = sceneWithEmission.sourceGs.containsKey(originalSourcePk) ? sceneWithEmission.sourceGs.get(originalSourcePk) : null;
//             Integer emissionAttenuation = sceneWithEmission.sourceEmissionAttenuation.containsKey(originalSourcePk) ? sceneWithEmission.sourceEmissionAttenuation.get(originalSourcePk) : null;
//             org.noise_planet.noisemodelling.pathfinder.utils.geometry.Orientation orientation = sceneWithEmission.getSourceOrientations().get(originalSourcePk);
//             Long bridgePk = sceneWithEmission.sourceBridgePk.get(originalSourcePk);

//             // Compute virtual emissions without mutating the scene
//             ArrayList<SceneWithEmission.PeriodEmission> virtualEmissions = computeVirtualEmissions(sceneWithEmission, originalSourcePk, bridge);

//             // Build VirtualSourceData and register via SceneVirtualSourceRegistrar to ensure consistent PK generation and registration logic
//             VirtualSourceBuilder.VirtualSourceData vs = new VirtualSourceBuilder.VirtualSourceData(
//                     virtualGeometry, gs, emissionAttenuation, orientation, bridgePk, virtualEmissions);

//             Long assignedPk = SceneVirtualSourceRegistrar.register(sceneWithEmission, originalSourcePk, vs);
//             return assignedPk;

//         } catch (Exception e) {
//             // Log error and continue processing other sources
//             System.err.println("Failed to create virtual source for " + originalSourcePk + ": " + e.getMessage());
//             return null;
//         }
//     }

//     /**
//      * Compute virtual emissions for a virtual source without mutating the scene.
//      * Returns a list of PeriodEmission to be attached to the virtual source data.
//      */
//     private ArrayList<SceneWithEmission.PeriodEmission> computeVirtualEmissions(SceneWithEmission sceneWithEmission,
//                                                                              Long originalSourcePk,
//                                                                              org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge bridge) {
//         ArrayList<SceneWithEmission.PeriodEmission> result = new ArrayList<>();
//         try {
//             // Attempt structural calculation for standard periods D/E/N
//             String[] periods = {"D", "E", "N"};
//             double[] periodTrafficFactors = {1.0, 0.7, 0.3};
//             List<Integer> frequencyArray = profileBuilder.getFrequencyArray();
//             SourceTrafficData trafficData = getSourceTrafficData(sceneWithEmission, originalSourcePk);
//             for (int periodIndex = 0; periodIndex < periods.length; periodIndex++) {
//                 double[] structuralLevels = calculateStructuralLevelsForPeriod(trafficData, bridge, periodTrafficFactors[periodIndex], frequencyArray);
//                 if (structuralLevels != null && structuralLevels.length > 0) {
//                     result.add(new SceneWithEmission.PeriodEmission(periods[periodIndex], structuralLevels));
//                 }
//             }

//             // Do not fall back to a heuristic traffic-based calculation here. If no structural
//             // emission periods were produced, treat it as an error so callers are aware.
//             if (result.isEmpty()) {
//                 throw new RuntimeException("computeVirtualEmissions: structural calculation produced no emission periods for source "
//                         + originalSourcePk + " (bridgePk=" + (bridge != null ? bridge.getPrimaryKey() : "null") + ")");
//             }

//             return result;
//         } catch (Exception e) {
//             // Propagate a clear runtime exception so higher-level code can handle or fail loudly.
//             throw new RuntimeException("computeVirtualEmissions failed for source " + originalSourcePk
//                     + " on bridge " + (bridge != null ? bridge.getPrimaryKey() : "null") + ": " + e.getMessage(), e);
//         }
//     }
    
    
//     /**
//      * Calculate structural levels for a specific period using bridge properties and traffic data.
//      * 
//      * @param trafficData Traffic data for the source
//      * @param bridge Bridge properties
//      * @param trafficFactor Period-specific traffic factor
//      * @param frequencyArray Frequency array for calculations
//      * @return Structural levels in W units for each frequency
//      */
//     private double[] calculateStructuralLevelsForPeriod(SourceTrafficData trafficData, 
//                                                        org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge bridge,
//                                                        double trafficFactor,
//                                                        List<Integer> frequencyArray) {
//         try {
//             // Prepare traffic parameters for this period.
//             // Prefer explicit LV/MV/HV counts if available in SourceTrafficData; otherwise
//             // fall back to distributing totalTrafficCount proportionally (but avoid 70/30 hardcode).
//             double periodTrafficCount = trafficData.totalTrafficCount * trafficFactor;

//             // Use available per-class counts when possible. If counts are zero, distribute
//             // proportionally based on availability: prefer LV, then MV, then HV.
//             double lv = trafficData.lightVehicleCount > 0 ? trafficData.lightVehicleCount * trafficFactor : 0.0;
//             double mv = trafficData.mediumVehicleCount > 0 ? trafficData.mediumVehicleCount * trafficFactor : 0.0;
//             double hv = trafficData.heavyVehicleCount > 0 ? trafficData.heavyVehicleCount * trafficFactor : 0.0;

//             double specifiedSum = lv + mv + hv;
//             if (specifiedSum <= 0.0) {
//                 // No per-class info available — distribute from total using a reasonable default split
//                 // but avoid hardcoded 70/30: use LV=60%, MV=10%, HV=30% as a slightly more refined default.
//                 lv = periodTrafficCount * 0.6;
//                 mv = periodTrafficCount * 0.1;
//                 hv = periodTrafficCount * 0.3;
//             } else if (Math.abs(specifiedSum - periodTrafficCount) > 1e-6) {
//                 // Normalize specified counts to match periodTrafficCount proportionally
//                 double scale = periodTrafficCount / specifiedSum;
//                 lv *= scale;
//                 mv *= scale;
//                 hv *= scale;
//             }
            
//             // Calculate base emission levels for traffic
//             double[] structuralLevels = new double[frequencyArray.size()];
            
//             for (int f = 0; f < frequencyArray.size(); f++) {
//                 Integer freq = frequencyArray.get(f);
                
//                 // Calculate base traffic emission level for this frequency
//                 // Per request: small cars = LV, large vehicles = MV + HGV
//                 double baseEmissionDb = calculateBaseTrafficEmission(lv, mv + hv, 
//                                                                    trafficData.averageSpeed, freq);
                
//                 // Apply structural transmission corrections
//                 double structuralCorrection = getStructuralTransmissionCorrection(freq, bridge);
//                 double correctedLevelDb = baseEmissionDb + structuralCorrection;
                
//                 // Convert to W units
//                 structuralLevels[f] = AcousticIndicatorsFunctions.dBToW(correctedLevelDb);
//             }
            
//             return structuralLevels;
            
//         } catch (Exception e) {
//             System.err.println("Failed to calculate structural levels for period: " + e.getMessage());
//             return null;
//         }
//     }
    
//     /**
//      * Calculate base traffic emission level for a specific frequency.
//      * This is a simplified emission calculation based on traffic parameters.
//      * 
//      * @param lightVehicles Number of light vehicles per hour
//      * @param heavyVehicles Number of heavy vehicles per hour
//      * @param averageSpeed Average speed in km/h
//      * @param frequency Frequency in Hz
//      * @return Base emission level in dB
//      */
//     private double calculateBaseTrafficEmission(double lightVehicles, double heavyVehicles, 
//                                               double averageSpeed, Integer frequency) {
//         // Simplified emission calculation
//         // Base levels for different vehicle types (typical values)
//         double lightVehicleBase = 65.0; // dB at reference conditions
//         double heavyVehicleBase = 75.0; // dB at reference conditions
        
//         // Speed correction (simplified)
//         double speedCorrection = 0.0;
//         if (averageSpeed > 50) {
//             speedCorrection = (averageSpeed - 50) * 0.1; // 0.1 dB per km/h above 50
//         }
        
//         // Frequency correction
//         double frequencyCorrection = 0.0;
//         if (frequency < 500) {
//             frequencyCorrection = -3.0; // Lower levels at low frequencies
//         } else if (frequency > 2000) {
//             frequencyCorrection = -5.0; // Lower levels at high frequencies
//         }
        
//         // Calculate total emission
//         double lightContribution = lightVehicleBase + 10 * Math.log10(lightVehicles / 1000.0); // Per 1000 vehicles reference
//         double heavyContribution = heavyVehicleBase + 10 * Math.log10(heavyVehicles / 100.0);  // Per 100 vehicles reference
        
//         // Combine contributions
//         double totalEmission = 10 * Math.log10(
//             Math.pow(10, lightContribution / 10.0) + Math.pow(10, heavyContribution / 10.0)
//         );
        
//         return totalEmission + speedCorrection + frequencyCorrection;
//     }
    
//     /**
//      * Get structural transmission correction based on frequency and bridge properties.
//      * This method applies frequency-dependent corrections for structural transmission.
//      * 
//      * @param frequency Frequency in Hz
//      * @param bridge Bridge properties
//      * @return Structural transmission correction in dB
//      */
//     private double getStructuralTransmissionCorrection(Integer frequency, 
//                                                       org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge bridge) {
//         // Base structural transmission loss (simplified model)
//         double baseTransmissionLoss = -10.0; // Base reduction for structural transmission
        
//         // Frequency-dependent correction
//         double frequencyCorrection = 0.0;
//         if (frequency < 250) {
//             frequencyCorrection = -5.0; // Higher transmission at low frequencies
//         } else if (frequency < 1000) {
//             frequencyCorrection = -8.0; // Moderate transmission at mid frequencies
//         } else {
//             frequencyCorrection = -12.0; // Lower transmission at high frequencies
//         }
        
//         // Bridge material correction
//         double materialCorrection = 0.0;
//         String girderType = bridge.getGirderType().name();
//         if (girderType.contains("STEEL")) {
//             materialCorrection = -2.0; // Steel transmits more vibration
//         } else if (girderType.contains("CONCRETE")) {
//             materialCorrection = -5.0; // Concrete provides more damping
//         }
        
//         return baseTransmissionLoss + frequencyCorrection + materialCorrection;
//     }
    
    
    
//     /**
//      * Get traffic data for a specific source.
//      * 
//      * @param sceneWithEmission The scene containing the sources
//      * @param sourcePk Source primary key
//      * @return Traffic data for the source
//      */
//     private SourceTrafficData getSourceTrafficData(SceneWithEmission sceneWithEmission, Long sourcePk) {
//         // Try to get data from cache first
//         SourceDataCache.OriginalSourceInfo cachedInfo = sceneWithEmission.getOriginalSourceInfo(sourcePk);
//         if (cachedInfo != null) {
//             double totalTrafficCount = 0.0;
//             double averageSpeed = cachedInfo.averageSpeed;
            
//             // Calculate total traffic count from day period data
//             Double lvD = cachedInfo.getTrafficCount("LV", "_D");
//             Double mvD = cachedInfo.getTrafficCount("MV", "_D");
//             Double hvD = cachedInfo.getTrafficCount("HV", "_D");

//             if (lvD != null) totalTrafficCount += lvD;
//             if (mvD != null) totalTrafficCount += mvD;
//             if (hvD != null) totalTrafficCount += hvD;

//             // If no explicit per-class data available, try previous heuristics
//             if (totalTrafficCount <= 0.0) {
//                 if (lvD != null && hvD == null && mvD == null) {
//                     totalTrafficCount = lvD * 1.3; // estimate
//                 } else if (hvD != null && lvD == null && mvD == null) {
//                     totalTrafficCount = hvD * 3.3; // estimate
//                 }
//             }

//             double lvCount = lvD != null ? lvD : 0.0;
//             double mvCount = mvD != null ? mvD : 0.0;
//             double hvCount = hvD != null ? hvD : 0.0;

//             // If none of the class counts are present, distribute defaults (60/10/30)
//             if (lvCount + mvCount + hvCount <= 0.0) {
//                 lvCount = totalTrafficCount * 0.6;
//                 mvCount = totalTrafficCount * 0.1;
//                 hvCount = totalTrafficCount * 0.3;
//             }

//             return new SourceTrafficData(totalTrafficCount, averageSpeed,
//                                        lvCount, mvCount, hvCount);
//         }
        
//     // Fallback to default values (total=1000, split 60/10/30)
//     return new SourceTrafficData(1000.0, 50.0, 600.0, 100.0, 300.0);
//     }
    
//     /**
//      * Data class to hold source-specific traffic information.
//      */
//     private static class SourceTrafficData {
//         final double totalTrafficCount;
//         final double averageSpeed;
//         final double lightVehicleCount;
//         final double mediumVehicleCount;
//         final double heavyVehicleCount;
        
//         SourceTrafficData(double totalTrafficCount, double averageSpeed, 
//                          double lightVehicleCount, double mediumVehicleCount, double heavyVehicleCount) {
//             this.totalTrafficCount = totalTrafficCount;
//             this.averageSpeed = averageSpeed;
//             this.lightVehicleCount = lightVehicleCount;
//             this.mediumVehicleCount = mediumVehicleCount;
//             this.heavyVehicleCount = heavyVehicleCount;
//         }
//     }
}
