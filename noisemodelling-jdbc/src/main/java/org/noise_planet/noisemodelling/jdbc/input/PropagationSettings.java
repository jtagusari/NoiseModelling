/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */
package org.noise_planet.noisemodelling.jdbc.input;

public class PropagationSettings {
    
    private final double maximumPropagationDistance;
    private final double maximumReflectionDistance;
    private final double gs;
    // Soil areas are split by the provided size in order to reduce the propagation time
    private final double groundSurfaceSplitSideLength;
    private final int soundReflectionOrder;

    private final boolean bodyBarrier; // it needs to be true if train propagation is computed (multiple reflection between the train and a screen)
    private final boolean computeHorizontalDiffraction;
    private final boolean computeVerticalDiffraction;


    public PropagationSettings(double maximumPropagationDistance, double maximumReflectionDistance, double gs, double groundSurfaceSplitSideLength, int soundReflectionOrder, boolean bodyBarrier, boolean verbose, boolean computeHorizontalDiffraction, boolean computeVerticalDiffraction) {
        this.maximumPropagationDistance = maximumPropagationDistance;
        this.maximumReflectionDistance = maximumReflectionDistance;
        this.gs = gs;
        this.groundSurfaceSplitSideLength = groundSurfaceSplitSideLength;
        this.soundReflectionOrder = soundReflectionOrder;
        this.bodyBarrier = bodyBarrier;
        this.computeHorizontalDiffraction = computeHorizontalDiffraction;
        this.computeVerticalDiffraction = computeVerticalDiffraction;
    }

    public static class Builder {
        private double maximumPropagationDistance = 750;
        private double maximumReflectionDistance = 100;
        private double gs = 0;
        // Soil areas are split by the provided size in order to reduce the propagation time
        private double groundSurfaceSplitSideLength = 200;
        private int soundReflectionOrder = 2;

        private boolean bodyBarrier = false; // it needs to be true if train propagation is computed (multiple reflection between the train and a screen)
        public boolean verbose = true;
        private boolean computeHorizontalDiffraction = true;
        private boolean computeVerticalDiffraction = true;

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

        public Builder setGroundSurfaceSplitSideLength(double groundSurfaceSplitSideLength) {
            this.groundSurfaceSplitSideLength = groundSurfaceSplitSideLength;
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

        public PropagationSettings build() {
            return new PropagationSettings(maximumPropagationDistance, maximumReflectionDistance, gs, groundSurfaceSplitSideLength, soundReflectionOrder, bodyBarrier, verbose, computeHorizontalDiffraction, computeVerticalDiffraction);
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

    public double getGroundSurfaceSplitSideLength() {
        return groundSurfaceSplitSideLength;
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

}