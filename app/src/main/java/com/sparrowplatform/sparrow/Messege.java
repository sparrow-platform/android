package com.sparrowplatform.sparrow;


public class Messege {


    private String data;

    public Messege(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
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
