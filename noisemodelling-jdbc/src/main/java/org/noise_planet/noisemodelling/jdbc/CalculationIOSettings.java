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
    public static final String DEFAULT_RECEIVERS_LEVEL_TABLE_NAME = "RECEIVERS_LEVEL"; 
    public enum ExportRaysMethods {TO_RAYS_TABLE, NONE}
    private final boolean exportAttenuationMatrix; // if true, export also the attenuation matrix (source, receiver, attenuation) in the database
    private final double noSourceNoiseLevel; // in dB, if mergeSources is true and no sound source were found, this noise level will be used for the receiver instead of -inf
    private final File CSVProfilerOutputPath; // if not null, write computation time and other statistics in a csv file in this folder
    private final int CSVProfilerWriteInterval; // create a new line in the csv profiler file after this time in seconds, if CSVProfilerOutputPath is not null
    private final boolean exportCnossosPathWithAttenuation; // if true, export also the json of the related cnossos path in the attenuation output, for debugging purpose
    private final boolean keepAbsorption; // in rays, keep store detailed absorption data
    private final int maximumRaysOutputCount; // if export rays, do not keep more than this number of rays (0 infinite)
    private final ExportRaysMethods exportRaysMethod; // if export rays, method to export rays, either in a database table or as a json in the results table, if exportAttenuationMatrix is true, the ray is exported in the RAYS table with attenuation values as attributes, otherwise only one ray per path is exported without attenuation values
    private final int coefficientVersion; // version of the coefficients to use for attenuation calculation, this is used to keep track of changes in the coefficients and avoid confusion when comparing results with different versions of the software, the value is stored in the database output for reference

    // Output config
    private final double maximumError; // in dB, if the sum of the contributions of the remaining sources is smaller than this value, the calculation is stopped and the final noise level is returned, this is used to speed up calculation by avoiding to calculate very low contributions that do not change significantly the final result, a value of 0 means that all sources are calculated, a value of 1 means that sources that contribute less than 1 dB to the final result are not calculated, a value of 3 means that sources that contribute less than 3 dB to the final result are not calculated, etc.

    private final int outputMaximumQueue; // maximum result stack to be inserted in database, if the stack is full, the computation core is waiting
    private final boolean mergeSources; // if true, merge the contributions of all sources at each receiver
    private final String receiversLevelTable; // output table with noise level per receiver/source, if mergeSources is true, this table contains the noise level per receiver, otherwise it contains the noise level per receiver and per source
    private final String raysTable; // output table for rays data
    private final Boolean sqlOutputFileCompression; // if true, compress the sql output file with gzip
    private final Boolean dropResultsTable; // if true, drop the output table before creating it, this is useful for debugging and for very large outputs that can cause performance issues when inserting in the database
    private final boolean computeLAEQOnly; // if true, only compute LAEQ values for each receiver, this is used to speed up calculation when only LAEQ values are needed, in this mode, the noise level for each source is not calculated and the maximum error threshold is not applied, the final LAEQ value is calculated by summing the contributions of all sources without applying the maximum error threshold, this means that the final LAEQ value is the same as if all sources were calculated, but the calculation is much faster because we do not need to calculate the noise level for each source and we do not need to apply the maximum error threshold

    /**
     * If true the position of the receiver (with the altitude if available) will be exported into the results tables
     */
    public boolean exportReceiverPosition = false;

    public CalculationIOSettings(double noSourceNoiseLevel, File CSVProfilerOutputPath, int CSVProfilerWriteInterval, boolean exportCnossosPathWithAttenuation, boolean exportAttenuationMatrix, boolean keepAbsorption, int maximumRaysOutputCount, ExportRaysMethods exportRaysMethod, int coefficientVersion, double maximumError, int outputMaximumQueue, boolean mergeSources, String receiversLevelTable, String raysTable, Boolean sqlOutputFileCompression, Boolean dropResultsTable, boolean computeLAEQOnly, boolean exportReceiverPosition) {
        this.noSourceNoiseLevel = noSourceNoiseLevel;
        this.CSVProfilerOutputPath = CSVProfilerOutputPath;
        this.CSVProfilerWriteInterval = CSVProfilerWriteInterval;
        this.exportCnossosPathWithAttenuation = exportCnossosPathWithAttenuation;
        this.exportAttenuationMatrix = exportAttenuationMatrix;
        this.keepAbsorption = keepAbsorption;
        this.maximumRaysOutputCount = maximumRaysOutputCount;
        this.exportRaysMethod = exportRaysMethod;
        this.coefficientVersion = coefficientVersion;
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
        private int coefficientVersion = 2;
        private double maximumError = 0;
        private int outputMaximumQueue = 50000;
        private boolean mergeSources = true;
        private String receiversLevelTable = DEFAULT_RECEIVERS_LEVEL_TABLE_NAME;
        private String raysTable = "RAYS";
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
            CalculationIOSettings settings = new CalculationIOSettings(this.noSourceNoiseLevel, this.CSVProfilerOutputPath, this.CSVProfilerWriteInterval, this.exportCnossosPathWithAttenuation, this.exportAttenuationMatrix, this.keepAbsorption, this.maximumRaysOutputCount, this.exportRaysMethod, this.coefficientVersion, this.maximumError, this.outputMaximumQueue, this.mergeSources, this.receiversLevelTable, this.raysTable, this.sqlOutputFileCompression, this.dropResultsTable, this.computeLAEQOnly, this.exportReceiverPosition);
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

    public int getCoefficientVersion() {
        return coefficientVersion;
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
