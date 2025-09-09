package org.noise_planet.noisemodelling.propagation.cnossos;

import org.locationtech.jts.geom.Coordinate;
import java.util.List;
import java.util.ArrayList;

/**
 * Coordinates the construction of complete acoustic paths.
 * Manages multiple acoustic path processors and aggregates their results
 * to build comprehensive SegmentPath and PointPath collections from diffraction coordinates.
 */
public class AcousticPathBuilder {
    // private Coordinate sourceCoordinate;
    // private List<SegmentBuildResult> segmentBuildResults;
    private final AcousticPathConfiguration configuration;
    private final Path acousticPath;

    public AcousticPathBuilder(AcousticPathConfiguration configuration) {
        this.configuration = configuration;
        this.acousticPath = new Path(new ArrayList<>(), new ArrayList<>());
        // this.segmentBuildResults = new ArrayList<>();
        // this.sourceCoordinate = null;
    }

    public Path createSegments() {
        
        // Validate input
        if (configuration.getDiffractionPoints().size() < 2) {
            throw new IllegalArgumentException("At least source and receiver points are required.");
        }
        
        // Handle simple direct path case
        if (configuration.getDiffractionPoints().size() == 2) {
            return processDirectPath(acousticPath);
        }
        
        // Handle complex path with multiple diffraction points
        return processComplexPath(acousticPath);
    }
    
    /**
     * Process direct path between source and receiver (no intermediate diffraction).
     */
    private Path processDirectPath(Path acousticPath) {
        // For direct paths, we only need to process one segment
        AcousticPathProcessor processor = new AcousticPathProcessor(configuration);
        processor.setSegmentIndex(1);
        Path updatedAcousticPath = processor.buildAcousticPath(acousticPath);
        // this.segmentBuildResults.add(buildResult);
        
        // this.sourceCoordinate = configuration.getCutProfile().getSource().getCoordinate();
        return updatedAcousticPath;
    }
    
    /**
     * Process complex path with multiple diffraction points.
     */
    private Path processComplexPath(Path acousticPath) {
        // this.sourceCoordinate = configuration.getCutProfile().getSource().getCoordinate();
        
        // Process each segment in the diffraction path
        AcousticPathProcessor processor = new AcousticPathProcessor(configuration);
        Path updatedAcousticPath = new Path();
        for (int segmentIndex = 1; segmentIndex < configuration.getDiffractionPoints().size(); segmentIndex++) {
            processor.setSegmentIndex(segmentIndex);
            updatedAcousticPath = processor.buildAcousticPath(acousticPath);
            // this.segmentBuildResults.add(buildResult);
            // Return null if any segment building failed
            // if (!buildResult.isSuccess()) {
            //     return;
            // }
            
            // Note: SegmentBuildResult contains the actual created acoustic elements
            // buildResult.getCreatedPoints() - points created in this segment
            // buildResult.getCreatedSegments() - segments created in this segment
            // This allows for segment-by-segment analysis and validation if needed
            
            // Update source coordinate after first segment
            // if (segmentIndex == 1) {
                // this.sourceCoordinate = configuration.getCutPointCoordinates2D().get(segment.getSourceIndex());
            // }
        }
        
        return updatedAcousticPath;
    }


    // public List<SegmentBuildResult> getSegmentBuildResults() {
    //     return segmentBuildResults;
    // }
    
    /**
     * Get all points created across all segments (equivalent to Path.getPointList()).
     */
    // public List<PointPath> getAllPoints() {
    //     List<PointPath> allPoints = new ArrayList<>();
    //     for (SegmentBuildResult acousticPath : segmentBuildResults) {
    //         allPoints.addAll(acousticPath.getCreatedPoints());
    //     }
    //     return allPoints;
    // }
    
    // /**
    //  * Get all segments created across all segments (equivalent to Path.getSegmentList()).
    //  */
    // public List<SegmentPath> getAllSegments() {
    //     List<SegmentPath> allSegments = new ArrayList<>();
    //     for (SegmentBuildResult acousticPath : segmentBuildResults) {
    //         allSegments.addAll(acousticPath.getCreatedSegments());
    //     }
    //     return allSegments;
    // }
    
    /**
     * Check if any points were created (equivalent to Path.hasNoPoints()).
     */
    // public boolean hasNoPoints() {
    //     return getAllPoints().isEmpty();
    // }
    
    /**
     * Get the total number of points created.
     */
    // public int getPointCount() {
    //     return getAllPoints().size();
    // }
    
    /**
     * Get the total number of segments created.
     */
    // public int getSegmentCount() {
    //     return getAllSegments().size();
    // }
}
