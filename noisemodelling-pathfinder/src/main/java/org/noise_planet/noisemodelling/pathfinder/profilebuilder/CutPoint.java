/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.locationtech.jts.geom.Coordinate;


/**
 * Abstract base class representing a point on a vertical cut profile between source and receiver.
 * This class serves as the foundation for different types of cut points encountered during
 * acoustic path analysis, including sources, receivers, obstacles, and topographic features.
 * 
 * Each cut point contains coordinate information, ground elevation, and ground absorption
 * coefficient properties that are essential for acoustic propagation calculations.
 * 
 * @author NoiseModelling contributors
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CutPointSource.class, name = "Source"),
        @JsonSubTypes.Type(value = CutPointReceiver.class, name = "Receiver"),
        @JsonSubTypes.Type(value = CutPointWall.class, name = "Wall"),
        @JsonSubTypes.Type(value = CutPointBridgeWall.class, name = "BridgeWall"),
        @JsonSubTypes.Type(value = CutPointReflection.class, name = "Reflection"),
        @JsonSubTypes.Type(value = CutPointGroundEffect.class, name = "GroundEffect"),
        @JsonSubTypes.Type(value = CutPointTopography.class, name = "Topography"),
        @JsonSubTypes.Type(value = CutPointVEdgeDiffraction.class, name = "VEdgeDiffraction")
})
public abstract class CutPoint implements Comparable<CutPoint> {
    /** {@link Coordinate} of the cut point. */
    public Coordinate coordinate = new Coordinate();

    /** Topographic height of the point. */
    public double zGround = Double.NaN;

    /**
     * Ground effect coefficient.
     * G=1.0 Soft, uncompacted ground (pasture, loose soil); snow etc
     * G=0.7 Compacted soft ground (lawns, park areas):
     * G=0.3 Compacted dense ground (gravel road, compacted soil):
     * G=0.0 Hard surfaces (asphalt, concrete, top of buildings):
     **/
    public double groundCoefficient = Double.NaN;

    /**
     * Default constructor for deserialization.
     */
    public CutPoint() {
    }

    /**
     * Constructor with coordinate only.
     * 
     * @param coordinate the 3D coordinate of this cut point
     */
    public CutPoint(Coordinate coordinate) {
        this.coordinate = coordinate;
    }

    /**
     * Constructor with all properties.
     * 
     * @param coordinate the 3D coordinate of this cut point
     * @param zGround the topographic ground height
     * @param groundCoefficient the ground absorption coefficient (0.0-1.0)
     */
    public CutPoint(Coordinate coordinate, double zGround, double groundCoefficient) {
        this.coordinate = coordinate;
        this.zGround = zGround;
        this.groundCoefficient = groundCoefficient;
    }

    /**
     * Copy constructor
     * @param other Other instance to copy
     */
    @SuppressWarnings("IncompleteCopyConstructor")
    public CutPoint(CutPoint other) {
        this.coordinate = other.coordinate.copy();
        this.zGround = other.zGround;
        this.groundCoefficient = other.groundCoefficient;
    }

    /**
     * Sets the coordinate of this cut point.
     * 
     * @param coordinate the new coordinate to set
     */
    public void setCoordinate(Coordinate coordinate) {
        this.coordinate = coordinate;
    }

    /**
     * Sets the ground coefficient of this point.
     * @param groundCoefficient The ground coefficient of this point.
     */
    public void setGroundCoefficient(double groundCoefficient) {
        this.groundCoefficient = groundCoefficient;
    }

    /**
     * Sets the topographic height.
     * @param zGround The topographic height.
     */
    public void setZGround(double zGround) {
        this.zGround = zGround;
    }


    /**
     * Retrieve the coordinate of the point.
     * @return The coordinate of the point.
     */
    public Coordinate getCoordinate(){
        return coordinate;
    }

    /**
     * Retrieve the ground effect coefficient of the point. If there is no coefficient, returns 0.
     * @return Ground effect coefficient or NaN.
     */
    public double getGroundCoefficient() {
        return groundCoefficient;
    }

    /**
     * Retrieve the topographic height of the point.
     * @return The topographic height of the point.
     */
    public Double getzGround() {
        return zGround;
    }

    /**
     * Compare this cut point with another based on coordinates.
     * 
     * @param cutPoint the object to be compared
     * @return comparison result based on coordinate comparison
     */
    @Override
    public int compareTo(CutPoint cutPoint) {
        return this.coordinate.compareTo(cutPoint.coordinate);
    }

    public boolean hasObstacle() {
        return  Double.compare(getCoordinate().z, getzGround()) != 0;
    }

    /**
     * Compares this cut point with another object for equality.
     * Two cut points are considered equal if they have the same coordinate,
     * ground height, and ground coefficient.
     * 
     * @param obj the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        CutPoint cutPoint = (CutPoint) obj;
        
        // Compare coordinates
        if (coordinate == null && cutPoint.coordinate != null) return false;
        if (coordinate != null && !coordinate.equals(cutPoint.coordinate)) return false;
        
        // Compare zGround (handling NaN values)
        if (Double.isNaN(zGround) && Double.isNaN(cutPoint.zGround)) {
            // Both are NaN, consider equal
        } else if (Double.compare(zGround, cutPoint.zGround) != 0) {
            return false;
        }
        
        // Compare groundCoefficient (handling NaN values)
        if (Double.isNaN(groundCoefficient) && Double.isNaN(cutPoint.groundCoefficient)) {
            // Both are NaN, consider equal
        } else if (Double.compare(groundCoefficient, cutPoint.groundCoefficient) != 0) {
            return false;
        }
        
        return true;
    }

    /**
     * Returns a hash code for this cut point.
     * 
     * @return hash code based on coordinate, zGround, and groundCoefficient
     */
    @Override
    public int hashCode() {
        int result = coordinate != null ? coordinate.hashCode() : 0;
        
        // Handle NaN values in hash computation
        long zGroundBits = Double.isNaN(zGround) ? 0L : Double.doubleToLongBits(zGround);
        result = 31 * result + (int) (zGroundBits ^ (zGroundBits >>> 32));
        
        long groundCoeffBits = Double.isNaN(groundCoefficient) ? 0L : Double.doubleToLongBits(groundCoefficient);
        result = 31 * result + (int) (groundCoeffBits ^ (groundCoeffBits >>> 32));
        
        return result;
    }

    @Override
    public String toString() {
        return "CutPoint{" +
                "coordinate=" + coordinate +
                ", zGround=" + zGround +
                ", groundCoefficient=" + groundCoefficient +
                '}';
    }
}