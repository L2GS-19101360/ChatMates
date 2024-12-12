package com.example.chatmates.activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.chatmates.R;
import com.example.chatmates.adapters.RecentConversationAdapter;
import com.example.chatmates.databinding.ActivityMainBinding;
import com.example.chatmates.listeners.ConversionListener;
import com.example.chatmates.models.ChatMessage;
import com.example.chatmates.models.User;
import com.example.chatmates.utilities.Constants;
import com.example.chatmates.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends BaseActivity implements ConversionListener {

    private ActivityMainBinding binding;
    private PreferenceManager preferenceManager;
    private List<ChatMessage> conversations;
    private List<ChatMessage> filteredConversations; // New list for filtered conversations
    private RecentConversationAdapter conversationAdapter;
    private FirebaseFirestore database;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        init();
        loadUserDetails();
        getToken();
        setListeners();
        listenConversations();
        setupSearchUser(); // New method for filtering conversations

        binding.main.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Code to refresh the content (e.g., fetch new data)
                reloadConversations();
            }
        });
    }

    private void reloadConversations() {
        // Clear the current conversations to avoid duplicates
        conversations.clear();

        // Reload the conversations (fetch new data, etc.)
        listenConversations();  // This will repopulate the conversations list

        // Notify the adapter of the dataset change
        conversationAdapter.notifyDataSetChanged();

        // Stop the refresh animation when the data is reloaded
        binding.main.setRefreshing(false);
    }

    private void init() {
        conversations = new ArrayList<>();
        filteredConversations = new ArrayList<>();
        conversationAdapter = new RecentConversationAdapter(filteredConversations, this);
        binding.conversationRecyclerView.setAdapter(conversationAdapter);
        database = FirebaseFirestore.getInstance();
    }

    private void setListeners() {
        binding.imageSignOut.setOnClickListener(v -> signOut());
        binding.fabNewChat.setOnClickListener(v -> startActivity(new Intent(getApplicationContext(), UsersActivity.class)));
        binding.imageProfile.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), ProfileActivity.class);
            intent.putExtra(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            intent.putExtra(Constants.KEY_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            intent.putExtra(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
            intent.putExtra(Constants.KEY_EMAIL, preferenceManager.getString(Constants.KEY_EMAIL));
            intent.putExtra(Constants.KEY_PASSWORD, preferenceManager.getString(Constants.KEY_PASSWORD));
            startActivity(intent);
        });

        // Add long-click listener for deleting conversations
        conversationAdapter.setOnConversationLongClickListener(chatMessage -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Delete Conversation")
                    .setMessage("Are you sure you want to delete this conversation?")
                    .setPositiveButton("Yes", (dialog, which) -> deleteMessage(chatMessage))
                    .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                    .show();
        });
    }

    private void deleteMessage(ChatMessage chatMessage) {
        // Remove conversation from Firestore
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .document(chatMessage.conversationId)
                .delete()
                .addOnSuccessListener(unused -> {
                    conversations.remove(chatMessage);
                    filterConversations(binding.searchUser.getText().toString());
                    showToast("Conversation deleted successfully");
                })
                .addOnFailureListener(e -> showToast("Failed to delete conversation"));
    }

    private void loadUserDetails() {
        binding.textName.setText(preferenceManager.getString(Constants.KEY_NAME));
        byte[] bytes = Base64.decode(preferenceManager.getString(Constants.KEY_IMAGE), Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        binding.imageProfile.setImageBitmap(bitmap);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void setupSearchUser() {
        binding.searchUser.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterConversations(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // No action needed
            }
        });
    }

    private void listenConversations() {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private void filterConversations(String query) {
        filteredConversations.clear();
        if (query.isEmpty()) {
            filteredConversations.addAll(conversations);
        } else {
            for (ChatMessage conversation : conversations) {
                if (conversation.conversionName != null &&
                        conversation.conversionName.toLowerCase().contains(query.toLowerCase())) {
                    filteredConversations.add(conversation);
                }
            }
        }
        conversationAdapter.notifyDataSetChanged();
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            return;
        }
        if (value != null) {
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    String documentId = documentChange.getDocument().getId();
                    String senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    String receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = senderId;
                    chatMessage.receiverId = receiverId;
                    chatMessage.conversationId = documentId;
                    if (preferenceManager.getString(Constants.KEY_USER_ID).equals(senderId)) {
                        chatMessage.conversionImage = documentChange.getDocument().getString(Constants.KEY_RECEIVER_IMAGE);
                        chatMessage.conversionName = documentChange.getDocument().getString(Constants.KEY_RECEIVER_NAME);
                        chatMessage.conversionId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                        chatMessage.conversionEmail = documentChange.getDocument().getString(Constants.KEY_RECEIVER_EMAIL);
                    } else {
                        chatMessage.conversionImage = documentChange.getDocument().getString(Constants.KEY_SENDER_IMAGE);
                        chatMessage.conversionName = documentChange.getDocument().getString(Constants.KEY_SENDER_NAME);
                        chatMessage.conversionId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                        chatMessage.conversionEmail = documentChange.getDocument().getString(Constants.KEY_SENDER_EMAIL);
                    }
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    conversations.add(chatMessage);
                } else if (documentChange.getType() == DocumentChange.Type.MODIFIED) {
                    for (int i = 0; i < conversations.size(); i++) {
                        String senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                        String receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                        if (conversations.get(i).senderId.equals(senderId) && conversations.get(i).receiverId.equals(receiverId)) {
                            conversations.get(i).message = documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                            conversations.get(i).dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                            break;
                        }
                    }
                } else if (documentChange.getType() == DocumentChange.Type.REMOVED) {
                    String documentId = documentChange.getDocument().getId();
                    for (int i = 0; i < conversations.size(); i++) {
                        if (conversations.get(i).conversationId.equals(documentId)) {
                            conversations.remove(i);
                            break;
                        }
                    }
                    filterConversations(binding.searchUser.getText().toString());
                }
            }
            Collections.sort(conversations, (obj1, obj2) -> obj2.dateObject.compareTo(obj1.dateObject));
            filterConversations(binding.searchUser.getText().toString()); // Apply filter on update
            binding.conversationRecyclerView.smoothScrollToPosition(0);
            binding.conversationRecyclerView.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.GONE);
        }
    };

    private void getToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(this::updateToken);
    }

    private void updateToken(String token) {
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_USERS).document(
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnFailureListener(e -> {
                    showToast("Unable to update token");
                });
    }

    private void signOut() {
        showToast("Signing Out...");
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_USERS).document(
                preferenceManager.getString(Constants.KEY_USER_ID)
        );
        HashMap<String, Object> updates = new HashMap<>();
        updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
        documentReference.update(updates)
                .addOnSuccessListener(unused -> {
                    preferenceManager.clear();
                    startActivity(new Intent(getApplicationContext(), SignInActivity.class));
                })
                .addOnFailureListener(e -> {
                    showToast("Unable to sign out");
                });
    }

    @Override
    public void onConversionClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER, user);
        startActivity(intent);
    }
}