package com.sparrowplatform.sparrow;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class FeedActivity extends AppCompatActivity {

    String title = "";
    FirebaseUser fbUser;
    DatabaseReference database;
    RecyclerView recyclerView;
    RecyclerView.LayoutManager mLayoutManager;
    ImageAdapter mAdapter;
    ArrayList<Image> images = new ArrayList<>();

    Uri downloadUrl = null;

    String imageKey;

    static final int RC_PERMISSION_READ_EXTERNAL_STORAGE = 1;
    static final int RC_IMAGE_GALLERY = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);

        fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) {
            finish();
        }

        database = FirebaseDatabase.getInstance().getReference();

        // Setup the RecyclerView
        recyclerView = findViewById(R.id.recyclerView);
        mLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new ImageAdapter(images, this);
        recyclerView.setAdapter(mAdapter);


        // Get the latest 100 images
        Query imagesQuery = database.child("images").orderByKey().limitToFirst(100);

        imagesQuery.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {

                // A new image has been added, add it to the displayed list
                final Image image = dataSnapshot.getValue(Image.class);

                // get the image user
                database.child("users/" + image.userId).addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        User user = dataSnapshot.getValue(User.class);
                        image.user = user;
                        mAdapter.notifyDataSetChanged();

                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {

                    }
                });



                mAdapter.addImage(image);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    public void uploadImage(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, RC_PERMISSION_READ_EXTERNAL_STORAGE);
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(intent, RC_IMAGE_GALLERY);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == RC_PERMISSION_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, RC_IMAGE_GALLERY);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_IMAGE_GALLERY && resultCode == RESULT_OK) {
            Uri uri = data.getData();

            StorageReference storageRef = FirebaseStorage.getInstance().getReference();
            StorageReference imagesRef = storageRef.child("images");
            final StorageReference userRef = imagesRef.child(fbUser.getUid());
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String filename = fbUser.getUid() + "_" + timeStamp;
            StorageReference fileRef = userRef.child(filename);




            UploadTask uploadTask = fileRef.putFile(uri);



            uploadTask.continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) throws Exception {
                    if(!task.isSuccessful()){
                        throw task.getException();
                    }
                    return userRef.getDownloadUrl();
                }
            }).addOnCompleteListener(new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                    if (task.isSuccessful()){
                        final Uri downloadUrl = task.getResult();
                        Log.i("The URL : ", downloadUrl.toString());

                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(FeedActivity.this);
                        alertDialog.setTitle("Title");
                        alertDialog.setMessage("Enter Title for document");
                        alertDialog.setCancelable(false);

                        final EditText input = new EditText(FeedActivity.this);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.MATCH_PARENT);
                        input.setLayoutParams(lp);
                        alertDialog.setView(input);


                        alertDialog.setPositiveButton("Done",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        title=input.getText().toString();
                                        imageKey = database.child("images").push().getKey();
                                        Image image = new Image(imageKey, fbUser.getUid(), downloadUrl.toString(), title, "");
                                        database.child("images").child(imageKey).setValue(image);

                                        RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());

                                        String URL = "";

                                        StringRequest postRequest = new StringRequest(Request.Method.POST, URL,
                                                new Response.Listener<String>()
                                                {
                                                    @Override
                                                    public void onResponse(String response) {
                                                        Log.d("Response", response);
                                                    }
                                                },
                                                new Response.ErrorListener()
                                                {
                                                    public void onErrorResponse(VolleyError error) {
                                                    }
                                                }
                                        ) {
                                            @Override
                                            protected Map<String, String> getParams()
                                            {
                                                Map<String, String> params = new HashMap<>();
                                                params.put("key",imageKey );
                                                params.put("url",downloadUrl.toString());

                                                return params;
                                            }
                                        };
                                        requestQueue.add(postRequest);

                                    }
                                });

                        alertDialog.show();

                    }
                }
            });

//
//
//            uploadTask.addOnFailureListener(new OnFailureListener() {
//                @Override
//                public void onFailure(@NonNull Exception exception) {
//                    // Handle unsuccessful uploads
//                    Toast.makeText(FeedActivity.this, "Upload failed!\n" + exception.getMessage(), Toast.LENGTH_LONG).show();
//                }
//            }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
//                @Override
//                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
////                    downloadUrl = taskSnapshot.getDownloadUrl();
//                    Toast.makeText(FeedActivity.this,
//                            "Upload finished!", Toast.LENGTH_SHORT).show();
//                    Uri downloadUri = taskSnapshot.getResult();
//                    Log.e("File name", downloadUrl.toString() );
//
//
//                    AlertDialog.Builder alertDialog = new AlertDialog.Builder(FeedActivity.this);
//                    alertDialog.setTitle("Title");
//                    alertDialog.setMessage("Enter Title for document");
//                    alertDialog.setCancelable(false);
//
//                    final EditText input = new EditText(FeedActivity.this);
//                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
//                            LinearLayout.LayoutParams.MATCH_PARENT,
//                            LinearLayout.LayoutParams.MATCH_PARENT);
//                    input.setLayoutParams(lp);
//                    alertDialog.setView(input);
//
//
//                    alertDialog.setPositiveButton("Done",
//                            new DialogInterface.OnClickListener() {
//                                public void onClick(DialogInterface dialog, int which) {
//                                    title=input.getText().toString();
//                                    imageKey = database.child("images").push().getKey();
//                                    Image image = new Image(imageKey, fbUser.getUid(), downloadUrl.toString(), title, "");
//                                    database.child("images").child(imageKey).setValue(image);
//
//                                    RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
//
//                                    String URL = "";
//
//                                    StringRequest postRequest = new StringRequest(Request.Method.POST, URL,
//                                            new Response.Listener<String>()
//                                            {
//                                                @Override
//                                                public void onResponse(String response) {
//                                                    Log.d("Response", response);
//                                                }
//                                            },
//                                            new Response.ErrorListener()
//                                            {
//                                                public void onErrorResponse(VolleyError error) {
//                                                }
//                                            }
//                                    ) {
//                                        @Override
//                                        protected Map<String, String> getParams()
//                                        {
//                                            Map<String, String> params = new HashMap<>();
//                                            params.put("key",imageKey );
//                                            params.put("url",downloadUrl.toString());
//
//                                            return params;
//                                        }
//                                    };
//                                    requestQueue.add(postRequest);
//
//                                }
//                            });
//
//                    alertDialog.show();
//
//                }
//            });
        }
    }

}
