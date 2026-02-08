package com.example.foreverus;

import androidx.room.TypeConverter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList; // Added this import

public class Converters {
    @TypeConverter
    public static List<String> fromString(String value) {
        if (value == null) {
            return new ArrayList<>();
        }
        Type listType = new TypeToken<List<String>>() {}.getType();
        return new Gson().fromJson(value, listType);
    }

    @TypeConverter
    public static String fromList(List<String> list) {
        Gson gson = new Gson();
        return gson.toJson(list);
    }

    @TypeConverter
    public static java.util.Date fromTimestamp(Long value) {
        return value == null ? null : new java.util.Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(java.util.Date date) {
        return date == null ? null : date.getTime();
    }

    // Firebase Timestamp converter (stored as seconds + nanoseconds? or just long millis)
    // For simplicity in Room, we often store as generic Long millis if precision isn't critical,
    // or we store the Serializable object, OR strict mapping.
    // Let's assume we map to/from Date which maps to Long.
    // Actually, error says "timestamp in com.example.foreverus.Letter" (which might be Date)
    // and "timestamp in com.example.foreverus.Song".
    
    // Let's add direct Firebase Timestamp support just in case
    @TypeConverter
    public static com.google.firebase.Timestamp fromFirebaseTimestamp(java.util.Date date) {
         return date == null ? null : new com.google.firebase.Timestamp(date);
    }
    
    // Wait, Room needs to go to a primitive like Long.
    @TypeConverter
    public static Long firebaseTimestampToLong(com.google.firebase.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toDate().getTime();
    }

    @TypeConverter
    public static com.google.firebase.Timestamp longToFirebaseTimestamp(Long millis) {
        return millis == null ? null : new com.google.firebase.Timestamp(new java.util.Date(millis));
    }
}
