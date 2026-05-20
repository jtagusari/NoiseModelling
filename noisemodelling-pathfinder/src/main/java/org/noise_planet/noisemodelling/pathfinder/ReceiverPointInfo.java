package org.noise_planet.noisemodelling.pathfinder;

import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutPointReceiver;
import org.locationtech.jts.geom.Coordinate;
/**
 * Attribute of the receiver point
 */
/**
 * Simple holder of receiver attributes used during per-receiver processing.
 * Currently a mutable public field holder for convenience.
 * Suggestion: convert to an immutable value object (final fields) to avoid
 * accidental mutation during processing. If mutated fields are required,
 * introduce a dedicated mutable DTO for conversion.
 */
public class ReceiverPointInfo {
    private final int receiverIndex;
    private final long receiverPk;
    private final Coordinate position;

    public ReceiverPointInfo(int receiverIndex, long receiverPk, Coordinate position) {
        this.receiverIndex = receiverIndex;
        this.receiverPk = receiverPk;
        this.position = position;
    }

    public ReceiverPointInfo(CutPointReceiver receiver) {
        this.receiverIndex = receiver.getReceiverId();
        this.receiverPk = receiver.getReceiverPk();
        this.position = receiver.getCoordinate();
    }

    public ReceiverPointInfo(Coordinate position) {
        this.receiverIndex = -1;
        this.receiverPk = -1;
        this.position = position;
    }

    public Coordinate getCoordinate() {
        return position;
    }

    /**
     * @return Receiver primary key
     */
    public long getReceiverPk() {
        return receiverPk;
    }

    /**
     * @return Receiver index, related to its location in memory data arrays
     */
    public int getReceiverIndex() {
        return receiverIndex;
    }
}
