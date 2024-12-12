package com.example.chatmates.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chatmates.R;
import com.example.chatmates.databinding.ActivityRecipientBinding;
import com.example.chatmates.utilities.Constants;

public class RecipientActivity extends AppCompatActivity {

    private ActivityRecipientBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRecipientBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        loadRecipientDetail();
        setListener();
    }

    private void setListener() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
    }

    private void loadRecipientDetail() {
        String recipientName = getIntent().getStringExtra(Constants.KEY_RECEIVER_NAME);
        String recipientEmail = getIntent().getStringExtra(Constants.KEY_RECEIVER_EMAIL);
        String recipientImage = getIntent().getStringExtra(Constants.KEY_RECEIVER_IMAGE);

        byte[] bytes = Base64.decode(recipientImage, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        binding.imageProfile.setImageBitmap(bitmap);

        binding.inputName.setText(recipientName);
        binding.inputEmail.setText(recipientEmail);
    }

}