package com.example.foreverus;

import com.google.firebase.firestore.FieldValue;

public class Relationship {
    private String user1_id;
    private String user2_id;
    private Object createdAt;

    public Relationship() {
        // Default constructor required for calls to DataSnapshot.getValue(Relationship.class)
    }

    public Relationship(String user1_id, String user2_id) {
        this.user1_id = user1_id;
        this.user2_id = user2_id;
        this.createdAt = FieldValue.serverTimestamp();
    }

    public String getUser1_id() {
        return user1_id;
    }

    public void setUser1_id(String user1_id) {
        this.user1_id = user1_id;
    }

    public String getUser2_id() {
        return user2_id;
    }

    public void setUser2_id(String user2_id) {
        this.user2_id = user2_id;
    }

    public Object getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Object createdAt) {
        this.createdAt = createdAt;
    }
}
