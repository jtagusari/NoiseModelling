package org.noise_planet.noisemodelling.pathfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.ProfileBuilder;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.Bridge;
import org.noise_planet.noisemodelling.pathfinder.path.SourceBridgeProperty;


/**
 * BridgeSourceBuilder
 *
 * Utility class that splits a source LineString by bridge footprints discovered
 * via a provided {@link ProfileBuilder}. The splitter produces a list of
 * linear fragments and associated {@link SourceBridgeProperty} describing
 * whether each fragment is on a bridge, under a bridge (imaginary source),
 * a mirror source, or not related to any bridge.
 *
 * Key responsibilities:
 * - Discover bridges intersecting the input line using {@link ProfileBuilder#getBridgesIn}
 * - Split the input LineString sequentially by bridge footprints, ordered by
 *   deck height, and collect fragments grouped by bridge primary key
 * - For small intersections below the configured threshold, remove the
 *   footprint from the remaining geometry but do not record a bridge fragment
 * - Provide helper methods that create actual/imaginary/mirror source entries
 *   used by higher-level profile-building logic
 *
 * The class is intentionally side-effect oriented: internal lists
 * (`splittedSegments`, `sourceBridgeProperties`) are populated by the public
 * createBridgeRelatedLineSources variants. Geometry operations are delegated
 * to JTS and are resilient to topology errors (exceptions are logged and
 * processing continues).
 */
public class BridgeSourceBuilder {
    private final ProfileBuilder profileBuilder;
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeSourceBuilder.class);
    /**
     * Minimum overlap length (in geometry coordinate units) below which an
     * intersection is considered insignificant and will not be recorded as a
     * bridge fragment. Note: JTS returns lengths in the geometry's coordinate
     * units; ensure callers pass thresholds in matching units (typically meters
     * when using projected coordinates).
     */
    private final double minOverlapLengthMeters;
    private static final double MIN_OVERLAP_LENGTH = 1.0;

    /**
     * A sentinel deck height used when no specific minimum deck height is
     * provided. This negative value prevents filtering out legitimate
     * bridges when MIN_DECK_HEIGHT is used as a lower bound. Consider making
     * this configurable if callers need a different default.
     */
    private static final double MIN_DECK_HEIGHT = -999.9;
    private static final double OFFSET = 0.01;

    // Output buffers populated by the public API variants
    private List<Geometry> splittedSegments;
    private List<SourceBridgeProperty> sourceBridgeProperties;

    public BridgeSourceBuilder(ProfileBuilder profileBuilder, double minOverlapLengthMeters) {
        this.profileBuilder = profileBuilder;
        this.minOverlapLengthMeters = minOverlapLengthMeters;
        this.splittedSegments = new ArrayList<>();
        this.sourceBridgeProperties = new ArrayList<>();
    }

    public BridgeSourceBuilder(ProfileBuilder profileBuilder) {
        this.profileBuilder = profileBuilder;
        this.minOverlapLengthMeters = MIN_OVERLAP_LENGTH;
        this.splittedSegments = new ArrayList<>();
        this.sourceBridgeProperties = new ArrayList<>();
    }
    /**
     * Return the list of geometries produced by the last processing run.
     * Each entry corresponds to a fragment added to {@code splittedSegments}.
     * Callers may iterate this list after invoking the public create* APIs.
     *
     * @return mutable list of Geometry fragments (may be empty but never null)
     */
    public List<Geometry> getSplittedSegments() {
        return this.splittedSegments;
    }



    /**
     * Return the list of SourceBridgeProperty associated with entries in
     * {@link #getSplittedSegments()}. The lists are parallel: the i-th entry in
     * this list corresponds to the i-th geometry in {@code splittedSegments}.
     *
     * @return mutable list of SourceBridgeProperty (may be empty but never null)
     */
    public List<SourceBridgeProperty> getSourceBridgeProperties() {
        return this.sourceBridgeProperties;
    }

    

    /**
     * Return the list of geometries produced by the last processing run.
     * Each entry corresponds to a fragment added to {@code splittedSegments}.
     * Callers may iterate this list after invoking the public create* APIs.
     *
     * @return mutable list of Geometry fragments (may be empty but never null)
     */
    public Integer getSegmentSize() {
        return this.splittedSegments.size();
    }



    private boolean checkSegmentSize() {
        return this.splittedSegments.size() == this.sourceBridgeProperties.size();
    }


    /**
     * Reset/initialize internal output buffers so the splitter can be reused for a
     * new source without retaining previous results. This clears both
     * {@code splittedSegments} and {@code sourceBridgeProperties}.
     */
    public void resetOutputBuffers() {
        if (this.splittedSegments == null) this.splittedSegments = new ArrayList<>(); else this.splittedSegments.clear();
        if (this.sourceBridgeProperties == null) this.sourceBridgeProperties = new ArrayList<>(); else this.sourceBridgeProperties.clear();
    }

    /**
     * Create a BridgeSourceBuilder.
     *
     * @param profileBuilder provider used to discover bridges and query bridge properties
     * @param minOverlapLengthMeters threshold (in geometry coordinate units, typically meters)
     *        below which an intersection between a line and a footprint is considered
     *        insignificant. Insignificant intersections are removed from the remaining
     *        geometry but are not recorded as bridge fragments.
     */

    /**
     * Variant used when caller knows the source is on a specific bridge.
     * This will validate inputs and delegate to the bridge-specific overload.
     *
    * <p>Side-effects:</p>
    * <ul>
    *   <li>Populates the internal lists {@code splittedSegments} and {@code sourceBridgeProperties}.</li>
    *   <li>If the bridge-specific fragment cannot be determined, the whole source is treated as ground.</li>
    * </ul>
    *
    * @param sourcePk source primary key (for logging/tracing)
    * @param sourceGeom source geometry (expected LineString)
    * @param isOnBridge must be true when bridgePk is provided
    * @param bridgePk bridge primary key
     */
    public void createBridgeRelatedLineSources(long sourcePk, Geometry sourceGeom, boolean isOnBridge, long bridgePk) {
        LOGGER.debug("sourcePk={} enter createBridgeRelatedLineSources(isOnBridge={}, bridgePk={})", sourcePk, isOnBridge, bridgePk);
        if (sourceGeom == null || !(sourceGeom instanceof LineString)) {
            LOGGER.error("sourcePk={} - Source geometry is null or not a LineString", sourcePk);
            return;
        }

        if (!isOnBridge) {
            LOGGER.warn("sourcePk={} - Parameter isOnBridge is set to true, since bridgePk is given. ", sourcePk);
        }

        createLinesOfSingleBridge(sourcePk, sourceGeom, bridgePk);
        // post-condition: internal buffers must remain aligned
        if (!checkSegmentSize()) {
            LOGGER.error("sourcePk={} - internal buffer size mismatch after createBridgeRelatedLineSources(single bridge): splittedSegments.size()={} sourceBridgeProperties.size()={}", sourcePk, this.splittedSegments.size(), this.sourceBridgeProperties.size());
            throw new IllegalStateException("Buffer size mismatch after createBridgeRelatedLineSources for sourcePk=" + sourcePk);
        }
        return;
        
    }

    /**
     * Variant called when caller indicates whether the source is on any bridge.
     * If isOnBridge is true, we delegate to the discovery-based overload; otherwise
     * we treat the whole line as ground (and create mirror sources for bridges higher
     * than MIN_DECK_HEIGHT).
    *
    * Side-effects:
    * - Populates {@code splittedSegments} and {@code sourceBridgeProperties} with
    *   fragments and their associated SourceBridgeProperty.
    * - When {@code isOnBridge} is false, the whole source is recorded as a ground
    *   fragment and mirror sources are generated for bridges above {@code MIN_DECK_HEIGHT}.
    *
    * Note: The method does not return the generated fragments; callers should
    * read the populated internal lists after invocation.
     */
    public void createBridgeRelatedLineSources(long sourcePk, Geometry sourceGeom, boolean isOnBridge) {
        LOGGER.debug("sourcePk={} enter createBridgeRelatedLineSources(isOnBridge={})", sourcePk, isOnBridge);
        if (sourceGeom == null || !(sourceGeom instanceof LineString)) {
            LOGGER.error("sourcePk={} - Source geometry is null or not a LineString", sourcePk);
            return;
        }

        if (isOnBridge) {
            createLinesOfMultipleBridges(sourcePk, sourceGeom);
            // post-condition: internal buffers must remain aligned
            if (!checkSegmentSize()) {
                LOGGER.error("sourcePk={} - internal buffer size mismatch after createBridgeRelatedLineSources(discovered bridges): splittedSegments.size()={} sourceBridgeProperties.size()={}", sourcePk, this.splittedSegments.size(), this.sourceBridgeProperties.size());
                throw new IllegalStateException("Buffer size mismatch after createBridgeRelatedLineSources for sourcePk=" + sourcePk);
            }
            return;
        } else {
            LOGGER.debug("sourcePk={} - treating full source as ground and generating mirror sources (min deck filter={})", sourcePk, MIN_DECK_HEIGHT);
            addSourcesOnGround((LineString) sourceGeom);
            addMirrorSources((LineString) sourceGeom, MIN_DECK_HEIGHT, -1);
        }
        // post-condition: when not on bridge we added ground/mirror sources; ensure buffers align
        if (!checkSegmentSize()) {
            LOGGER.error("sourcePk={} - internal buffer size mismatch after createBridgeRelatedLineSources(ground flow): splittedSegments.size()={} sourceBridgeProperties.size()={}", sourcePk, this.splittedSegments.size(), this.sourceBridgeProperties.size());
            throw new IllegalStateException("Buffer size mismatch after createBridgeRelatedLineSources for sourcePk=" + sourcePk);
        }
    }

    /**
     * Handle the case where the caller indicates the source is located on a
     * specific bridge identified by {@code bridgePk}.
     *
     * Contract:
     * - If the profile builder has no bridges, the original geometry is kept as
     *   a single ground fragment.
     * - If a bridge-specific fragment cannot be obtained (missing footprint or
     *   no linear intersection), the whole source is treated as ground to avoid
     *   NPEs and to keep behavior predictable.
     * - Otherwise, create actual and imaginary source entries for the
     *   fragment and generate mirror sources based on deck heights.
     *
     * @param sourcePk caller-provided source id (for tracing/logging)
     * @param sourceGeom input geometry, expected to be a LineString
     * @param bridgePk target bridge primary key
     */
    private void createLinesOfSingleBridge(long sourcePk, Geometry sourceGeom, long bridgePk) {
        LOGGER.debug("sourcePk={} createLinesOfSingleBridge bridgePk={}", sourcePk, bridgePk);
        if (sourceGeom == null || !(sourceGeom instanceof LineString)) {
            LOGGER.error("sourcePk={} - Source geometry is null or not a LineString", sourcePk);
            return;
        }

        if (!profileBuilder.hasBridges()) {
            LOGGER.debug("sourcePk={} - profileBuilder has no bridges, recording as ground fragment", sourcePk);
            this.splittedSegments.add(sourceGeom);
            this.sourceBridgeProperties.add(new SourceBridgeProperty());
            return;
        }

        LineString lineStringOnTargetBridge = splitLineStringOnBridge((LineString) sourceGeom, bridgePk);
        if (lineStringOnTargetBridge == null) {
            // If we couldn't obtain a bridge-specific fragment, treat the source as ground
            // to avoid NPE and keep behavior predictable for callers/tests.
            LOGGER.debug("sourcePk={} - no bridge fragment found for bridgePk={}, treating as ground", sourcePk, bridgePk);
            addSourcesOnGround((LineString) sourceGeom);
            addMirrorSources((LineString) sourceGeom, bridgePk);
            return;
        }

        LOGGER.debug("sourcePk={} - found bridge fragment for bridgePk={}, adding bridge and mirror sources", sourcePk, bridgePk);
        addSourcesOnBridge(lineStringOnTargetBridge, bridgePk);
        addMirrorSources(lineStringOnTargetBridge, bridgePk);
    }


    /**
     * Discovery-based flow: discover all bridges overlapping the provided
     * {@code sourceGeom} (via the profile builder), split the line by bridge
     * footprints and produce source fragments.
     *
     * Behavior notes:
     * - Fragments not belonging to any bridge are recorded under key -1L.
     * - Bridges below the configured deck height filter are ignored.
     * - The method populates internal buffers used by callers: {@code
     *   splittedSegments} and {@code sourceBridgeProperties}.
     *
     * @param sourcePk caller-provided source id (for tracing/logging)
     * @param sourceGeom input geometry, expected to be a LineString
     */
    private void createLinesOfMultipleBridges(long sourcePk, Geometry sourceGeom) {
        if (sourceGeom == null || !(sourceGeom instanceof LineString)) {
            LOGGER.error("Source geometry is null or not a LineString");
            return;
        }

        if (!profileBuilder.hasBridges()) {
            this.splittedSegments.add(sourceGeom);
            this.sourceBridgeProperties.add(new SourceBridgeProperty());
            return;
        }


        LOGGER.debug("sourcePk={} - discovering bridge fragments (minDeckHeight={})", sourcePk, MIN_DECK_HEIGHT);
        // Discover fragments grouped by bridge primary key.
        // The returned map uses key -1L for fragments not belonging to any bridge
        // (ground fragments). The minDeckHeight parameter filters out bridges
        // below the provided absolute deck elevation.
        Map<Long, List<LineString>> lineStringMap = splitLineStringWithBridge(
            sourcePk,
            (LineString) sourceGeom, MIN_DECK_HEIGHT
        );

        
        for (Map.Entry<Long, List<LineString>> entry : lineStringMap.entrySet()) {
            Long bridgePkKey = entry.getKey();
            List<LineString> fragments = entry.getValue();
            if (fragments == null) continue;
            for (LineString frag : fragments) {
                if (bridgePkKey != -1) {
                    // Bridge fragment: create actual/imaginary and mirror sources.
                    // NOTE: addSourcesOnBridge intentionally adds two Source entries
                    // (actual + imaginary) using the same LineString instance.
                    LOGGER.debug("sourcePk={} - adding bridge fragments for bridgePk={}, fragmentLength={}", sourcePk, bridgePkKey, frag.getLength());
                    addSourcesOnBridge(frag, bridgePkKey);
                    addMirrorSources(frag, bridgePkKey);
                } else {
                    // Non-bridge fragments (key -1) represent ground parts of the line.
                    // These are recorded as SOURCE_NOT_RELATED_TO_BRIDGE entries.
                    LOGGER.debug("sourcePk={} - adding ground fragment fragmentLength={}", sourcePk, frag.getLength());
                    addSourcesOnGround(frag);
                }
            }
        }

    }

    /**
     * Record a fragment that is not related to any bridge (ground source).
     *
     * Adds a single entry to {@code splittedSegments} and a corresponding
     * {@link SourceBridgeProperty} with default values indicating the fragment
     * is not related to a bridge.
     *
     * @param lineString linear fragment representing ground portion
     */
    private void addSourcesOnGround(LineString lineString) {
        this.splittedSegments.add((Geometry) lineString);
        this.sourceBridgeProperties.add(new SourceBridgeProperty());
    }

    /**
     * Record bridge-related fragments for a given linear piece that lies on a
     * bridge.
     *
     * This method intentionally appends two entries sharing the same
     * LineString instance:
     * - an ACTUAL_SOURCE_ON_BRIDGE (real source on deck)
     * - an IMAGINARY_SOURCE_UNDER_BRIDGE (imaginary source used by the model)
     *
     * The caller is responsible for ensuring the fragment indeed belongs to the
     * bridge identified by {@code bridgePk}.
     *
     * @param lineString linear fragment on the bridge
     * @param bridgePk bridge primary key
     */
    private void addSourcesOnBridge(LineString lineString, long bridgePk) {
        this.splittedSegments.add((Geometry) lineString);
        this.sourceBridgeProperties.add(new SourceBridgeProperty(SourceBridgeProperty.SourceType.ACTUAL_SOURCE_ON_BRIDGE, bridgePk, -1));

        this.splittedSegments.add((Geometry) lineString);
        this.sourceBridgeProperties.add(new SourceBridgeProperty(SourceBridgeProperty.SourceType.IMAGINARY_SOURCE_UNDER_BRIDGE, bridgePk, -1));
        return;
    }

    /**
     * Generate mirror sources for fragments that are located on bridges whose
     * deck elevation is greater than {@code targetDeckHeight}.
     *
     * This method performs a discovery split with {@code targetDeckHeight +
     * OFFSET} to find bridges strictly above the provided deck elevation
     * and creates MIRROR_SOURCE entries for each matching fragment.
     *
     * @param lineString fragment to test for mirror creation
     * @param targetDeckHeight deck height threshold to compare against
     */
    private void addMirrorSources(LineString lineString, double targetDeckHeight, long bridgePkOn) {
        Map<Long, List<LineString>> linestringMap = splitLineStringWithBridge(
            -1L,
            (LineString) lineString,
            targetDeckHeight + OFFSET
        );

        for (Map.Entry<Long, List<LineString>> entry : linestringMap.entrySet()) {
            Long bridgePkKey = entry.getKey();
            List<LineString> fragments = entry.getValue();
            if (fragments == null) continue;
            for (LineString frag : fragments) {
                if (bridgePkKey != -1) {
                    this.splittedSegments.add((Geometry) frag);
                    this.sourceBridgeProperties.add(new SourceBridgeProperty(SourceBridgeProperty.SourceType.MIRROR_SOURCE, bridgePkOn, bridgePkKey));
                }
            }
        }
        return;
    }

    /**
     * Convenience overload: compute mirror sources using the deck height of a
     * specific bridge (identified by {@code bridgePk}). If {@code bridgePk} is
     * -1 no action is performed.
     *
     * @param lineString fragment to test
     * @param bridgePk bridge primary key whose deck height will be used
     */
    private void addMirrorSources(LineString lineString, long bridgePkOn) {
        double targetDeckHeight;
        if (bridgePkOn == -1) {
            targetDeckHeight = MIN_DECK_HEIGHT;
        } else {
            targetDeckHeight = profileBuilder.getBridgeByPk(bridgePkOn).getAverageAbsoluteDeckHeight();
        }

        addMirrorSources(lineString, targetDeckHeight, bridgePkOn);
        return;
    }

    /**
     * Split a LineString using a single bridge identified by {@code bridgePk}.
     *
     * This helper performs a discovery split using the bridge's deck height as
     * a filter, then returns the first fragment associated with the bridge pk
     * or {@code null} when no fragment was found.
     *
     * @param lineString input LineString to split
     * @param bridgePk bridge primary key used as filter
     * @return first LineString fragment for the bridge or null if none
     */
    private LineString splitLineStringOnBridge(LineString lineString, long bridgePk) {
        Map<Long, List<LineString>> lineStringMap = splitLineStringWithBridge(
            -1L,
            lineString,
            profileBuilder.getBridgeByPk(bridgePk).getAverageAbsoluteDeckHeight() - OFFSET
        );

        List<LineString> list = lineStringMap.get(bridgePk);
        if (list == null || list.isEmpty()) return null;
        return list.get(0);
    }

    
    /**
     * Convenience overload: split by discovering bridges overlapping the line.
     */
    /**
     * Split the provided LineString by discovering bridges whose deck height is
     * greater than or equal to {@code minDeckHeight}.
     *
     * @param sourcePk caller source id used only for logging; may be -1 when not available
     * @param lineString input LineString
     * @param minDeckHeight minimum absolute deck elevation to include bridges
     * @return map of bridgePk -> list of LineString fragments (key -1L for ground fragments)
     */
    private Map<Long, List<LineString>> splitLineStringWithBridge(long sourcePk, LineString lineString, double minDeckHeight) {

        Map<Long, List<LineString>> result = new HashMap<>();
        if (lineString == null) {
            return result;
        }

        Geometry remaining = lineString;

        Envelope lineEnvelope = lineString.getEnvelopeInternal();
        
        List<Bridge> bridges = profileBuilder.getBridgesIn(lineEnvelope);
        bridges.removeIf(b -> {
            Double v = b.getAverageAbsoluteDeckHeight();
            return v == null || v.doubleValue() < minDeckHeight;
        });
        bridges.sort(Comparator.comparingDouble(b -> b.getAverageAbsoluteDeckHeight()));

        if (bridges.size() == 0) {
            addRemainingFragments(remaining, result);
            return result;
        }

        for (Bridge bridge : bridges) {
            Geometry footprint = bridge.getFootprintGeometry();
            if (footprint == null) continue;

            try {
                remaining = processBridgeIntersection(sourcePk, remaining, footprint, bridge.getPrimaryKey(), result);
            } catch (Exception e) {
                LOGGER.warn("sourcePk={} Error while processing bridge pk {}: {}", sourcePk, bridge.getPrimaryKey(), e.getMessage());
            }
        }

        // add remaining non-bridge fragments under key -1
        addRemainingFragments(remaining, result);

        return result;
    }



    /**
     * Compute intersection between the current remaining geometry and a bridge footprint.
     * Side-effects:
     * - Appends any LineString fragments of the intersection to the result map under the
     *   provided bridge primary key (pk).
     * - Returns the updated remaining geometry with the footprint area removed (difference).
     *
     * Notes and edge-cases:
     * - If remaining is null/empty or bounding boxes do not intersect, the original remaining
     *   geometry is returned unchanged.
     * - If the intersection length is below minOverlapLengthMeters the intersection is
     *   considered insignificant and only removed from remaining (not added to result).
     * - Geometry operations may throw runtime exceptions due to topology issues; exceptions
     *   are caught and logged (WARN) and the original remaining is returned to keep processing
     *   robust.
     *
     * @param remaining current geometry representing parts of the line not yet assigned to any bridge
     * @param bridgeFootprint footprint geometry for the bridge (expected 2D polygon)
     * @param pk bridge primary key used as result map key
     * @param result map collecting fragments per bridge pk (modified by this method)
     * @return updated remaining geometry after subtracting the bridge footprint (or the original on error)
     */
    /**
     * Compute intersection between the current remaining geometry and a bridge footprint.
     * This variant accepts {@code sourcePk} for improved logging context.
     *
     * @param sourcePk caller source id for logging; may be -1 when unknown
     * @param remaining current geometry representing parts of the line not yet assigned to any bridge
     * @param bridgeFootprint footprint geometry for the bridge (expected 2D polygon)
     * @param pk bridge primary key used as result map key
     * @param result map collecting fragments per bridge pk (modified by this method)
     * @return updated remaining geometry after subtracting the bridge footprint (or the original on error)
     */
    private Geometry processBridgeIntersection(long sourcePk, Geometry remaining, Geometry bridgeFootprint, Long pk, Map<Long, List<LineString>> result) {
        if (remaining == null || remaining.isEmpty()) return remaining;
        if (!remaining.getEnvelopeInternal().intersects(bridgeFootprint.getEnvelopeInternal())) return remaining;

        Geometry inter = remaining.intersection(bridgeFootprint);
        if (inter != null && !inter.isEmpty()) {
            double interLength = inter.getLength();
            if (interLength < this.minOverlapLengthMeters) {
                // insignificant overlap: remove footprint area from remaining but do not record fragments
                try {
                    LOGGER.debug("sourcePk={} bridgePk={} - insignificant intersection length={} < threshold={}", sourcePk, pk, interLength, this.minOverlapLengthMeters);
                    return remaining.difference(bridgeFootprint); // may be LineString / MultiLineString / null Geometry
                } catch (Exception ex) {
                    LOGGER.warn("sourcePk={} Error while subtracting insignificant bridge footprint for bridge pk {}: {}", sourcePk, pk, ex.getMessage(), ex);
                    return remaining;
                }
            }

            addIntersectionFragments(inter, pk, result);

            try {
                LOGGER.debug("sourcePk={} bridgePk={} - recording intersection length={}", sourcePk, pk, inter.getLength());
                return remaining.difference(bridgeFootprint); // may be LineString / MultiLineString / null Geometry
            } catch (Exception ex) {
                LOGGER.warn("sourcePk={} Error while subtracting bridge footprint for bridge pk {}: {}", sourcePk, pk, ex.getMessage(), ex);
                return remaining;
            }
        }

        return remaining;
    }

    /**
     * Extract LineString fragments from an intersection geometry and append them to the
     * result map under the given bridge pk.
     *
     * The intersection geometry may be a LineString, MultiLineString or other geometry types.
     * Only contained LineString geometries are added to the result. This keeps the result
     * focused on linear fragments suitable for later processing.
     *
     * @param inter geometry resulting from intersection between the original line and a footprint
     * @param pk bridge primary key to use as the result map key
     * @param result result map to append fragments into (modified by this method)
     */
    private void addIntersectionFragments(Geometry inter, Long pk, Map<Long, List<LineString>> result) {
        if (inter instanceof LineString) {
            result.computeIfAbsent(pk, k -> new ArrayList<>()).add((LineString) inter);
        } else if (inter instanceof MultiLineString) {
            MultiLineString mls = (MultiLineString) inter;
            for (int i = 0; i < mls.getNumGeometries(); i++) {
                Geometry g = mls.getGeometryN(i);
                if (g instanceof LineString) {
                    result.computeIfAbsent(pk, k -> new ArrayList<>()).add((LineString) g);
                }
            }
        }
    }

    /**
     * Add remaining fragments (those not covered by any bridge footprint) into the
     * result map under the key -1L.
     *
     * Only LineString geometries are recorded. If the remaining geometry is a
     * MultiLineString, each contained LineString is added separately.
     *
     * @param remaining geometry representing unassigned parts of the original line
     * @param result map to append remaining fragments into (modified by this method)
     */
    private void addRemainingFragments(Geometry remaining, Map<Long, List<LineString>> result) {
        if (remaining == null || remaining.isEmpty()) return;
        if (remaining instanceof LineString) {
            result.computeIfAbsent(-1L, k -> new ArrayList<>()).add((LineString) remaining);
        } else if (remaining instanceof MultiLineString) {
            MultiLineString mls = (MultiLineString) remaining;
            for (int i = 0; i < mls.getNumGeometries(); i++) {
                Geometry g = mls.getGeometryN(i);
                if (g instanceof LineString) {
                    result.computeIfAbsent(-1L, k -> new ArrayList<>()).add((LineString) g);
                }
            }
        }
    }
}
