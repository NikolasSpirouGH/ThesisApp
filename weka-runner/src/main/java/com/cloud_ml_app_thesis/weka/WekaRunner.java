package com.cloud_ml_app_thesis.weka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for Weka ML operations in containerized environments.
 *
 * Usage:
 *   java -jar weka-runner.jar train    - Run training
 *   java -jar weka-runner.jar predict  - Run prediction
 *
 * Environment Variables:
 *   DATA_DIR  - Directory containing input files (params.json, dataset.csv)
 *   MODEL_DIR - Directory for output files (model.ser, metrics.json, predictions.csv)
 */
public class WekaRunner {

    private static final Logger log = LoggerFactory.getLogger(WekaRunner.class);

    public static void main(String[] args) {
        if (args.length == 0) {
            log.error("Usage: java -jar weka-runner.jar <train|predict>");
            System.exit(1);
        }

        String command = args[0].toLowerCase();

        String dataDir = System.getenv("DATA_DIR");
        String modelDir = System.getenv("MODEL_DIR");

        if (dataDir == null || dataDir.isEmpty()) {
            log.error("DATA_DIR environment variable is not set");
            System.exit(1);
        }

        if (modelDir == null || modelDir.isEmpty()) {
            log.error("MODEL_DIR environment variable is not set");
            System.exit(1);
        }

        log.info("=== Weka Runner ===");
        log.info("Command: {}", command);
        log.info("DATA_DIR: {}", dataDir);
        log.info("MODEL_DIR: {}", modelDir);

        try {
            switch (command) {
                case "train" -> {
                    WekaTrainer trainer = new WekaTrainer(dataDir, modelDir);
                    trainer.train();
                    log.info("Training completed successfully!");
                }
                case "predict" -> {
                    WekaPredictor predictor = new WekaPredictor(dataDir, modelDir);
                    predictor.predict();
                    log.info("Prediction completed successfully!");
                }
                default -> {
                    log.error("Unknown command: {}. Use 'train' or 'predict'", command);
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            log.error("Execution failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
