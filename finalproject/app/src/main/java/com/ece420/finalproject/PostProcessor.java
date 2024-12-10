package com.ece420.finalproject;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import java.util.*;
//public class PostProcessor {
//    public Mat postprocessForeground(Mat foreground, int areaThreshold, Mat prevMask) {
//        // Morphological opening to clean small noise
//        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
//        Mat cleanForeground = new Mat();
//        Imgproc.morphologyEx(foreground, cleanForeground, Imgproc.MORPH_OPEN, kernel);
//
//        // Find connected components to remove small regions
//        Mat labels = new Mat();
//        Mat stats = new Mat();
//        Mat centroids = new Mat();
//        int numLabels = Imgproc.connectedComponentsWithStats(cleanForeground, labels, stats, centroids);
//
//        for (int i = 1; i < numLabels; i++) {
//            if (stats.get(i, Imgproc.CC_STAT_AREA)[0] < areaThreshold) {
//                Core.compare(labels, new Scalar(i), cleanForeground, Core.CMP_NE);
//            }
//        }
//
//        // Fill holes
//        Mat filledForeground = new Mat();
//        Imgproc.morphologyEx(cleanForeground, filledForeground, Imgproc.MORPH_CLOSE, kernel);
//
//        // Find largest contour
//        List<MatOfPoint> contours = new ArrayList<>();
//        Imgproc.findContours(filledForeground, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
//        if (contours.isEmpty()) return Mat.zeros(foreground.size(), CvType.CV_8UC1);
//
//        Mat largestContourMask = Mat.zeros(foreground.size(), CvType.CV_8UC1);
//        Imgproc.drawContours(largestContourMask, contours, -1, new Scalar(255), Imgproc.FILLED);
//
//        // Temporal smoothing
//        if (prevMask != null) {
//            Mat smoothedMask = new Mat();
//            Core.addWeighted(largestContourMask, 0.7, prevMask, 0.3, 0, smoothedMask);
//            Imgproc.threshold(smoothedMask, smoothedMask, 127, 255, Imgproc.THRESH_BINARY);
//            return smoothedMask;
//        }
//
//        return largestContourMask;
//    }
//}

