package com.blue.curator;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;
    private static final String PREF_DIRECTORY_URI = "directoryUri";
    private static final String PREF_LAST_IMAGE_INDEX = "lastImageIndex";
    private static final String TAG = "MainActivity";

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
//    private ImageButton micButton;

    private File selectedFile;
    private File notSelectedFile;
    private File notSureFile;
    private Handler handler = new Handler();
    private Button yesButton;
    private Button noButton;
    private Button notSureButton;

    private TextView selectedCountTextView;

    private ProgressBar exportProgressBar;
    private TextView exportProgressText;
    private Button successButton;
    private boolean isVoiceRecognitionEnabled = true; // Default to enabled
    private ImageButton gearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        setContentView(R.layout.activity_main);

        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        imageView = findViewById(R.id.imageView);
        toastMessage = findViewById(R.id.toastMessage);
        voiceProgressBar = findViewById(R.id.voiceProgressBar);
        progressTextView = findViewById(R.id.progressTextView);
        gearButton = findViewById(R.id.gearButton);

        // Initialize buttons
        yesButton = findViewById(R.id.yesButton);
        noButton = findViewById(R.id.noButton);
        notSureButton = findViewById(R.id.notSureButton);
        selectedCountTextView = findViewById(R.id.selectedCountTextView);

        // Initialize success button and hide it by default
        exportProgressBar = findViewById(R.id.exportProgressBar);
        exportProgressText = findViewById(R.id.exportProgressText);

        // Set up gear button listener
        gearButton.setOnClickListener(v -> showVoiceRecognitionToggle());

        // Display the selection dialog on app start
        showSelectionDialog();
    }

    private void showSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose an option")
                .setMessage("Select one of the following options:")
                .setPositiveButton("View Selected Images", (dialog, which) -> {
                    viewSelectedImages();
                })
                .setNegativeButton("Choose Directory and Get into Curator", (dialog, which) -> {
                    chooseDirectoryAndContinue();
                })
                .setCancelable(false)
                .show();
    }

    private void viewSelectedImages() {
        initializeTextFiles();  // Ensure selectedFile is initialized
        loadImagesFromCategory(selectedFile);

        if (!categorizedYes.isEmpty()) {
            currentIndex = 0;  // Start from the first image in the list
            displaySelectedImage(currentIndex);  // Display the first selected image
            setupImageNavigationForSelected();  // Setup previous/next navigation for selected images
        } else {
            showToast("No selected images to display.");
        }
    }

    private void setupImageNavigationForSelected() {
        ImageButton nextButton = findViewById(R.id.nextButton);
        ImageButton previousButton = findViewById(R.id.previousButton);

        nextButton.setOnClickListener(v -> nextSelectedImage());
        previousButton.setOnClickListener(v -> previousSelectedImage());
    }


    private void displaySelectedImage(int index) {
        Log.d(TAG, "displaySelectedImage called: index=" + index);
        if (index >= 0 && index < categorizedYes.size()) {
            Uri imageUri = Uri.parse(categorizedYes.get(index));
            Glide.with(this)
                    .load(imageUri)
                    .thumbnail(0.1f)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView);

            showToast("Image " + (index + 1) + " of " + categorizedYes.size());
            updateProgressTextViewForSelected();
        }
    }

    private void nextSelectedImage() {
        Log.d(TAG, "Next selected image requested");
        if (currentIndex < categorizedYes.size() - 1) {
            currentIndex++;
            displaySelectedImage(currentIndex);
        } else {
            Log.d(TAG, "No more selected images");
            showToast("No more images.");
        }
    }

    private void previousSelectedImage() {
        Log.d(TAG, "Previous selected image requested");
        if (currentIndex > 0) {
            currentIndex--;
            displaySelectedImage(currentIndex);
        } else {
            Log.d(TAG, "Already at the first selected image");
            showToast("Already at the first image.");
        }
    }

    private void updateProgressTextViewForSelected() {
        progressTextView.setText("Image " + (currentIndex + 1) + " of " + categorizedYes.size());
    }


    private void chooseDirectoryAndContinue() {
        if (directoryUri == null) {
            openDirectoryPicker();
        } else {
            executorService.submit(() -> {
                loadImagesFromDirectory(directoryUri);
                runOnUiThread(() -> {
                    displayImage(currentIndex);
                });
            });
        }
        setupGestureDetection();
        setupVoiceRecognition();
        setupButtonListeners();
    }

    private void loadImagesFromCategory(File categoryFile) {
        if (categoryFile == null || !categoryFile.exists()) {
            Log.e(TAG, "Category file is null or does not exist");
            return;
        }
        categorizedYes.clear();  // Assuming you want to show only the "Yes" images
        try {
            List<String> lines = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                lines = java.nio.file.Files.readAllLines(categoryFile.toPath());
            }
            for (String line : lines) {
                categorizedYes.add(line);
            }
            updateProgressTextView();  // Update UI to reflect the number of images loaded
        } catch (IOException e) {
            logErrorToFile(e);
        }
    }

    private void showVoiceRecognitionToggle() {
        // Display a dialog with a toggle switch
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Voice Recognition");

        final Switch toggleSwitch = new Switch(this);
        toggleSwitch.setChecked(isVoiceRecognitionEnabled);
        toggleSwitch.setText("  Enable Voice Recognition");

        builder.setView(toggleSwitch);

        builder.setPositiveButton("OK", (dialog, which) -> {
            isVoiceRecognitionEnabled = toggleSwitch.isChecked();
            Toast.makeText(MainActivity.this, "Voice Recognition " + (isVoiceRecognitionEnabled ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        builder.create().show();
    }

    private void updateSelectedCount() {
        int selectedCount = categorizedYes.size();
        selectedCountTextView.setText(selectedCount + "/300");
    }

    private void setupButtonListeners() {
        yesButton.setOnClickListener(v -> {
            Log.d("Button", "Yes button clicked");
            categorizeImage("Yes");
            showAcknowledgmentToast("Selected: Yes");
        });

        noButton.setOnClickListener(v -> {
            Log.d("Button", "No button clicked");
            categorizeImage("No");
            showAcknowledgmentToast("Selected: No");
        });

        notSureButton.setOnClickListener(v -> {
            Log.d("Button", "Not Sure button clicked");
            categorizeImage("Not Sure");
            showAcknowledgmentToast("Selected: Not Sure");
        });
    }

    private void initializeTextFiles() {
        // Initialize the file objects
        selectedFile = new File(getExternalFilesDir(null), "selected.txt");
        notSelectedFile = new File(getExternalFilesDir(null), "not_selected.txt");
        notSureFile = new File(getExternalFilesDir(null), "not_sure.txt");

        // Check if the file objects are properly initialized
        if (selectedFile != null && notSelectedFile != null && notSureFile != null) {
            try {
                // Create the files if they don't exist
                if (!selectedFile.exists()) {
                    selectedFile.createNewFile();
                }
                if (!notSelectedFile.exists()) {
                    notSelectedFile.createNewFile();
                }
                if (!notSureFile.exists()) {
                    notSureFile.createNewFile();
                }
                Log.d(TAG, "Text files created or already exist");
            } catch (IOException e) {
                logErrorToFile(e);
            }
        } else {
            Log.e(TAG, "Error initializing text files: One or more File objects are null");
        }
    }


    private void setStartingIndexFromTextFiles() {
        // Assume the latest file modified determines the last action
        File latestFile = selectedFile;

        if (notSelectedFile.lastModified() > latestFile.lastModified()) {
            latestFile = notSelectedFile;
        }
        if (notSureFile.lastModified() > latestFile.lastModified()) {
            latestFile = notSureFile;
        }

        // Determine the starting index based on the content of the latest file
        try {
            List<String> lines = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                lines = java.nio.file.Files.readAllLines(latestFile.toPath());
            }
            if (!lines.isEmpty()) {
                String lastUri = lines.get(lines.size() - 1);
                for (int i = 0; i < imageFiles.size(); i++) {
                    if (imageFiles.get(i).getUri().toString().equals(lastUri)) {
                        currentIndex = i;
                        break;
                    }
                }
            }
        } catch (IOException e) {
            logErrorToFile(e);
        }
    }

    private void showButtons() {
        yesButton.setVisibility(View.VISIBLE);
        noButton.setVisibility(View.VISIBLE);
        notSureButton.setVisibility(View.VISIBLE);
    }

    private void hideButtons() {
        yesButton.setVisibility(View.GONE);
        noButton.setVisibility(View.GONE);
        notSureButton.setVisibility(View.GONE);
    }

    private void startVoiceRecognition() {
        Log.d(TAG, "startVoiceRecognition called");
        hideButtons();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizer.startListening(intent);

        handler.postDelayed(this::showButtons, 10000);  // 10 seconds listening duration
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
                initializeTextFiles(); // Create text files when the directory is first selected
                loadImagesFromDirectory(directoryUri);
                setStartingIndexFromTextFiles();
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

            hideButtons(); // Hide buttons when displaying a new image

            if (isVoiceRecognitionEnabled) {
                handler.postDelayed(this::startVoiceRecognition, 3000);  // Delay 3 seconds before starting voice recognition
            } else {
                handler.postDelayed(this::showButtons, 3000);  // Delay 3 seconds before showing buttons
            }

            preloadAdjacentImages(index);
            showToast("Image " + (index + 1) + " of " + imageFiles.size());
            updateProgressTextView();
        }
    }

    private void showAcknowledgmentToast(String message) {
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 100);
        toast.show();
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
            Glide.with(this).load(imageFiles.get(index + 1).getUri()).preload(); // Preload next image
        }
        if (index < imageFiles.size() - 2) {
            Glide.with(this).load(imageFiles.get(index + 2).getUri()).preload(); // Preload image after next
        }
    }


    private void showToast(String message) {
        Log.d(TAG, "showToast called: " + message);
        Toast toast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 100);
        toast.show();
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
                    case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                        message = "Insufficient permissions. Please allow microphone access.";
                        break;
                    case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                        message = "Recognition service busy. Please try again.";
                        break;
                    case SpeechRecognizer.ERROR_SERVER:
                        message = "Server error. Please try again later.";
                        break;
                    case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                        message = "No speech input detected. Please try again.";
                        break;
                    default:
                        message = "Error recognizing speech. Try again.";
                        break;
                }
                showToast(message);
                logErrorToFile(new Exception("SpeechRecognizer error: " + message + " (Error code: " + error + ")"));
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
        updateSelectedCount();  // Update count whenever an image is categorized
        removePreviousSelection(file.getUri().toString());  // Remove previous selection
        switch (category) {
            case "Yes":
                categorizedYes.add(file.getUri().toString());
                writeToFile(selectedFile, file.getUri().toString());
                break;
            case "No":
                categorizedNo.add(file.getUri().toString());
                writeToFile(notSelectedFile, file.getUri().toString());
                break;
            case "Not Sure":
                categorizedNotSure.add(file.getUri().toString());
                writeToFile(notSureFile, file.getUri().toString());
                break;
        }
        showToast("Image categorized as " + category);
        nextImage();
    }

    private void writeToFile(File file, String data) {
        try {
            FileWriter writer = new FileWriter(file, true);
            writer.append(data).append("\n");
            writer.close();
            Log.d(TAG, "Data written to file: " + file.getName());
        } catch (IOException e) {
            logErrorToFile(e);
        }
    }

    private void removeFromFile(File file, String data) {
        try {
            List<String> lines = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                lines = new ArrayList<>(java.nio.file.Files.readAllLines(file.toPath()));
                lines.remove(data);
                java.nio.file.Files.write(file.toPath(), lines);
            }
            Log.d(TAG, "Data removed from file: " + file.getName());
        } catch (IOException e) {
            logErrorToFile(e);
        }
    }

    private void removePreviousSelection(String uri) {
        Log.d(TAG, "Removing previous selection for URI: " + uri);
        categorizedYes.remove(uri);
        categorizedNo.remove(uri);
        categorizedNotSure.remove(uri);

        // Remove from files
        removeFromFile(selectedFile, uri);
        removeFromFile(notSelectedFile, uri);
        removeFromFile(notSureFile, uri);
    }

    private void exitApp() {
        Log.d(TAG, "Exiting app");
        exportCategorizedImages();
        finish();
    }

    private void exportCategorizedImages() {
        try {
            List<String> allUris = new ArrayList<>();
            allUris.addAll(categorizedYes);
            allUris.addAll(categorizedNo);
            allUris.addAll(categorizedNotSure);

            int totalFiles = allUris.size();
            showProgress(totalFiles);

            moveFilesToDirectory(categorizedYes, new File(getExternalFilesDir(null), "Yes"), totalFiles);
            moveFilesToDirectory(categorizedNo, new File(getExternalFilesDir(null), "No"), totalFiles);
            moveFilesToDirectory(categorizedNotSure, new File(getExternalFilesDir(null), "NotSure"), totalFiles);

            logCategorizedImages();  // Log the current categorization to the text files

            hideProgress();

        } catch (Exception e) {
            logErrorToFile(e);
            showToast("Error exporting images. See log for details.");
        }
    }


//    private void moveFilesToDirectory(List<String> uris, File directory) {
//        Log.d(TAG, "Moving files to directory: " + directory.getAbsolutePath());
//        if (!directory.exists()) {
//            directory.mkdirs();
//        }
//
//        for (String uriString : uris) {
//            Uri uri = Uri.parse(uriString);
//            DocumentFile file = DocumentFile.fromSingleUri(this, uri);
//            if (file != null) {
//                File destination = new File(directory, file.getName());
//                // Implement the actual file moving/copying logic here
//                Log.d(TAG, "File moved to: " + destination.getAbsolutePath());
//                // You might need to use InputStream/OutputStream to copy the files.
//            }
//        }
//    }

    private void moveFilesToDirectory(List<String> uris, File directory, int totalFiles) {
        Log.d(TAG, "Moving files to directory: " + directory.getAbsolutePath());
        if (!directory.exists()) {
            directory.mkdirs(); // Ensure the directory exists
        }

        int currentFile = 0;
        for (String uriString : uris) {
            currentFile++;
            updateProgress(currentFile, totalFiles);

            Uri uri = Uri.parse(uriString);
            DocumentFile file = DocumentFile.fromSingleUri(this, uri);
            if (file != null) {
                File destination = new File(directory, file.getName());
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(destination)) {

                    // Copy the file content from source to destination
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }

                    Log.d(TAG, "File moved to: " + destination.getAbsolutePath());

                    // Optionally, delete the source file after copying
//                    if (file.delete()) {
//                        Log.d(TAG, "Source file deleted: " + uriString);
//                    } else {
//                        Log.e(TAG, "Failed to delete source file: " + uriString);
//                    }

                } catch (IOException e) {
                    logErrorToFile(e);
                    Log.e(TAG, "Error moving file: " + uriString, e);
                }
            } else {
                Log.e(TAG, "DocumentFile is null for URI: " + uriString);
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

    private void showProgress(int totalFiles) {
        exportProgressBar.setMax(totalFiles);
        exportProgressBar.setProgress(0);
        exportProgressBar.setVisibility(View.VISIBLE);
        exportProgressText.setVisibility(View.VISIBLE);
    }

    private void updateProgress(int currentFile, int totalFiles) {
        exportProgressBar.setProgress(currentFile);
        exportProgressText.setText(currentFile + "/" + totalFiles);
    }

    private void hideProgress() {
        exportProgressBar.setVisibility(View.GONE);
        exportProgressText.setVisibility(View.GONE);
    }

    private void logErrorToFile(Exception e) {
        Log.e(TAG, "Error logged: " + e.getMessage(), e);
        try {
            File logDir = getExternalFilesDir(null);
            if (logDir != null && !logDir.exists()) {
                logDir.mkdirs(); // Ensure the directory exists
            }

            File logFile = new File(logDir, "ErrorLog.txt");

            // Limit log file size (e.g., 1MB)
            if (logFile.length() > 1024 * 1024) {
                logFile.delete(); // Delete if it exceeds 1MB
                logFile.createNewFile(); // Create a new log file
            }

            FileWriter writer = new FileWriter(logFile, true);
            writer.append(e.getMessage()).append("\n");
            for (StackTraceElement element : e.getStackTrace()) {
                writer.append(element.toString()).append("\n");
            }
            writer.close();
            Log.d(TAG, "Error logged to file successfully");
        } catch (IOException ioException) {
            Log.e(TAG, "Failed to log error to file", ioException);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
        saveState();
        exportCategorizedImages();
    }

    private void saveState() {
        Log.d(TAG, "saveState called");
        SharedPreferences preferences = getSharedPreferences("MyAppPreferences", MODE_PRIVATE);
        if (directoryUri != null) {
            preferences.edit()
                    .putString(PREF_DIRECTORY_URI, directoryUri.toString())
                    .putInt(PREF_LAST_IMAGE_INDEX, currentIndex)
                    .apply();
        } else {
            Log.w(TAG, "Directory URI is null. Skipping state save.");
            logErrorToFile(new Exception("Directory URI is null in saveState"));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        exportCategorizedImages();  // Ensure files are saved when the app is destroyed
        // Check if activity is finishing or destroyed before trying to clear Glide
        Log.d(TAG, "onDestroy called");
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (!executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (!isFinishing() && !isDestroyed()) {
            Glide.with(this).clear(imageView);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with voice recognition
            } else {
                // Permission denied, notify the user
                showToast("Microphone permission is required for voice recognition");
            }
        }
    }

}
