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
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static okhttp3.internal.Util.UTF_8;


public class Sparrow extends Service implements MqttCallback {


    private Context context;
    private String  TAG_SPARROW_WIFI_SERVICE = "SPARROW WIFI SERVICE";
    WifiManager mWifiManager;
    String deviceName;
    WifiManager.WifiLock mWifiLock = null;
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
    private int ttl = 60*60*2;

    public PassiveExpiringMap cache = new PassiveExpiringMap<>(ttl, TimeUnit.SECONDS);



    final String serverUri = "tcp://ec2-52-15-84-63.us-east-2.compute.amazonaws.com:1883" +
            "";

    public String mqttClientID;


    MqttClient mqClient;

    String subscribeMqtt = "sparrow_response/sparrow:";
    String publishMqtt = "sparrow_receive/sparrow:";

    String subscriptionTopic, publishTopic;


    IBinder mBinder = new LocalBinder();


    Context serviceContext;

    MqttConnectOptions options = new MqttConnectOptions();

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
//        deviceName = "Sparrow:" + getUniqueID();

        super.onCreate();
        context = getApplicationContext();
        Random random = new Random();
        deviceName = "sp"+random.nextInt(100);
        startMyOwnForeground();

        serviceContext = this;


        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        handler = new Handler();
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("send-payload"));
        startServer();

    }

    public Sparrow() {
        super();
    }


    @Override
    public void onDestroy() {

//        stoptimertask();

        stopServer();
        super.onDestroy();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        connectionsClient = Nearby.getConnectionsClient(getBaseContext());

//        startTimer();



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

        return START_STICKY;
    }




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



    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");

            try {
                JSONObject jsonObject = new JSONObject(message);
                cache.put(jsonObject.getString("key"), new Messege(jsonObject.toString()));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "Sending message through BLE service: " + message);
        }
    };


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
            try {
                sendMessegeToActivity(characteristic.getStringValue(0), "mesh");
            } catch (IOException e) {
                e.printStackTrace();
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
            for (Object key : keys){
                Object keyObj = key.toString();
                Messege msg = (Messege) cache.get(keyObj);

                if(!msg.isSent(device.getAddress())) {
                    notifyToDeivce(device, msg.getData());
                    msg.sentTo(device.getAddress());
                }

            }


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

    /**
     * Callback to receive information about the advertisement process.
     */
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
            Log.i(TAG,"BLE scan result: "+result.getScanRecord().getDeviceName());
            List<ParcelUuid> serviceUuids = result.getScanRecord().getServiceUuids();
            if(serviceUuids != null && serviceUuids.contains(new ParcelUuid(SparrowBLEProfile.SPARROW_SERVICE))) {
                Log.i(TAG, "Sparrow device detected: " + result.getScanRecord().getDeviceName());
                if(!mAvailableDevices.contains(result.getDevice())) {
                    mAvailableDevices.add(result.getDevice());
                    //result.getDevice().connectGatt(context,false,mGattConnectionCallback);
                    Log.i(TAG,"Connecting to sparrow device");
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.i(TAG,"BLE scan batch result: "+results.size());
            for (ScanResult scanResult: results) {
                Log.i(TAG,"BLE scan result: "+scanResult.getScanRecord().getDeviceName());
                List<ParcelUuid> serviceUuids = scanResult.getScanRecord().getServiceUuids();
                if(serviceUuids != null && serviceUuids.contains(new ParcelUuid(SparrowBLEProfile.SPARROW_SERVICE))) {
                    Log.i(TAG, "Sparrow device detected: " + scanResult.getScanRecord().getDeviceName());
                    if(!mAvailableDevices.contains(scanResult.getDevice())) {
                        mAvailableDevices.add(scanResult.getDevice());
                        Log.i(TAG,"Connecting to sparrow device");
                    }
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i(TAG,"BLE scan failed "+ errorCode);

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

        Runnable messegeBufferManager = new Runnable() {
            @Override
            public void run() {
                // TODO: 15/6/19 Make sure messege buffer is maintained
            }
        };

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









/********************************TIMER********************

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




/*********************SPARROW**********************

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
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        Log.i(TAG, "Unique ID is " + androidId);
        return androidId.substring(6);
    }


    private void sendMessegeToActivity(String message, String type) throws IOException {
        Log.i(TAG, "Sender: Broadcasting message");
        Intent intent = new Intent("payload-received");
        intent.putExtra("message", message);
        intent.putExtra("type", type);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        Toast toast=Toast.makeText(context,message,Toast.LENGTH_LONG);
        toast.show();
    }




    private void startMyOwnForeground(){
        Intent intentAction = new Intent(context, SparrowBroadcastReceiver.class);

        intentAction.putExtra("action", "exit");

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, -1, intentAction, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notificationBuilder;
        String NOTIFICATION_CHANNEL_ID = "com.sparrowplatform.sparrow";
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


    public void publishMessage(String msg){
        if (!mqClient.isConnected()){
            initMQTT(mqttClientID, false);
        }

        try {
            mqClient.publish(publishTopic, new MqttMessage(msg.getBytes()));

            Log.i(TAG, "Sending MQTT message: " + msg);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void connectionLost(Throwable cause) {
        Toast.makeText(context, "Disconnected from internet", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String msg = message.toString();

        Log.i(TAG, "Received message with topic "+ topic +", Message is: "+ msg);
        if (topic.equals(subscriptionTopic)){
            sendMessegeToActivity(msg, "mqtt");
        }

    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }




}

