package org.noise_planet.noisemodelling.propagation;

import org.noise_planet.noisemodelling.pathfinder.PathFinder;
import org.noise_planet.noisemodelling.pathfinder.SourcePointInfo;
import org.noise_planet.noisemodelling.pathfinder.ReceiverPointInfo;

/**
 * Attenuation or noise level value at receiver location
 * May be linked with a source
 * May be linked with a period
 */
public class ReceiverNoiseLevel {
    private SourcePointInfo source = null;
    private ReceiverPointInfo receiver = null;
    private String period = "";
    private double [] levels = new double[0];

    public ReceiverNoiseLevel(SourcePointInfo source,
                                ReceiverPointInfo receiver,
                                String period,
                                double[] levels) {
        this.levels = levels;
        this.period = period;
        this.receiver = receiver;
        this.source = source;
    }

    public SourcePointInfo getSource() {
        return source;
    }

    public ReceiverPointInfo getReceiver() {
        return receiver;
    }

    public String getPeriod() {
        return period;
    }

    public double[] getLevels() {
        return levels;
    }

    public ReceiverNoiseLevel() {
    }
}
