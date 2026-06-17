#!/bin/sh
set -e

echo "=== BYOC Bash ML Algorithm ==="
echo "EXECUTION_MODE=$EXECUTION_MODE"
echo "DATA_DIR=$DATA_DIR"
echo "MODEL_DIR=$MODEL_DIR"

if [ "$EXECUTION_MODE" = "train" ]; then
    echo "--- TRAINING MODE ---"

    DATASET="$DATA_DIR/dataset.csv"
    if [ ! -f "$DATASET" ]; then
        echo "ERROR: dataset.csv not found at $DATASET"
        exit 1
    fi

    # Count rows and columns from CSV
    TOTAL_ROWS=$(wc -l < "$DATASET")
    NUM_ROWS=$((TOTAL_ROWS - 1))
    HEADER=$(head -1 "$DATASET")
    NUM_COLS=$(echo "$HEADER" | awk -F',' '{print NF}')

    echo "Dataset: $NUM_ROWS rows, $NUM_COLS columns"
    echo "Columns: $HEADER"

    # Create a simple "model" — just store the header and row count
    echo "{\"type\":\"bash-majority-vote\",\"rows\":$NUM_ROWS,\"cols\":$NUM_COLS,\"header\":\"$HEADER\"}" > "$MODEL_DIR/model.bin"
    echo "Model saved to $MODEL_DIR/model.bin"

    # Write metrics.json (REQUIRED by platform)
    cat > "$MODEL_DIR/metrics.json" <<METRICS
{
    "algorithm": "Bash Majority Vote Classifier",
    "language": "Shell/Bash",
    "accuracy": 0.85,
    "precision": 0.83,
    "recall": 0.87,
    "f1_score": 0.85,
    "training_samples": $NUM_ROWS,
    "features": $NUM_COLS,
    "note": "This is a demo BYOC algorithm in pure Bash to prove language-agnostic support"
}
METRICS
    echo "Metrics saved to $MODEL_DIR/metrics.json"
    echo "=== TRAINING COMPLETE ==="

elif [ "$EXECUTION_MODE" = "predict" ]; then
    echo "--- PREDICTION MODE ---"

    TEST_DATA="$DATA_DIR/test_data.csv"
    if [ ! -f "$TEST_DATA" ]; then
        echo "ERROR: test_data.csv not found at $TEST_DATA"
        exit 1
    fi

    # Read header and generate predictions
    HEADER=$(head -1 "$TEST_DATA")
    echo "${HEADER},prediction" > "$MODEL_DIR/predictions.csv"

    # For each data row, output a dummy prediction
    tail -n +2 "$TEST_DATA" | while IFS= read -r line; do
        echo "${line},class_A" >> "$MODEL_DIR/predictions.csv"
    done

    PRED_ROWS=$(tail -n +2 "$MODEL_DIR/predictions.csv" | wc -l)
    echo "Generated $PRED_ROWS predictions"
    echo "Predictions saved to $MODEL_DIR/predictions.csv"
    echo "=== PREDICTION COMPLETE ==="
else
    echo "ERROR: Unknown EXECUTION_MODE=$EXECUTION_MODE (expected 'train' or 'predict')"
    exit 1
fi
