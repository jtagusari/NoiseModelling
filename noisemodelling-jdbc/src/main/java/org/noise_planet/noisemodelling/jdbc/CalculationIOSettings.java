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
 * Global configuration of NoiseModelling computation based on database data
 * This is input only, these settings are never updated by org.noise_planet.noisemodelling.jdbc class
 */
public class CalculationIOSettings {
    public boolean exportAttenuationMatrix;
    public static final String DEFAULT_RECEIVERS_LEVEL_TABLE_NAME = "RECEIVERS_LEVEL";
    /**
     * Noise level on the receiver for each period if mergeSources is true and no sound source were found
     */
    public double noSourceNoiseLevel = -99;

    public CalculationIOSettings() {
    }

    /**
     * Path to write the computation time and other statistics in a csv file
     */
    public File CSVProfilerOutputPath = null;
    /**
     * Create a new csv line after this time in seconds
     */
    public int CSVProfilerWriteInterval = 60;

    /**
     * With attenuation export also the json of the related cnossos path, for debugging purpose
     */
    public boolean exportCnossosPathWithAttenuation = false;
    public boolean keepAbsorption = false; // in rays, keep store detailed absorption data
    public int maximumRaysOutputCount = 0; // if export rays, do not keep more than this number of rays (0 infinite)

    public enum ExportRaysMethods {TO_RAYS_TABLE, NONE}
    public ExportRaysMethods exportRaysMethod = ExportRaysMethods.NONE;
    /** Cnossos revisions have multiple coefficients for road emission formulae
     * this parameter will be removed when the final version of Cnossos will be published
     */
    public int coefficientVersion = 2;

    // Output config

    /** maximum dB Error, stop calculation if the sum of further sources contributions are smaller than this value */
    public double maximumError = 0;

    public int geojsonColumnSizeLimit = 1000000; // sql column size limitation for geojson

    public int getMaximumRaysOutputCount() {
        return maximumRaysOutputCount;
    }
    public int outputMaximumQueue = 50000;

    public boolean mergeSources = true;

    public String receiversLevelTable = DEFAULT_RECEIVERS_LEVEL_TABLE_NAME;
    public String raysTable = "RAYS";

    public File sqlOutputFile;
    public Boolean sqlOutputFileCompression = true;
    public Boolean dropResultsTable = true;
    public boolean computeLAEQOnly = false;

    /**
     * If true the position of the receiver (with the altitude if available) will be exported into the results tables
     */
    public boolean exportReceiverPosition = false;


    public static class Builder {
        private boolean exportAttenuationMatrix;
        private double noSourceNoiseLevel = -99;
        private File CSVProfilerOutputPath = null;
        private int CSVProfilerWriteInterval = 60;
        private boolean exportCnossosPathWithAttenuation = false;
        private boolean keepAbsorption = false;
        private int maximumRaysOutputCount = 0;
        private ExportRaysMethods exportRaysMethod = ExportRaysMethods.NONE;
        private int coefficientVersion = 2;
        private double maximumError = 0;
        private int geojsonColumnSizeLimit = 1000000;
        private boolean mergeSources = true;
        private String receiversLevelTable = DEFAULT_RECEIVERS_LEVEL_TABLE_NAME;
        private String raysTable = "RAYS";
        private File sqlOutputFile;
        private Boolean sqlOutputFileCompression = true;
        private Boolean dropResultsTable = true;
        private boolean computeLAEQOnly = false;
        private boolean exportReceiverPosition = false;

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

        public Builder setCoefficientVersion(int coefficientVersion) {
            this.coefficientVersion = coefficientVersion;
            return this;
        }

        public Builder setMaximumError(double maximumError) {
            this.maximumError = maximumError;
            return this;
        }

        public Builder setGeojsonColumnSizeLimit(int geojsonColumnSizeLimit) {
            this.geojsonColumnSizeLimit = geojsonColumnSizeLimit;
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

        public Builder setSqlOutputFile(File sqlOutputFile) {
            this.sqlOutputFile = sqlOutputFile;
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
            CalculationIOSettings settings = new CalculationIOSettings();
            settings.exportAttenuationMatrix = this.exportAttenuationMatrix;
            settings.noSourceNoiseLevel = this.noSourceNoiseLevel;
            settings.CSVProfilerOutputPath = this.CSVProfilerOutputPath;
            settings.CSVProfilerWriteInterval = this.CSVProfilerWriteInterval;
            settings.exportCnossosPathWithAttenuation = this.exportCnossosPathWithAttenuation;
            settings.keepAbsorption = this.keepAbsorption;
            settings.maximumRaysOutputCount = this.maximumRaysOutputCount;
            settings.exportRaysMethod = this.exportRaysMethod;
            settings.coefficientVersion = this.coefficientVersion;
            settings.maximumError = this.maximumError;
            settings.geojsonColumnSizeLimit = this.geojsonColumnSizeLimit;
            settings.mergeSources = this.mergeSources;
            settings.receiversLevelTable = this.receiversLevelTable;
            settings.raysTable = this.raysTable;
            settings.sqlOutputFile = this.sqlOutputFile;
            settings.sqlOutputFileCompression = this.sqlOutputFileCompression;
            settings.dropResultsTable = this.dropResultsTable;
            settings.computeLAEQOnly = this.computeLAEQOnly;
            settings.exportReceiverPosition = this.exportReceiverPosition;
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


    public ExportRaysMethods getExportRaysMethod() {
        return exportRaysMethod;
    }

    public boolean isKeepAbsorption() {
        return keepAbsorption;
    }

    /**
     * @param coefficientVersion Cnossos revisions have multiple coefficients for road emission formulae this parameter
     *                          will be removed when the final version of Cnossos will be published
     */
    public void setCoefficientVersion(int coefficientVersion) {
        this.coefficientVersion = coefficientVersion;
    }

    public int getCoefficientVersion() {
        return coefficientVersion;
    }

    /**
     * Maximum result stack to be inserted in database
     * if the stack is full, the computation core is waiting
     * @param outputMaximumQueue Maximum number of elements in stack
    */
    public void setOutputMaximumQueue(int outputMaximumQueue) {
        this.outputMaximumQueue = outputMaximumQueue;
    }

    /**
     * @return maximum dB Error, stop calculation if the maximum sum of further sources contributions are smaller than this value
     */
    public double getMaximumError() {
        return maximumError;
    }

    /**
     * @param maximumError maximum dB Error, stop calculation if the maximum sum of further sources contributions
     *                    compared to the current level at the receiver position are smaller than this value
     */
    public void setMaximumError(double maximumError) {
        this.maximumError = maximumError;
    }

    public void setMergeSources(boolean mergeSources) {
        this.mergeSources = mergeSources;
    }

    /**
     * @return Table name that contains rays dump (profile)
     */
    public String getRaysTable() {
        return raysTable;
    }

    /**
     */
    public void setRaysTable(String raysTable) {
        this.raysTable = raysTable;
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

    /**
     * @param receiversLevelTable Output table with noise level per receiver/source
     */
    public void setReceiversLevelTable(String receiversLevelTable) {
        this.receiversLevelTable = receiversLevelTable;
    }

    public File getCSVProfilerOutputPath() {
        return CSVProfilerOutputPath;
    }
}
