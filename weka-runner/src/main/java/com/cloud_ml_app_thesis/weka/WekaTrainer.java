package com.cloud_ml_app_thesis.weka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.clusterers.ClusterEvaluation;
import weka.clusterers.Clusterer;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.Utils;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.PrincipalComponents;
import weka.filters.unsupervised.attribute.Remove;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Weka training logic for containerized execution.
 * Produces metrics.json in format compatible with VisualizationService.
 */
public class WekaTrainer {

    private static final Logger log = LoggerFactory.getLogger(WekaTrainer.class);

    private final Path dataDir;
    private final Path modelDir;
    private final ObjectMapper mapper = new ObjectMapper();

    public WekaTrainer(String dataDir, String modelDir) {
        this.dataDir = Path.of(dataDir);
        this.modelDir = Path.of(modelDir);
    }

    public void train() throws Exception {
        // 1. Read params.json
        Path paramsFile = dataDir.resolve("params.json");
        if (!Files.exists(paramsFile)) {
            throw new IllegalStateException("params.json not found in DATA_DIR: " + paramsFile);
        }

        JsonNode params = mapper.readTree(paramsFile.toFile());
        log.info("Loaded parameters: {}", params);

        String algorithmClassName = params.get("algorithmClassName").asText();
        String algorithmType = params.get("algorithmType").asText();
        String options = params.has("options") && !params.get("options").isNull()
                ? fixNestedOptions(params.get("options").asText())
                : "";
        String targetColumn = params.has("targetColumn") && !params.get("targetColumn").isNull()
                ? params.get("targetColumn").asText()
                : null;
        String basicAttributesColumns = params.has("basicAttributesColumns") && !params.get("basicAttributesColumns").isNull()
                ? params.get("basicAttributesColumns").asText()
                : null;

        log.info("Algorithm: {} ({})", algorithmClassName, algorithmType);
        log.info("Options: {}", options);
        log.info("Target column: {}", targetColumn);
        log.info("Basic attributes columns: {}", basicAttributesColumns);

        // 2. Load dataset
        Path datasetFile = dataDir.resolve("dataset.csv");
        if (!Files.exists(datasetFile)) {
            throw new IllegalStateException("dataset.csv not found in DATA_DIR: " + datasetFile);
        }

        CSVLoader loader = new CSVLoader();
        loader.setSource(datasetFile.toFile());
        Instances data = loader.getDataSet();
        log.info("Loaded dataset: {} instances, {} attributes", data.numInstances(), data.numAttributes());

        // 2b. Select columns (same as DatasetUtil.selectColumns in backend)
        data = selectColumns(data, basicAttributesColumns, targetColumn);
        log.info("After column selection: {} instances, {} attributes", data.numInstances(), data.numAttributes());

        // 3. For clustering, unset class index (selectColumns may have set it)
        if ("CLUSTERING".equalsIgnoreCase(algorithmType)) {
            data.setClassIndex(-1);
            log.info("Clustering mode - class index unset");
        } else {
            log.info("Class index: {} ({})", data.classIndex(),
                    data.classIndex() >= 0 ? data.classAttribute().name() : "none");
        }

        // 4. Train based on algorithm type
        Object trainedModel;
        ObjectNode metricsJson = mapper.createObjectNode();

        switch (algorithmType.toUpperCase()) {
            case "CLASSIFICATION" -> {
                trainedModel = trainClassifier(algorithmClassName, options, data, metricsJson);
            }
            case "REGRESSION" -> {
                trainedModel = trainRegressor(algorithmClassName, options, data, metricsJson);
            }
            case "CLUSTERING" -> {
                trainedModel = trainClusterer(algorithmClassName, options, data, metricsJson);
            }
            default -> throw new IllegalArgumentException("Unknown algorithm type: " + algorithmType);
        }

        // 5. Save model
        Path modelFile = modelDir.resolve("model.ser");
        SerializationHelper.write(modelFile.toString(), trainedModel);
        log.info("Model saved to: {}", modelFile);

        // 6. Save metrics
        Path metricsFile = modelDir.resolve("metrics.json");
        try (FileWriter writer = new FileWriter(metricsFile.toFile())) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, metricsJson);
        }
        log.info("Metrics saved to: {}", metricsFile);
    }

    private Classifier trainClassifier(String className, String options, Instances data, ObjectNode metrics) throws Exception {
        log.info("Training classifier: {}", className);

        // Convert numeric class to nominal if needed for classification
        if (data.classAttribute().isNumeric()) {
            log.info("Converting numeric class to nominal for classification");
            NumericToNominal convert = new NumericToNominal();
            convert.setAttributeIndices(String.valueOf(data.classIndex() + 1));
            convert.setInputFormat(data);
            data = Filter.useFilter(data, convert);
        }

        // Split data 70/30
        data.randomize(new Random(1));
        int trainSize = (int) (data.numInstances() * 0.7);
        Instances trainData = new Instances(data, 0, trainSize);
        Instances testData = new Instances(data, trainSize, data.numInstances() - trainSize);

        // Instantiate and configure classifier
        Classifier classifier = (Classifier) Class.forName(className).getDeclaredConstructor().newInstance();
        if (options != null && !options.isEmpty()) {
            String[] optionsArray = Utils.splitOptions(options);
            ((weka.core.OptionHandler) classifier).setOptions(optionsArray);
        }

        // Train
        log.info("Building classifier on {} training instances...", trainData.numInstances());
        classifier.buildClassifier(trainData);

        // Evaluate
        Evaluation eval = new Evaluation(trainData);
        eval.evaluateModel(classifier, testData);

        // Calculate weighted averages for precision, recall, f1
        double weightedPrecision = eval.weightedPrecision() * 100;
        double weightedRecall = eval.weightedRecall() * 100;
        double weightedFMeasure = eval.weightedFMeasure() * 100;

        // Format as strings with % (matching EvaluationResult format)
        metrics.put("accuracy", String.format("%.2f%%", eval.pctCorrect()));
        metrics.put("precision", String.format("%.2f%%", weightedPrecision));
        metrics.put("recall", String.format("%.2f%%", weightedRecall));
        metrics.put("f1Score", String.format("%.2f%%", weightedFMeasure));
        metrics.put("summary", eval.toSummaryString());

        // Build confusion matrix as 2D array
        double[][] confMatrix = eval.confusionMatrix();
        ArrayNode confusionMatrixNode = metrics.putArray("confusionMatrix");
        for (double[] row : confMatrix) {
            ArrayNode rowNode = confusionMatrixNode.addArray();
            for (double val : row) {
                rowNode.add((int) val);
            }
        }

        // Class labels
        ArrayNode classLabelsNode = metrics.putArray("classLabels");
        for (int i = 0; i < data.numClasses(); i++) {
            classLabelsNode.add(data.classAttribute().value(i));
        }

        log.info("Classification accuracy: {}%", String.format("%.2f", eval.pctCorrect()));
        return classifier;
    }

    private Classifier trainRegressor(String className, String options, Instances data, ObjectNode metrics) throws Exception {
        log.info("Training regressor: {}", className);

        // Split data 70/30
        data.randomize(new Random(1));
        int trainSize = (int) (data.numInstances() * 0.7);
        Instances trainData = new Instances(data, 0, trainSize);
        Instances testData = new Instances(data, trainSize, data.numInstances() - trainSize);

        // Instantiate and configure classifier (regression uses same interface)
        Classifier classifier = (Classifier) Class.forName(className).getDeclaredConstructor().newInstance();
        if (options != null && !options.isEmpty()) {
            String[] optionsArray = Utils.splitOptions(options);
            ((weka.core.OptionHandler) classifier).setOptions(optionsArray);
        }

        // Train
        log.info("Building regressor on {} training instances...", trainData.numInstances());
        classifier.buildClassifier(trainData);

        // Evaluate and collect actual/predicted values
        Evaluation eval = new Evaluation(trainData);

        List<Double> actualValues = new ArrayList<>();
        List<Double> predictedValues = new ArrayList<>();

        for (int i = 0; i < testData.numInstances(); i++) {
            Instance instance = testData.instance(i);
            double actual = instance.classValue();
            double predicted = classifier.classifyInstance(instance);
            actualValues.add(actual);
            predictedValues.add(predicted);
        }

        eval.evaluateModel(classifier, testData);

        // Regression metrics (matching RegressionEvaluationResult format)
        metrics.put("rmse", eval.rootMeanSquaredError());
        metrics.put("mae", eval.meanAbsoluteError());
        metrics.put("rSquared", eval.correlationCoefficient() * eval.correlationCoefficient()); // R² = r²
        metrics.put("summary", eval.toSummaryString());

        // Actual and predicted values arrays
        ArrayNode actualNode = metrics.putArray("actualValues");
        for (Double val : actualValues) {
            actualNode.add(val);
        }

        ArrayNode predictedNode = metrics.putArray("predictedValues");
        for (Double val : predictedValues) {
            predictedNode.add(val);
        }

        log.info("Regression RMSE: {}", String.format("%.4f", eval.rootMeanSquaredError()));
        return classifier;
    }

    private Clusterer trainClusterer(String className, String options, Instances data, ObjectNode metrics) throws Exception {
        log.info("Training clusterer: {}", className);

        // Instantiate and configure clusterer
        Clusterer clusterer = (Clusterer) Class.forName(className).getDeclaredConstructor().newInstance();
        if (options != null && !options.isEmpty()) {
            String[] optionsArray = Utils.splitOptions(options);
            ((weka.core.OptionHandler) clusterer).setOptions(optionsArray);
        }

        // Build clusterer
        log.info("Building clusterer on {} instances...", data.numInstances());
        clusterer.buildClusterer(data);

        // Evaluate
        ClusterEvaluation eval = new ClusterEvaluation();
        eval.setClusterer(clusterer);
        eval.evaluateClusterer(data);

        // Metrics (matching ClusterEvaluationResult format)
        metrics.put("numClusters", clusterer.numberOfClusters());
        metrics.put("logLikelihood", eval.getLogLikelihood());
        metrics.put("summary", eval.clusterResultsToString());

        // Cluster assignments as array of strings "Instance X assigned to cluster Y"
        ArrayNode assignmentsNode = metrics.putArray("clusterAssignments");
        double[] assignments = eval.getClusterAssignments();
        for (int i = 0; i < assignments.length; i++) {
            assignmentsNode.add("Instance " + i + " assigned to cluster " + (int) assignments[i]);
        }

        // 2D projection using PCA for visualization
        try {
            ArrayNode projectionNode = metrics.putArray("projection2D");

            // Apply PCA to reduce to 2 dimensions
            PrincipalComponents pca = new PrincipalComponents();
            pca.setMaximumAttributes(2);
            pca.setInputFormat(data);
            Instances projectedData = Filter.useFilter(data, pca);

            for (int i = 0; i < projectedData.numInstances(); i++) {
                Instance inst = projectedData.instance(i);
                ObjectNode point = projectionNode.addObject();
                point.put("x", inst.numAttributes() > 0 ? inst.value(0) : 0.0);
                point.put("y", inst.numAttributes() > 1 ? inst.value(1) : 0.0);
            }
            log.info("Generated 2D projection for {} instances", projectedData.numInstances());
        } catch (Exception e) {
            log.warn("Could not generate 2D projection: {}", e.getMessage());
            // Add empty projection if PCA fails
            metrics.putArray("projection2D");
        }

        log.info("Clustering completed: {} clusters", clusterer.numberOfClusters());
        return clusterer;
    }

    /**
     * Select columns from dataset (same as DatasetUtil.selectColumns in backend).
     * Keeps only the basicAttributesColumns + targetColumn.
     */
    private Instances selectColumns(Instances data, String basicAttributesColumns, String targetColumn) throws Exception {
        log.info("Selecting columns: basicAttributes={}, target={}", basicAttributesColumns, targetColumn);

        // Resolve target class attribute name
        String classAttrName = null;
        if (targetColumn != null && !targetColumn.isEmpty()) {
            try {
                int targetIdx = Integer.parseInt(targetColumn.trim()) - 1; // 1-based to 0-based
                if (targetIdx >= 0 && targetIdx < data.numAttributes()) {
                    classAttrName = data.attribute(targetIdx).name();
                }
            } catch (NumberFormatException e) {
                classAttrName = targetColumn;
            }
        }
        log.info("Target class attribute name: {}", classAttrName);

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

        log.info("Selected basic attributes: {}", columnNames);

        // Ensure class attribute is kept
        if (classAttrName != null && data.attribute(classAttrName) != null && !columnNames.contains(classAttrName)) {
            columnNames.add(classAttrName);
            log.info("Added class attribute '{}' to selected columns", classAttrName);
        }

        log.info("Final columns to keep: {}", columnNames);

        // Build indices to keep
        List<Integer> indicesToKeep = new ArrayList<>();
        for (int i = 0; i < data.numAttributes(); i++) {
            if (columnNames.contains(data.attribute(i).name())) {
                indicesToKeep.add(i);
            }
        }

        // Apply column filtering if needed
        if (indicesToKeep.size() < data.numAttributes()) {
            Remove removeFilter = new Remove();
            removeFilter.setAttributeIndicesArray(indicesToKeep.stream().mapToInt(i -> i).toArray());
            removeFilter.setInvertSelection(true);
            removeFilter.setInputFormat(data);
            data = Filter.useFilter(data, removeFilter);
        }

        // Set class index after filtering
        if (classAttrName != null) {
            Attribute classAttr = data.attribute(classAttrName);
            if (classAttr != null) {
                data.setClassIndex(classAttr.index());
                log.info("Set class index to {} ({})", classAttr.index(), classAttr.name());
            }
        }

        // Fallback to last attribute
        if (data.classIndex() < 0 && data.numAttributes() > 0) {
            data.setClassIndex(data.numAttributes() - 1);
            log.info("Fallback: Set class index to last attribute: {}", data.classAttribute().name());
        }

        return data;
    }

    /**
     * Fix nested options (same as AlgorithmUtil.fixNestedOptions in backend).
     * Handles special cases like EuclideanDistance -R option.
     */
    private String fixNestedOptions(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }

        if (raw.contains("-A weka.core.EuclideanDistance") && raw.contains("-R first-last")) {
            raw = raw.replace("-A weka.core.EuclideanDistance -R first-last",
                              "-A \"weka.core.EuclideanDistance -R first-last\"");
        }

        // Fix incomplete scientific notation (e.g., "1.0E" -> "1.0")
        raw = raw.replaceAll("(\\d+\\.\\d+)[Ee](?![+-]?\\d)", "$1");

        return raw;
    }
}
