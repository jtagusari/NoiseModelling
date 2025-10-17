/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.locationtech.jts.geom.Coordinate;
import org.noise_planet.noisemodelling.pathfinder.ReceiverPointInfo;

/**
 * Represents a receiver point in a vertical cut profile.
 * This class extends CutPoint to include receiver-specific properties such as
 * receiver identification and external database references.
 * 
 * Receivers are typically positioned at 4 meters above ground level by default
 * for noise level calculations.
 * 
 * @author NoiseModelling contributors
 */
public class CutPointReceiver  extends CutPoint {

    /**
     * External identifier of the receiver (from table)
     */
    private long receiverPk = -1;

    /**
     * Default constructor for deserialization.
     */
    public CutPointReceiver() {

    }

    /**
     * Constructor with location coordinate.
     * 
     * @param location the 3D coordinate of the receiver
     */
    public CutPointReceiver(Coordinate location) {
        this.coordinate = location;
    }

    /**
     * Copy constructor.
     * 
     * @param receiver the receiver cut point to copy
     */
    public CutPointReceiver(CutPoint receiver) {
        super(receiver);
        if (receiver instanceof CutPointReceiver) {
            CutPointReceiver cutPointReceiver = (CutPointReceiver) receiver;
            this.receiverPk = cutPointReceiver.receiverPk;
            this.id = cutPointReceiver.id;
        }
    }

    /**
     * Index in the subdomain.
     */
    @JsonIgnore
    private int id = -1;

    /**
     * Get the external receiver primary key.
     * 
     * @return the receiver primary key
     */
    public long getReceiverPk() {
        return receiverPk;
    }
    
    /**
     * Set the external receiver primary key.
     * 
     * @param receiverPk the receiver primary key to set
     */
    public void setReceiverPk(long receiverPk) {
        this.receiverPk = receiverPk;
    }

    
    public CutPointReceiver migrateFromReceiverPointInfo(ReceiverPointInfo receiverPointInfo) {
        this.id = receiverPointInfo.getReceiverIndex();
        this.receiverPk = receiverPointInfo.getReceiverPk();
        return this;
    }

    /**
     * Get the receiver identifier in the subdomain.
     * 
     * @return the receiver ID
     */
    @JsonIgnore
    public int getReceiverId() {
        return id;
    }

    /**
     * Set the receiver id
     * @param receiver
     */
    public void setReceiverId(int id) {
        this.id = id;
    }

    /**
     * Get the receiver id
     * @return the receiver id
     */
    public int getId() {
        return id;
    }

    /**
     * Create default receiver information with 4 meters above ground level.
     * 
     * @param receiver receiver information containing coordinates and metadata
     */
    public CutPointReceiver(ReceiverPointInfo receiver) {
        super(receiver.getCoordinate(), receiver.getCoordinate().z - 4.0, 0);
        id = receiver.getReceiverIndex();
        receiverPk = receiver.getReceiverPk();
    }

    @Override
    public String toString() {
        return "CutPointReceiver{" +
                "\ngroundCoefficient=" + groundCoefficient +
                "\n, zGround=" + zGround +
                "\n, coordinate=" + coordinate +
                "\n, id=" + id +
                "\n, receiverPk=" + receiverPk +
                "\n}\n";
    }
}
