/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.propagation.cnossos;

import org.noise_planet.noisemodelling.pathfinder.profilebuilder.CutProfile;

/**
 * All the datas Path of Cnossos
 */
public class CnossosPathExt extends CnossosPath {
    public  double[] double_aBoundaryU = new double[0];
    public  double[] aRetroDiffU = new double[0];
    public  double[] aGlobalU = new double[0];
    public double[] aDifU = new double[0];
    public double deltaU = Double.MAX_VALUE;
    public double deltaPrimeU= Double.MAX_VALUE;
    public double deltaSRPrimeU= Double.MAX_VALUE;
    public ABoundary aBoundaryU = new ABoundary();
    public double e=0;
    public double deltaRetroU= Double.MAX_VALUE;

    public CnossosPathExt() {
    }

    public CnossosPathExt(CutProfile cutProfile) {
        super(cutProfile);
    }

    public CnossosPathExt(CnossosPathExt other) {
        super(other);
        this.double_aBoundaryU = other.double_aBoundaryU;
        this.aRetroDiffU = other.aRetroDiffU;
        this.aGlobalU = other.aGlobalU;
        this.aDifU = other.aDifU;
        this.deltaU = other.deltaU;
        this.deltaPrimeU = other.deltaPrimeU;
        this.deltaSRPrimeU = other.deltaSRPrimeU;
        this.aBoundaryU = other.aBoundaryU;
        this.deltaSRPrimeU = other.deltaSRPrimeU;
        this.deltaRetroU = other.deltaRetroU;
    }
}