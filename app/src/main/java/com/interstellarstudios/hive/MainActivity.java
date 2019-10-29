package com.interstellarstudios.hive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomnavigation.LabelVisibilityMode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.interstellarstudios.hive.firestore.GetData;
import com.interstellarstudios.hive.fragments.ChatsFragment;
import com.interstellarstudios.hive.fragments.ProfileFragment;
import com.interstellarstudios.hive.fragments.UsersFragment;
import com.interstellarstudios.hive.models.User;
import com.interstellarstudios.hive.repository.Repository;
import com.sjl.foreground.Foreground;
import com.squareup.picasso.Picasso;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements Foreground.Listener {

    private Context context = this;
    private Repository repository;
    private Window window;
    private View container;
    private TextView textViewFragmentTitle;
    private ImageView imageViewProfilePic;
    private FirebaseFirestore mFireBaseFireStore;
    private String mCurrentUserId;
    //private boolean newMessages = false;
    private Foreground.Binding listenerBinding;

    private BottomNavigationView.OnNavigationItemSelectedListener navListener =
            new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    Fragment selectedFragment = null;

                    switch (item.getItemId()) {
                        case R.id.navigation_chats:
                            selectedFragment = new ChatsFragment();
                            textViewFragmentTitle.setText("Chats ");
                            break;
                        case R.id.navigation_users:
                            selectedFragment = new UsersFragment();
                            textViewFragmentTitle.setText("Users");
                            break;
                        case R.id.navigation_profile:
                            selectedFragment = new ProfileFragment();
                            textViewFragmentTitle.setText("Profile");
                            break;
                    }
                    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_frame, selectedFragment).commit();
                    return true;
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repository = new Repository(getApplication());

        FirebaseAuth mFirebaseAuth = FirebaseAuth.getInstance();
        mFireBaseFireStore = FirebaseFirestore.getInstance();
        FirebaseUser firebaseUser = mFirebaseAuth.getCurrentUser();
        if (firebaseUser != null) {
            mCurrentUserId = firebaseUser.getUid();
        }

        imageViewProfilePic = findViewById(R.id.image_view_profile_pic);

        window = this.getWindow();
        container = findViewById(R.id.container);

        window.setStatusBarColor(ContextCompat.getColor(context, R.color.PrimaryLight));
        if (container != null) {
            container.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            container.setBackgroundColor(ContextCompat.getColor(context, R.color.PrimaryLight));
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("");
        setSupportActionBar(toolbar);

        textViewFragmentTitle = findViewById(R.id.text_view_fragment_title);
        textViewFragmentTitle.setText("Chats");

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_frame,
                    new ChatsFragment()).commit();
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnNavigationItemSelectedListener(navListener);
        bottomNav.setLabelVisibilityMode(LabelVisibilityMode.LABEL_VISIBILITY_SELECTED);

        profilePicOperations();
        //unreadMessages();
        registerToken();

        listenerBinding = Foreground.get(getApplication()).addListener(this);
    }

    private void profilePicOperations() {

        DocumentReference currentUserRef = mFireBaseFireStore.collection("User").document(mCurrentUserId);
        currentUserRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot snapshot,
                                @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    User user = snapshot.toObject(User.class);
                    if (user.getProfilePicUrl() != null) {
                        Picasso.get().load(user.getProfilePicUrl()).into(imageViewProfilePic);
                        GetData.currentUser(mFireBaseFireStore, mCurrentUserId, repository);
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        listenerBinding.unbind();
    }

    @Override
    public void onBecameForeground() {
        DocumentReference chatPath = mFireBaseFireStore.collection("User").document(mCurrentUserId);
        chatPath.update("onlineOffline", "online");
    }

    @Override
    public void onBecameBackground() {
        DocumentReference chatPath = mFireBaseFireStore.collection("User").document(mCurrentUserId);
        chatPath.update("onlineOffline", "offline");
    }

    private void registerToken() {

        FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
            @Override
            public void onSuccess(InstanceIdResult instanceIdResult) {
                String deviceToken = instanceIdResult.getToken();

                Map<String, Object> userToken = new HashMap<>();
                userToken.put("User_Token_ID", deviceToken);

                DocumentReference userTokenPath = mFireBaseFireStore.collection("User").document(mCurrentUserId).collection("Tokens").document("User_Token");
                userTokenPath.set(userToken);
            }
        });
    }

    /*private void unreadMessages() {

        CollectionReference unreadPath = mFireBaseFireStore.collection("Chats").document(mCurrentUserId).collection("Single");
        unreadListener = unreadPath.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot value,
                                @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    return;
                }

                for (QueryDocumentSnapshot doc : value) {

                    CollectionReference messagesPath = mFireBaseFireStore.collection("Chats").document(mCurrentUserId).collection("Single").document(doc.getId()).collection("Messages");
                    messagesPath.whereEqualTo("isRead", false).addSnapshotListener(new EventListener<QuerySnapshot>() {
                        @Override
                        public void onEvent(@Nullable QuerySnapshot value,
                                            @Nullable FirebaseFirestoreException e) {
                            if (e != null) {
                                return;
                            }

                            for (QueryDocumentSnapshot doc : value) {

                                if (doc.exists()) {
                                    bottomNav.getMenu().findItem(R.id.navigation_chats).setTitle("New");
                                }
                            }
                        }
                    });
                }
            }
        });
    }*/
}
