/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.propagation.cnossos;

import java.util.ArrayList;

import org.locationtech.jts.algorithm.CGAlgorithms3D;
import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;
import org.noise_planet.noisemodelling.pathfinder.utils.geometry.JTSUtility;

/**
 * All the datas Path of Cnossos
 */
public class CnossosPathExt extends CnossosPath {

    private CnossosPath cnossosPathBottomRoute = null;
    private CnossosPath cnossosPathTopRoute = null;

    public CnossosPath getCnossosPathBottomRoute() {return cnossosPathBottomRoute;}
    public void setCnossosPathBottomRoute(CnossosPath cnossosPathBottomRoute) {this.cnossosPathBottomRoute = cnossosPathBottomRoute;}
    public CnossosPath getCnossosPathTopRoute() {return cnossosPathTopRoute;}
    public void setCnossosPathTopRoute(CnossosPath cnossosPathTopRoute) {this.cnossosPathTopRoute = cnossosPathTopRoute;}

    public CnossosPathExt() {
    }

    public CnossosPathExt(CutProfile cutProfile) {
        super(cutProfile);
    }

    public CnossosPathExt(CnossosPathExt other) {
        super(other);
        this.cnossosPathBottomRoute = other.getCnossosPathBottomRoute();
        this.cnossosPathTopRoute = other.getCnossosPathTopRoute();
    }

    public CnossosPathExt(Path path, AcousticPathConfiguration configuration) {

        this.setPointList(path.getPointList());
        this.setSegmentList(path.getSegmentList());
        this.setSourceOrientation(path.getSourceOrientation());
        this.setRaySourceReceiverDirectivity(path.getRaySourceReceiverDirectivity());
        this.setFavorable(true);
        
        this.setCutProfile(configuration.getCutProfile());
        
        double[] meanPlane = JTSUtility.getMeanPlaneCoefficients(configuration.getElevationProfile2D());

        SegmentPath sourceToReceiverPath = CnossosSegmentComputer.createSegmentPathWithGroundFactors(
            configuration.getCutPointCoordinates2D(), 
            meanPlane, 
            configuration.getCutProfile().calculateWeightedGroundAbsorption(), 
            configuration.getCutProfile().getGroundAbsorptionAtSource()
        );

        sourceToReceiverPath.setElevationProfile2D(configuration.getElevationProfile2D());

        sourceToReceiverPath.setDirectRayDistance(
            CGAlgorithms3D.distance(
                configuration.getCutProfile().getReceiver().getCoordinate(),
                configuration.getCutProfile().getSource().getCoordinate()
            )
        );

        this.setSRSegment(sourceToReceiverPath);

        return;
    }

}