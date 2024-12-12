package com.example.chatmates.activities;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chatmates.R;
import com.example.chatmates.adapters.UsersAdapter;
import com.example.chatmates.databinding.ActivityUsersBinding;
import com.example.chatmates.listeners.UserListener;
import com.example.chatmates.models.User;
import com.example.chatmates.utilities.Constants;
import com.example.chatmates.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class UsersActivity extends BaseActivity implements UserListener {

    private ActivityUsersBinding binding;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUsersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
        setupSwipeToRefresh();
        getUsers();
        setupSearchListener(); // Add this line to initialize the search functionality
    }

    private List<User> allUsers = new ArrayList<>(); // Store all users fetched from Firestore
    private UsersAdapter usersAdapter;

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
    }

    private void setupSwipeToRefresh() {
        binding.main.setOnRefreshListener(() -> {
            getUsers();  // Reload the users list when pulled to refresh
        });
    }

    private void getUsers() {
        loading(true);
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        database.collection(Constants.KEY_COLLECTION_USERS)
                .get()
                .addOnCompleteListener(task -> {
                    loading(false);
                    String currentUserId = preferenceManager.getString(Constants.KEY_USER_ID);
                    if (task.isSuccessful() && task.getResult() != null) {
                        allUsers.clear(); // Clear the list to avoid duplicates
                        for (QueryDocumentSnapshot queryDocumentSnapshot : task.getResult()) {
                            if (currentUserId.equals(queryDocumentSnapshot.getId())) {
                                continue;
                            }
                            User user = new User();
                            user.name = queryDocumentSnapshot.getString(Constants.KEY_NAME);
                            user.email = queryDocumentSnapshot.getString(Constants.KEY_EMAIL);
                            user.image = queryDocumentSnapshot.getString(Constants.KEY_IMAGE);
                            user.token = queryDocumentSnapshot.getString(Constants.KEY_FCM_TOKEN);
                            user.id = queryDocumentSnapshot.getId();
                            allUsers.add(user);
                        }
                        if (allUsers.size() > 0) {
                            usersAdapter = new UsersAdapter(allUsers, this);
                            binding.usersRecyclerView.setAdapter(usersAdapter);
                            binding.usersRecyclerView.setVisibility(View.VISIBLE);
                        } else {
                            showErrorMessage();
                        }
                    } else {
                        showErrorMessage();
                    }

                    // Stop the refreshing animation once data is fetched
                    binding.main.setRefreshing(false);
                });
    }

    private void showErrorMessage() {
        binding.textErrorMessage.setText(String.format("%s", "No users available"));
        binding.textErrorMessage.setVisibility(View.VISIBLE);
    }

    private void loading(Boolean isLoading) {
        if (isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
        }
    }

    private void setupSearchListener() {
        binding.searchUser.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed here
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterUsers(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed here
            }
        });
    }

    private void filterUsers(String query) {
        if (usersAdapter == null) {
            return;
        }

        if (query.isEmpty()) {
            // If search is empty, show all users
            usersAdapter.updateUserList(allUsers);
        } else {
            // Filter users based on the query
            List<User> filteredUsers = new ArrayList<>();
            for (User user : allUsers) {
                if (user.name.toLowerCase().contains(query.toLowerCase())) {
                    filteredUsers.add(user);
                }
            }
            if (filteredUsers.isEmpty()) {
                binding.textErrorMessage.setText("No users found");
                binding.textErrorMessage.setVisibility(View.VISIBLE);
                binding.usersRecyclerView.setVisibility(View.GONE);
            } else {
                binding.textErrorMessage.setVisibility(View.GONE);
                binding.usersRecyclerView.setVisibility(View.VISIBLE);
                usersAdapter.updateUserList(filteredUsers);
            }
        }
    }

    @Override
    public void onUserClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
        finish();
    }

}