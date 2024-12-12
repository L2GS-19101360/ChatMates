package com.example.chatmates.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatmates.R;
import com.example.chatmates.databinding.ActivityProfileBinding;
import com.example.chatmates.utilities.Constants;
import com.example.chatmates.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private PreferenceManager preferenceManager;
    private String encodedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        loadProfileDetail();
        setListeners();
    }

    void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.buttonUpdate.setOnClickListener(v -> {
            if (isValidProfileDetails()) {
                updateProfile();
            }
        });
        binding.layoutImage.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pickImage.launch(intent);
        });

        binding.inputPassword.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                int drawableEndIndex = 2; // index for drawableEnd
                if (binding.inputPassword.getCompoundDrawables()[drawableEndIndex] != null) {
                    float drawableWidth = binding.inputPassword.getCompoundDrawables()[drawableEndIndex].getBounds().width();
                    if (event.getRawX() >= (binding.inputPassword.getRight() - drawableWidth - binding.inputPassword.getPaddingEnd())) {
                        togglePasswordVisibility(binding.inputPassword);
                        return true;
                    }
                }
            }
            return false;
        });

        binding.inputConfirmPassword.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                int drawableEndIndex = 2; // index for drawableEnd
                if (binding.inputConfirmPassword.getCompoundDrawables()[drawableEndIndex] != null) {
                    float drawableWidth = binding.inputConfirmPassword.getCompoundDrawables()[drawableEndIndex].getBounds().width();
                    if (event.getRawX() >= (binding.inputConfirmPassword.getRight() - drawableWidth - binding.inputConfirmPassword.getPaddingEnd())) {
                        togglePasswordVisibility(binding.inputConfirmPassword);
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private void togglePasswordVisibility(EditText editText) {
        if (editText.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            editText.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    getDrawable(R.drawable.round_visibility_off_24), null);
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            editText.setCompoundDrawablesWithIntrinsicBounds(null, null,
                    getDrawable(R.drawable.round_visibility_24), null);
        }
        editText.setSelection(editText.getText().length()); // Maintain cursor position
    }

    private void updateProfile() {
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        String userId = preferenceManager.getString(Constants.KEY_USER_ID);

        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_NAME, binding.inputName.getText().toString());
        updates.put(Constants.KEY_EMAIL, binding.inputEmail.getText().toString());
        updates.put(Constants.KEY_PASSWORD, binding.inputPassword.getText().toString());
        if (encodedImage != null) {
            updates.put(Constants.KEY_IMAGE, encodedImage);
        }

        database.collection(Constants.KEY_COLLECTION_USERS)
                .document(userId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    loading(false);
                    showToast("Profile updated successfully!");
                    logOut();
                })
                .addOnFailureListener(exception -> {
                    loading(false);
                    showToast("Failed to update profile: " + exception.getMessage());
                });
    }

    private void loadProfileDetail() {
        String userName = preferenceManager.getString(Constants.KEY_NAME);
        String userEmail = preferenceManager.getString(Constants.KEY_EMAIL);
        String userPassword = preferenceManager.getString(Constants.KEY_PASSWORD);

        String encodedImage = preferenceManager.getString(Constants.KEY_IMAGE);
        if (encodedImage != null) {
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            binding.imageProfile.setImageBitmap(bitmap);
        }

        binding.inputName.setText(userName);
        binding.inputEmail.setText(userEmail);
        binding.inputPassword.setText(userPassword);
        binding.inputConfirmPassword.setText(userPassword);
    }

    private void logOut() {
        preferenceManager.clear();
        Intent intent = new Intent(getApplicationContext(), SignInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private String encodeImage(Bitmap bitmap) {
        int previewWidth = 150;
        int previewHeight = bitmap.getHeight() * previewWidth / bitmap.getWidth();
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    if (result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            InputStream inputStream = getContentResolver().openInputStream(imageUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            binding.imageProfile.setImageBitmap(bitmap);
                            binding.textAddImage.setVisibility(View.GONE);
                            encodedImage = encodeImage(bitmap);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
    );

    private Boolean isValidProfileDetails() {
        if (binding.inputName.getText().toString().trim().isEmpty()) {
            showToast("Enter name");
            return false;
        } else if (binding.inputEmail.getText().toString().trim().isEmpty()) {
            showToast("Enter email");
            return false;
        } else if (binding.inputPassword.getText().toString().trim().isEmpty()) {
            showToast("Enter password");
            return false;
        } else if (binding.inputConfirmPassword.getText().toString().trim().isEmpty()) {
            showToast("Confirm your password");
            return false;
        } else if (!binding.inputPassword.getText().toString().equals(binding.inputConfirmPassword.getText().toString())) {
            showToast("Password and confirm password must be the same");
            return false;
        } else {
            return true;
        }
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.buttonUpdate.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
            binding.buttonUpdate.setVisibility(View.VISIBLE);
        }
    }
}
