package com.example.chatapp.Activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.chatapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Objects;

public class MyId extends AppCompatActivity {
    FirebaseAuth mAuth;
    TextView textView ;
    TextView txtv;
    DatabaseReference mDbRef;
    String ownId;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_id);
//        getSupportActionBar().setTitle("MY ID");

        textView =findViewById(R.id.Txt_urid);
        txtv = findViewById(R.id.Txttest);

        mAuth = FirebaseAuth.getInstance();
        ownId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

        mDbRef = FirebaseDatabase.getInstance().getReference();
        mDbRef.child("IdToUid").child(ownId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                textView.setText(snapshot.getValue(String.class));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });


    }
}