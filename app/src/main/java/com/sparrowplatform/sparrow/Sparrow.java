package com.sparrowplatform.sparrow;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import static okhttp3.internal.Util.UTF_8;


public class Sparrow extends Service {


    private Context context;
    private String  TAG_SPARROW_WIFI_SERVICE = "SPARROW WIFI SERVICE";
    WifiManager mWifiManager;
    String deviceName;
    WifiManager.WifiLock mWifiLock = null;
    ConnectionsClient connectionsClient;


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        deviceName = "Sparrow" + getUniqueID();
        startMyOwnForeground();
    }

    public Sparrow() {
        super();
    }


    @Override
    public void onDestroy() {
        stoptimertask();
        super.onDestroy();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        connectionsClient = Nearby.getConnectionsClient(getBaseContext());

        startTimer();
        return START_STICKY;
    }



/********************************TIMER********************/

    private Timer timer;
    private TimerTask timerTask;

    public void startTimer() {
        timer = new Timer();
        initializeTimerTask();
        timer.schedule(timerTask, 1000, 60000); //
    }

    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                //Do something here
                startSparrowService();
            }
        };
    }

    public void stoptimertask() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        resetSparrowConnections();

    }


/********************************TIMER********************/




/*********************SPARROW**********************/

    private void resetSparrowConnections() {

        connectionsClient.stopAllEndpoints();
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
    }

    private void startSparrowService() {
        Log.i("SPARROW MESH", "Sparrow connection refresh");
        resetSparrowConnections();
        initiateWifi();
        startAdvertising();
        startDiscovery();
    }

    private void initiateWifi() {
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(mWifiManager.isWifiEnabled()==false)
        {
            mWifiManager.setWifiEnabled(true);
        }
        if( mWifiLock == null )
            mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG_SPARROW_WIFI_SERVICE);
        mWifiLock.setReferenceCounted(false);
        if( !mWifiLock.isHeld() )
            mWifiLock.acquire();
    }


    private void startDiscovery() {
        // Note: Discovery may fail. To keep this demo simple, we don't handle failures.
        Log.i("SPARROW MESH", "Starting discovery");
        connectionsClient.startDiscovery(
                getPackageName(), mEndpointDiscoveryCallback,
                new DiscoveryOptions(Strategy.P2P_CLUSTER));
    }


    private void startAdvertising() {
        // Note: Advertising may fail. To keep this demo simple, we don't handle failures.
        Log.i("SPARROW MESH", "Starting advertisement");
        connectionsClient.startAdvertising(
                deviceName, getPackageName(), connectionLifecycleCallback,
                new AdvertisingOptions(Strategy.P2P_CLUSTER));
    }


    private final ConnectionLifecycleCallback connectionLifecycleCallback =
        new ConnectionLifecycleCallback() {
            @Override
            public void onConnectionInitiated(String endpointId, ConnectionInfo connectionInfo) {
                // Automatically accept the connection on both sides.
                Log.i("SPARROW MESH", "Initiated connection with "+endpointId);
                connectionsClient.acceptConnection(endpointId, mPayloadCallback);
            }

            @Override
            public void onConnectionResult(String endpointId, ConnectionResolution result) {
                if (result.getStatus().isSuccess()){
                    Log.i("SPARROW MESH", "Established connection with "+endpointId);
                    try {
                        sendMessegeToActivity("Got message from" + endpointId);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(deviceName.getBytes(UTF_8)));
                }

                switch (result.getStatus().getStatusCode()) {
                    case ConnectionsStatusCodes.STATUS_OK:
                        // We're connected! Can now start sending and receiving data.
                        break;
                    case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                        // The connection was rejected by one or both sides.
                        break;
                    default:
                        // The connection was broken before it was accepted.
                        break;
                }
            }

            @Override
            public void onDisconnected(String endpointId) {
                // We've been disconnected from this endpoint. No more data can be
                // sent or received.
                Log.i("SPARROW MESH", "Ended connection with "+endpointId);
            }
        };



    private final EndpointDiscoveryCallback mEndpointDiscoveryCallback =
        new EndpointDiscoveryCallback() {
            @Override
            public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                Log.i("SPARROW MESH", "Found a new endpoint "+endpointId);
                connectionsClient.requestConnection(deviceName, endpointId, connectionLifecycleCallback);
            }
            @Override
            public void onEndpointLost(String endpointId) {
                Log.i("SPARROW MESH", "Lost an endpoint "+endpointId);}
        };



    private final PayloadCallback mPayloadCallback =
        new PayloadCallback() {
            @Override
            public void onPayloadReceived(String endpointId, Payload payload) {
                // A new payload is being sent over.
                String message = new String(payload.asBytes(), UTF_8);
                Log.i("SPARROW MESH", "Payload received from "+endpointId );
                Log.i("SPARROW MESH", "Payload content : " + message);
                try {
                    sendMessegeToActivity("Received message from" + endpointId + "/n" + message);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            @Override
            public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                // Payload progress has updated.
                Log.i("SPARROW MESH", "Payload transfer to "+endpointId + " " + update.toString());
            }
        };

/*********************SPARROW**********************/


    private static final String ALLOWED_CHARACTERS ="0123456789qwertyuiopasdfghjklzxcvbnm";

    private static String getRandomString(final int sizeOfRandomString)
    {
        final Random random=new Random();
        final StringBuilder sb=new StringBuilder(sizeOfRandomString);
        for(int i=0;i<sizeOfRandomString;++i)
            sb.append(ALLOWED_CHARACTERS.charAt(random.nextInt(ALLOWED_CHARACTERS.length())));
        return sb.toString();
    }

    private String getTimestamp(){
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String currentDateandTime = sdf.format(new Date());
        return currentDateandTime;
    };


    public String getUniqueID() {
        return getRandomString(10);
    }


    private void sendMessegeToActivity(String message) throws IOException {
        Log.i("sender", "Broadcasting message");
        Intent intent = new Intent("payload-received");
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Toast toast=Toast.makeText(context,message,Toast.LENGTH_LONG);
        toast.show();
    }




    private void startMyOwnForeground(){
        Intent intentAction = new Intent(context, SparrowBroadcastReceiver.class);

        intentAction.putExtra("action", "exit");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, -1, intentAction, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder;
        String NOTIFICATION_CHANNEL_ID = "in.skylinelabs.sparrow";
        String channelName = "Sparrow Background Service";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = null;
            notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            notificationChannel.setLightColor(Color.BLUE);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(notificationChannel);

            notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        }
        else {
            notificationBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID);
        }
        Notification notification = notificationBuilder.setOngoing(true)
                .setContentTitle("Sparrow is keeping you connected")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(R.drawable.ic_clear_black_24dp, "Stop", pendingIntent)
                .build();
        startForeground(1, notification);
    }




}

