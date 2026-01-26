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
import org.noise_planet.noisemodelling.pathfinder.path.Scene;

import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.LineString;

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

    private Bridge.GirderType girderType = null;

    private Bridge.SlabType slabType = null;

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
     * @param girderType Girder type of the bridge
     * @param slabType Slab type of the bridge
     */
    public BridgePoint(Coordinate coordinate, long primaryKey, long bridgePrimaryKey,
                      double absoluteDeckHeight, double relativeDeckHeight,
                      double deckThickness, double rightWidth, double leftWidth,
                      double rightBarrierHeight, double leftBarrierHeight, Bridge.GirderType girderType, Bridge.SlabType slabType) {
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
        this.position = Position.CENTER; // Default position
        this.girderType = girderType != null ? girderType : Bridge.GirderType.STEEL_BOX; // Default girder type
        this.slabType = slabType != null ? slabType : Bridge.SlabType.STEEL; // Default slab type
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
        this.girderType = other.girderType;
        this.slabType = other.slabType;
    }

    /**
     * Create a list of BridgePoints from a list of coordinates.
     * Each coordinate is converted to a BridgePoint with the specified structural properties.
     * The height interpretation depends on the heightType parameter (ABSOLUTE or RELATIVE).
     * 
     * @param coordinates List of 3D coordinates representing bridge point positions
     * @param bridgePrimaryKey Primary key identifier for the bridge
     * @param heightType Type of height interpretation (ABSOLUTE for elevation, RELATIVE for height above ground)
     * @param deckThickness Thickness of the bridge deck in meters
     * @param rightWidth Width of the bridge deck on the right side in meters
     * @param leftWidth Width of the bridge deck on the left side in meters
     * @param rightBarrierHeight Height of the right side barrier in meters
     * @param leftBarrierHeight Height of the left side barrier in meters
     * @param girderType Type of bridge girder structure
     * @param slabType Type of bridge slab material
     * @return List of BridgePoint objects created from the coordinates
     */
    public static List<BridgePoint> createBridgePoints(
        List<Coordinate> coordinates, long bridgePrimaryKey,
        Scene.HeightType heightType, double deckThickness, double rightWidth, double leftWidth,
        double rightBarrierHeight, double leftBarrierHeight, Bridge.GirderType girderType, Bridge.SlabType slabType) {

        List<BridgePoint> bridgePoints = new ArrayList<>();
        for (long pk = 0; pk < coordinates.size(); pk++) {
            Coordinate coord = coordinates.get((int) pk);
            double absoluteDeckHeight = NaN;
            double relativeDeckHeight = NaN;
            if (heightType == Scene.HeightType.ABSOLUTE) {
                absoluteDeckHeight = coord.getZ();
            } else if (heightType == Scene.HeightType.RELATIVE) {
                relativeDeckHeight = coord.getZ();
            }
            BridgePoint point = new BridgePoint(
                coord, pk, bridgePrimaryKey,
                absoluteDeckHeight, relativeDeckHeight,
                deckThickness, rightWidth, leftWidth,
                rightBarrierHeight, leftBarrierHeight,
                girderType, slabType);
            bridgePoints.add(point);
        }
        return bridgePoints;
    }

    /**
     * Create a list of BridgePoints from a LineString geometry.
     * Extracts all coordinates from the LineString and converts them to BridgePoints
     * with the specified structural properties.
     * The height interpretation depends on the heightType parameter (ABSOLUTE or RELATIVE).
     * 
     * @param lineString LineString geometry representing the bridge center line
     * @param bridgePrimaryKey Primary key identifier for the bridge
     * @param heightType Type of height interpretation (ABSOLUTE for elevation, RELATIVE for height above ground)
     * @param deckThickness Thickness of the bridge deck in meters
     * @param rightWidth Width of the bridge deck on the right side in meters
     * @param leftWidth Width of the bridge deck on the left side in meters
     * @param rightBarrierHeight Height of the right side barrier in meters
     * @param leftBarrierHeight Height of the left side barrier in meters
     * @param girderType Type of bridge girder structure
     * @param slabType Type of bridge slab material
     * @return List of BridgePoint objects created from the LineString coordinates
     */
    public static List<BridgePoint> createBridgePoints(
        LineString lineString, long bridgePrimaryKey,
        Scene.HeightType heightType, double deckThickness, double rightWidth, double leftWidth,
        double rightBarrierHeight, double leftBarrierHeight, Bridge.GirderType girderType, Bridge.SlabType slabType) {

        List<BridgePoint> bridgePoints = new ArrayList<>();
        for (int i = 0; i < lineString.getNumPoints(); i++) {
            Coordinate coord = lineString.getCoordinateN(i);
            double absoluteDeckHeight = NaN;
            double relativeDeckHeight = NaN;
            if (heightType == Scene.HeightType.ABSOLUTE) {
                absoluteDeckHeight = coord.getZ();
            } else if (heightType == Scene.HeightType.RELATIVE) {
                relativeDeckHeight = coord.getZ();
            }
            BridgePoint point = new BridgePoint(
                coord, (long) i, bridgePrimaryKey,
                absoluteDeckHeight, relativeDeckHeight,
                deckThickness, rightWidth, leftWidth,
                rightBarrierHeight, leftBarrierHeight,
                girderType, slabType);
            bridgePoints.add(point);
        }
        return bridgePoints;
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
     * Get the primary key identifier of the bridge this point belongs to.
     * @return The bridge primary key
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
     * Get the deck thickness in meters (vertical thickness of the bridge deck).
     * @return The deck thickness, or NaN if not set
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
     * Get the width of the bridge deck on the specified side.
     * Convenience method that returns left or right width based on the position parameter.
     * 
     * @param side Position side (LEFT or RIGHT) - CENTER is not valid for this method
     * @return The width on the specified side, or NaN if not set
     * @throws IllegalArgumentException if side is CENTER instead of LEFT or RIGHT
     */
    public double getWidth(Position side) {
        if(side == Position.LEFT) {
            return getLeftWidth();
        } else if(side == Position.RIGHT) {
            return getRightWidth();
        } else {
            throw new IllegalArgumentException("LEFT or RIGHT must be specified to get bridge width");
        }
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

    /**
     * Get the barrier height on the specified side of the bridge.
     * Convenience method that returns left or right barrier height based on the position parameter.
     * 
     * @param side Position side (LEFT or RIGHT) - CENTER is not valid for this method
     * @return The barrier height on the specified side, or NaN if not set
     * @throws IllegalArgumentException if side is CENTER instead of LEFT or RIGHT
     */
    public double getBarrierHeight(Position side) {
        if(side == Position.LEFT) {
            return getLeftBarrierHeight();
        } else if(side == Position.RIGHT) {
            return getRightBarrierHeight();
        } else {
            throw new IllegalArgumentException("LEFT or RIGHT must be specified to get bridge barrier height");
        }
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
     * Set the primary key identifier of the bridge this point belongs to.
     * @param bridgePrimaryKey The bridge primary key to set
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
     * Set the deck thickness in meters (vertical thickness of the bridge deck).
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

    /**
     * Get the girder type of the bridge at this point.
     * 
     * @return Girder type (STEEL_BOX, STEEL_PLATE, CONCRETE_BOX, etc.)
     */
    public Bridge.GirderType getGirderType() {
        return girderType;
    }
    
    /**
     * Get the slab type (deck material) of the bridge at this point.
     * 
     * @return Slab type (STEEL or CONCRETE)
     */
    public Bridge.SlabType getSlabType() {
        return slabType;
    }
    
    /**
     * Returns a string representation of this BridgePoint.
     * Includes all non-null and non-NaN fields for debugging and logging purposes.
     * 
     * @return String representation of this BridgePoint with all available data
     */
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        BridgePoint that = (BridgePoint) obj;
        
        if (primaryKey != that.primaryKey) return false;
        if (bridgePrimaryKey != that.bridgePrimaryKey) return false;
        if (Double.compare(that.absoluteDeckHeight, absoluteDeckHeight) != 0) return false;
        if (Double.compare(that.relativeDeckHeight, relativeDeckHeight) != 0) return false;
        if (Double.compare(that.deckThickness, deckThickness) != 0) return false;
        if (Double.compare(that.rightWidth, rightWidth) != 0) return false;
        if (Double.compare(that.leftWidth, leftWidth) != 0) return false;
        if (Double.compare(that.rightBarrierHeight, rightBarrierHeight) != 0) return false;
        if (Double.compare(that.leftBarrierHeight, leftBarrierHeight) != 0) return false;
        
        if (coordinate != null ? !coordinate.equals(that.coordinate) : that.coordinate != null) return false;
        if (position != that.position) return false;
        if (girderType != that.girderType) return false;
        if (slabType != that.slabType) return false;
        
        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = coordinate != null ? coordinate.hashCode() : 0;
        result = 31 * result + (int) (primaryKey ^ (primaryKey >>> 32));
        result = 31 * result + (int) (bridgePrimaryKey ^ (bridgePrimaryKey >>> 32));
        temp = Double.doubleToLongBits(absoluteDeckHeight);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(relativeDeckHeight);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(deckThickness);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(rightWidth);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(leftWidth);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(rightBarrierHeight);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(leftBarrierHeight);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (position != null ? position.hashCode() : 0);
        result = 31 * result + (girderType != null ? girderType.hashCode() : 0);
        result = 31 * result + (slabType != null ? slabType.hashCode() : 0);
        return result;
    }

}
