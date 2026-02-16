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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * All the datas Path of Cnossos
 */
public class CnossosPathExt extends CnossosPath {

    private static final Logger LOGGER = LoggerFactory.getLogger(CnossosPathExt.class);
    private CnossosPathExt cnossosPathBottomRoute = null;
    private CnossosPathExt cnossosPathTopRoute = null;

    public CnossosPathExt getCnossosPathBottomRoute() {return cnossosPathBottomRoute;}
    public void setCnossosPathBottomRoute(CnossosPathExt cnossosPathBottomRoute) {this.cnossosPathBottomRoute = cnossosPathBottomRoute;}
    public CnossosPathExt getCnossosPathTopRoute() {return cnossosPathTopRoute;}
    public void setCnossosPathTopRoute(CnossosPathExt cnossosPathTopRoute) {this.cnossosPathTopRoute = cnossosPathTopRoute;}

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
        boolean invalidMeanPlane = false;
        for (double num: meanPlane){
            if(!Double.isFinite(num)){
                invalidMeanPlane = true;
            }
        }
        if(invalidMeanPlane) {
            LOGGER.warn("Invalid mean plane coefficients detected; mean plane will be set to [0, 0] (horizontal) to allow path processing to continue, but results may be inaccurate.");
            meanPlane = new double[]{0, 0};
        }
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