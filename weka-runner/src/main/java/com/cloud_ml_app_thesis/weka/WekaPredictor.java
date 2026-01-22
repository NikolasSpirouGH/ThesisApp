package com.cloud_ml_app_thesis.weka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.clusterers.Clusterer;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Weka prediction logic for containerized execution.
 *
 * Reads params.json with:
 * - algorithmType: CLASSIFICATION, REGRESSION, or CLUSTERING
 * - targetColumn: Name of the target column (for setting class index in test data)
 *
 * Input files:
 * - DATA_DIR/params.json
 * - DATA_DIR/test_data.csv
 * - MODEL_DIR/model.ser (the trained model)
 *
 * Output files:
 * - MODEL_DIR/predictions.csv (original data with prediction column added)
 * - MODEL_DIR/prediction_metadata.json (metadata about the prediction run)
 */
public class WekaPredictor {

    private static final Logger log = LoggerFactory.getLogger(WekaPredictor.class);

    private final Path dataDir;
    private final Path modelDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public WekaPredictor(String dataDir, String modelDir) {
        this.dataDir = Path.of(dataDir);
        this.modelDir = Path.of(modelDir);
    }

    public void predict() throws Exception {
        // 1. Read params.json
        Path paramsFile = dataDir.resolve("params.json");
        if (!Files.exists(paramsFile)) {
            throw new IllegalStateException("params.json not found in DATA_DIR: " + paramsFile);
        }

        JsonNode params = mapper.readTree(paramsFile.toFile());
        log.info("Loaded parameters: {}", params);

        String algorithmType = params.get("algorithmType").asText();
        String targetColumn = params.has("targetColumn") && !params.get("targetColumn").isNull()
                ? params.get("targetColumn").asText()
                : null;
        String basicAttributesColumns = params.has("basicAttributesColumns") && !params.get("basicAttributesColumns").isNull()
                ? params.get("basicAttributesColumns").asText()
                : null;

        log.info("Algorithm type: {}", algorithmType);
        log.info("Target column: {}", targetColumn);
        log.info("Basic attributes columns: {}", basicAttributesColumns);

        // 2. Load model
        Path modelFile = modelDir.resolve("model.ser");
        if (!Files.exists(modelFile)) {
            throw new IllegalStateException("model.ser not found in MODEL_DIR: " + modelFile);
        }

        Object model = SerializationHelper.read(modelFile.toString());
        log.info("Loaded model: {}", model.getClass().getName());

        // 2b. Load training data header (for class labels in classification)
        Instances trainingHeader = null;
        Path headerFile = modelDir.resolve("header.ser");
        if (Files.exists(headerFile)) {
            trainingHeader = (Instances) SerializationHelper.read(headerFile.toString());
            log.info("Loaded training header: {} attributes, classIndex={}",
                    trainingHeader.numAttributes(), trainingHeader.classIndex());
        } else {
            log.warn("header.ser not found - class labels may not be available for classification");
        }

        // 3. Load test data
        Path testDataFile = dataDir.resolve("test_data.csv");
        if (!Files.exists(testDataFile)) {
            throw new IllegalStateException("test_data.csv not found in DATA_DIR: " + testDataFile);
        }

        CSVLoader loader = new CSVLoader();
        loader.setSource(testDataFile.toFile());
        Instances testData = loader.getDataSet();
        log.info("Loaded test data: {} instances, {} attributes", testData.numInstances(), testData.numAttributes());

        // Log all attributes for debugging
        log.info("Original attributes:");
        for (int i = 0; i < testData.numAttributes(); i++) {
            log.info("  [{}] {}", i + 1, testData.attribute(i).name());
        }

        // 3b. Select columns and add placeholder target column if missing
        // This matches the behavior of DatasetUtil.selectColumns() in the backend
        if (!"CLUSTERING".equalsIgnoreCase(algorithmType)) {
            testData = selectColumnsAndEnsureTarget(testData, basicAttributesColumns, targetColumn);
        } else {
            // For clustering, just select the basic attributes (no target column)
            if (basicAttributesColumns != null && !basicAttributesColumns.isEmpty()) {
                testData = selectColumns(testData, basicAttributesColumns, null);
            }
            testData.setClassIndex(-1);
        }

        // 4. Prepare predictions based on algorithm type
        List<String> predictions;
        ObjectNode metadata = mapper.createObjectNode();
        metadata.put("algorithmType", algorithmType);
        metadata.put("numInstances", testData.numInstances());

        switch (algorithmType.toUpperCase()) {
            case "CLASSIFICATION" -> {
                predictions = predictClassification((Classifier) model, testData, targetColumn, trainingHeader, metadata);
            }
            case "REGRESSION" -> {
                predictions = predictRegression((Classifier) model, testData, targetColumn, metadata);
            }
            case "CLUSTERING" -> {
                predictions = predictClustering((Clusterer) model, testData, metadata);
            }
            default -> throw new IllegalArgumentException("Unknown algorithm type: " + algorithmType);
        }

        // 5. Write predictions.csv (use original data without placeholder target column)
        writePredictionsCsv(testData, predictions);

        // 6. Write metadata
        Path metadataFile = modelDir.resolve("prediction_metadata.json");
        try (FileWriter writer = new FileWriter(metadataFile.toFile())) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, metadata);
        }
        log.info("Prediction metadata saved to: {}", metadataFile);
    }

    private List<String> predictClassification(Classifier classifier, Instances data, String targetColumn, Instances trainingHeader, ObjectNode metadata) throws Exception {
        log.info("Running classification predictions...");

        // Class index should already be set by ensureTargetColumnExists
        // Only set if not already set
        if (data.classIndex() < 0) {
            if (targetColumn != null && data.attribute(targetColumn) != null) {
                data.setClassIndex(data.attribute(targetColumn).index());
            } else {
                data.setClassIndex(data.numAttributes() - 1);
            }
        }
        log.info("Using class index: {} ({})", data.classIndex(), data.classAttribute().name());

        // Convert numeric class to nominal if needed (to match training)
        if (data.classAttribute().isNumeric()) {
            log.info("Converting numeric class to nominal for prediction");
            NumericToNominal convert = new NumericToNominal();
            convert.setAttributeIndices(String.valueOf(data.classIndex() + 1));
            convert.setInputFormat(data);
            data = Filter.useFilter(data, convert);
        }

        // Determine which attribute to use for class labels
        // Prefer training header's class attribute (has correct labels from training)
        Attribute labelSource = null;
        if (trainingHeader != null && trainingHeader.classIndex() >= 0) {
            labelSource = trainingHeader.classAttribute();
            log.info("Using training header for class labels: {} values", labelSource.numValues());
        } else {
            labelSource = data.classAttribute();
            log.info("Using test data class attribute for labels: {} values", labelSource.numValues());
        }

        List<String> predictions = new ArrayList<>();
        int numClassValues = labelSource.numValues();

        log.info("Label source attribute '{}' has {} values, isNominal={}",
                labelSource.name(), numClassValues, labelSource.isNominal());

        for (int i = 0; i < data.numInstances(); i++) {
            Instance instance = data.instance(i);
            double pred = classifier.classifyInstance(instance);

            // Convert numeric prediction to class label
            String predLabel;
            if (labelSource.isNominal() && numClassValues > 0 && (int) pred < numClassValues) {
                // Normal case: get label from class attribute (training header preferred)
                predLabel = labelSource.value((int) pred);
            } else {
                // Fallback: no class labels available
                predLabel = "Class_" + (int) pred;
                log.debug("Using fallback label {} (no class values available)", predLabel);
            }
            predictions.add(predLabel);
        }

        metadata.put("numPredictions", predictions.size());
        log.info("Generated {} classification predictions", predictions.size());
        return predictions;
    }

    private List<String> predictRegression(Classifier classifier, Instances data, String targetColumn, ObjectNode metadata) throws Exception {
        log.info("Running regression predictions...");

        // Class index should already be set by ensureTargetColumnExists
        // Only set if not already set
        if (data.classIndex() < 0) {
            if (targetColumn != null && data.attribute(targetColumn) != null) {
                data.setClassIndex(data.attribute(targetColumn).index());
            } else {
                data.setClassIndex(data.numAttributes() - 1);
            }
        }
        log.info("Using class index: {} ({})", data.classIndex(), data.classAttribute().name());

        List<String> predictions = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < data.numInstances(); i++) {
            Instance instance = data.instance(i);
            double pred = classifier.classifyInstance(instance);
            predictions.add(String.valueOf(pred));
            sum += pred;
        }

        metadata.put("numPredictions", predictions.size());
        metadata.put("meanPrediction", sum / predictions.size());
        log.info("Generated {} regression predictions", predictions.size());
        return predictions;
    }

    private List<String> predictClustering(Clusterer clusterer, Instances data, ObjectNode metadata) throws Exception {
        log.info("Running clustering predictions...");

        // No class index for clustering
        data.setClassIndex(-1);

        List<String> predictions = new ArrayList<>();
        int[] clusterCounts = new int[clusterer.numberOfClusters()];

        for (int i = 0; i < data.numInstances(); i++) {
            Instance instance = data.instance(i);
            int cluster = clusterer.clusterInstance(instance);
            predictions.add("Cluster_" + cluster);
            clusterCounts[cluster]++;
        }

        metadata.put("numPredictions", predictions.size());
        metadata.put("numClusters", clusterer.numberOfClusters());

        ObjectNode clusterDistribution = metadata.putObject("clusterDistribution");
        for (int i = 0; i < clusterCounts.length; i++) {
            clusterDistribution.put("Cluster_" + i, clusterCounts[i]);
        }

        log.info("Generated {} clustering predictions across {} clusters", predictions.size(), clusterer.numberOfClusters());
        return predictions;
    }

    /**
     * Writes predictions to CSV - replaces "?" in target column with predicted values.
     * Same logic as DatasetUtil.replaceQuestionMarksWithPredictionResultsAsCSV but
     * always replaces missing values in target column (not just when named "class").
     */
    private void writePredictionsCsv(Instances data, List<String> predictions) throws Exception {
        Path outputFile = modelDir.resolve("predictions.csv");
        int classIndex = data.classIndex();

        // Replace missing values in the class/target column with predictions
        if (classIndex >= 0) {
            Attribute classAttr = data.classAttribute();
            for (int i = 0; i < data.numInstances(); i++) {
                String predValue = predictions.get(i);
                if (classAttr.isNumeric()) {
                    // For regression: parse as double
                    data.instance(i).setValue(classIndex, Double.parseDouble(predValue));
                } else {
                    // For classification: set as string value
                    data.instance(i).setValue(classIndex, predValue);
                }
            }
            log.info("Replaced missing values in target column '{}' with predictions", classAttr.name());
        }

        // Write CSV
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile.toFile()))) {
            // Header
            StringBuilder header = new StringBuilder();
            for (int i = 0; i < data.numAttributes(); i++) {
                if (i > 0) header.append(",");
                header.append(data.attribute(i).name());
            }
            writer.println(header);

            // Data rows
            for (int i = 0; i < data.numInstances(); i++) {
                StringBuilder row = new StringBuilder();
                Instance instance = data.instance(i);

                for (int j = 0; j < data.numAttributes(); j++) {
                    if (j > 0) row.append(",");
                    if (data.attribute(j).isNumeric()) {
                        row.append(instance.value(j));
                    } else {
                        row.append(instance.stringValue(j));
                    }
                }
                writer.println(row);
            }
        }

        log.info("Predictions saved to: {}", outputFile);
    }

    /**
     * Selects the specified columns and ensures the target column exists.
     * This matches the behavior of DatasetUtil.selectColumns() in the backend.
     *
     * @param data                    The dataset
     * @param basicAttributesColumns  Comma-separated 1-based column indices (e.g., "1,2,3")
     * @param targetColumn            The target column (1-based index)
     * @return Modified dataset with selected columns and target
     */
    private Instances selectColumnsAndEnsureTarget(Instances data, String basicAttributesColumns, String targetColumn) throws Exception {
        log.info("Selecting columns: basicAttributes={}, target={}", basicAttributesColumns, targetColumn);

        // Parse target column index (1-based to 0-based)
        int targetIdx = -1;
        String targetAttrName = null;
        if (targetColumn != null && !targetColumn.isEmpty()) {
            try {
                targetIdx = Integer.parseInt(targetColumn.trim()) - 1;
                if (targetIdx >= 0 && targetIdx < data.numAttributes()) {
                    targetAttrName = data.attribute(targetIdx).name();
                    log.info("Target column: index {} -> name '{}'", targetIdx, targetAttrName);
                } else {
                    // Target column doesn't exist in prediction data - we'll add it
                    targetAttrName = "target";
                    log.info("Target column index {} is beyond dataset ({}). Will add placeholder.", targetIdx, data.numAttributes());
                }
            } catch (NumberFormatException e) {
                // It's a column name
                targetAttrName = targetColumn;
                Attribute attr = data.attribute(targetColumn);
                if (attr != null) {
                    targetIdx = attr.index();
                }
            }
        }

        // Build list of column names to keep
        List<String> columnNames = new ArrayList<>();

        if (basicAttributesColumns == null || basicAttributesColumns.isEmpty()) {
            // Keep all columns except target (if it exists)
            for (int i = 0; i < data.numAttributes(); i++) {
                columnNames.add(data.attribute(i).name());
            }
        } else {
            // Parse basic attributes columns (1-based indices)
            for (String indexStr : basicAttributesColumns.split(",")) {
                int idx = Integer.parseInt(indexStr.trim()) - 1;
                if (idx >= 0 && idx < data.numAttributes()) {
                    columnNames.add(data.attribute(idx).name());
                } else {
                    log.warn("Ignoring invalid basic attribute index: {}", idx + 1);
                }
            }
        }

        log.info("Selected basic attribute columns: {}", columnNames);

        // Ensure target column is in the list (add at end if not present)
        if (targetAttrName != null && !columnNames.contains(targetAttrName)) {
            columnNames.add(targetAttrName);
            log.info("Added target column '{}' to selected columns", targetAttrName);
        }

        log.info("Final columns to keep: {}", columnNames);

        // Build indices to keep
        List<Integer> indicesToKeep = new ArrayList<>();
        for (int i = 0; i < data.numAttributes(); i++) {
            if (columnNames.contains(data.attribute(i).name())) {
                indicesToKeep.add(i);
            }
        }

        log.info("Indices to keep: {}", indicesToKeep);

        // Apply column filtering if needed
        Instances filteredData;
        if (indicesToKeep.size() < data.numAttributes()) {
            Remove removeFilter = new Remove();
            removeFilter.setAttributeIndicesArray(indicesToKeep.stream().mapToInt(i -> i).toArray());
            removeFilter.setInvertSelection(true);
            removeFilter.setInputFormat(data);
            filteredData = Filter.useFilter(data, removeFilter);
            log.info("Filtered dataset to {} attributes", filteredData.numAttributes());
        } else {
            filteredData = data;
        }

        // Check if target column exists in filtered data, if not add it
        Attribute classAttr = filteredData.attribute(targetAttrName);
        if (classAttr == null && targetAttrName != null) {
            log.info("Target column '{}' not found in filtered data. Adding placeholder numeric column.", targetAttrName);

            // Add a new numeric attribute with missing values (like backend does for REGRESSION)
            filteredData.insertAttributeAt(new Attribute(targetAttrName), filteredData.numAttributes());

            // Set all values to missing
            int newTargetIdx = filteredData.numAttributes() - 1;
            for (int i = 0; i < filteredData.numInstances(); i++) {
                filteredData.instance(i).setMissing(newTargetIdx);
            }
            log.info("Added placeholder target column '{}' at index {} with missing values", targetAttrName, newTargetIdx);
        }

        // Set class index
        classAttr = filteredData.attribute(targetAttrName);
        if (classAttr != null) {
            filteredData.setClassIndex(classAttr.index());
            log.info("Set class index to {} ({})", classAttr.index(), classAttr.name());
        } else if (filteredData.numAttributes() > 0) {
            filteredData.setClassIndex(filteredData.numAttributes() - 1);
            log.info("Fallback: Set class index to last attribute: {} ({})",
                    filteredData.classIndex(), filteredData.classAttribute().name());
        }

        // Log final attributes
        log.info("Final filtered attributes:");
        for (int i = 0; i < filteredData.numAttributes(); i++) {
            log.info("  [{}] {} {}", i, filteredData.attribute(i).name(),
                    i == filteredData.classIndex() ? "(class)" : "");
        }

        return filteredData;
    }

    /**
     * Selects columns for clustering (no target column).
     */
    private Instances selectColumns(Instances data, String basicAttributesColumns, String targetColumn) throws Exception {
        log.info("Selecting columns for clustering: {}", basicAttributesColumns);

        // Parse basic attributes columns (1-based indices)
        List<Integer> indicesToKeep = new ArrayList<>();
        for (String indexStr : basicAttributesColumns.split(",")) {
            int idx = Integer.parseInt(indexStr.trim()) - 1;
            if (idx >= 0 && idx < data.numAttributes()) {
                indicesToKeep.add(idx);
            } else {
                log.warn("Ignoring invalid basic attribute index: {}", idx + 1);
            }
        }

        if (indicesToKeep.isEmpty()) {
            log.warn("No valid columns to keep, returning original data");
            return data;
        }

        log.info("Indices to keep: {}", indicesToKeep);

        // Apply column filtering
        Remove removeFilter = new Remove();
        removeFilter.setAttributeIndicesArray(indicesToKeep.stream().mapToInt(i -> i).toArray());
        removeFilter.setInvertSelection(true);
        removeFilter.setInputFormat(data);
        Instances filteredData = Filter.useFilter(data, removeFilter);

        log.info("Filtered dataset to {} attributes", filteredData.numAttributes());
        return filteredData;
    }
}
