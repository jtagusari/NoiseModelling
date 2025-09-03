package org.noise_planet.noisemodelling.pathfinder.path;


/**
 * Container for properties describing how a source relates to a bridge.
 * <p>
 * This class is used to mark a source as being located on a bridge, under a
 * bridge (imaginary), a mirror source, or not related to any bridge. It also
 * carries the bridge primary key when relevant.
 */
public class SourceBridgeProperty {
    /**
     * Enumerates how a source is related to a bridge.
     */
    public enum SourceType {
        /** Source is not related to any bridge. */
        SOURCE_NOT_RELATED_TO_BRIDGE,
        /** The source is an actual physical source located on a bridge. */
        ACTUAL_SOURCE_ON_BRIDGE,
        /** An imaginary (virtual) source located under a bridge. */
        IMAGINARY_SOURCE_UNDER_BRIDGE,
        /** A mirror source used for reflection modelling. */
        MIRROR_SOURCE
    }

    /** Relation type of the source to a bridge. Defaults to not related. */
    private SourceType sourceType = SourceType.SOURCE_NOT_RELATED_TO_BRIDGE;

    /** Primary key of the bridge related to this source (-1 if none). */
    private long bridgePkOn = -1L;
    /** Primary key of the bridge related to this source (-1 if none). */
    private long bridgePkAbove = -1L;

    /**
     * Create an empty SourceBridgeProperty instance with default values.
     */
    public SourceBridgeProperty() {}

    /**
     * Create SourceBridgeProperty with explicit type and bridge identifier.
     *
     * @param sourceType relation of the source to a bridge
     * @param bridgePk primary key of the bridge (use 0 if not applicable)
     */
    public SourceBridgeProperty(SourceType sourceType, long bridgePkOn, long bridgePkAbove) {
        this.sourceType = sourceType;
        this.bridgePkOn = bridgePkOn;
        this.bridgePkAbove = bridgePkAbove;
    }

    /**
     * Get the bridge primary key associated with these properties.
     *
     * @return bridge primary key (or -1 if not set)
     */
    public long getBridgePkOn() {
        return bridgePkOn;
    }

    public long getBridgePkAbove() {
        return bridgePkAbove;
    }

    /**
     * Get the source type indicating relation to a bridge.
     *
     * @return SourceType enum value describing the relation
     */
    public SourceType getSourceType() {
        return sourceType;
    }

}
