"""
Custom Random Forest Classifier Algorithm
This algorithm implements a Random Forest classifier using scikit-learn.
"""

import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler


class Algorithm:
    """
    Random Forest Classifier with preprocessing.

    Expected parameters in params.json:
    - n_estimators: Number of trees (default: 100)
    - max_depth: Maximum tree depth (default: None)
    - min_samples_split: Minimum samples to split (default: 2)
    - random_state: Random seed (default: 42)
    """

    def __init__(self, params: dict):
        """Initialize the algorithm with parameters from params.json"""
        # Extract hyperparameters
        self.n_estimators = params.get("n_estimators", 100)
        self.max_depth = params.get("max_depth", None)
        self.min_samples_split = params.get("min_samples_split", 2)
        self.random_state = params.get("random_state", 42)

        # Initialize models
        self.scaler = StandardScaler()
        self.model = RandomForestClassifier(
            n_estimators=self.n_estimators,
            max_depth=self.max_depth,
            min_samples_split=self.min_samples_split,
            random_state=self.random_state,
            n_jobs=-1  # Use all CPU cores
        )

        print(f"🌲 Random Forest initialized:")
        print(f"   - n_estimators: {self.n_estimators}")
        print(f"   - max_depth: {self.max_depth}")
        print(f"   - min_samples_split: {self.min_samples_split}")
        print(f"   - random_state: {self.random_state}")

    def fit(self, X, y):
        """
        Train the Random Forest classifier.

        Args:
            X: Training features (numpy array or similar)
            y: Training labels (numpy array or similar)
        """
        print("🚀 Starting Random Forest training...")

        # Convert to numpy arrays
        X = np.array(X)
        y = np.array(y)

        print(f"📊 Training data shape: X={X.shape}, y={y.shape}")
        print(f"📊 Target classes: {np.unique(y)}")

        # Standardize features
        print("📐 Standardizing features...")
        X_scaled = self.scaler.fit_transform(X)

        # Train the model
        print(f"🌳 Training {self.n_estimators} trees...")
        self.model.fit(X_scaled, y)

        # Calculate training accuracy
        train_accuracy = self.model.score(X_scaled, y)

        print(f"✅ Training completed!")
        print(f"   - Training accuracy: {train_accuracy:.4f}")
        print(f"   - Number of features: {X.shape[1]}")
        print(f"   - Number of samples: {X.shape[0]}")

        # Print feature importance
        if hasattr(self.model, 'feature_importances_'):
            importances = self.model.feature_importances_
            print(f"📊 Top 5 feature importances:")
            top_indices = np.argsort(importances)[-5:][::-1]
            for idx in top_indices:
                print(f"   - Feature {idx}: {importances[idx]:.4f}")

    def predict(self, X):
        """
        Make predictions using the trained model.

        Args:
            X: Test features (numpy array or similar)

        Returns:
            Predicted labels (numpy array)
        """
        print("🔮 Making predictions...")

        # Convert to numpy array
        X = np.array(X)
        print(f"📊 Prediction data shape: {X.shape}")

        # Standardize features (using fitted scaler from training)
        X_scaled = self.scaler.transform(X)

        # Make predictions
        predictions = self.model.predict(X_scaled)

        print(f"✅ Generated {len(predictions)} predictions")
        print(f"📊 Prediction distribution: {dict(zip(*np.unique(predictions, return_counts=True)))}")

        return predictions

    def predict_proba(self, X):
        """
        Predict class probabilities.

        Args:
            X: Test features

        Returns:
            Class probabilities (numpy array)
        """
        X = np.array(X)
        X_scaled = self.scaler.transform(X)
        return self.model.predict_proba(X_scaled)
