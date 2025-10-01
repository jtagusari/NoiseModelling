package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPoint;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameter object containing all geometric and processing data for acoustic path construction.
 * Consolidates multiple parameters into a cohesive configuration object to reduce method complexity.
 */
public class AcousticPathConfiguration {
    
    // Geometric data
    private List<Coordinate> horizontalEdgePivotPoints;
    private final CutProfile cutProfile;
    private final List<CutPoint> cutPoints;
    private final List<Coordinate> cutPointCoordinates2D;
    private Coordinate sourceCoordinate2D;
    private Coordinate receiverCoordinate2D;
    private final List<Integer> cutPointExpandedIndices;
    private final Coordinate[] elevationProfile2D;
    
    // Processing parameters
    private final boolean bodyBarrier;
    private final double groundAttenuationCoefficient;
    private final List<Double> exactFrequencyArray;
    
    public AcousticPathConfiguration(CutProfile cutProfile, List<Double> exactFrequencyArray, double groundAttenuationCoefficient, boolean bodyBarrier) {
        this.cutProfile = cutProfile;
        this.cutPoints = cutProfile.getCutPoints();
        this.cutPointCoordinates2D = cutProfile.generateCutPointCoordinates2D();
        
        this.cutPointExpandedIndices = new ArrayList<>(cutPoints.size());
        this.elevationProfile2D = cutProfile.generateElevationProfile2D(cutPointExpandedIndices).toArray(new Coordinate[0]);

        this.sourceCoordinate2D = cutPointCoordinates2D.get(0);
        this.receiverCoordinate2D = cutPointCoordinates2D.get(cutPointCoordinates2D.size() - 1);

        if (cutPointCoordinates2D.size() != cutPoints.size()) {
            throw new IllegalArgumentException("The two arrays (cutPoint and cutPointCoordinates2D) size should be the same");
        }
        
        this.bodyBarrier = bodyBarrier;
        this.groundAttenuationCoefficient = groundAttenuationCoefficient;
        this.exactFrequencyArray = exactFrequencyArray;
    }

    public void setHorizontalEdgePivotPoints(List<Coordinate> horizontalEdgePivotPoints) {
        this.horizontalEdgePivotPoints = horizontalEdgePivotPoints;
        if (horizontalEdgePivotPoints == null) {
            return;
        }

        // this.sourceCoordinate = cutProfile.getSource().getCoordinate();
        if (horizontalEdgePivotPoints.size() < 2) {
            throw new IllegalArgumentException("At least source and receiver points are required.");
        } 
    }
    
    // Getters
    public List<Coordinate> getHorizontalEdgePivotPoints() { return horizontalEdgePivotPoints; }
    public CutProfile getCutProfile() { return cutProfile; }
    public List<CutPoint> getCutProfilePoints() { return cutPoints; }
    public List<Coordinate> getPts2D() { return cutPointCoordinates2D; }
    public List<Integer> getCut2DGroundIndex() { return cutPointExpandedIndices; }
    public Coordinate[] getPts2DGround() { return elevationProfile2D; }
    public boolean isBodyBarrier() { return bodyBarrier; }
    public double getGroundAttenuationCoefficient() { return groundAttenuationCoefficient; }
    public List<Double> getExactFrequencyArray() { return exactFrequencyArray; }


    public Coordinate getCutProfilePointCoordinate(int index) {
        return cutPoints.get(index).getCoordinate();
    }

    public List<Coordinate> getCutPointCoordinates2D() {
        return cutPointCoordinates2D;
    }

    public Coordinate[] getElevationProfile2D() {
        return elevationProfile2D;
    }

    public List<Integer> getCutPointExpandedIndices() {
        return cutPointExpandedIndices;
    }

    public Coordinate getSourceCoordinate2D() {
        return sourceCoordinate2D;
    }

    public Coordinate getReceiverCoordinate2D() {
        return receiverCoordinate2D;
    }

}
