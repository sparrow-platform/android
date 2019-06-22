package com.sparrowplatform.sparrow;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttService;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.security.Key;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static okhttp3.internal.Util.UTF_8;


public class Sparrow extends Service implements MqttCallback {


    private Context context;
    ConnectionsClient connectionsClient;

    private String TAG = "SPARROW SERVICE";
    private int nextDeviceIndex = 0;

    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothLeScanner mBluetoothLeScanner;
    private BluetoothGatt currentBluetoothGatt;


    private Set<BluetoothDevice> mConnectedClientDevices = new HashSet<>();
    private Set<BluetoothDevice> mConnectedServerDevices = new HashSet<>();
    private ArrayList<BluetoothDevice> mAvailableDevices = new ArrayList<>();

    private Handler handler;

    //Set timeout to 2 hrs
    private int ttl = 2 * 3600 * 1000;

    public HashMap<String, Messege> cache = new HashMap<String, Messege>();

    final String serverUri = "tcp://18.221.210.97:1883" ;
    public String mqttClientID;

    public int mqttRefresh = 5000;
    public int meshRefresh = 30000;



    MqttClient mqClient;

    String subscribeMqtt = "sparrow_response/sparrow:";
    String publishMqtt = "sparrow_receive/sparrow:";

    String subscriptionTopic, publishTopic;

    IBinder mBinder = new LocalBinder();

    String deviceName;

    Context serviceContext;

    MqttConnectOptions options = new MqttConnectOptions();

    private DatabaseHandler db;

    static boolean mainActivityOpen = false;

    WifiManager mWifiManager;
    WifiManager.WifiLock mWifiLock = null;

    HashSet<String> nearbyDevices = new HashSet<>();


    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public Sparrow getServerInstance() {
            return Sparrow.this;
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();

//        deviceName = "sparrow_" + getUniqueID();
        Random random = new Random();
        deviceName = "sp"+random.nextInt(100);

        startMyOwnForeground();

        serviceContext = this;
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        handler = new Handler();

        db = new DatabaseHandler(this);
        startServer();
    }

    public Sparrow() {
        super();
    }


    @Override
    public void onDestroy() {
        stoptimertask();
        stopMeshTimer();
        stopServer();
        super.onDestroy();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        connectionsClient = Nearby.getConnectionsClient(getBaseContext());

        final String PREFS_NAME = "sparrowPreferences";
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, 0);
        mqttClientID = "sparrow:" + preferences.getString("name",getUniqueID());

        subscriptionTopic = subscribeMqtt + preferences.getString("name",getUniqueID());
        publishTopic = publishMqtt + preferences.getString("name",getUniqueID());
        initMQTT(mqttClientID, false);



        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            Toast.makeText(context, "Bluetooth support unavailable", Toast.LENGTH_SHORT).show();
            // TODO: 15/5/19 Use wifi instead
            return START_STICKY;
        }

        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
        }


        startMeshTimer();
        startTimer();

        return START_STICKY;
    }



    public void addToCache(String message, String key) {

        cache.put(key, new Messege(message));
        Log.d(TAG, "Added message to cache" + message);

    }


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
        DateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd:HH:mm:ss:SSS");
        Date date = new Date();
        return dateFormat.format(date);
    };


    public String getUniqueID() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return androidId.substring(6);
    }


    private void sendMessegeToActivity(String message) {

        if(mainActivityOpen) {
            Log.i(TAG, "\nReceived new MQTT Message");
            Log.i(TAG, "Sending message to Activity");
            Intent intent = new Intent("payload-received");
            intent.putExtra("message", message);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
        else{
            Log.i(TAG, "\nReceived new MQTT Message");
            Log.i(TAG, "Adding message to DB");

            ChatMessage m = new ChatMessage();
            m.setMessage(message);
            m.setDate(DateFormat.getDateTimeInstance().format(new Date()));
            m.setTag(0);
            db.addtodatabase(m);
        }
    }




    private void startMyOwnForeground(){
        Intent intentAction = new Intent(context, SparrowBroadcastReceiver.class);
        intentAction.putExtra("action", "exit");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, -1, intentAction, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder;
        String NOTIFICATION_CHANNEL_ID = "com.sparrow-platform.sparrow.mesh";
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
                .setSmallIcon(R.drawable.sparrow)
                .setContentTitle("Sparrow is keeping you connected")
                .setPriority(NotificationManager.IMPORTANCE_MAX)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(R.drawable.ic_clear_black_24dp, "Stop", pendingIntent)
                .build();
        startForeground(1, notification);
    }




    /*********************SPARROW**********************/


    private void resetSparrowConnections() {
        nearbyDevices.clear();
        connectionsClient.stopAllEndpoints();
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
    }

    private void startSparrowService() {
        Log.i(TAG, "Initiated BLE");
        resetSparrowConnections();
        initiateWifi();
        startDiscovery();
        startAdvertising();
    }


    private void initiateWifi() {
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(mWifiManager.isWifiEnabled()==false)
        {
            mWifiManager.setWifiEnabled(true);
        }
        if( mWifiLock == null )
            mWifiLock = mWifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
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
                    connectionsClient.acceptConnection(endpointId, mPayloadCallback);
                    Log.i(TAG, "BLE Connection connection initiated");
                }

                @Override
                public void onConnectionResult(String endpointId, ConnectionResolution result) {
                    if(result.getStatus().isInterrupted()){
                        Log.i(TAG, "BLE Connection failed");
                    }

                    if (result.getStatus().isSuccess()){

                        //Send messages here
                        Log.i(TAG, "Descriptor write request recieved through BLE: " + endpointId);
                        Set keys = cache.keySet();

                        Log.i(TAG, "Sending data to " +endpointId);
                        for (Object key : keys){
                            Log.i(TAG, "Sending message \n" + "Key: " + key.toString());
                            String keyObj = key.toString();
                            Messege msg = cache.get(keyObj);
                            try {
                                if(!msg.isSent(endpointId)) {
                                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(msg.getData().getBytes()));
                                    msg.sentTo(endpointId);
                                    Log.i(TAG, "Data sent over BLE to " + endpointId);
                                    Thread.sleep(2000);
                                }
                            } catch (Exception e) {
                                Log.i(TAG, "Data transfer over BLE failed");
                                e.printStackTrace();
                            }
                        }
                        Log.i(TAG, "Messages transfer completed");
                    }
                }
                @Override
                public void onDisconnected(String endpointId) {
                    Log.i(TAG, "BLE Connection ended");

                }
            };



    private final EndpointDiscoveryCallback mEndpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(String endpointId, DiscoveredEndpointInfo info) {
                    nearbyDevices.add(endpointId);
                    Log.i(TAG, "Printing Nearby devices");
                    for (String device : nearbyDevices){
                        Log.i(TAG, "Nearby device ID is: " + device);
                    }
                    connectionsClient.requestConnection(deviceName, endpointId, connectionLifecycleCallback);
                }
                @Override
                public void onEndpointLost(String endpointId) {
                    nearbyDevices.remove(endpointId);
                    Log.i(TAG, "Printing Nearby devices");
                    for (String device : nearbyDevices){
                        Log.i(TAG, "Nearby device ID is: " + device);
                    }
                }
            };



    private final PayloadCallback mPayloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(String endpointId, Payload payload) {
                    String bleMsg = new String(payload.asBytes(), UTF_8);
                    Log.i(TAG, "Received data from BLE" + "\n" + bleMsg);

                    try {
                        JSONObject jsonObject = new JSONObject(bleMsg);
                        String msg = jsonObject.getString("message");
                        String destination = jsonObject.getString("destination");
                        String key = jsonObject.getString("key");

                        if (destination.equals(mqttClientID)){
                            Log.i(TAG, "BLE Data is for this user, sending to activity" );
                            sendMessegeToActivity(msg);
                        }
                        else{
                            addToCache(bleMsg, key);
                            Log.i(TAG, "Added message from BLE to cache" );
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.i(TAG, "Something went wrong while receiving msg on BLE" );
                    }
                }
                @Override
                public void onPayloadTransferUpdate(String endpointId, PayloadTransferUpdate update) {
                }
            };

    /*********************SPARROW**********************/


    /************BLE based transfer*********************/

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    startServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stopServer();
                    break;
                default:
                    // Do nothing
            }

        }
    };

    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }

        return true;
    }



    private void startBLEAdvertising() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        bluetoothAdapter.setName(deviceName);
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(SparrowBLEProfile.SPARROW_SERVICE))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);
        startBLEDiscovering();
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private void stopBLEAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);

        stopBLEDiscovering();
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private void startServer() {
        try {
            mBluetoothGattServer = mBluetoothManager.openGattServer(context, mGattServerCallback);
            mBluetoothGattServer.addService(SparrowBLEProfile.createSparrowService());
            startBLEAdvertising();
        }
        catch(Exception e){
            return;
        }
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }
    }

    /**
     * Shut down the GATT server.
     */
    private void stopServer() {
        if (mBluetoothGattServer == null) return;

        mBluetoothGattServer.close();
        stopBLEAdvertising();
    }

    private void notifyToDeivce(BluetoothDevice device, String message){
        BluetoothGattCharacteristic sparrowCharacteristic = mBluetoothGattServer
                .getService(SparrowBLEProfile.SPARROW_SERVICE)
                .getCharacteristic(SparrowBLEProfile.SPARROW_NOTIFICATION);
        for(int i=0;i < message.length();i+=20) {
            sparrowCharacteristic.setValue(message.substring(i,i+20<message.length()?i+20:message.length()));
            mBluetoothGattServer.notifyCharacteristicChanged(device, sparrowCharacteristic,false);
        }
    }


    private BluetoothGattCallback mGattConnectionCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);

            Log.i(TAG, "Client BluetoothDevice ConnectionChange " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Client BluetoothDevice CONNECTED: " + gatt.getDevice().getName());
                gatt.discoverServices();
                gatt.requestMtu(512);
                mConnectedServerDevices.add(gatt.getDevice());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Client BluetoothDevice DISCONNECTED: " + gatt.getDevice().getName());
                //Remove device from any active subscriptions
                mConnectedServerDevices.remove(gatt.getDevice());

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            BluetoothGattCharacteristic sparrowCharacteristic = gatt
                    .getService(SparrowBLEProfile.SPARROW_SERVICE)
                    .getCharacteristic(SparrowBLEProfile.SPARROW_NOTIFICATION);

            gatt.setCharacteristicNotification(sparrowCharacteristic,true);

            BluetoothGattDescriptor descriptor = sparrowCharacteristic.getDescriptor(SparrowBLEProfile.CLIENT_CONFIG);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            String bleMsg = characteristic.getStringValue(0);

            Log.i(TAG, "Received data from BLE" + "\n" + bleMsg);

            try {
                JSONObject jsonObject = new JSONObject(bleMsg);
                String msg = jsonObject.getString("message");
                String destination = jsonObject.getString("destination");
                String key = jsonObject.getString("key");

                if (destination.equals(mqttClientID)){
                    Log.i(TAG, "BLE Data is for this user, sending to activity" );
                    sendMessegeToActivity(msg);
                }
                else{
                    addToCache(bleMsg, key);
                    Log.i(TAG, "Added message from BLE to cache" );
                }

            } catch (JSONException e) {
                e.printStackTrace();
                Log.i(TAG, "Something went wrong while receiving msg on BLE" );
            }
        }
    };


    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.i(TAG, "Server BluetoothDevice ConnectionChange " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Server BluetoothDevice CONNECTED: " + device);
                mConnectedClientDevices.add(device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Server BluetoothDevice DISCONNECTED: " + device);
                //Remove device from any active subscriptions
                mConnectedClientDevices.remove(device);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            Log.i(TAG, "Descriptor write request recieved through BLE: " + device.getName());
            Set keys = cache.keySet();

            Log.i(TAG, "Sending data to " + device.getName());
            for (Object key : keys){
                Log.i(TAG, "Sending message \n" + "Key: " + key.toString());
                String keyObj = key.toString();
                Messege msg = cache.get(keyObj);
                try {
                    if(!msg.isSent(device.getAddress())) {
                        notifyToDeivce(device, msg.getData());
                        msg.sentTo(device.getAddress());
                        Log.i(TAG, "Data sent over BLE to " + device.getName());
                    }
                } catch (Exception e) {
                    Log.i(TAG, "Data transfer over BLE failed");
                    e.printStackTrace();
                }
            }

            Log.i(TAG, "Messages transfer completed");
            mBluetoothGattServer.cancelConnection(device);
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
            Log.i(TAG,"MTu changed to: "+mtu);
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            Log.i(TAG,"Service added" + status);
        }
    };





    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: "+errorCode);
        }
    };





    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d(TAG,"BLE scan result: "+result.getScanRecord().getDeviceName());
            List<ParcelUuid> serviceUuids = result.getScanRecord().getServiceUuids();
            if(serviceUuids != null && serviceUuids.contains(new ParcelUuid(SparrowBLEProfile.SPARROW_SERVICE))) {
                Log.d(TAG, "Sparrow device detected: " + result.getScanRecord().getDeviceName());
                if(!mAvailableDevices.contains(result.getDevice())) {
                    mAvailableDevices.add(result.getDevice());
                    Log.d(TAG,"Connecting to sparrow device");
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.d(TAG,"BLE scan batch result: "+results.size());
            for (ScanResult scanResult: results) {
                Log.d(TAG,"BLE scan result: "+scanResult.getScanRecord().getDeviceName());
                List<ParcelUuid> serviceUuids = scanResult.getScanRecord().getServiceUuids();
                if(serviceUuids != null && serviceUuids.contains(new ParcelUuid(SparrowBLEProfile.SPARROW_SERVICE))) {
                    Log.d(TAG, "Sparrow device detected: " + scanResult.getScanRecord().getDeviceName());
                    if(!mAvailableDevices.contains(scanResult.getDevice())) {
                        mAvailableDevices.add(scanResult.getDevice());
                        Log.d(TAG,"Connecting to sparrow device");
                    }
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.d(TAG,"BLE scan failed "+ errorCode);

        }
    };




    private void stopBLEDiscovering() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        mBluetoothLeScanner.stopScan(scanCallback);
    }




    private void startBLEDiscovering() {
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (mBluetoothLeScanner == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }
        List<ScanFilter> scanFilters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder().build();
        scanFilters.add(filter);
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        Runnable changeConnection = new Runnable() {
            @Override
            public void run() {
                if(mAvailableDevices.size() > 0) {
                    if (currentBluetoothGatt != null)
                        currentBluetoothGatt.close();
                    currentBluetoothGatt = mAvailableDevices.get(nextDeviceIndex).connectGatt(context, false, mGattConnectionCallback);
                    Log.d(TAG,"Connecting to: "+nextDeviceIndex);
                    nextDeviceIndex = mAvailableDevices.size() <= ++nextDeviceIndex? 0 : nextDeviceIndex;
                }
                handler.postDelayed(this,5000);
            }
        };

        Runnable startScan = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"Starting BLE discovery");
                mBluetoothLeScanner.startScan(scanFilters,scanSettings,scanCallback);
                handler.postDelayed(this,20000);
            }
        };

        Runnable stopcan = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"Stopping BLE discovery");
                mBluetoothLeScanner.stopScan(scanCallback);
                //mConnectedDevices.clear();
                handler.postDelayed(this,20000);
            }
        };

        handler.postDelayed(startScan,2000);
        handler.postDelayed(changeConnection,8000);
        handler.postDelayed(stopcan,8000);

    }
    /************BLE based transfer*********************/




    public void initMQTT(String id, Boolean changed)  {
        if (changed){
            Log.i(TAG, "1 Replacing existing client");

            final String PREFS_NAME = "sparrowPreferences";
            SharedPreferences preferences = getSharedPreferences(PREFS_NAME, 0);
            mqttClientID = "sparrow:" + preferences.getString("name",getUniqueID());

            Log.i(TAG, "2 Replacing existing client");
            subscriptionTopic = subscribeMqtt + preferences.getString("name",getUniqueID());
            publishTopic = publishMqtt + preferences.getString("name",getUniqueID());


            //Disconnect first
            if(mqClient.isConnected()){
                try {
                    mqClient.disconnect();
                } catch (MqttException e) {
                    e.printStackTrace();
                }
            }

            Log.i(TAG, "3 Replacing existing client");

            MqttCallback callBack = this;

            Thread thread = new Thread() {
                @Override
                public void run() {
                    try {
                        mqClient = new MqttClient(serverUri, mqttClientID, new MemoryPersistence());
                        mqClient.connect(options);
                        mqClient.setCallback(callBack);
                        mqClient.subscribe(subscriptionTopic);

                        Log.i(TAG, "Connected to MQTT");
                        Log.i(TAG, "Subscribed to topic " + subscriptionTopic);

                    } catch (MqttException e) {
                    }
                }
            };
            thread.start();
        }

        else{
            try {
                mqClient = new MqttClient(serverUri, id,  new MemoryPersistence());
                mqClient.connect(options);
                mqClient.setCallback(this);
                mqClient.subscribe(subscriptionTopic);
                Log.i(TAG, "Connected to MQTT");
                Log.i(TAG, "Subscribed to topic " + subscriptionTopic);

            } catch (MqttException e) {
                Log.i(TAG, "Error in MQTT " + e.toString());
            }
        }
    }



    public boolean publishMessage(String msg){

        if (!mqClient.isConnected()){
            initMQTT(mqttClientID, false);
        }

        try {
            mqClient.publish(publishTopic, new MqttMessage(msg.getBytes()));
            Log.i(TAG, "Sending MQTT message: " + msg);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            String ts = getTimestamp();
            String userId =  mqttClientID;
            String key = userId + "_" + ts;

            JSONObject obj = new JSONObject();
            try {
                obj.put("key", key);
                obj.put("timeStamp", ts);
                obj.put("userId", userId);
                obj.put("destination", "sparrow");
                obj.put("message", msg);
            } catch (JSONException ee) {
                ee.printStackTrace();
            }

            Log.i(TAG, "MQTT publish failed, adding msg to cache");
            addToCache(obj.toString(), key);
            return false;
        }
    }


    @Override
    public void connectionLost(Throwable cause) {
        Toast.makeText(context, "Disconnected from internet", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message)  {
        String msg = message.toString();

        Log.i(TAG, "Received message with topic "+ topic +", Message is: "+ msg);
        if (topic.equals(subscriptionTopic)){
            sendMessegeToActivity(msg);
        }
        else{
            try {
                String ts = getTimestamp();
                String userId = topic.split(":")[1];
                userId = "sparrow:" + userId;
                String key = "sparrow_" + userId + "_" + ts;

                JSONObject obj = new JSONObject();
                obj.put("key", key);
                obj.put("timeStamp", ts);
                obj.put("userId", userId);
                obj.put("destination", userId);
                obj.put("message", msg);
                addToCache(obj.toString(), key);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }




    private Timer timer, timerMesh;
    private TimerTask timerTask, timerMeshTask;

    public void startTimer() {
        timer = new Timer();
        initializeTimerTask();
        timer.schedule(timerTask, 1000, mqttRefresh); //
    }

    public void startMeshTimer() {
        timerMesh = new Timer();
        initializeMeshTimerTask();
        timerMesh.schedule(timerMeshTask, 1000, meshRefresh); //
    }



    public void initializeTimerTask() {
        timerTask = new TimerTask() {
            public void run() {
                //Do something here
                refreshMQTT();
            }
        };
    }

    public void initializeMeshTimerTask() {
        timerMeshTask = new TimerTask() {
            public void run() {
                startSparrowService();
            }
        };
    }



    public void stoptimertask() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
    public void stopMeshTimer() {
        if (timerMesh != null) {
            timerMesh.cancel();
            timerMesh = null;
        }

        resetSparrowConnections();
    }
    /********************************TIMER********************/


    public void refreshMQTT(){
//        for (String device : nearbyDevices){
//            Log.i(TAG, "Attempting connection to " + device);
//            connectionsClient.requestConnection(deviceName, device, connectionLifecycleCallback);
//        }

        Log.i(TAG, "Printing messages in Cache");
        Set keys = cache.keySet();
        String data = "";

        try {
            if (!mqClient.isConnected()) {
                initMQTT(mqttClientID, false);
            }
        }
        catch(Exception e){

        }

        for (Object key : keys){
            Object keyObj = key.toString();
            Messege msg =  cache.get(keyObj);

            Log.i(TAG, msg.getData());

            //Checking if messages have expired
            try {
                JSONObject json = new JSONObject(msg.getData());

                String oldTs= json.getString("timeStamp");
                Date messageOriginTs = new SimpleDateFormat("yyyy:MM:dd:HH:mm:ss:SSS").parse(oldTs);
                Date expiry = new Date(messageOriginTs.getTime() +  ttl);
                Date ts =new SimpleDateFormat("yyyy:MM:dd:HH:mm:ss:SSS").parse(getTimestamp());

                if(ts.after(expiry)){
                    Log.i(TAG, "Removing message from cache");
                    cache.remove(keyObj);
                    break;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }



            //If message source is server, dont publish over MQTT
            String sourceID = key.toString().split("_")[0];
            if (sourceID.equals("sparrow")){
                continue;
            }



            if(!msg.isMqttPublished() && mqClient.isConnected()) {
                String dataStr = msg.getData();
                String backgroundMQTTPublishTopic = "", backgroundMQTTSubscribeTopic = "";
                try {
                    JSONObject josnObj = new JSONObject(dataStr);
                    data = josnObj.get("message").toString();
                    backgroundMQTTPublishTopic = "sparrow_receive/" + josnObj.get("userId").toString();
                    backgroundMQTTSubscribeTopic = "sparrow_response/" + josnObj.get("userId").toString();
                }
                catch(Exception e){
                }

                if (!data.equals("") && !backgroundMQTTPublishTopic.equals("")){
                    msg.mqttPublished();
                    try{
                        mqClient.subscribe(backgroundMQTTSubscribeTopic);
                        mqClient.publish(backgroundMQTTPublishTopic, new MqttMessage(data.getBytes()));
                        cache.remove(key);
                        break;
                    }
                    catch(Exception e){
                        msg.mqttNotPublished();
                    }
                }
            }
            else{
                try{
                    initMQTT(mqttClientID, false);
                }
                catch(Exception e){}


            }
        }
    }
}

