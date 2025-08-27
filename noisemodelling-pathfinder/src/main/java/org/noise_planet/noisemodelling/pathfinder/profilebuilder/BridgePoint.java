/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;

import static java.lang.Double.NaN;

/**
 * BridgePoint class represents a single point on a bridge structure.
 * 
 * Primary Responsibilities:
 * - Storing geometric and structural information for a specific bridge location
 * - Managing height data (absolute and relative deck heights)
 * - Managing bridge geometry properties (width, thickness, barrier heights)
 * - Supporting positional information (CENTER, LEFT, RIGHT)
 * 
 * Data Scope:
 * - All height values are in meters using absolute elevation coordinates unless specified otherwise
 * - Supports both absolute elevation and relative height (above ground surface) data
 * - Immutable data container with validation methods
 * 
 * This class is a pure data container and does not handle:
 * - Collection management operations (delegated to BridgePointManager)
 * - Geometric calculations or interpolations (delegated to other components)
 * - Spatial queries or relationships (delegated to BridgeQueryHelper)
 */
public class BridgePoint {
    
    /**
     * Enum for bridge point position
     */
    public enum Position {
        CENTER,
        LEFT,
        RIGHT
    }
    /** 3D coordinate of the bridge point */
    private Coordinate coordinate;
    
    /** Primary key identifier for this bridge point */
    private long primaryKey = -1;
    
    /** Primary key identifier for this bridge */
    private long bridgePrimaryKey = -1;

    /** Position of the bridge point (center, left, or right) */
    private Position position = Position.CENTER;

    /** Absolute deck height in meters (elevation above sea level) */
    private double absoluteDeckHeight = NaN;
    
    /** Relative deck height in meters (height above ground surface) */
    private double relativeDeckHeight = NaN;

    /** Deck thickness in meters */
    private double deckThickness = NaN;

    /** Width of the bridge deck on the right side in meters */
    private double rightWidth = NaN;
    
    /** Width of the bridge deck on the left side in meters */
    private double leftWidth = NaN;
    
    /** Height of the right side barrier/parapet in meters */
    private double rightBarrierHeight = NaN;
    
    /** Height of the left side barrier/parapet in meters */
    private double leftBarrierHeight = NaN;

    /**
     * Default constructor.
     */
    public BridgePoint() {
    }

    /**
     * Constructor with coordinate.
     * @param coordinate The coordinate of the bridge point
     */
    public BridgePoint(Coordinate coordinate) {
        // Create a defensive copy to ensure independence from the original coordinate
        this.coordinate = coordinate != null ? new Coordinate(coordinate) : null;
    }

    /**
     * Full constructor with all parameters.
     * @param coordinate The 3D coordinate of the bridge point
     * @param primaryKey Primary key identifier of the bridge point
     * @param bridgePrimaryKey Primary key identifier of the bridge
     * @param absoluteDeckHeight Absolute deck height in meters (elevation above sea level)
     * @param relativeDeckHeight Relative deck height in meters (height above ground surface)
     * @param deckThickness Deck thickness in meters
     * @param rightWidth Width of the bridge deck on the right side in meters
     * @param leftWidth Width of the bridge deck on the left side in meters
     * @param rightBarrierHeight Height of the right side barrier/parapet in meters
     * @param leftBarrierHeight Height of the left side barrier/parapet in meters
     */
    public BridgePoint(Coordinate coordinate, long primaryKey, long bridgePrimaryKey,
                      double absoluteDeckHeight, double relativeDeckHeight,
                      double deckThickness, double rightWidth, double leftWidth,
                      double rightBarrierHeight, double leftBarrierHeight) {
        // Create a defensive copy to ensure independence from the original coordinate
        this.coordinate = coordinate != null ? new Coordinate(coordinate) : null;
        this.primaryKey = primaryKey;
        this.bridgePrimaryKey = bridgePrimaryKey;
        this.absoluteDeckHeight = absoluteDeckHeight;
        this.relativeDeckHeight = relativeDeckHeight;
        this.deckThickness = deckThickness;
        this.rightWidth = rightWidth;
        this.leftWidth = leftWidth;
        this.rightBarrierHeight = rightBarrierHeight;
        this.leftBarrierHeight = leftBarrierHeight;
    }

    /**
     * Copy constructor that creates a deep copy of another BridgePoint.
     * All fields are copied, including a deep copy of the coordinate.
     * @param other The BridgePoint to copy
     */
    public BridgePoint(BridgePoint other) {
        if (other == null) {
            throw new IllegalArgumentException("Cannot copy from null BridgePoint");
        }
        
        // Deep copy of coordinate
        this.coordinate = other.coordinate != null ? new Coordinate(other.coordinate) : null;
        
        // Copy all other fields
        this.primaryKey = other.primaryKey;
        this.bridgePrimaryKey = other.bridgePrimaryKey;
        this.position = other.position;
        this.absoluteDeckHeight = other.absoluteDeckHeight;
        this.relativeDeckHeight = other.relativeDeckHeight;
        this.deckThickness = other.deckThickness;
        this.rightWidth = other.rightWidth;
        this.leftWidth = other.leftWidth;
        this.rightBarrierHeight = other.rightBarrierHeight;
        this.leftBarrierHeight = other.leftBarrierHeight;
    }

    // Getters
    
    /**
     * Get the 3D coordinate of this bridge point.
     * @return The coordinate of the bridge point
     */
    public Coordinate getCoordinate() {
        return coordinate;
    }

    /**
     * Get the primary key identifier of this bridge point.
     * @return The primary key
     */
    public long getPrimaryKey() {
        return primaryKey;
    }

    /**
     * Get the primary key identifier of this bridge point.
     * @return The primary key
     */
    public long getBridgePrimaryKey() {
        return bridgePrimaryKey;
    }

    /**
     * Get the position of this bridge point.
     * @return The position (CENTER, LEFT, or RIGHT)
     */
    public Position getPosition() {
        return position;
    }

    /**
     * Get the absolute deck height in meters (elevation above sea level).
     * @return The absolute deck height, or NaN if not set
     */
    public double getAbsoluteDeckHeight() {
        return absoluteDeckHeight;
    }

    /**
     * Get the relative deck height in meters (height above ground surface).
     * @return The relative deck height, or NaN if not set
     */
    public double getRelativeDeckHeight() {
        return relativeDeckHeight;
    }

    /**
     * Get the relative deck height in meters (height above ground surface).
     * @return The relative deck height, or NaN if not set
     */
    public double getDeckThickness() {
        return deckThickness;
    }

    /**
     * Get the width of the bridge deck on the right side in meters.
     * @return The right width, or NaN if not set
     */
    public double getRightWidth() {
        return rightWidth;
    }

    /**
     * Get the width of the bridge deck on the left side in meters.
     * @return The left width, or NaN if not set
     */
    public double getLeftWidth() {
        return leftWidth;
    }

    /**
     * Get the height of the right side barrier/parapet in meters.
     * @return The right barrier height, or NaN if not set
     */
    public double getRightBarrierHeight() {
        return rightBarrierHeight;
    }

    /**
     * Get the height of the left side barrier/parapet in meters.
     * @return The left barrier height, or NaN if not set
     */
    public double getLeftBarrierHeight() {
        return leftBarrierHeight;
    }

    // Setters
    
    /**
     * Set the 3D coordinate of this bridge point.
     * @param coordinate The coordinate to set
     */
    public void setCoordinate(Coordinate coordinate) {
        this.coordinate = coordinate;
    }

    /**
     * Set the primary key identifier of this bridge point.
     * @param primaryKey The primary key to set
     */
    public void setPrimaryKey(long primaryKey) {
        this.primaryKey = primaryKey;
    }

    /**
     * Set the primary key identifier of this bridge point.
     * @param bridgePrimaryKey The primary key to set
     */
    public void setBridgePrimaryKey(long bridgePrimaryKey) {
        this.bridgePrimaryKey = bridgePrimaryKey;
    }

    /**
     * Set the position of this bridge point.
     * @param position The position to set (CENTER, LEFT, or RIGHT)
     */
    public void setPosition(Position position) {
        this.position = position;
    }
    /**
     * Set the absolute deck height in meters (elevation above sea level).
     * @param absoluteDeckHeight The absolute deck height to set
     */
    public void setAbsoluteDeckHeight(double absoluteDeckHeight) {
        this.absoluteDeckHeight = absoluteDeckHeight;
    }

    /**
     * Set the relative deck height in meters (height above ground surface).
     * @param relativeDeckHeight The relative deck height to set
     */
    public void setRelativeDeckHeight(double relativeDeckHeight) {
        this.relativeDeckHeight = relativeDeckHeight;
    }

    /**
     * Set the relative deck thickness in meters.
     * @param deckThickness The deck thickness to set
     */
    public void setDeckThickness(double deckThickness) {
        this.deckThickness = deckThickness;
    }


    /**
     * Set the width of the bridge deck on the right side in meters.
     * @param rightWidth The right width to set
     */
    public void setRightWidth(double rightWidth) {
        this.rightWidth = rightWidth;
    }

    /**
     * Set the width of the bridge deck on the left side in meters.
     * @param leftWidth The left width to set
     */
    public void setLeftWidth(double leftWidth) {
        this.leftWidth = leftWidth;
    }

    /**
     * Set the height of the right side barrier/parapet in meters.
     * @param rightBarrierHeight The right barrier height to set
     */
    public void setRightBarrierHeight(double rightBarrierHeight) {
        this.rightBarrierHeight = rightBarrierHeight;
    }

    /**
     * Set the height of the left side barrier/parapet in meters.
     * @param leftBarrierHeight The left barrier height to set
     */
    public void setLeftBarrierHeight(double leftBarrierHeight) {
        this.leftBarrierHeight = leftBarrierHeight;
    }
    
    /**
     * Check if this bridge point has a valid coordinate.
     * @return true if coordinate is not null
     */
    public boolean hasValidCoordinate() {
        return coordinate != null;
    }
    
    /**
     * Check if this bridge point has absolute deck height data.
     * @return true if absolute deck height is not NaN
     */
    public boolean hasAbsoluteDeckHeight() {
        return !Double.isNaN(absoluteDeckHeight);
    }
    
    /**
     * Check if this bridge point has relative deck height data.
     * @return true if relative deck height is not NaN
     */
    public boolean hasRelativeDeckHeight() {
        return !Double.isNaN(relativeDeckHeight);
    }
    
    /**
     * Check if this bridge point has width data.
     * @return true if either right or left width is not NaN
     */
    public boolean hasWidthData() {
        return !Double.isNaN(rightWidth) || !Double.isNaN(leftWidth);
    }
    
    /**
     * Check if this bridge point has barrier height data.
     * @return true if either right or left barrier height is not NaN
     */
    public boolean hasBarrierHeightData() {
        return !Double.isNaN(rightBarrierHeight) || !Double.isNaN(leftBarrierHeight);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("BridgePoint{");
        sb.append("bridgePrimaryKey=").append(bridgePrimaryKey);
        sb.append(", position=").append(position);
        if (coordinate != null) {
            sb.append(", coordinate=").append(coordinate);
        }
        if (!Double.isNaN(absoluteDeckHeight)) {
            sb.append(", absoluteDeckHeight=").append(absoluteDeckHeight);
        }
        if (!Double.isNaN(relativeDeckHeight)) {
            sb.append(", relativeDeckHeight=").append(relativeDeckHeight);
        }
        if (!Double.isNaN(deckThickness)) {
            sb.append(", deckThickness=").append(deckThickness);
        }
        if (!Double.isNaN(rightWidth)) {
            sb.append(", rightWidth=").append(rightWidth);
        }
        if (!Double.isNaN(leftWidth)) {
            sb.append(", leftWidth=").append(leftWidth);
        }
        if (!Double.isNaN(rightBarrierHeight)) {
            sb.append(", rightBarrierHeight=").append(rightBarrierHeight);
        }
        if (!Double.isNaN(leftBarrierHeight)) {
            sb.append(", leftBarrierHeight=").append(leftBarrierHeight);
        }
        sb.append('}');
        return sb.toString();
    }
}
