package com.ece420.finalproject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.VideoView;
import android.net.Uri;
import android.util.Log;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.database.Cursor;
import android.os.Build;
import android.content.pm.PackageManager;

import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.video.BackgroundSubtractorMOG2;
import org.opencv.video.Video;
import org.opencv.video.BackgroundSubtractor;
import org.opencv.android.OpenCVLoader;
import org.opencv.videoio.VideoCapture;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.VideoWriter;
import org.opencv.videoio.Videoio;
import org.opencv.core.Core;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.*;
public class MainActivity extends AppCompatActivity {

    private Button recordVideoBtn, toggleVideoBtn, processForegroundBtn;
    private VideoView videoView;
    private Uri recordedVideoUri, processedVideoUri,foregroundVideoUri;
    private boolean isShowingProcessedVideo = false;
    private int frame_Count = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(OpenCVLoader.initLocal()){
            Log.d("MainActivity","Opencv is loaded");
        }
        else{
            Log.e("MainActivity","Failed loading Opencv");
        }

        // initializing variables on below line.
        recordVideoBtn = findViewById(R.id.idBtnRecordVideo);
        toggleVideoBtn = findViewById(R.id.idBtnToggleVideo);
        processForegroundBtn = findViewById(R.id.idBtnProcessForeground);
        videoView = findViewById(R.id.videoView);

        // adding click listener for recording button.
        recordVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // on below line opening an intent to capture a video.
                Intent i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                // on below line starting an activity for result.
                startActivityForResult(i, 1);
            }
        });
        toggleVideoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recordedVideoUri != null) {
                    Log.d("MainActivity", "Processed background video is ready.");
                    videoView.stopPlayback();
                    videoView.setVideoURI(null);
                    processForegroundBtn.setVisibility(View.VISIBLE);
                    videoView.setVideoURI(processedVideoUri);
                    videoView.start();
                }
                else {
                    Log.d("MainActivity", "Processed background video is not ready yet.");
                }
            }
        });
        processForegroundBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (foregroundVideoUri != null)
                {
                    videoView.stopPlayback(); // Stop current playback
                    videoView.setVideoURI(null);
                    Log.d("MainActivity", "Foreground video is ready.");
                    videoView.setVideoURI(foregroundVideoUri);
                    // Start playback with listeners for errors and completion
                    videoView.start();

                } else {
                    Log.d("MainActivity", "Foreground video is not ready yet.");
                }
            }
        });
        toggleVideoBtn.setVisibility(View.GONE);
        processForegroundBtn.setVisibility(View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1) {
            recordedVideoUri = data.getData();

            // Process video in the background
            AlertDialog processingDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Processing Background Video")
                    .setMessage("Please wait while the video is being processed...")
                    .setCancelable(true)
                    .create();
            processingDialog.show();
            new Thread(() -> {
                foregroundVideoUri = seperateForeground(recordedVideoUri);
                Log.d("MainActivity", "Foreground processing complete");
                runOnUiThread(() -> {
                    if (foregroundVideoUri != null) {
                        processForegroundBtn.setVisibility(View.VISIBLE);
                    }
                });
            }).start();
            // Process the video in a background thread
            new Thread(() -> {
                processedVideoUri = seperateBackground(recordedVideoUri);

                // Run on the main thread to update the UI
                runOnUiThread(() -> {
                    processingDialog.dismiss(); // Dismiss the dialog

                    // Display the original video first
                    videoView.setVideoURI(recordedVideoUri);
                    videoView.start();

                    // Show the toggle button
                    toggleVideoBtn.setVisibility(View.VISIBLE);
                });
            }).start();
        }
    }

    private Uri seperateBackground(Uri videoUri) {

        File outputFile = new File(getExternalFilesDir(null), "processed_background.mp4");
        File outputImageFile = new File(getExternalFilesDir(null), "median_background.jpg");
        if (!outputFile.getParentFile().exists()) {
            boolean created = outputFile.getParentFile().mkdirs();
            Log.d("MainActivity", "Movies directory created: " + created);
        }
        Log.d("MainActivity", getRealPathFromUri(videoUri));

        //Use the Recorded video
//        VideoCapture videoCapture = new VideoCapture("/storage/emulated/0/Movies/sample6.mp4");
        VideoCapture videoCapture = new VideoCapture(getRealPathFromUri(videoUri));

        if (!videoCapture.isOpened()) {
            Log.e("MainActivity", "Failed to open video");
            return videoUri;
        }
        Log.d("MainActivity", "successfully open the video");
        int targetWidth = 320;
        int targetHeight = 240;
        double fps = videoCapture.get(Videoio.CAP_PROP_FPS);

        // Initialize video writer
        VideoWriter videoWriter = new VideoWriter(
                outputFile.getAbsolutePath(),
                VideoWriter.fourcc('H', '2', '6', '4'),
                fps,
                new Size(targetHeight, targetWidth)
        );

        if (!videoWriter.isOpened()) {
            Log.e("MainActivity", "Failed to initialize video writer");
            videoCapture.release();
            return videoUri;
        }
        Log.d("MainActivity", "videowriter loaded");


        Mat frame = new Mat();
        Mat resizedFrame = new Mat();

        // The List stores all frames to compute median
        List<Mat> frameList = new ArrayList<>();

        while (videoCapture.read(frame)) {
            Imgproc.resize(frame, resizedFrame, new Size(targetWidth, targetHeight));
            Core.rotate(resizedFrame, resizedFrame, Core.ROTATE_90_CLOCKWISE);
            Imgproc.cvtColor(resizedFrame, resizedFrame, Imgproc.COLOR_RGB2BGR);
            frameList.add(resizedFrame.clone());
        }
        int numFrames = frameList.size();
        Mat[] framesArray = frameList.toArray(new Mat[0]);
        Mat medianBackground = new Mat(targetWidth, targetHeight, CvType.CV_8UC3);

        for (int row = 0; row < targetWidth; row++) {
            for (int col = 0; col < targetHeight; col++){
                double[] pixelValuesB = new double[numFrames];
                double[] pixelValuesG = new double[numFrames];
                double[] pixelValuesR = new double[numFrames];
                for (int i = 0; i < numFrames; i++) {
                    double[] pixel = framesArray[i].get(row, col);
                    pixelValuesB[i] = pixel[0]; // Blue channel
                    pixelValuesG[i] = pixel[1]; // Green channel
                    pixelValuesR[i] = pixel[2]; // Red channel
                }
                Arrays.sort(pixelValuesB);
                Arrays.sort(pixelValuesG);
                Arrays.sort(pixelValuesR);
                double medianB = pixelValuesB[numFrames / 2];
                double medianG = pixelValuesG[numFrames / 2];
                double medianR = pixelValuesR[numFrames / 2];
                medianBackground.put(row, col, new double[]{medianB, medianG, medianR});

            }
        }

        boolean imageSaved = Imgcodecs.imwrite(outputImageFile.getAbsolutePath(), medianBackground);
        for (int i = 0; i < numFrames; i++) {
            videoWriter.write(medianBackground);
        }
        for (Mat m : frameList) {
            m.release();
        }
        // Release resources
        frame.release();
        resizedFrame.release();
        videoCapture.release();
        videoWriter.release();

        Log.d("MainActivity", "Video processing complete");
        return Uri.fromFile(outputFile);
    }

    private Uri seperateForeground(Uri videoUri) {
        Log.d("MainActivity", "start seperateForeground");
        String inputVideoPath = getRealPathFromUri(videoUri);
        String outputVideoPath = getExternalFilesDir(null) + "/masked_output_video.avi";
        String outputFrameDirPath = getExternalFilesDir(null) + "/frames";

        // Create the output directory for frames if it doesn't exist
        File outputFrameDir = new File(outputFrameDirPath);
        if (!outputFrameDir.exists()) {
            if (outputFrameDir.mkdirs()) {
                Log.d("MainActivity", "Created directory for frames: " + outputFrameDirPath);
            } else {
                Log.e("MainActivity", "Failed to create directory for frames.");
                return null;
            }
        }

        // Open input video
//        VideoCapture cap = new VideoCapture("/storage/emulated/0/Movies/sample6.mp4");
        VideoCapture cap = new VideoCapture(inputVideoPath);
        if (!cap.isOpened()) {
            Log.e("MainActivity", "Failed to open video");
            return null;
        }

        // Initialize output video
        int targetWidth = 320;
        int targetHeight = 240;
        int fps = 30;
        VideoWriter writer = new VideoWriter(
                outputVideoPath,
                VideoWriter.fourcc('H', '2', '6', '4'),
                fps,
                new Size(targetWidth, targetHeight)
        );

        if (!writer.isOpened()) {
            Log.e("MainActivity", "Failed to initialize video writer");
            cap.release();
            return null;
        }

        // Initialize models
        BackgroundModel model = new BackgroundModel(targetWidth, targetHeight, 5, 6, 128);
        Mat prevMask = null;

        // Process frames
        Mat frame = new Mat();
        Mat resizedFrame = new Mat();
        Mat ycbcrFrame = new Mat();
        Mat blackBackground = Mat.zeros(new Size(targetWidth, targetHeight), CvType.CV_8UC3); // Black frame
        int frameCount = 0;

        Log.d("MainActivity", "start processing frames");
        while (cap.read(frame)) {
//            if(frameCount%10!=0){
//                frameCount++;
//                continue;
//            }
            Imgproc.resize(frame, resizedFrame, new Size(targetWidth, targetHeight));
            Imgproc.cvtColor(resizedFrame, ycbcrFrame, Imgproc.COLOR_BGR2YCrCb);

            Mat foregroundMask = model.processFrame(ycbcrFrame);

            Mat processedForeground = model.postProcessForeground(foregroundMask, 50, prevMask,0.7);
            String frameFilePath1 = outputFrameDirPath + "/mask" + String.format("%04d", frameCount) + ".png";
            boolean success1 = Imgcodecs.imwrite(frameFilePath1, processedForeground);
            prevMask = processedForeground.clone();

            Mat maskBinary = new Mat();
            Imgproc.threshold(processedForeground, maskBinary, 127, 255, Imgproc.THRESH_BINARY);


            // Create a frame with the background blacked out
            Mat foregroundFrame = new Mat();
            Core.bitwise_and(resizedFrame, resizedFrame, foregroundFrame, maskBinary);
            Mat finalFrame = new Mat();
            Imgproc.cvtColor(foregroundFrame, finalFrame, Imgproc.COLOR_BGR2RGB);
            // Write the frame to the output video
            writer.write(finalFrame);

            // Save the current frame as an image
            String frameFilePath = outputFrameDirPath + "/frame_" + String.format("%04d", frameCount) + ".png";
            boolean success = Imgcodecs.imwrite(frameFilePath, finalFrame);
            if (success) {
                Log.d("MainActivity", "Saved frame to " + frameFilePath);
            } else {
                Log.e("MainActivity", "Failed to save frame " + frameFilePath);
            }
            frameCount++;
            frame_Count++;
            Log.d("MainActivity", "Processing frame " + frameCount);
        }

        cap.release();
        writer.release();
        Log.d("MainActivity", "Processing complete. Video saved as " + outputVideoPath);
        return Uri.fromFile(new File(outputVideoPath));
    }
    private String getRealPathFromUri(Uri contentUri) {
        String[] projection = { MediaStore.Video.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, projection, null, null, null);
        if (cursor != null) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
            cursor.moveToFirst();
            String filePath = cursor.getString(columnIndex);
            cursor.close();
            return filePath;
        }
        return null;
    }
}
