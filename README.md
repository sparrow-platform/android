# Sparrow App

This repo contains code for Sparrow Android application. Sparrow App performs 3 major functions:
<br>
1. Chat interface to talk with Sparrow<br>
2. Sparrow Mesh - Offline communication with community<br>
3. Sparrow Offline features<br>

<h3>Sparrow App - Another interface to talk with Sparrow</h3>
<p align="center">
<img  height=500 src="https://raw.githubusercontent.com/sparrow-platform/sparrow-android/master/Sparrow-App-screenshot.png"/>
</p>
Users can reach Sparrow through any chat interfaces like Whatsapp, FB Messenger, Viber, WeChat, SMS, etc. 
<br>
But, Sparrow app enables the most feature rich communication.

<h3>One app for all communication</h3>
Sparrow app allows cross-platform communication - Users can send messages from Sparrow app to users on any other chat platforms. 
<br>
The same app also is the interface to Sparrow Mesh - Sparrow mesh uses Wifi, BLE, Bluetooth, Sound waves to share messages with all nearby phones with Sparrow app. 
The resultant network is a P2P Mesh of Smartphones with Sparrow App, which enables people to communicate without internet or any additional hardware.

<h3>Sparrow Mesh</h3>
<p align="center">
<img  height=500 src="https://sparrow-platform.com/images/sparrow/SparrowMeshP2P.png"/>
</p>
More details on Sparrow Mesh Github repo - <br>
https://github.com/sparrow-platform/sparrow-mesh

<h3>Offline EMR Vault</h3>
<p align="center">
<img  height=500 src="https://sparrow-platform.com/images/sparrow/sparrowEMR.png"/>
</p>
Sparrow App serves as an offline EMR store -

Sparrow App is a offline vault that syncs with HIPA compliant DB when internet is available.

Accessing EMR documents during disasters is easier than ever! Simply add your documents to Sparrow Vault before disasters strike - and all our EMR documents are available in your phone during disasters! <br>
<br>
Sparrow App comes with one click share option for all EMR documents - Share documents to others via Sparrow mesh or any other medium of media share available on phones (NFC, Wifi, Bluetooth, etc) 
<br>

<h3>Sparrow offline features</h3>
Sparrow app comes with many offline features to help medical workers and first responders - 

```
1. Note-taking
2. Broadcast messages over Sparrow Mesh
3. Offline translate
4. Track live location of people via Sparrow mesh
5. Send SOS messages by pressing power button 3 times

The list is potentially infinite!
```

<h3>Technology</h3>
Sparrow App's offline vault syncs to Firebase based database.<br>
All offline intelligence is powered by ML Kit - We can use custom models / outof the box models for adding more offline intelligence features similar to offline translations.
<p align="center">
<img src="https://raw.githubusercontent.com/sparrow-platform/sparrow-android/master/Tech.png"/>
</p>


<h3>Setup instructions</h3>
1. Create new application on firebase console and add google-services.json file to app folder<br>
2. Sparrow uses Authentication, Realtime database and file storage features from firebase<br>
3. Build and run project on Android studio






