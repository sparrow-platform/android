package com.sparrowplatform.sparrow;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.CallSuper;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Home extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener{

    Intent mServiceIntent;
    private TextView messages;
    private Sparrow sparrowService;
    private String TAG = "SparrowLog";
    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;
    private static final String[] REQUIRED_PERMISSIONS =
            new String[] {
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.READ_PHONE_STATE
            };

    private EditText messageET, edt;
    private ListView messagesContainer;
    private ImageView sendBtn;
    private ChatAdapter adapter;
    private ArrayList<ChatMessage> chatHistory;
    private DatabaseHandler db;
    protected static final int RESULT_SPEECH = 1;
    private int account_flag = 0;
    public String year, month;

    public String username;


    final String serverUri = "tcp://test.mosquitto.org:1883";

    boolean mBounded;
    Sparrow sparrowServiceRunning;

    private BottomNavigationView.OnNavigationItemSelectedListener mOnNavigationItemSelectedListener
            = item -> {
        switch (item.getItemId()) {
            case R.id.home:
                return true;
            case R.id.records:
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
                return true;
            case R.id.notifications:
                //                    Intent intent2 = new Intent(getApplicationContext(), Home.class);
                //                    startActivity(intent2);
                //                    finish();
                return true;
        }
        return false;
    };





    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            Log.i(TAG, "Adding message from MQTT to chat");
            String message = intent.getStringExtra("message");
            String type = intent.getStringExtra("type");

            if (type=="mesh"){
                messages.append(message+"\n");
                Log.d("receiver", "Got message: " + message);
            }

            if (type=="mqtt"){
                DisplayContent(message);
            }

        }
    };





    @Override
    protected void onStart() {
        super.onStart();
        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
            }
            else{
                ActivityCompat.requestPermissions(this,REQUIRED_PERMISSIONS,REQUEST_CODE_REQUIRED_PERMISSIONS);
            }
        }


        sparrowService = new Sparrow();
        mServiceIntent = new Intent(this, sparrowService.getClass());
        if (!isMyServiceRunning(sparrowService.getClass())) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                startForegroundService(new Intent(this, Sparrow.class));
            } else {
                startService(new Intent(this, Sparrow.class));
            }
        }

        Intent mIntent = new Intent(this, Sparrow.class);
        bindService(mIntent, mConnection, BIND_AUTO_CREATE);


    }

    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {

            mBounded = false;
            sparrowServiceRunning = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            mBounded = true;
            Sparrow.LocalBinder mLocalBinder = (Sparrow.LocalBinder)service;
            sparrowServiceRunning = mLocalBinder.getServerInstance();
        }
    };






    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_home);

        messages = findViewById(R.id.messages);
        messages.setMovementMethod(new ScrollingMovementMethod());
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("payload-received"));


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);


        BottomNavigationView navView = findViewById(R.id.bottomNav);
        navView.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        navView.setSelectedItemId(R.id.home);

        Date d = new Date( );
        SimpleDateFormat mft = new SimpleDateFormat("MM");
        SimpleDateFormat yft = new SimpleDateFormat("yyyy");
        month = mft.format(d);
        year = yft.format(d);

        messageET = (EditText) findViewById(R.id.messageEdit);
        messagesContainer = (ListView) findViewById(R.id.messagesContainer);

        adapter = new ChatAdapter(Home.this, new ArrayList<ChatMessage>());
        messagesContainer.setAdapter(adapter);
        db = new DatabaseHandler(this);

        final String PREFS_NAME = "sparrowPreferences";
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, 0);
        username = preferences.getString("name","null");

        loadHistory();
        initMsgSendHandler();


        scroll();

    }


    private void initMsgSendHandler() {

        if(username == "null") {
            DisplayContentTemp("Hi, I am here to help you!");
            DisplayContentTemp("Before I connect you to internet, I need to know your name. What should I call you?");
        }

        sendBtn = (FloatingActionButton) findViewById(R.id.sendButton);
        sendBtn.setOnClickListener(v -> {
            messageET = (EditText) findViewById(R.id.messageEdit);
            if(username == "null"){
                String messageText = messageET.getText().toString();
                if (TextUtils.isEmpty(messageText)) {
                    Toast.makeText(getApplicationContext(), "Please enter username", Toast.LENGTH_SHORT).show();
                }
                else {
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.setId(122);//dummy
                    chatMessage.setMessage(messageText);
                    chatMessage.setDate(DateFormat.getDateTimeInstance().format(new Date()));
                    chatMessage.setTag(1);

                    messageET.setText("");
                    Log.d("Insert: ", "Inserting ..");
                    db.addtodatabase(chatMessage);
                    displayMessage(chatMessage);

                    final String PREFS_NAME = "sparrowPreferences";
                    final SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                    username =  messageText.replaceAll("\\s","") + getUniqueID();
                    settings.edit().putString("name", username).commit();


                    initMQTT("sparrow:" + username, true);
                    DisplayContent("Hey there, your username is " + username + ". I am now connecting you to my online counterpart!");

                }
            }
            else{

                String messageText = messageET.getText().toString();
                if (TextUtils.isEmpty(messageText)) {
                    return;
                }
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setId(122);//dummy
                chatMessage.setMessage(messageText);
                chatMessage.setDate(DateFormat.getDateTimeInstance().format(new Date()));
                chatMessage.setTag(1);

                messageET.setText("");
                Log.d("Insert: ", "Inserting ..");
                db.addtodatabase(chatMessage);
                displayMessage(chatMessage);


                final String PREFS_NAME = "sparrowPreferences";
                SharedPreferences preferences = getSharedPreferences(PREFS_NAME, 0);
                username = preferences.getString("name","null");

                publishMessage(messageText);
            }
        });



    }

    public void displayMessage(ChatMessage message) {
        adapter.add(message);
        adapter.notifyDataSetChanged();
        scroll();
    }


    public void DisplayContent(String message1)
    {
        ChatMessage m = new ChatMessage();
        m.setMessage(message1);
        m.setDate(DateFormat.getDateTimeInstance().format(new Date()));
        m.setTag(0);
        db.addtodatabase(m);
        displayMessage(m);
        scroll();
        scroll();
    }

    public void DisplayContentTemp(String message1)
    {
        ChatMessage m = new ChatMessage();
        m.setMessage(message1);
        m.setDate(DateFormat.getDateTimeInstance().format(new Date()));
        m.setTag(0);
        displayMessage(m);
        scroll();
        scroll();
    }

    private void scroll() {
        messagesContainer.setSelection(messagesContainer.getCount() - 1);
    }

    private void loadHistory(){

        adapter = new ChatAdapter(Home.this, new ArrayList<ChatMessage>());
        messagesContainer.setAdapter(adapter);

        Log.d("Reading: ", "Reading all contacts..");
        List<ChatMessage> cm = db.getAllMessages();


        for(int i=0; i<cm.size(); i++) {
            ChatMessage message = cm.get(i);
            displayMessage(message);
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        BottomNavigationView navView = findViewById(R.id.bottomNav);
        navView.setOnNavigationItemSelectedListener(mOnNavigationItemSelectedListener);
        navView.setSelectedItemId(R.id.home);

        scroll();
    }


    private boolean isMyServiceRunning(Class<?> serviceClass) {
        Log.i(TAG,serviceClass.getName());
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.i (TAG, "Service is running.");
                return true;
            }
        }
        Log.i (TAG, "Service is not running.");
        return false;
    }

    /** Returns true if the app was granted all the permissions. Otherwise, returns false. */
    private static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.records, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_tools) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onDestroy();
    }


    @Override
    protected void onStop() {
        super.onStop();
        if(mBounded) {
            unbindService(mConnection);
            mBounded = false;
        }
    };


    /** Handles user acceptance (or denial) of our permission request. */
    @CallSuper
    @Override
    public void onRequestPermissionsResult
    (
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CODE_REQUIRED_PERMISSIONS) {
            Intent broadcastIntent = new Intent(this, SparrowBroadcastReceiver.class);
            broadcastIntent.putExtra("action","restart");
            sendBroadcast(broadcastIntent);
            return;
        }

        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Error missing permissions", Toast.LENGTH_LONG).show();
//                finish();
                return;
            }
        }
        recreate();
    }

    public String getUniqueID() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.i(TAG, "Unique ID is " + androidId);
        return androidId.substring(6);
    }




    public void initMQTT(String id, Boolean changed)  {
        sparrowServiceRunning.initMQTT(id, changed);
    }



    public void publishMessage(String msg){
        sparrowServiceRunning.publishMessage(msg);
    }


}

