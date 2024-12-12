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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.chatmates.R;
import com.example.chatmates.adapters.ChatAdapter;
import com.example.chatmates.databinding.ActivityChatBinding;
import com.example.chatmates.models.ChatMessage;
import com.example.chatmates.models.User;
import com.example.chatmates.utilities.Constants;
import com.example.chatmates.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private String conversionId = null;
    private Boolean isReceiverAvailable = false;
    private String isSentMessageSwipedId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceivedDetail();
        init();
        listenMessages();
        binding.main.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Refresh the activity
                reloadActivity();
            }
        });
        binding.inputMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                boolean isInputEmpty = charSequence.toString().trim().isEmpty();
                binding.layoutSend.setEnabled(!isInputEmpty);
                binding.layoutSend.setAlpha(isInputEmpty ? 0.5f : 1.0f); // Optional: Dim button when disabled
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        binding.layoutSend.setEnabled(false);
        binding.layoutSend.setAlpha(0.5f);
    }

    private void reloadActivity() {
        // Clear previous messages and refresh the chat
        chatMessages.clear();
        chatAdapter.notifyDataSetChanged();

        // Re-initialize data or reload it as needed (e.g., listen for new messages)
        listenMessages();

        // Stop the refreshing animation
        binding.main.setRefreshing(false);
    }

    private void init() {
        preferenceManager = new PreferenceManager(getApplicationContext());
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID),
                this::showDeleteConfirmationDialog
        );
        binding.chatRecyclerView.setAdapter(chatAdapter);
        database = FirebaseFirestore.getInstance();
        setupSwipeToLeft();
    }

    private void setupSwipeToLeft() {
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false; // We don't support moving items
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (direction == ItemTouchHelper.LEFT) {
                    int position = viewHolder.getAdapterPosition();
                    ChatMessage swipedMessage = chatMessages.get(position);
                    String documentId = swipedMessage.sentMessageDocumentId;
                    String messageContent = swipedMessage.message;

                    isSentMessageSwipedId = documentId;

//                    Toast.makeText(getApplicationContext(), documentId, Toast.LENGTH_SHORT).show();
                    binding.inputMessage.setText(messageContent);

                    // Reset the swipe animation
                    chatAdapter.notifyItemChanged(position);
                }
            }
        };

        // Attach the ItemTouchHelper to the RecyclerView
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
        itemTouchHelper.attachToRecyclerView(binding.chatRecyclerView);
    }

    private void updateSentMessage() {
        if (isSentMessageSwipedId != null) {
            // Get the updated message from the input field
            String updatedMessage = binding.inputMessage.getText().toString();

            // Create a map for the updated message
            HashMap<String, Object> updatedMessageMap = new HashMap<>();
            updatedMessageMap.put(Constants.KEY_MESSAGE, updatedMessage);
            updatedMessageMap.put(Constants.KEY_TIMESTAMP, new Date()); // Update timestamp if needed

            // Update the message in Firestore
            database.collection(Constants.KEY_COLLECTION_CHAT)
                    .document(isSentMessageSwipedId)
                    .update(updatedMessageMap)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // If successful, update the message locally in the chat
                            for (ChatMessage message : chatMessages) {
                                if (message.sentMessageDocumentId.equals(isSentMessageSwipedId)) {
                                    message.message = updatedMessage;
                                    break;
                                }
                            }
                            chatAdapter.notifyDataSetChanged(); // Notify adapter to refresh the chat view
                            updateConversion(preferenceManager.getString(Constants.KEY_NAME) + ": " + updatedMessage);
                            binding.inputMessage.setText(""); // Clear the input field
                            isSentMessageSwipedId = null; // Reset the swiped message ID
                        } else {
                            // Handle failure
                            Toast.makeText(ChatActivity.this, "Failed to update message", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void showDeleteConfirmationDialog(ChatMessage chatMessage) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Message")
                .setMessage("Are you sure you want to delete this message?")
                .setPositiveButton("Yes", (dialog, which) -> deleteMessage(chatMessage))
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteMessage(ChatMessage chatMessage) {
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, chatMessage.senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, chatMessage.receiverId)
                .whereEqualTo(Constants.KEY_MESSAGE, chatMessage.message)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot snapshot : queryDocumentSnapshots.getDocuments()) {
                            snapshot.getReference().delete();
                        }
                        chatMessages.remove(chatMessage);
                        updateConversion(preferenceManager.getString(Constants.KEY_NAME) + " deleted a message");
                        chatAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void sendMessage() {
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        message.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());
        message.put(Constants.KEY_TIMESTAMP, new Date());
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message);
        if (conversionId != null) {
            updateConversion(preferenceManager.getString(Constants.KEY_NAME) + ": " + binding.inputMessage.getText().toString());
        } else {
            HashMap<String, Object> conversion = new HashMap<>();
            conversion.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversion.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversion.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversion.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversion.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversion.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            conversion.put(Constants.KEY_SENDER_EMAIL, preferenceManager.getString(Constants.KEY_EMAIL));
            conversion.put(Constants.KEY_RECEIVER_EMAIL, receiverUser.email);
            conversion.put(Constants.KEY_LAST_MESSAGE, preferenceManager.getString(Constants.KEY_NAME) + ": " + binding.inputMessage.getText().toString());
            conversion.put(Constants.KEY_TIMESTAMP, new Date());
            addConversion(conversion);
        }
        binding.inputMessage.setText(null);
    }

    private void listenAvailabilityOfReceiver() {
        database.collection(Constants.KEY_COLLECTION_USERS).document(
                receiverUser.id
        ).addSnapshotListener(ChatActivity.this, (value, error) -> {
            if (error != null) {
                return;
            }
            if (value != null) {
                if (value.getLong(Constants.KEY_AVAILABILITY) != null) {
                    int availability = Objects.requireNonNull(
                            value.getLong(Constants.KEY_AVAILABILITY)
                    ).intValue();
                    isReceiverAvailable = availability == 1;
                }
                receiverUser.token = value.getString(Constants.KEY_FCM_TOKEN);
            }
            if (isReceiverAvailable) {
                binding.textAvailability.setVisibility(View.VISIBLE);
            } else {
                binding.textAvailability.setVisibility(View.GONE);
            }
        });
    }

    private void listenMessages() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if (error != null) {
            return;
        }
        if (value != null) {
            int count = chatMessages.size();
            for (DocumentChange documentChange : value.getDocumentChanges()) {
                if (documentChange.getType() == DocumentChange.Type.ADDED) {
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    chatMessage.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    chatMessage.message = documentChange.getDocument().getString(Constants.KEY_MESSAGE);
                    chatMessage.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    chatMessage.sentMessageDocumentId = documentChange.getDocument().getId();
                    chatMessages.add(chatMessage);
                } else if (documentChange.getType() == DocumentChange.Type.MODIFIED) {
                    String modifiedMessageId = documentChange.getDocument().getId();
                    String modifiedMessageContent = documentChange.getDocument().getString(Constants.KEY_MESSAGE);

                    for (ChatMessage message : chatMessages) {
                        if (message.sentMessageDocumentId.equals(modifiedMessageId)) {
                            message.message = modifiedMessageContent; // Update the message content
                            chatAdapter.notifyDataSetChanged(); // Refresh the adapter
                            break;
                        }
                    }
                } else if (documentChange.getType() == DocumentChange.Type.REMOVED) {
                    String removedMessageId = documentChange.getDocument().getId();

                    for (int i = 0; i < chatMessages.size(); i++) {
                        if (chatMessages.get(i).sentMessageDocumentId.equals(removedMessageId)) {
                            chatMessages.remove(i); // Remove the message from the list
                            chatAdapter.notifyItemRemoved(i); // Notify the adapter about the removed item
                            break;
                        }
                    }
                }
            }
            Collections.sort(chatMessages, (obj1, obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            if (count == 0) {
                chatAdapter.notifyDataSetChanged();
            } else {
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size() - 1);
            }
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
        if (conversionId == null) {
            checkForConversion();
        }
    };

    private Bitmap getBitmapFromEncodedString(String encodedImage) {
        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private void loadReceivedDetail() {
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receiverUser.name);
        byte[] bytes = Base64.decode(receiverUser.image, Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        binding.imageUser.setImageBitmap(bitmap);
    }

    private void setListeners() {
        binding.imageBack.setOnClickListener(v -> onBackPressed());
        binding.layoutSend.setOnClickListener(v -> {
            if (isSentMessageSwipedId == null) {
                sendMessage(); // If no message is swiped, send a new message
            } else {
                updateSentMessage(); // If a message is swiped, update the sent message
            }
        });
        binding.imageInfo.setOnClickListener(v -> {
            receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
            Intent intent = new Intent(getApplicationContext(), RecipientActivity.class);
            intent.putExtra(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            intent.putExtra(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            intent.putExtra(Constants.KEY_RECEIVER_EMAIL, receiverUser.email);
            startActivity(intent);
        });
    }

    private String getReadableDateTime(Date date) {
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }

    private void addConversion(HashMap<String, Object> conversion) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversion)
                .addOnSuccessListener(documentReference -> conversionId = documentReference.getId());
    }

    private void updateConversion(String message) {
        DocumentReference documentReference = database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.KEY_TIMESTAMP, new Date()
        );
    }

    private void checkForConversion() {
        if (chatMessages.size() != 0) {
            checkForConversionRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.id
            );
            checkForConversionRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    private void checkForConversionRemotely(String senderId, String receiverId) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(conversionOnCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> conversionOnCompleteListener = task -> {
        if (task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size() > 0) {
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversionId = documentSnapshot.getId();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }
}