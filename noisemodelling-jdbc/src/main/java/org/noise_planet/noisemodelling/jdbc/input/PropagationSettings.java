/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

/**
 * Physical assumptions under which sound propagation is computed: phenomena to include
 * (reflection, diffraction, ground absorption), spatial extent, and calculation standard.
 * Use {@link Builder} to construct instances.
 */
public class PropagationSettings {

    /** Maximum distance from a source at which propagation paths are traced, in metres. */
    private final double maximumPropagationDistance;
    /** Maximum distance from a source at which specular reflections are searched, in metres. */
    private final double maximumReflectionDistance;
    /** Default ground absorption coefficient (Gs): 0 = acoustically hard, 1 = fully absorptive. */
    private final double gs;
    /** Number of successive specular reflections to compute. 0 disables reflection entirely. */
    private final int soundReflectionOrder;
    /** Enable multiple reflections between a train body and a trackside screen (required for railway scenarios). */
    private final boolean bodyBarrier;
    /** Compute diffraction around vertical edges of obstacles (horizontal plan). */
    private final boolean computeHorizontalDiffraction;
    /** Compute diffraction over the tops of obstacles (vertical plan). */
    private final boolean computeVerticalDiffraction;
    /** CNOSSOS-EU coefficient version: 1 = 2015 standard, 2 = 2020 standard. */
    private final int coefficientVersion;

    public PropagationSettings(double maximumPropagationDistance, double maximumReflectionDistance, double gs, int soundReflectionOrder, boolean bodyBarrier, boolean verbose, boolean computeHorizontalDiffraction, boolean computeVerticalDiffraction, int coefficientVersion) {
        this.maximumPropagationDistance = maximumPropagationDistance;
        this.maximumReflectionDistance = maximumReflectionDistance;
        this.gs = gs;
        this.soundReflectionOrder = soundReflectionOrder;
        this.bodyBarrier = bodyBarrier;
        this.computeHorizontalDiffraction = computeHorizontalDiffraction;
        this.computeVerticalDiffraction = computeVerticalDiffraction;
        this.coefficientVersion = coefficientVersion;
    }

    public static class Builder {
        private double maximumPropagationDistance = 750;
        private double maximumReflectionDistance = 100;
        private double gs = 0;
        private int soundReflectionOrder = 2;
        private boolean bodyBarrier = false;
        public boolean verbose = true;
        private boolean computeHorizontalDiffraction = true;
        private boolean computeVerticalDiffraction = true;
        private int coefficientVersion = 2;

        public Builder setMaximumPropagationDistance(double maximumPropagationDistance) {
            this.maximumPropagationDistance = maximumPropagationDistance;
            return this;
        }

        public Builder setMaximumReflectionDistance(double maximumReflectionDistance) {
            this.maximumReflectionDistance = maximumReflectionDistance;
            return this;
        }

        public Builder setGs(double gs) {
            this.gs = gs;
            return this;
        }

        public Builder setSoundReflectionOrder(int soundReflectionOrder) {
            this.soundReflectionOrder = soundReflectionOrder;
            return this;
        }

        public Builder setBodyBarrier(boolean bodyBarrier) {
            this.bodyBarrier = bodyBarrier;
            return this;
        }

        public Builder setVerbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder setComputeHorizontalDiffraction(boolean computeHorizontalDiffraction) {
            this.computeHorizontalDiffraction = computeHorizontalDiffraction;
            return this;
        }

        public Builder setComputeVerticalDiffraction(boolean computeVerticalDiffraction) {
            this.computeVerticalDiffraction = computeVerticalDiffraction;
            return this;
        }

        public Builder setCoefficientVersion(int coefficientVersion) {
            this.coefficientVersion = coefficientVersion;
            return this;
        }

        public PropagationSettings build() {
            return new PropagationSettings(maximumPropagationDistance, maximumReflectionDistance, gs, soundReflectionOrder, bodyBarrier, verbose, computeHorizontalDiffraction, computeVerticalDiffraction, coefficientVersion);
        }
    }

    public double getMaximumPropagationDistance() {
        return maximumPropagationDistance;
    }

    public double getMaximumReflectionDistance() {
        return maximumReflectionDistance;
    }

    public double getGs() {
        return gs;
    }

    public int getSoundReflectionOrder() {
        return soundReflectionOrder;
    }

    public boolean isBodyBarrier() {
        return bodyBarrier;
    }

    public boolean isComputeHorizontalDiffraction() {
        return computeHorizontalDiffraction;
    }

    public boolean isComputeVerticalDiffraction() {
        return computeVerticalDiffraction;
    }

    public int getCoefficientVersion() {
        return coefficientVersion;
    }
}