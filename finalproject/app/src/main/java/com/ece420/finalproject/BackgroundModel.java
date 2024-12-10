package com.ece420.finalproject;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import java.util.*;
import org.opencv.imgproc.Imgproc;
import android.util.Log;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.opencv.imgcodecs.Imgcodecs;

public class BackgroundModel {
    private int width, height, numClusters, manhattanThreshold, L;
    private double[][][][] clusters;

    public BackgroundModel(int width, int height, int numClusters, int manhattanThreshold, int L) {
        this.width = width;
        this.height = height;
        this.numClusters = numClusters;
        this.manhattanThreshold = manhattanThreshold;
        this.L = L;
        this.clusters = new double[height][width][numClusters][4]; // (weight, Y, Cb, Cr)
    }

    private int manhattanDistance(double[] centroid, double[] pixel) {
        double Y = centroid[1], Cb = centroid[2], Cr = centroid[3];
        double p_Y = pixel[0], p_Cb = pixel[1], p_Cr = pixel[2];
        return (int) (Math.abs(Y - p_Y) + Math.abs(Cb - p_Cb) + Math.abs(Cr - p_Cr));
    }

    private void updateWeights(double[] weights, int matchedIndex) {
        for (int k = 0; k < weights.length; k++) {
            if (k == matchedIndex) {
                weights[k] += (1.0 / L) * (1 - weights[k]);
            } else {
                weights[k] += (1.0 / L) * (0 - weights[k]);
            }
        }
    }

    private void normalizeWeights(double[] weights) {
        double total = Arrays.stream(weights).sum();
        if (total > 0) {
            for (int i = 0; i < weights.length; i++) {
                weights[i] /= total;
            }
        }
    }

    private void adaptCentroid(double[] centroid, double[] pixel) {
        for (int i = 1; i < centroid.length; i++) {
            double error = centroid[i] - pixel[i - 1];
            if (error > L - 1) centroid[i] -= 1;
            else if (error < -L) centroid[i] += 1;
        }
    }

    private double classifyPixel(double[] clusterWeights, int matchedIndex) {
        double P = 0;
        for (int i = matchedIndex + 1; i < clusterWeights.length; i++) {
            P += clusterWeights[i];
        }
        return P;
    }

    public Mat processFrame(Mat frame) {
        Mat output = Mat.zeros(height, width, CvType.CV_8U);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double[] pixel = frame.get(y, x);
                double[][] clusterGroup = clusters[y][x];
                ArrayList<Integer> matches = new ArrayList<>();
                ArrayList<Double> distances = new ArrayList<>();

                for (int k = 0; k < clusterGroup.length; k++) {
                    distances.add((double) manhattanDistance(clusterGroup[k], pixel));
                }
                for (int i = 0; i < distances.size(); i++) {
                    if (distances.get(i) <= manhattanThreshold) {
                        matches.add(i);
                    }
                }

                if (!matches.isEmpty()) {
                    int matchedIndex = matches.get(0);
                    adaptCentroid(clusterGroup[matchedIndex], pixel);
                    updateWeights(Arrays.stream(clusterGroup).mapToDouble(c -> c[0]).toArray(), matchedIndex);
                } else {
                    int minWeightIndex = 0;
                    for (int i = 1; i < clusterGroup.length; i++) {
                        if (clusterGroup[i][0] < clusterGroup[minWeightIndex][0]) {
                            minWeightIndex = i;
                        }
                    }
                    clusterGroup[minWeightIndex] = new double[]{0.01, pixel[0], pixel[1], pixel[2]};
                }

                double[] weights = Arrays.stream(clusterGroup).mapToDouble(c -> c[0]).toArray();
                normalizeWeights(weights);
                for (int k = 0; k < clusterGroup.length; k++) {
                    clusterGroup[k][0] = weights[k];
                }

                Arrays.sort(clusterGroup, (a, b) -> Double.compare(b[0], a[0]));
                double P = classifyPixel(weights, matches.isEmpty() ? -1 : matches.get(0));
                output.put(y, x, P > 0.5 ? 255 : 0);
                clusters[y][x] = clusterGroup;
            }
        }
        return output;
    }

    public Mat postProcessForeground(Mat foreground, int areaThreshold, Mat prevMask, double alpha) {
        // Debug: Check if the input foreground mask is valid
        if (foreground.empty()) {
            Log.e("PostProcess", "Input foreground mask is empty.");
            return Mat.zeros(foreground.size(), foreground.type());
        }


        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Mat cleanForeground = new Mat();
        Imgproc.morphologyEx(foreground, cleanForeground, Imgproc.MORPH_OPEN, kernel);


        Imgcodecs.imwrite("debug_cleanForeground.png", cleanForeground);


        Mat stats = new Mat();
        Mat centroids = new Mat();
        Mat labels = new Mat();

        int numLabels = Imgproc.connectedComponentsWithStats(cleanForeground, labels, stats, centroids);

        Mat filteredMask = Mat.zeros(cleanForeground.size(), CvType.CV_8U);

        for (int i = 1; i < numLabels; i++) { // Skip label 0 (background)
            int area = (int) stats.get(i, Imgproc.CC_STAT_AREA)[0];

            // If the region is above the area threshold, retain it
            if (area >= areaThreshold) {
                Mat regionMask = new Mat();
                Core.inRange(labels, new Scalar(i), new Scalar(i), regionMask);
                Core.bitwise_or(filteredMask, regionMask, filteredMask);
            }
        }


        cleanForeground = filteredMask;
        // Debug: Save result after connected components
        Imgcodecs.imwrite("debug_afterConnectedComponents.png", cleanForeground);

        Mat filledForeground = new Mat();
        Imgproc.morphologyEx(cleanForeground, filledForeground, Imgproc.MORPH_CLOSE, kernel);


        Imgcodecs.imwrite("debug_filledForeground.png", filledForeground);


        ArrayList<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(filledForeground, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        if (contours.isEmpty()) {
            Log.w("PostProcess", "No contours found after post-processing.");
            return Mat.zeros(foreground.size(), foreground.type());
        }


        Log.d("PostProcess", "Number of contours: " + contours.size());

        MatOfPoint largestContour = Collections.max(contours, (c1, c2) -> Double.compare(Imgproc.contourArea(c1), Imgproc.contourArea(c2)));
        Mat largestContourMask = Mat.zeros(foreground.size(), CvType.CV_8U);
        Imgproc.drawContours(largestContourMask, Collections.singletonList(largestContour), -1, new Scalar(255), Core.FILLED);


        Imgcodecs.imwrite("debug_largestContourMask.png", largestContourMask);


        if (prevMask != null) {
            Mat smoothedMask = new Mat();
            Core.addWeighted(largestContourMask, alpha, prevMask, 1 - alpha, 0, smoothedMask);
            Imgproc.threshold(smoothedMask, smoothedMask, 127, 255, Imgproc.THRESH_BINARY);
            return smoothedMask;
        }

        return largestContourMask;
    }
}
