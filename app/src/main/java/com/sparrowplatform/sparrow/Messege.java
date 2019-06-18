package com.sparrowplatform.sparrow;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class Messege {


    private String data;
    Set recievedBy = new HashSet();


    public Messege(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }


    boolean isSent(String deviceAddress){
        return recievedBy.contains(deviceAddress);
    }

    void sentTo(String deviceAddress){
        recievedBy.add(deviceAddress);
    }

}


//Message format:

//{
//
//    "key":userID_timestamp,
//    "userId":"",
//    "timeStamp":"",
//    "destination":""
//
//}
//