package com.sparrowplatform.sparrow;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    DatabaseReference database;

    private static final int RC_SIGN_IN = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        database = FirebaseDatabase.getInstance().getReference();

        File backup = new File("/data/data/com.sparrowplatform.sparrow/files/docs");
        try {
            if (!backup.getParentFile().exists())
                backup.getParentFile().mkdirs();
            backup.createNewFile();
            Log.i("LOCAL BACKUP", "SUCCESS");
        } catch (IOException e) {
            e.printStackTrace();
            Log.i("LOCAL BACKUP", "FAILED");
        }

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if(fbUser != null) {
            // User already signed in

            // get the FCM token
            String token = FirebaseInstanceId.getInstance().getToken();

            // save the user info in the database to users/UID/
            // we'll use the UID as part of the path
            User user = new User(fbUser.getUid(), fbUser.getDisplayName(), token);
            database.child("users").child(user.uid).setValue(user);

            // go to feed activity
            Intent intent = new Intent(this, Home.class);
            startActivity(intent);

            finish();
        }

        else{
            startActivityForResult(
                    // Get an instance of AuthUI based on the default app
                    AuthUI.getInstance().createSignInIntentBuilder().build(),
                    RC_SIGN_IN);

        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            IdpResponse response = IdpResponse.fromResultIntent(data);

            if (resultCode == RESULT_OK) {
                // Successfully signed in

                // get the Firebase user
                FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();

                // get the FCM token
                String token = FirebaseInstanceId.getInstance().getToken();

                // save the user info in the database to users/UID/
                // we'll use the UID as part of the path
                User user = new User(fbUser.getUid(), fbUser.getDisplayName(), token);
                database.child("users").child(user.uid).setValue(user);

                // go to feed activity
                Intent intent = new Intent(this, Home.class);
                startActivity(intent);
                finish();
            } else {
                // Sign in failed, check response for error code
                if (response != null) {
                    Toast.makeText(this, response.getError().getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
