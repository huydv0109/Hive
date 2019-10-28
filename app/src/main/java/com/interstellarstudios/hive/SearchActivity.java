package com.interstellarstudios.hive;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.interstellarstudios.hive.adapters.RecentSearchesAdapter;
import com.interstellarstudios.hive.adapters.UserAdapter;
import com.interstellarstudios.hive.database.RecentSearchesEntity;
import com.interstellarstudios.hive.database.UserEntity;
import com.interstellarstudios.hive.firestore.GetData;
import com.interstellarstudios.hive.models.User;
import com.interstellarstudios.hive.repository.Repository;
import com.sjl.foreground.Foreground;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity implements Foreground.Listener {

    private Context context = this;
    private Repository repository;
    private Window window;
    private View container;
    private ImageView imageViewProfilePic;
    private FirebaseFirestore mFireBaseFireStore;
    private String mCurrentUserId;
    private AutoCompleteTextView searchField;
    private List<User> mUsers = new ArrayList<>();
    private ArrayList<String> searchSuggestions = new ArrayList<>();
    private RecyclerView recyclerView;
    private Foreground.Binding listenerBinding;
    private RecyclerView mRecentSearchesRecyclerView;
    private List<RecentSearchesEntity> recentSearchesList = new ArrayList<>();
    private ArrayList<String> recentSearchesStringArrayList = new ArrayList<>();
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE);

        repository = new Repository(getApplication());

        FirebaseAuth mFirebaseAuth = FirebaseAuth.getInstance();
        mFireBaseFireStore = FirebaseFirestore.getInstance();
        FirebaseUser firebaseUser = mFirebaseAuth.getCurrentUser();
        if (firebaseUser != null) {
            mCurrentUserId = firebaseUser.getUid();
        }

        ImageView imageViewBack = findViewById(R.id.image_view_back);
        imageViewBack.setVisibility(View.VISIBLE);
        imageViewBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        imageViewProfilePic = findViewById(R.id.image_view_profile_pic);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        mRecentSearchesRecyclerView = findViewById(R.id.recent_searches_recycler);
        mRecentSearchesRecyclerView.setLayoutManager(new LinearLayoutManager(context));
        mRecentSearchesRecyclerView.setNestedScrollingEnabled(false);

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

        TextView textViewFragmentTitle = findViewById(R.id.text_view_fragment_title);
        textViewFragmentTitle.setText("Search Results");

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            String searchTerm = bundle.getString("searchTerm");
            performSearch(searchTerm);

            searchSuggestions = bundle.getStringArrayList("searchSuggestions");
        }

        searchField = findViewById(R.id.searchField);
        searchField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId,
                                          KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    setupSearch();
                    return true;
                }
                return false;
            }
        });

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, searchSuggestions);
        searchField.setAdapter(adapter);

        profilePicOperations();

        listenerBinding = Foreground.get(getApplication()).addListener(this);
    }

    private void setupSearch() {

        repository.deleteAllUsers();

        CollectionReference usersPath = mFireBaseFireStore.collection("User");
        usersPath.get()
                .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<QuerySnapshot> task) {
                        if (task.isSuccessful()) {
                            for (QueryDocumentSnapshot document : task.getResult()) {

                                User user = document.toObject(User.class);

                                if (!user.getId().equals(mCurrentUserId)) {

                                    UserEntity userEntity = new UserEntity(user.getId(), user.getUsername(), user.getProfilePicUrl(), user.getOnlineOffline(), user.getStatus(), user.getEmailAddress());
                                    repository.insert(userEntity);
                                }
                            }
                            String searchTerm = searchField.getText().toString().trim().toLowerCase();

                            recentSearchesList.clear();
                            recentSearchesStringArrayList.clear();

                            recentSearchesList = repository.getRecentSearches();

                            for (RecentSearchesEntity recentSearches : recentSearchesList) {
                                String recentSearchesListString = recentSearches.getSearchTerm();
                                recentSearchesStringArrayList.add(recentSearchesListString);
                            }

                            if (!recentSearchesStringArrayList.contains(searchTerm) && !searchTerm.equals("")) {
                                mRecentSearchesRecyclerView.setVisibility(View.VISIBLE);
                                long timeStamp = System.currentTimeMillis();
                                RecentSearchesEntity recentSearches = new RecentSearchesEntity(timeStamp, searchTerm);
                                repository.insert(recentSearches);

                            } else if (recentSearchesStringArrayList.contains(searchTerm)) {
                                long timeStampQuery = repository.getTimeStamp(searchTerm);
                                RecentSearchesEntity recentSearchesOld = new RecentSearchesEntity(timeStampQuery, searchTerm);
                                repository.delete(recentSearchesOld);

                                long timeStamp = System.currentTimeMillis();
                                RecentSearchesEntity recentSearchesNew = new RecentSearchesEntity(timeStamp, searchTerm);
                                repository.insert(recentSearchesNew);
                            }

                            performSearch(searchTerm);
                        }
                    }
                });
    }

    private void performSearch(String searchTerm) {

        List<UserEntity> userEntityList = repository.searchAllUsers(searchTerm);

        mUsers.clear();

        for (UserEntity userEntity : userEntityList) {

            User user = new User(userEntity.getId(), userEntity.getUsername(), userEntity.getProfilePicUrl(), userEntity.getOnlineOffline(), userEntity.getStatus(), userEntity.getEmailAddress());
            mUsers.add(user);
        }

        UserAdapter userAdapter = new UserAdapter(context, mUsers, true);
        recyclerView.setAdapter(userAdapter);

        recentSearchesList = repository.getRecentSearches();

        if (recentSearchesList.isEmpty()) {
            mRecentSearchesRecyclerView.setVisibility(View.GONE);
        }

        mRecentSearchesRecyclerView.setAdapter(new RecentSearchesAdapter(recentSearchesList, sharedPreferences, context, new RecentSearchesAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(RecentSearchesEntity item) {
                searchField.setText(item.getSearchTerm());
                String searchTerm = searchField.getText().toString().trim().toLowerCase();
                performSearch(searchTerm);
            }
        }));
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(R.anim.push_right_in, R.anim.push_right_out);
    }
}
