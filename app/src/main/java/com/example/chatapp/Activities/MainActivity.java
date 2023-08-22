package com.example.chatapp.Activities;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.chatapp.ImageCompression.ImageCompress;
import com.example.chatapp.ImageCompression.ImageCompressionCompletionListener;
import com.example.chatapp.ProfilePicture.ProfilePictureOperations;
import com.example.chatapp.R;
import com.example.chatapp.RSAEncryption.MyKeyPair;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements ImageCompressionCompletionListener ,ProfilePictureOperations.picFetchSuccessListener{
    private static final String TAG2 = "MainActivity";
    private static final String TAG = "ProfilePictureOperation";

    public  RecyclerView RwView;
    private UserAdapter userAdapter;
    private ArrayList<AppUser> userList;
    private DatabaseReference mDbRef;
    private FirebaseAuth mAuth;
    private StorageReference mStoreRef;
    public  StorageTask mStoragetask;  //USED TO KEEP CHECK ON A UPLOADING TASK. CHECKS IF AN UPLOADING TASK IS COMPLETE OR NOT

    private String MAIN_KEY="main key";
    private ProgressBar pgbr;
    private ImageView profilePic;
    public ProfilePictureOperations ppo;
    private Toolbar toolbar;
    public Uri profileImageUri;
    private String encodedImage;
    public Handler mainHandler= new Handler();
    public int onlyOnce=1;// MAKING ON_CHILD_LISTENER SKIP THE FIRST CALL
    int count=0; //EACH FRIEND'S POSITION IN THE LIST +1  (EACH FRIEND'S PLACE IN THE LIST LIKE 1ST , 2ND ... ETC ).
    boolean frstCllBck=true;  //FIRST CALLBACK FROM ADD_VALUE_LISTENER
    int listSize=0;//LIST SIZE FOR ON_DATA_CHANGE LISTENER
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        toolbar=findViewById(R.id.actionbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);
        //Changing status bar color
        Window window = this.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.setStatusBarColor(this.getResources().getColor(R.color.purple_200));
        pgbr = findViewById(R.id.pgbr_m);
        profilePic=findViewById(R.id.profilePic);
        RwView = findViewById(R.id.RwView);
        userList = new ArrayList<AppUser>();
//        Log.d("bug","got_response1");
        RwView.setHasFixedSize(true);
        RwView.setLayoutManager(new LinearLayoutManager(this));
        userAdapter = new UserAdapter(this);
        ppo=new ProfilePictureOperations(mainHandler,userAdapter,this,this);
        ppo.fetchAvailablePics();
        ppo.manageCurrUserPic();
//        Log.d("bug","got_response12");
        RwView.setAdapter(userAdapter);

        //***************** ACCESSING DATABASE *********************
        mAuth = FirebaseAuth.getInstance();
        mDbRef= FirebaseDatabase.getInstance().getReference();
        mStoreRef= FirebaseStorage.getInstance().getReference("uploads");

        //GETTING THE KEYPAIR (PUBLIC_KEY , PRIVATE_KEY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("tag","key initial");
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MyKeyPair.initializeKeyPair();
                    mDbRef.child("PublicKeys").child(Objects.requireNonNull(mAuth.getCurrentUser()).getUid()).setValue(MyKeyPair.StrPublickey);
                }
            }).start();
        }

        frstCllBck=true;
        listSize=0;
        //FRIENDS ----> CURRENT USER_ID ----> {LIST OF FRIENDS}
        //IN THIS WE THE LISTENER WILL REACT ONLY WHEN FIRST TIME CALLED OR WHEN THERE IS REMOVAL OF CHILD
        //THIS WILL NOT REACT TO ANY CHILD ADDITION
        mDbRef.child("Friends").child(Objects.requireNonNull(mAuth.getCurrentUser()).getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()) {
                    if (snapshot.getChildrenCount() <= listSize || frstCllBck) {
                        pgbr.setVisibility(View.VISIBLE);
                        ProfilePictureOperations.EchFrndCall.clear();
                        userList.clear();
                        count = 0;
//                  Toast.makeText(MainActivity.this, "EchFrndCall "+ProfilePictureOperations.EchFrndCall.size(), Toast.LENGTH_SHORT).show();

                        long total = snapshot.getChildrenCount();
                        Log.d("chkX", "onDataChange: data-----------for loop -------");
                        for (DataSnapshot datasnapshot : snapshot.getChildren()) {//EACH FRIEND'S SNAPSHOT
                            AppUser apu = datasnapshot.getValue(AppUser.class); //EACH FRIEND'S OBJECT
                            apu.NodeKeyForDeletion = datasnapshot.getKey(); //INSERTING THE NODE KEY OF THE FRIEND FOR DELETION PURPOSE

                            ProfilePictureOperations.EchFrndCall.add(0);
                            ppo.getFriendsPic(apu.getUid(), ++count, total);
                            //CHECKING IF THE DATA MATCHES THE CURRENT USER ID
                            if (!(apu.getUid().equals(mAuth.getCurrentUser().getUid()))) {
                                Log.d("chkX", "new user added ------------------COUNT "+count);
                                userList.add(apu);
                            }
                        }
                        Log.d("chkX", "submitting to the adapter ---------------------------------");
                        userAdapter.submitList(new ArrayList<>(userList));
                        listSize=userList.size();
                        frstCllBck=false;
                        pgbr.setVisibility(View.INVISIBLE);
                    }
                    else { //THIS  elseIf EXECUTES WHEN NEW CHILD IS ADDED
                        listSize++;
                    }
                }
                else
                {
                    userList.clear();
                    userAdapter.submitList(new ArrayList<>(userList));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        //UPON REMOVAL OF ANY ITE THE ON_CHILD_LISTENER WIL NOT ALTER THE USER_LIST OR PERFORM ANY KIND OF OPERATION
        //ONLY REACT TO CHILD ADDITION . IN CASE NO CHILD IS LEFT EVEN THEN IT WILL DO NOTHING
        mDbRef.child("Friends").child(mAuth.getCurrentUser().getUid()).limitToLast(1).addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                Toast.makeText(MainActivity.this,"onlyOnce value-->"+onlyOnce,Toast.LENGTH_SHORT).show();
                if(onlyOnce==1)
                {
                    onlyOnce++;
                    return;
                }
                if(snapshot.exists()) {
                    //NEW FRIEND'S SNAPSHOT
                    Log.d("chkX", "ON_CHILD_ADD --- getting AppUser Object [apu] ----");
                    AppUser apu = snapshot.getValue(AppUser.class); //NEW FRIEND'S OBJECT
//                        if(apu==null){
//                            return;
//                        }
                    //FOR CHECKING REMOVED CHILD-----------------------------------------
                    if(userList.size()>1 && apu.getUid().equals(userList.get(userList.size()-2))||apu.getUid().equals(userList.get(userList.size()-1)))
                    {
                        Log.d("chkX", "ON_CHILD_ADD --- removal of ele ( RETURNING ) ----");
                        return;
                    }
                    listSize=userList.size();
                    apu.NodeKeyForDeletion=snapshot.getKey(); //INSERTING THE NODE KEY OF THE FRIEND FOR DELETION PURPOSE

                    ProfilePictureOperations.EchFrndCall.add(1);
                    ppo.getFriendsPic(apu.getUid(),++count,userList.size()+1);

                    //CHECKING IF THE DATA MATCHES THE CURRENT USER ID
                    if(!(apu.getUid().equals(mAuth.getCurrentUser().getUid())))
                    {                     Log.d("chkX", "ON_CHILD_ADD ---- new child added ------child name->"+apu.getName()+"   list2nd last"+userList.get(userList.size()-2).getName());
                        userList.add(apu);
                    }
                    Log.d("chkX", "ON_CHILD_ADD submitting to the adapter from onChild---------------------------------");
                    userAdapter.submitList(new ArrayList<>(userList));
                }
//                else{
//                    userList.clear();
//                    userAdapter.submitList(new ArrayList<>(userList));
//                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
//                Log.d("chk", "on CHILD REMOVED---------------------------------");
//                AppUser apu = snapshot.getValue(AppUser.class);
//                Log.d("chkX", "on CHILD REMOVED----------------child name--->"+apu.getName());
//                Log.d("chkX", "on CHILD REMOVED----------------list size --->"+userList.size());
////                userList.remove(apu);
////                userAdapter.submitList(new ArrayList<>(userList));
//                Log.d("chkX", "on CHILD REMOVED--after removal-list size --->"+userList.size());
//
//                Log.d("chkX", "on CHILD REMOVED----------------submitted to adapter");
            }
            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
        UpdateToken();
        //*******************************************************************
//        pgbr.setVisibility(View.INVISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId()==R.id.logout) {
            mAuth.signOut();
            SharedPreferences shpf = getSharedPreferences(MAIN_KEY, MODE_PRIVATE);
            SharedPreferences.Editor editor = shpf.edit();
            editor.putBoolean("loggeddk", false);
            editor.apply();

            Intent intent = new Intent(MainActivity.this, SignInActivity.class);
            finish();
            startActivity(intent);

        }
        else if(item.getItemId()==R.id.mypic)
        {
//           Toast.makeText(this,"menu pic",Toast.LENGTH_SHORT).show();
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(intent,10);
        }
        else if(item.getItemId()==R.id.addUser)
        {
            //Add user activity
            Intent intent = new Intent(MainActivity.this, AddUser.class);
            startActivity(intent);
        }
        else
        {
            Intent intent = new Intent(MainActivity.this, MyId.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    //UPDATING THE USERS TOKEN AS IT FREQUENTLY GETS RENEWED BY THE SYSTEM
    public static String tkn="0";
    public void UpdateToken(){
        Log.d("tag",tkn);
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {

                        if (!task.isSuccessful()) {
                            Log.d("bug", "Fetching FCM registration token failed");
                            return;
                        }
                        // Get new FCM registration token
                        tkn = task.getResult();
                        mDbRef.child("Token").child(Objects.requireNonNull(mAuth.getCurrentUser()).getUid()).setValue(tkn);
                        // Toast.makeText(MainActivity.this, "messaging yey"+task.getResult(), Toast.LENGTH_SHORT).show();
                    }
                });

        mDbRef.child("User").child(Objects.requireNonNull(mAuth.getCurrentUser()).getUid()).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                UserDetails.uname=snapshot.getValue(String.class);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==10 && resultCode==RESULT_OK && data!=null && data.getData()!=null)
        {
//            Toast.makeText(this,"got result",Toast.LENGTH_SHORT).show();
            profileImageUri=data.getData();
            try {
                new ImageCompress().getCompressedImage(this,getContentResolver().openInputStream(profileImageUri),this);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void uploadOnCompression() {
        profileImageUri=Uri.fromFile(new File("/storage/emulated/0/Android/data/com.codezilla.chatapp/files/compressed/newcompimage.jpg"));
//        Bitmap bm=BitmapFactory.decodeFile("/storage/emulated/0/Android/data/com.codezilla.chatapp/files/compressed/newcompimage.jpg");
//        String res= StringToBitmapViseVersa.BitmapToString(bm);
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
//                uploadProfilePic(profileImageUri);
                ppo.uploadProfilePic(profileImageUri);

            }
        });
    }
    @Override
    public void getProgress(int p) {}

    public void uploadProfilePic(Uri ImageUri)
    {
        ppo.uploadProfilePic(ImageUri);
    }

    public void picUploadResult(int i,Bitmap bm){
        if(i==1){
            profilePic.setImageBitmap(bm);
            Toast.makeText(MainActivity.this, "PIC Updated", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(MainActivity.this, "Failed to Update", Toast.LENGTH_LONG).show();

    }
//    //GET CURRENT USER PROFILE PIC
//    public void CurrProfilePic(String cUid)
//    {
//
//    }

    @Override
    public void fetchSuccessful(Bitmap bm) {
        Log.d("TAGX","point fetchSuccessful   Thread id --> "+Thread.currentThread().getId());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d("TAGX","point handler   Thread id --> "+Thread.currentThread().getId());
                profilePic.setImageBitmap(bm);
            }
        });
    }
    public void updateAdapter(int res) //UPDATE ADAPTER AFTER FETCHING THE PICS OF FRIENDS
    {
//        if(res==0) {
//            userAdapter.submitList(new ArrayList<>(userList));
//        }else{
//            Toast.makeText(MainActivity.this, "got new pic", Toast.LENGTH_LONG).show();
        userAdapter.notifyItemChanged(res);
//        }
    }
//    UPLOADING PROFILE PICTURE
//    public void uploadProfilePic(String res)
//    {
////        Toast.makeText(MainActivity.this, "uploading pic", Toast.LENGTH_SHORT).show();
//        Log.d(TAG,"In updloading");
//        mDbRef.child("ProfilePic").child(mAuth.getCurrentUser().getUid()).setValue(res).addOnSuccessListener(new OnSuccessListener<Void>() {
//            @Override
//            public void onSuccess(Void unused) {
//                Log.d(TAG,"Profile Updated Successfully ");
////                Toast.makeText(MainActivity.this, "Added", Toast.LENGTH_SHORT).show();
//                mDbRef.child("ProfilePic").child(mAuth.getCurrentUser().getUid()).addValueEventListener(new ValueEventListener() {
//                    @Override
//                    public void onDataChange(@NonNull DataSnapshot snapshot) {
////                        Log.d(TAG,"on value event");
////                        Log.d(TAG,"res->"+snapshot.getValue(String.class));
//                          String s=snapshot.getValue(String.class);
//                          Bitmap bm= StringToBitmapViseVersa.StringToBitmap(s);
//                          profilePic.setImageBitmap(bm);
//                    }
//
//                    @Override
//                    public void onCancelled(@NonNull DatabaseError error) {
//                    }
//                });
//            }
//        });
//    }
}