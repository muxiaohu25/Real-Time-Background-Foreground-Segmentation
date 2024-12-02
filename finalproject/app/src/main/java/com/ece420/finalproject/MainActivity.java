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

    private Button recordVideoBtn, toggleVideoBtn;
    private VideoView videoView;
    private Uri recordedVideoUri, processedVideoUri;
    private boolean isShowingProcessedVideo = false;

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
                    if (isShowingProcessedVideo) {
                        // Show original video
                        videoView.setVideoURI(recordedVideoUri);
                        toggleVideoBtn.setText("Show Processed Video");
                    } else {
                        // Show processed video
                        videoView.setVideoURI(processedVideoUri);
                        toggleVideoBtn.setText("Show Original Video");
                    }
                    isShowingProcessedVideo = !isShowingProcessedVideo;
                    videoView.start();
                }
            }
        });
        toggleVideoBtn.setVisibility(View.GONE);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1) {
            recordedVideoUri = data.getData();

            // Process video in the background
            AlertDialog processingDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Processing Video")
                    .setMessage("Please wait while the video is being processed...")
                    .setCancelable(false)
                    .create();
            processingDialog.show();

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
//        VideoCapture videoCapture = new VideoCapture(getRealPathFromUri(videoUri));
        VideoCapture videoCapture = new VideoCapture("/storage/emulated/0/Movies/VID_20241130_152925334.mp4");
        if (!videoCapture.isOpened()) {
            Log.e("MainActivity", "Failed to open video");
            return videoUri;
        }
        Log.d("MainActivity", "successfully open the video");
//        Use the original video size
//        int frameWidth = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_WIDTH);
//        int frameHeight = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
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

        File outputFile = new File(getExternalFilesDir(null), "processed_foreground.mp4");
        if (!outputFile.getParentFile().exists()) {
            boolean created = outputFile.getParentFile().mkdirs();
            Log.d("MainActivity", "Movies directory created: " + created);
        }
        Log.d("MainActivity", getRealPathFromUri(videoUri));

//        VideoCapture videoCapture = new VideoCapture(getRealPathFromUri(videoUri));
//        VideoCapture videoCapture = new VideoCapture("/storage/emulated/0/Movies/Original.mp4");
        VideoCapture videoCapture = new VideoCapture("/storage/emulated/0/Movies/VID_20241130_152925334.mp4");
        if (!videoCapture.isOpened()) {
            Log.e("MainActivity", "Failed to open video");
            return videoUri;
        }
        Log.d("MainActivity", "successfully open the video");
//        Use the original video size
//        int frameWidth = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_WIDTH);
//        int frameHeight = (int) videoCapture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
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



        Log.d("MainActivity", "Video processing complete");
        return Uri.fromFile(outputFile);
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
