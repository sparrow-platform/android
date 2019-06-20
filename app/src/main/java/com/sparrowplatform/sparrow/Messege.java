package com.sparrowplatform.sparrow;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Messege {


    private String data;
    Set recievedBy = new HashSet();

    public Boolean mqttPublished = false;


    public Messege(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }


    boolean isSent(String deviceAddress){
        return recievedBy.contains(deviceAddress);
    }

    boolean isMqttPublished(){
        return this.mqttPublished;
    }

    void sentTo(String deviceAddress){
        recievedBy.add(deviceAddress);
    }

    void mqttPublished(){
        this.mqttPublished = true;
    }
}


//Message format:

//{
//    "message" : ""
//    "key":userID_timestamp,
//    "userId":"",
//    "timeStamp":"",
//    "destination":""
//
//}
//