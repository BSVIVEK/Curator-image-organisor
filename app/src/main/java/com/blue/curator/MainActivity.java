package com.blue.curator;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;
    private static final String PREF_DIRECTORY_URI = "directoryUri";
    private static final String PREF_LAST_IMAGE_INDEX = "lastImageIndex";

    private Uri directoryUri;
    private List<DocumentFile> imageFiles = new ArrayList<>();
    private List<String> categorizedYes = new ArrayList<>();
    private List<String> categorizedNo = new ArrayList<>();
    private List<String> categorizedNotSure = new ArrayList<>();
    private int currentIndex = 0;

    private ImageView imageView;
    private TextView toastMessage;
    private ProgressBar voiceProgressBar;
    private GestureDetector gestureDetector;
    private SpeechRecognizer speechRecognizer;
    private TextView progressTextView;
    private ImageButton micButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate called");

        imageView = findViewById(R.id.imageView);
        toastMessage = findViewById(R.id.toastMessage);
        voiceProgressBar = findViewById(R.id.voiceProgressBar);
        progressTextView = findViewById(R.id.progressTextView);
        micButton = findViewById(R.id.micButton);

        loadSavedState();

        if (directoryUri == null) {
            openDirectoryPicker();
        } else {
            loadImagesFromDirectory(directoryUri);
            displayImage(currentIndex);
        }

        setupGestureDetection();
        setupVoiceRecognition();

        micButton.setOnClickListener(v -> {
            Log.d(TAG, "Mic button clicked");
            startVoiceRecognition();
        });
    }

    private void startVoiceRecognition() {
        Log.d(TAG, "startVoiceRecognition called");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizer.startListening(intent);
    }



    private void loadSavedState() {
        Log.d(TAG, "loadSavedState called");
        SharedPreferences preferences = getSharedPreferences("MyAppPreferences", MODE_PRIVATE);
        String savedUri = preferences.getString(PREF_DIRECTORY_URI, null);
        if (savedUri != null) {
            directoryUri = Uri.parse(savedUri);
            currentIndex = preferences.getInt(PREF_LAST_IMAGE_INDEX, 0);
            Log.d(TAG, "Saved state loaded: URI=" + savedUri + ", Index=" + currentIndex);
        }
    }

    private void openDirectoryPicker() {
        Log.d(TAG, "openDirectoryPicker called");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult called: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
            if (data != null) {
                directoryUri = data.getData();
                saveDirectoryUri(directoryUri);
                loadImagesFromDirectory(directoryUri);
                displayImage(currentIndex);
                Log.d(TAG, "Directory selected: " + directoryUri);
            }
        }
    }

    private void saveDirectoryUri(Uri uri) {
        Log.d(TAG, "saveDirectoryUri called: " + uri);
        SharedPreferences preferences = getSharedPreferences("MyAppPreferences", MODE_PRIVATE);
        preferences.edit().putString(PREF_DIRECTORY_URI, uri.toString()).apply();
    }

    private void loadImagesFromDirectory(Uri directoryUri) {
        Log.d(TAG, "loadImagesFromDirectory called: " + directoryUri);
        DocumentFile directory = DocumentFile.fromTreeUri(this, directoryUri);
        if (directory != null && directory.isDirectory()) {
            imageFiles.clear();
            for (DocumentFile file : directory.listFiles()) {
                if (file.isFile() && file.getType().startsWith("image/")) {
                    imageFiles.add(file);
                    Log.d(TAG, "Image added: " + file.getUri());
                }
            }
            updateProgressTextView();
        }
    }

    private void displayImage(int index) {
        Log.d(TAG, "displayImage called: index=" + index);
        if (index >= 0 && index < imageFiles.size()) {
            DocumentFile file = imageFiles.get(index);
            Glide.with(this)
                    .load(file.getUri())
                    .thumbnail(0.1f)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView);
            preloadAdjacentImages(index);
            showToast("Image " + (index + 1) + " of " + imageFiles.size());
            updateProgressTextView();
        }
    }

    private void updateProgressTextView() {
        Log.d(TAG, "updateProgressTextView called");
        progressTextView.setText("Image " + (currentIndex + 1) + " of " + imageFiles.size());
    }

    private void preloadAdjacentImages(int index) {
        Log.d(TAG, "preloadAdjacentImages called: index=" + index);
        if (index > 0) {
            Glide.with(this).load(imageFiles.get(index - 1).getUri()).preload();
        }
        if (index < imageFiles.size() - 1) {
            Glide.with(this).load(imageFiles.get(index + 1).getUri()).preload();
        }
    }

    private void showToast(String message) {
        Log.d(TAG, "showToast called: " + message);
        toastMessage.setText(message);
        toastMessage.setVisibility(TextView.VISIBLE);
        toastMessage.postDelayed(() -> toastMessage.setVisibility(TextView.GONE), 2000);
    }

    private void setupGestureDetection() {
        Log.d(TAG, "setupGestureDetection called");
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private int tapCount = 0;
            private long lastTapTime = 0;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                Log.d("Gesture", "onFling detected");
                final int SWIPE_THRESHOLD = 100;
                final int SWIPE_VELOCITY_THRESHOLD = 100;

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            Log.d("Gesture", "Swipe right detected");
                            previousImage();
                        } else {
                            Log.d("Gesture", "Swipe left detected");
                            nextImage();
                        }
                        return true;
                    }
                } else {
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            Log.d("Gesture", "Swipe down detected");
                            categorizeImage("No");
                        } else {
                            Log.d("Gesture", "Swipe up detected");
                            categorizeImage("Yes");
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                Log.d("Gesture", "Double tap detected");
                categorizeImage("Not Sure");
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                Log.d("Gesture", "Single tap detected");
                handleTripleTap();
                return false;
            }

            private void handleTripleTap() {
                long currentTime = System.currentTimeMillis();
                Log.d("Gesture", "Triple tap detected");

                if (currentTime - lastTapTime < 300) {
                    tapCount++;
                    if (tapCount == 3) {
                        Log.d("Gesture", "Exiting app after triple tap");
                        exitApp();
                        tapCount = 0;  // Reset after exiting
                    }
                } else {
                    tapCount = 1;  // Reset tap count if too much time has passed
                }

                lastTapTime = currentTime;
            }
        });

        imageView.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
        Log.d("Gesture", "Gesture detection setup complete");
    }

    private void setupVoiceRecognition() {
        Log.d(TAG, "setupVoiceRecognition called");
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Voice recognition ready for speech");
                voiceProgressBar.setVisibility(View.VISIBLE);
                showToast("Listening...");
            }

            @Override
            public void onResults(Bundle results) {
                Log.d(TAG, "Voice recognition results received");
                voiceProgressBar.setVisibility(View.GONE);
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String command = matches.get(0).toLowerCase(Locale.getDefault());
                    Log.d("The command received is: ", command);
                    processVoiceCommand(command);
                }
            }

            @Override
            public void onError(int error) {
                Log.d(TAG, "Voice recognition error: " + error);
                voiceProgressBar.setVisibility(View.GONE);
                String message;
                switch (error) {
                    case SpeechRecognizer.ERROR_NETWORK:
                        message = "Network error. Please check your connection.";
                        break;
                    case SpeechRecognizer.ERROR_AUDIO:
                        message = "Audio error. Please check your microphone.";
                        break;
                    case SpeechRecognizer.ERROR_NO_MATCH:
                        message = "No match found. Please try again.";
                        break;
                    default:
                        message = "Error recognizing speech. Try again.";
                        break;
                }
                showToast(message);
            }

            // Other RecognitionListener methods can be implemented as needed
            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onPartialResults(Bundle partialResults) {}

            @Override
            public void onRmsChanged(float rmsdB) {}
        });
    }

    private void processVoiceCommand(String command) {
        Log.d(TAG, "Processing voice command: " + command);
        switch (command) {
            case "yes":
                categorizeImage("Yes");
                break;
            case "no":
                categorizeImage("No");
                break;
            case "not sure":
                categorizeImage("Not Sure");
                break;
            case "exit":
                exitApp();
                break;
            default:
                showToast("Command not recognized.");
                break;
        }
    }

    private void nextImage() {
        Log.d(TAG, "Next image requested");
        if (currentIndex < imageFiles.size() - 1) {
            currentIndex++;
            displayImage(currentIndex);
        } else {
            Log.d(TAG, "No more images");
            showToast("No more images.");
        }
    }

    private void previousImage() {
        Log.d(TAG, "Previous image requested");
        if (currentIndex > 0) {
            currentIndex--;
            displayImage(currentIndex);
        } else {
            Log.d(TAG, "Already at the first image");
            showToast("Already at the first image.");
        }
    }

    private void categorizeImage(String category) {
        Log.d(TAG, "Categorizing image as: " + category);
        DocumentFile file = imageFiles.get(currentIndex);
        switch (category) {
            case "Yes":
                categorizedYes.add(file.getUri().toString());
                break;
            case "No":
                categorizedNo.add(file.getUri().toString());
                break;
            case "Not Sure":
                categorizedNotSure.add(file.getUri().toString());
                break;
        }
        showToast("Image categorized as " + category);
        nextImage();
    }

    private void exitApp() {
        Log.d(TAG, "Exiting app");
        exportCategorizedImages();
        finish();
    }

    private void exportCategorizedImages() {
        Log.d(TAG, "Exporting categorized images");
        try {
            File exportDir = new File(getExternalFilesDir(null), "CategorizedImages");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
                Log.d(TAG, "Created directory: " + exportDir.getAbsolutePath());
            }

            moveFilesToDirectory(categorizedYes, new File(exportDir, "Yes"));
            moveFilesToDirectory(categorizedNo, new File(exportDir, "No"));
            moveFilesToDirectory(categorizedNotSure, new File(exportDir, "NotSure"));

            logCategorizedImages();

        } catch (Exception e) {
            logErrorToFile(e);
            showToast("Error exporting images. See log for details.");
        }
    }

    private void moveFilesToDirectory(List<String> uris, File directory) {
        Log.d(TAG, "Moving files to directory: " + directory.getAbsolutePath());
        if (!directory.exists()) {
            directory.mkdirs();
        }

        for (String uriString : uris) {
            Uri uri = Uri.parse(uriString);
            DocumentFile file = DocumentFile.fromSingleUri(this, uri);
            if (file != null) {
                File destination = new File(directory, file.getName());
                // Implement the actual file moving/copying logic here
                Log.d(TAG, "File moved to: " + destination.getAbsolutePath());
                // You might need to use InputStream/OutputStream to copy the files.
            }
        }
    }

    private void logCategorizedImages() {
        Log.d(TAG, "Logging categorized images");
        try {
            File logFile = new File(getExternalFilesDir(null), "CategorizedImagesLog.txt");
            FileWriter writer = new FileWriter(logFile);
            writer.append("Yes: ").append(categorizedYes.toString()).append("\n");
            writer.append("No: ").append(categorizedNo.toString()).append("\n");
            writer.append("Not Sure: ").append(categorizedNotSure.toString()).append("\n");
            writer.close();
            Log.d(TAG, "Categorized images logged successfully");
        } catch (IOException e) {
            logErrorToFile(e);
        }
    }

    private void logErrorToFile(Exception e) {
        Log.e(TAG, "Error logged: " + e.getMessage(), e);
        try {
            File logFile = new File(getExternalFilesDir(null), "ErrorLog.txt");
            FileWriter writer = new FileWriter(logFile, true);
            writer.append(e.getMessage()).append("\n");
            for (StackTraceElement element : e.getStackTrace()) {
                writer.append(element.toString()).append("\n");
            }
            writer.close();
            Log.d(TAG, "Error logged to file successfully");
        } catch (IOException ioException) {
            Log.e(TAG, "Failed to log error to file", ioException);
            ioException.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
        saveState();
    }

    private void saveState() {
        Log.d(TAG, "saveState called");
        SharedPreferences preferences = getSharedPreferences("MyAppPreferences", MODE_PRIVATE);
        if (directoryUri != null) {
            preferences.edit()
                    .putString(PREF_DIRECTORY_URI, directoryUri.toString())
                    .putInt(PREF_LAST_IMAGE_INDEX, currentIndex)
                    .apply();
            exportCategorizedImages();  // Export images on exit or pause
        } else {
            logErrorToFile(new Exception("Directory URI is null in saveState"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Check if activity is finishing or destroyed before trying to clear Glide
        Log.d(TAG, "onDestroy called");
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (imageView != null && !isFinishing() && !isDestroyed()) {
            Glide.with(this).clear(imageView);
        }
    }

}
