package org.noise_planet.noisemodelling.propagation.cnossos;

import java.util.List;
import java.util.Collections;

/**
 * Represents the result of building a path segment.
 * Contains the actual built elements and statistics about the segment construction process.
 * 
 * This class provides access to the concrete PointPath and SegmentPath objects
 * that were created during the acoustic path segment construction, allowing for
 * detailed analysis, validation, and further processing of the constructed acoustic elements.
 * 
 * Key features:
 * - Contains actual constructed PointPath objects (not just counts)
 * - Contains actual constructed SegmentPath objects (not just counts) 
 * - Provides immutable access to prevent accidental modification
 * - Includes success/failure status for error handling
 * - Supports segment-by-segment analysis of complex acoustic paths
 */
public class SegmentBuildResult {
    private final int segmentIndex;
    private final List<PointPath> createdPoints;
    private final List<SegmentPath> createdSegments;
    private final boolean success;
    
    /**
     * Constructor for segment build result.
     * 
     * @param segmentIndex Index of the segment that was built
     * @param createdPoints List of points created during build
     * @param createdSegments List of segments created during build
     * @param success Whether the build was successful
     */
    public SegmentBuildResult(int segmentIndex, List<PointPath> createdPoints, List<SegmentPath> createdSegments, boolean success) {
        this.segmentIndex = segmentIndex;
        this.createdPoints = Collections.unmodifiableList(createdPoints);
        this.createdSegments = Collections.unmodifiableList(createdSegments);
        this.success = success;
    }
    
    /**
     * Get the index of the segment that was built.
     * @return segment index (1-based)
     */
    public int getSegmentIndex() {
        return segmentIndex;
    }
    
    /**
     * Get the points created during segment building.
     * @return immutable list of created points
     */
    public List<PointPath> getCreatedPoints() {
        return createdPoints;
    }
    
    /**
     * Get the segments created during segment building.
     * @return immutable list of created segments
     */
    public List<SegmentPath> getCreatedSegments() {
        return createdSegments;
    }
    
    /**
     * Get the number of points created during segment building.
     * @return number of created points
     */
    public int getCreatedPointsCount() {
        return createdPoints.size();
    }
    
    /**
     * Get the number of segments created during segment building.
     * @return number of created segments
     */
    public int getCreatedSegmentsCount() {
        return createdSegments.size();
    }
    
    /**
     * Check if the segment building was successful.
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Check if any acoustic elements were created during building.
     * @return true if points or segments were created
     */
    public boolean hasCreatedElements() {
        return !createdPoints.isEmpty() || !createdSegments.isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("SegmentBuildResult{segment=%d, points=%d, segments=%d, success=%s}", 
                           segmentIndex, getCreatedPointsCount(), getCreatedSegmentsCount(), success);
    }
}
