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

        // Class labels from training (for classification)
        List<String> classLabels = new ArrayList<>();
        if (params.has("classLabels") && params.get("classLabels").isArray()) {
            for (JsonNode label : params.get("classLabels")) {
                classLabels.add(label.asText());
            }
        }

        log.info("Algorithm type: {}", algorithmType);
        log.info("Target column: {}", targetColumn);
        log.info("Basic attributes columns: {}", basicAttributesColumns);
        log.info("Class labels from training: {}", classLabels);

        // 2. Load model
        Path modelFile = modelDir.resolve("model.ser");
        if (!Files.exists(modelFile)) {
            throw new IllegalStateException("model.ser not found in MODEL_DIR: " + modelFile);
        }

        Object model = SerializationHelper.read(modelFile.toString());
        log.info("Loaded model: {}", model.getClass().getName());

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
            // Pass classLabels so classification gets a proper nominal attribute
            testData = selectColumnsAndEnsureTarget(testData, basicAttributesColumns, targetColumn, classLabels);
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
                predictions = predictClassification((Classifier) model, testData, targetColumn, classLabels, metadata);
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

    private List<String> predictClassification(Classifier classifier, Instances data, String targetColumn,
            List<String> classLabels, ObjectNode metadata) throws Exception {
        log.info("Running classification predictions...");

        // Class index should already be set by selectColumnsAndEnsureTarget
        if (data.classIndex() < 0) {
            if (targetColumn != null && data.attribute(targetColumn) != null) {
                data.setClassIndex(data.attribute(targetColumn).index());
            } else {
                data.setClassIndex(data.numAttributes() - 1);
            }
        }
        log.info("Using class index: {} ({})", data.classIndex(), data.classAttribute().name());

        // Class labels must be provided by backend (extracted from training)
        if (classLabels == null || classLabels.isEmpty()) {
            throw new IllegalStateException("❌ No class labels provided in params.json for classification");
        }

        // Verify class attribute is nominal (should be set up by selectColumnsAndEnsureTarget)
        Attribute classAttr = data.classAttribute();
        if (classAttr.isNumeric()) {
            throw new IllegalStateException("❌ Class attribute is numeric but classification requires nominal. " +
                    "Ensure classLabels are provided in params.json.");
        }

        // Log class structure for debugging
        log.info("Class attribute '{}' has {} values: {}", classAttr.name(), classAttr.numValues(),
                java.util.stream.IntStream.range(0, classAttr.numValues())
                        .mapToObj(classAttr::value)
                        .collect(java.util.stream.Collectors.toList()));
        log.info("Expected class labels from training: {}", classLabels);

        List<String> predictions = new ArrayList<>();

        for (int i = 0; i < data.numInstances(); i++) {
            Instance instance = data.instance(i);
            double pred = classifier.classifyInstance(instance);
            int predIndex = (int) pred;

            // Convert numeric prediction to class label
            String predLabel = classLabels.get(predIndex);
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
     *
     * For clustering (classIndex == -1), adds a new "cluster" column with predictions.
     */
    private void writePredictionsCsv(Instances data, List<String> predictions) throws Exception {
        Path outputFile = modelDir.resolve("predictions.csv");
        int classIndex = data.classIndex();

        // Detect if predictions are numeric (regression) or string labels (classification/clustering)
        boolean predictionsAreNumeric = false;
        if (!predictions.isEmpty()) {
            try {
                Double.parseDouble(predictions.get(0));
                predictionsAreNumeric = true;
            } catch (NumberFormatException e) {
                predictionsAreNumeric = false;
            }
        }

        // Check if this is clustering (classIndex == -1)
        boolean isClustering = classIndex < 0;

        log.info("Writing predictions CSV. Class index: {}, predictions are numeric: {}, is clustering: {}",
                classIndex, predictionsAreNumeric, isClustering);

        // For regression, update the data instances
        if (classIndex >= 0 && predictionsAreNumeric) {
            Attribute classAttr = data.classAttribute();
            for (int i = 0; i < data.numInstances(); i++) {
                String predValue = predictions.get(i);
                data.instance(i).setValue(classIndex, Double.parseDouble(predValue));
            }
            log.info("Replaced missing values in target column '{}' with regression predictions", classAttr.name());
        }

        // Write CSV
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile.toFile()))) {
            // Header
            StringBuilder header = new StringBuilder();
            for (int i = 0; i < data.numAttributes(); i++) {
                if (i > 0) header.append(",");
                header.append(data.attribute(i).name());
            }
            // For clustering, add "cluster" column at the end
            if (isClustering) {
                header.append(",cluster");
            }
            writer.println(header);

            // Data rows
            for (int i = 0; i < data.numInstances(); i++) {
                StringBuilder row = new StringBuilder();
                Instance instance = data.instance(i);

                for (int j = 0; j < data.numAttributes(); j++) {
                    if (j > 0) row.append(",");

                    // For the class column with non-numeric predictions (classification),
                    // write the prediction string directly
                    if (j == classIndex && !predictionsAreNumeric) {
                        row.append(predictions.get(i));
                    } else if (data.attribute(j).isNumeric()) {
                        row.append(instance.value(j));
                    } else {
                        row.append(instance.stringValue(j));
                    }
                }

                // For clustering, append the cluster assignment at the end
                if (isClustering) {
                    row.append(",").append(predictions.get(i));
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
     * @param classLabels             Class labels from training (for classification) - if not empty, creates nominal attribute
     * @return Modified dataset with selected columns and target
     */
    private Instances selectColumnsAndEnsureTarget(Instances data, String basicAttributesColumns, String targetColumn, List<String> classLabels) throws Exception {
        log.info("Selecting columns: basicAttributes={}, target={}", basicAttributesColumns, targetColumn);

        // Parse target column index (1-based to 0-based)
        int targetIdx = -1;
        String targetAttrName = null;
        boolean needsPlaceholder = false;

        if (targetColumn != null && !targetColumn.isEmpty()) {
            try {
                targetIdx = Integer.parseInt(targetColumn.trim()) - 1;
                if (targetIdx >= 0 && targetIdx < data.numAttributes()) {
                    targetAttrName = data.attribute(targetIdx).name();
                    log.info("Target column: index {} -> name '{}'", targetIdx, targetAttrName);
                } else {
                    // Target column doesn't exist in prediction data - we'll add it
                    targetAttrName = "target";
                    needsPlaceholder = true;
                    log.info("Target column index {} is beyond dataset ({}). Will add placeholder.", targetIdx, data.numAttributes());
                }
            } catch (NumberFormatException e) {
                // It's a column name
                targetAttrName = targetColumn;
                Attribute attr = data.attribute(targetColumn);
                if (attr != null) {
                    targetIdx = attr.index();
                } else {
                    needsPlaceholder = true;
                }
            }
        } else {
            // No targetColumn specified - add placeholder to match training structure
            // This matches the training behavior where last column becomes the class
            targetAttrName = "class";
            needsPlaceholder = true;
            log.info("No target column specified. Will add placeholder 'class' column to match training structure.");
        }

        // Build list of column names to keep
        List<String> columnNames = new ArrayList<>();

        if (basicAttributesColumns == null || basicAttributesColumns.isEmpty()) {
            // Keep all columns
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

        // Ensure target column is in the list (add at end if not present and not a placeholder)
        if (targetAttrName != null && !needsPlaceholder && !columnNames.contains(targetAttrName)) {
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
        if ((classAttr == null || needsPlaceholder) && targetAttrName != null) {
            // For CLASSIFICATION: create nominal attribute with exact training class labels
            // For REGRESSION: create numeric attribute
            if (classLabels != null && !classLabels.isEmpty()) {
                log.info("Target column '{}' not found. Adding NOMINAL placeholder with training class labels: {}",
                        targetAttrName, classLabels);
                // Create nominal attribute with the exact same values as training - critical for Weka classifiers
                Attribute nominalAttr = new Attribute(targetAttrName, new ArrayList<>(classLabels));
                filteredData.insertAttributeAt(nominalAttr, filteredData.numAttributes());
            } else {
                log.info("Target column '{}' not found. Adding NUMERIC placeholder for regression.", targetAttrName);
                // Add a new numeric attribute with missing values (for REGRESSION)
                filteredData.insertAttributeAt(new Attribute(targetAttrName), filteredData.numAttributes());
            }

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
