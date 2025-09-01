package org.noise_planet.noisemodelling.pathfinder.profilebuilder;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.triangulate.quadedge.Vertex;


/**
 * Utility encapsulating the profile-building algorithm used to compute a
 * {@link CutProfile} between a source and a receiver.
 *
 * <p>Rationale: the procedural logic that assembles cut-points, interpolates
 * ground/topography elevations and resolves obstacle interactions is complex
 * and was extracted from {@code ProfileBuilder} to keep that class small and
 * focused. This final helper is stateless and provides a single static
 * entry-point {@link #getProfile(...)}.</p>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Initialize the cut profile and source/receiver ground coefficients.</li>
 *   <li>Fetch and append topography cut points using {@link TopographyService}.</li>
 *   <li>Detect and append building, wall, bridge and ground-effect intersections
 *       using the corresponding services and utility helpers.</li>
 *   <li>Propagate ground coefficients across cut points and interpolate
 *       intermediate ground Z values when needed.</li>
 * </ul>
 *</p>
 */
public final class ProfileRetriever {
    private ProfileRetriever() {}

    /**
     * Build a {@link CutProfile} between {@code sourceCoordinate} and
     * {@code receiverCoordinate}.
     *
     * <p>Inputs:
     * <ul>
     *   <li>{@code defaultGroundAttenuation} is the fallback ground coefficient
     *       applied when no ground-effect feature is found.</li>
     *   <li>{@code stopAtObstacleOverSourceReceiver} controls whether the
     *       algorithm must stop early when encountering an obstacle that is
     *       above the source->receiver ray.</li>
     *   <li>Service parameters ({@code buildingService}, {@code wallService},
     *       {@code bridgeService}, {@code topographyService}, {@code groundService})
     *       provide geometry and spatial indexes required to detect intersections
     *       and to populate cut points.</li>
     *   <li>{@code maxLineLength} influences how long segments are split when
     *       querying spatial indexes.</li>
     * </ul>
     *
     * Outputs and side-effects:
     * <ul>
     *   <li>Returns a fully populated {@link CutProfile} with source, receiver
     *       and a sequenced list of {@link CutPoint}s representing topography,
     *       building/wall/bridge intersections and ground-effect transitions.</li>
     *   <li>The method does not mutate the provided service instances (it only
     *       queries them), but it relies on them being pre-populated and indexed
     *       (for example, {@link TopographyService#processDelaunay()} and
     *       {@link WallService#buildProcessedWallRtree()} should have been called
     *       by callers preparing the scene).</li>
     * </ul>
     *
     * Error modes and guarantees:
     * <ul>
     *   <li>If a service index is null (for example no topo tree), the method
     *       falls back to sensible defaults (zero ground elevation).</li>
     *   <li>The algorithm attempts to fill unknown ground coefficients and
     *       interpolate missing ground Z values; it does not throw on missing
     *       geometry but the resulting profile may be less detailed.</li>
     * </ul>
     *
     * @return built {@link CutProfile}
     */
    public static CutProfile getProfile(Coordinate sourceCoordinate,
                                        Coordinate receiverCoordinate,
                                        double defaultGroundAttenuation,
                                        boolean stopAtObstacleOverSourceReceiver,
                                        double maxLineLength,
                                        BuildingService buildingService,
                                        WallService wallService,
                                        BridgeService bridgeService,
                                        TopographyService topographyService,
                                        GroundService groundService,
                                        ProcessedWallService processedWallService,
                                        GeometryFactory factory) {

        CutPointSource sourcePoint  = new CutPointSource(sourceCoordinate);
        CutPointReceiver receiverPoint = new CutPointReceiver(receiverCoordinate);

        CutProfile profile = new CutProfile(sourcePoint, receiverPoint);

        // Add sourceCoordinate
        int groundAbsorptionIndex = groundService.getIntersectingGroundAbsorption(factory.createPoint(sourceCoordinate));
        if(groundAbsorptionIndex >= 0) {
            sourcePoint.setGroundCoefficient(groundService.getGroundAbsorptions().get(groundAbsorptionIndex).getCoefficient());
        } else {
            sourcePoint.setGroundCoefficient(defaultGroundAttenuation);
        }

        // Fetch topography evolution between sourceCoordinate and receiverCoordinate
        if(topographyService.getTopoRtree() != null) {
            topographyService.addTopoCutPts(sourceCoordinate, receiverCoordinate, profile, stopAtObstacleOverSourceReceiver);
            if(stopAtObstacleOverSourceReceiver && profile.hasTopographyIntersection) {
                return profile;
            }
        } else {
            profile.getSource().zGround = 0.0;
            profile.getReceiver().zGround = 0.0;
        }

        // Add Buildings/Walls/Bridges and Ground effect transition points
        if(processedWallService.getProcessedRtree() != null) {
            LineSegment fullLine = new LineSegment(sourceCoordinate, receiverCoordinate);
            ProfileUtils.addObstacleCutPts(fullLine, profile, stopAtObstacleOverSourceReceiver, maxLineLength,
                buildingService, wallService, bridgeService, groundService, processedWallService, factory);
            // Stop early if obstacle intersection is found and requested
            if(stopAtObstacleOverSourceReceiver && (profile.hasBuildingIntersection || profile.hasBridgeIntersection)) {
                return profile;
            }
        }

        // Propagate ground coefficient for unknown coefficients
        double currentCoefficient = sourcePoint.groundCoefficient;
        for (CutPoint cutPoint : profile.cutPoints) {
            if(Double.isNaN(cutPoint.groundCoefficient)) {
                cutPoint.setGroundCoefficient(currentCoefficient);
            } else if (cutPoint instanceof CutPointGroundEffect) {
                currentCoefficient = cutPoint.getGroundCoefficient();
            }
        }

        // Compute the interpolation of Z ground for intermediate points
        CutPoint previousZGround = sourcePoint;
        int nextPointIndex = 0;
        for (int pointIndex = 1; pointIndex < profile.cutPoints.size() - 1; pointIndex++) {
            CutPoint cutPoint = profile.cutPoints.get(pointIndex);
            if(Double.isNaN(cutPoint.zGround)) {
                if(nextPointIndex <= pointIndex) {
                    // look for next reference Z ground point
                    for (int i = pointIndex + 1; i < profile.cutPoints.size(); i++) {
                        CutPoint nextPoint = profile.cutPoints.get(i);
                        if (!Double.isNaN(nextPoint.zGround)) {
                            nextPointIndex = i;
                            break;
                        }
                    }
                }
                CutPoint nextPoint = profile.cutPoints.get(nextPointIndex);
                cutPoint.zGround = Vertex.interpolateZ(cutPoint.coordinate,
                        new Coordinate(previousZGround.coordinate.x, previousZGround.coordinate.y,
                                previousZGround.getzGround()),
                        new Coordinate(nextPoint.coordinate.x, nextPoint.coordinate.y, nextPoint.getzGround()));
                if(Double.isNaN(cutPoint.coordinate.z) || cutPoint instanceof CutPointGroundEffect) {
                    // Bottom of walls are set to NaN z because it can be computed here at low cost
                    // (without fetch dem r-tree)
                    // ground effect change points is taking the Z of ground in coordinate too
                    cutPoint.coordinate.setZ(cutPoint.zGround);
                }
            } else {
                // we have an update on Z ground
                previousZGround = cutPoint;
            }
        }
        return profile;
    }
}
