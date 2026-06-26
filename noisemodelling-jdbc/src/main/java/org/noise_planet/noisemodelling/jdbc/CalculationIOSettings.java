/**
 * NoiseModelling is a library capable of producing noise maps. It can be freely used either for research and education, as well as by experts in a professional use.
 * <p>
 * NoiseModelling is distributed under GPL 3 license. You can read a copy of this License in the file LICENCE provided with this software.
 * <p>
 * Official webpage : http://noise-planet.org/noisemodelling.html
 * Contact: contact@noise-planet.org
 */

package org.noise_planet.noisemodelling.jdbc;


import java.io.File;

/**
 * Output and accuracy configuration for the noise map computation:
 * output table names, source merging, error threshold, ray export options, and profiling.
 * Use {@link Builder} to construct instances.
 */
public class CalculationIOSettings {
    public static final String DEFAULT_RECEIVERS_LEVEL_TABLE_NAME = "RECEIVERS_LEVEL";
    public enum ExportRaysMethods {TO_RAYS_TABLE, NONE}
    /** When {@code true}, export per-source per-receiver attenuation matrix to the database. */
    private final boolean exportAttenuationMatrix;
    /** Noise level assigned to receivers when no source is found, in dB. */
    private final double noSourceNoiseLevel;
    /** Directory for profiler CSV output; {@code null} disables profiling. */
    private final File CSVProfilerOutputPath;
    /** Interval between profiler CSV rows, in seconds. */
    private final int CSVProfilerWriteInterval;
    /** When {@code true}, embed the CNOSSOS path JSON in the attenuation output (for debugging). */
    private final boolean exportCnossosPathWithAttenuation;
    /** When {@code true}, retain detailed per-path absorption data in exported rays. */
    private final boolean keepAbsorption;
    /** Maximum number of exported rays; 0 = unlimited. */
    private final int maximumRaysOutputCount;
    /** How to export propagation paths: {@code NONE} or {@code TO_RAYS_TABLE}. */
    private final ExportRaysMethods exportRaysMethod;
    /**
     * Stop adding source contributions when their remaining sum is below this threshold, in dB.
     * 0 = compute all sources.
     */
    private final double maximumError;
    /** Maximum number of results queued for database writing; computation blocks when the queue is full. */
    private final int outputMaximumQueue;
    /** When {@code true}, sum all source contributions at each receiver into a single row. */
    private final boolean mergeSources;
    /** Output table name for receiver noise levels. */
    private final String receiversLevelTable;
    /** Output table name for propagation path (ray) data. */
    private final String raysTable;
    /** When {@code true}, compress the SQL output file with gzip. */
    private final Boolean sqlOutputFileCompression;
    /** When {@code true}, drop the output table before writing results. */
    private final Boolean dropResultsTable;
    /** When {@code true}, compute only L_Aeq (skips per-source levels; faster). */
    private final boolean computeLAEQOnly;
    /** When {@code true}, include receiver coordinates in the output table. */
    public boolean exportReceiverPosition = false;

    public CalculationIOSettings(double noSourceNoiseLevel, File CSVProfilerOutputPath, int CSVProfilerWriteInterval, boolean exportCnossosPathWithAttenuation, boolean exportAttenuationMatrix, boolean keepAbsorption, int maximumRaysOutputCount, ExportRaysMethods exportRaysMethod, double maximumError, int outputMaximumQueue, boolean mergeSources, String receiversLevelTable, String raysTable, Boolean sqlOutputFileCompression, Boolean dropResultsTable, boolean computeLAEQOnly, boolean exportReceiverPosition) {
        this.noSourceNoiseLevel = noSourceNoiseLevel;
        this.CSVProfilerOutputPath = CSVProfilerOutputPath;
        this.CSVProfilerWriteInterval = CSVProfilerWriteInterval;
        this.exportCnossosPathWithAttenuation = exportCnossosPathWithAttenuation;
        this.exportAttenuationMatrix = exportAttenuationMatrix;
        this.keepAbsorption = keepAbsorption;
        this.maximumRaysOutputCount = maximumRaysOutputCount;
        this.exportRaysMethod = exportRaysMethod;
        this.maximumError = maximumError;
        this.outputMaximumQueue = outputMaximumQueue;
        this.mergeSources = mergeSources;
        this.receiversLevelTable = receiversLevelTable;
        this.raysTable = raysTable;
        this.sqlOutputFileCompression = sqlOutputFileCompression;
        this.dropResultsTable = dropResultsTable;
        this.computeLAEQOnly = computeLAEQOnly;
        this.exportReceiverPosition = exportReceiverPosition;
    }



    public static class Builder {
        private boolean exportAttenuationMatrix = false;
        private double noSourceNoiseLevel = -99;
        private File CSVProfilerOutputPath = null;
        private int CSVProfilerWriteInterval = 60;
        private boolean exportCnossosPathWithAttenuation = false;
        private boolean keepAbsorption = false;
        private int maximumRaysOutputCount = 0;
        private ExportRaysMethods exportRaysMethod = ExportRaysMethods.NONE;
        private double maximumError = 0;
        private int outputMaximumQueue = 50000;
        private boolean mergeSources = true;
        private String receiversLevelTable = DEFAULT_RECEIVERS_LEVEL_TABLE_NAME;
        private String raysTable = "RAYS";
        private Boolean sqlOutputFileCompression = true;
        private Boolean dropResultsTable = true;
        private boolean computeLAEQOnly = false;
        private boolean exportReceiverPosition = true;

        public Builder setExportAttenuationMatrix(boolean exportAttenuationMatrix) {
            this.exportAttenuationMatrix = exportAttenuationMatrix;
            return this;
        }

        public Builder setNoSourceNoiseLevel(double noSourceNoiseLevel) {
            this.noSourceNoiseLevel = noSourceNoiseLevel;
            return this;
        }

        public Builder setCSVProfilerOutputPath(File CSVProfilerOutputPath) {
            this.CSVProfilerOutputPath = CSVProfilerOutputPath;
            return this;
        }

        public Builder setCSVProfilerWriteInterval(int CSVProfilerWriteInterval) {
            this.CSVProfilerWriteInterval = CSVProfilerWriteInterval;
            return this;
        }

        public Builder setExportCnossosPathWithAttenuation(boolean exportCnossosPathWithAttenuation) {
            this.exportCnossosPathWithAttenuation = exportCnossosPathWithAttenuation;
            return this;
        }

        public Builder setKeepAbsorption(boolean keepAbsorption) {
            this.keepAbsorption = keepAbsorption;
            return this;
        }

        public Builder setMaximumRaysOutputCount(int maximumRaysOutputCount) {
            this.maximumRaysOutputCount = maximumRaysOutputCount;
            return this;
        }

        public Builder setExportRaysMethod(ExportRaysMethods exportRaysMethod) {
            this.exportRaysMethod = exportRaysMethod;
            return this;
        }

        public Builder setMaximumError(double maximumError) {
            this.maximumError = maximumError;
            return this;
        }

        public Builder setOutputMaximumQueue(int outputMaximumQueue) {
            this.outputMaximumQueue = outputMaximumQueue;
            return this;
        }

        public Builder setMergeSources(boolean mergeSources) {
            this.mergeSources = mergeSources;
            return this;
        }

        public Builder setReceiversLevelTable(String receiversLevelTable) {
            this.receiversLevelTable = receiversLevelTable;
            return this;
        }

        public Builder setRaysTable(String raysTable) {
            this.raysTable = raysTable;
            return this;
        }

        public Builder setSqlOutputFileCompression(Boolean sqlOutputFileCompression) {
            this.sqlOutputFileCompression = sqlOutputFileCompression;
            return this;
        }

        public Builder setDropResultsTable(Boolean dropResultsTable) {
            this.dropResultsTable = dropResultsTable;
            return this;
        }

        public Builder setComputeLAEQOnly(boolean computeLAEQOnly) {
            this.computeLAEQOnly = computeLAEQOnly;
            return this;
        }

        public Builder setExportReceiverPosition(boolean exportReceiverPosition) {
            this.exportReceiverPosition = exportReceiverPosition;
            return this;
        }

        public CalculationIOSettings build() {
            CalculationIOSettings settings = new CalculationIOSettings(this.noSourceNoiseLevel, this.CSVProfilerOutputPath, this.CSVProfilerWriteInterval, this.exportCnossosPathWithAttenuation, this.exportAttenuationMatrix, this.keepAbsorption, this.maximumRaysOutputCount, this.exportRaysMethod, this.maximumError, this.outputMaximumQueue, this.mergeSources, this.receiversLevelTable, this.raysTable, this.sqlOutputFileCompression, this.dropResultsTable, this.computeLAEQOnly, this.exportReceiverPosition);
            return settings;
        }
    }

    /**
     * @return If true the position of the receiver (with the altitude if available) will be exported into the results
     * tables
     */
    public boolean isExportReceiverPosition() {
        return exportReceiverPosition;
    }

    public boolean isComputeLAEQOnly() {
        return computeLAEQOnly;
    }

    public double getNoSourceNoiseLevel() {
        return noSourceNoiseLevel;
    }


    public ExportRaysMethods getExportRaysMethod() {
        return exportRaysMethod;
    }

    public boolean isKeepAbsorption() {
        return keepAbsorption;
    }

    /**
     * @return maximum dB Error, stop calculation if the maximum sum of further sources contributions are smaller than this value
     */
    public double getMaximumError() {
        return maximumError;
    }


    /**
     * @return Table name that contains rays dump (profile)
     */
    public String getRaysTable() {
        return raysTable;
    }


    public boolean isMergeSources() {
        return mergeSources;
    }

    /**
     * @return Output table with noise level per receiver/source
     */
    public String getReceiversLevelTable() {
        return receiversLevelTable;
    }

    public File getCSVProfilerOutputPath() {
        return CSVProfilerOutputPath;
    }

    public int getCSVProfilerWriteInterval() {
        return CSVProfilerWriteInterval;
    }

    public boolean isExportAttenuationMatrix() {
        return exportAttenuationMatrix;
    }

    public boolean isExportCnossosPathWithAttenuation() {
        return exportCnossosPathWithAttenuation;
    }
    
    public int getMaximumRaysOutputCount() {
        return maximumRaysOutputCount;
    }

    public boolean isDropResultsTable() {
        return dropResultsTable;
    }

    public boolean isSqlOutputFileCompression() {
        return sqlOutputFileCompression;
    }

    public int getOutputMaximumQueue() {
        return outputMaximumQueue;
    }
}
