/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.emission.road.asj;

import java.io.IOException;

/**
 * RoadSource parameters for ASJ (Acoustical Society of Japan) method
 * Based on Japanese road noise emission calculation methodology
 *
 * @author NoiseModelling Team
 */
public class RoadAsjParameters {
    
    // Vehicle flow rates (vehicles per hour)
    private double lvPerHour; // Light vehicles
    private double mvPerHour; // Medium vehicles  
    private double hgvPerHour; // Heavy vehicles
    private double wavPerHour; // Two-wheel vehicles (light)
    private double wbvPerHour; // Two-wheel vehicles (heavy)

    // Vehicle speeds (km/h)
    private double speedLv; // Light vehicle speed
    private double speedMv; // Medium vehicle speed
    private double speedHgv; // Heavy vehicle speed
    private double speedWav; // Light 2-wheel speed
    private double speedWbv; // Heavy 2-wheel speed

    // Environmental parameters
    private int frequency; // Frequency in Hz (octave band)
    private double temperature; // Temperature in °C
    private String roadSurface; // Road surface type identifier
    
    // Road geometry
    private double slopePercentage = 0; // Slope in %
    private int way = 1; // 1=direct, 2=inverse, 3=bidirectional
    
    // Bridge parameters for virtual source calculation
    private boolean hasBridge = false;
    private String bridgeGirderType = "STEEL_BOX"; // STEEL_BOX, STEEL_PLATE, CONCRETE_BOX, etc.
    private String bridgeSlabType = "CONCRETE"; // CONCRETE, STEEL
    private double bridgeThickness = 0.5; // Bridge deck thickness in meters
    private double bridgeLength = 0.0; // Bridge length in meters
    private double bridgeWidth = 0.0; // Bridge width in meters
    
    // Traffic characteristics for bridge calculation
    private double totalTrafficFlow = 0.0; // Total vehicles per hour
    private double averageSpeed = 50.0; // Average speed in km/h
    
    // ASJ specific parameters
    private int coefficientVersion = 1; // ASJ coefficient version

    /**
     * Default constructor
     */
    public RoadAsjParameters() {
    }

    /**
     * Constructor for road noise evaluation with ASJ method
     * 
     * @param lv_speed Light vehicle speed
     * @param mv_speed Medium vehicle speed
     * @param hgv_speed Heavy vehicle speed
     * @param wav_speed Light 2-wheel speed
     * @param wbv_speed Heavy 2-wheel speed
     * @param lvPerHour Light vehicles per hour
     * @param mvPerHour Medium vehicles per hour
     * @param hgvPerHour Heavy vehicles per hour
     * @param wavPerHour Light 2-wheels per hour
     * @param wbvPerHour Heavy 2-wheels per hour
     * @param frequency Frequency in Hz
     * @param temperature Temperature in °C
     * @param roadSurface Road surface type
     */
    public RoadAsjParameters(double lv_speed, double mv_speed, double hgv_speed, 
                           double wav_speed, double wbv_speed, double lvPerHour, 
                           double mvPerHour, double hgvPerHour, double wavPerHour, 
                           double wbvPerHour, int frequency, double temperature, 
                           String roadSurface) {
        
        // Validation
        if (lvPerHour < 0) throw new IllegalArgumentException("Light vehicle flow must be >= 0");
        if (mvPerHour < 0) throw new IllegalArgumentException("Medium vehicle flow must be >= 0");
        if (hgvPerHour < 0) throw new IllegalArgumentException("Heavy vehicle flow must be >= 0");
        if (wavPerHour < 0) throw new IllegalArgumentException("Light 2-wheel flow must be >= 0");
        if (wbvPerHour < 0) throw new IllegalArgumentException("Heavy 2-wheel flow must be >= 0");
        
        if (lv_speed < 0) throw new IllegalArgumentException("Light vehicle speed must be >= 0");
        if (mv_speed < 0) throw new IllegalArgumentException("Medium vehicle speed must be >= 0");
        if (hgv_speed < 0) throw new IllegalArgumentException("Heavy vehicle speed must be >= 0");
        if (wav_speed < 0) throw new IllegalArgumentException("Light 2-wheel speed must be >= 0");
        if (wbv_speed < 0) throw new IllegalArgumentException("Heavy 2-wheel speed must be >= 0");
        
        // Set values
        this.lvPerHour = Math.max(0, lvPerHour);
        this.mvPerHour = Math.max(0, mvPerHour);
        this.hgvPerHour = Math.max(0, hgvPerHour);
        this.wavPerHour = Math.max(0, wavPerHour);
        this.wbvPerHour = Math.max(0, wbvPerHour);
        
        this.speedLv = lv_speed;
        this.speedMv = mv_speed;
        this.speedHgv = hgv_speed;
        this.speedWav = wav_speed;
        this.speedWbv = wbv_speed;
        
        this.frequency = Math.max(0, frequency);
        this.temperature = temperature;
        this.roadSurface = roadSurface;
        
        // Calculate derived values
        this.totalTrafficFlow = lvPerHour + mvPerHour + hgvPerHour + wavPerHour + wbvPerHour;
        this.averageSpeed = calculateAverageSpeed();
    }

    /**
     * Calculate weighted average speed based on traffic flow
     */
    private double calculateAverageSpeed() {
        if (totalTrafficFlow <= 0) return 50.0; // Default speed
        
        double weightedSpeed = (speedLv * lvPerHour + speedMv * mvPerHour + 
                               speedHgv * hgvPerHour + speedWav * wavPerHour + 
                               speedWbv * wbvPerHour);
        
        return weightedSpeed / totalTrafficFlow;
    }

    // Getters and Setters
    
    public double getLvPerHour() { return lvPerHour; }
    public void setLvPerHour(double lvPerHour) { this.lvPerHour = Math.max(0, lvPerHour); }
    
    public double getMvPerHour() { return mvPerHour; }
    public void setMvPerHour(double mvPerHour) { this.mvPerHour = Math.max(0, mvPerHour); }
    
    public double getHgvPerHour() { return hgvPerHour; }
    public void setHgvPerHour(double hgvPerHour) { this.hgvPerHour = Math.max(0, hgvPerHour); }
    
    public double getWavPerHour() { return wavPerHour; }
    public void setWavPerHour(double wavPerHour) { this.wavPerHour = Math.max(0, wavPerHour); }
    
    public double getWbvPerHour() { return wbvPerHour; }
    public void setWbvPerHour(double wbvPerHour) { this.wbvPerHour = Math.max(0, wbvPerHour); }

    public double getSpeedLv() throws IOException {
        return speedLv < 20 ? 20 : speedLv; // Minimum 20 km/h
    }
    
    public double getSpeedMv() throws IOException {
        return speedMv < 20 ? 20 : speedMv;
    }
    
    public double getSpeedHgv() throws IOException {
        return speedHgv < 20 ? 20 : speedHgv;
    }
    
    public double getSpeedWav() throws IOException {
        return speedWav < 20 ? 20 : speedWav;
    }
    
    public double getSpeedWbv() throws IOException {
        return speedWbv < 20 ? 20 : speedWbv;
    }

    public int getFrequency() { return frequency; }
    public void setFrequency(int frequency) { this.frequency = frequency; }
    
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    
    public String getRoadSurface() { return roadSurface; }
    public void setRoadSurface(String roadSurface) { this.roadSurface = roadSurface; }
    
    public double getSlopePercentage() { return slopePercentage; }
    public void setSlopePercentage(double slopePercentage) {
        this.slopePercentage = Math.min(12., Math.max(-12., slopePercentage));
    }
    
    public int getWay() { return way; }
    public void setWay(int way) { this.way = way; }
    
    // Bridge parameters
    public boolean hasBridge() { return hasBridge; }
    public void setHasBridge(boolean hasBridge) { this.hasBridge = hasBridge; }

    public String getBridgeGirderType() { return bridgeGirderType; }
    public void setBridgeGirderType(String bridgeGirderType) { this.bridgeGirderType = bridgeGirderType; }
    
    public String getBridgeSlabType() { return bridgeSlabType; }
    public void setBridgeSlabType(String bridgeSlabType) { this.bridgeSlabType = bridgeSlabType; }
    
    public double getBridgeThickness() { return bridgeThickness; }
    public void setBridgeThickness(double bridgeThickness) { this.bridgeThickness = Math.max(0.1, bridgeThickness); }
    
    public double getBridgeLength() { return bridgeLength; }
    public void setBridgeLength(double bridgeLength) { this.bridgeLength = Math.max(0, bridgeLength); }
    
    public double getBridgeWidth() { return bridgeWidth; }
    public void setBridgeWidth(double bridgeWidth) { this.bridgeWidth = Math.max(0, bridgeWidth); }
    
    public double getTotalTrafficFlow() { return totalTrafficFlow; }
    public void setTotalTrafficFlow(double totalTrafficFlow) { this.totalTrafficFlow = Math.max(0, totalTrafficFlow); }
    
    public double getAverageSpeed() { return averageSpeed; }
    public void setAverageSpeed(double averageSpeed) { this.averageSpeed = Math.max(20, averageSpeed); }
    
    public int getCoefficientVersion() { return coefficientVersion; }
    public void setCoefficientVersion(int coefficientVersion) { this.coefficientVersion = coefficientVersion; }
}
