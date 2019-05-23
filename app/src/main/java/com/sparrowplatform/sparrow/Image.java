package com.sparrowplatform.sparrow;

import com.google.firebase.database.Exclude;
import com.google.firebase.database.IgnoreExtraProperties;

import java.io.Serializable;

@IgnoreExtraProperties
public class Image implements Serializable {
    public String key;
    public String userId;
    public String downloadUrl;
    public String title;
    public String content;
    public String timeStamp;
    public String description;

    // these properties will not be saved to the database
    @Exclude
    public User user;

    public Image() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    public Image(String key, String userId, String downloadUrl, String title, String description, String content, String timeStamp) {
        this.key = key;
        this.userId = userId;
        this.downloadUrl = downloadUrl;
        this.title = title;
        this.content = content;
        this.timeStamp = timeStamp;
        this.description = description;
    }


}
