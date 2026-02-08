package com.example.foreverus;

public final class FirestoreConstants {
    private FirestoreConstants() {}

    // Collections
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_RELATIONSHIPS = "relationships";
    public static final String COLLECTION_LETTERS = "letters";
    public static final String COLLECTION_STORIES = "stories";


    // User Fields
    public static final String FIELD_PAIRING_CODE = "pairingCode";
    public static final String FIELD_RELATIONSHIP_ID = "relationshipId";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_AVATAR_URL = "avatarUrl";

    // Relationship Fields
    public static final String FIELD_MEMBERS = "members";
    public static final String FIELD_CREATED_AT = "createdAt";
    public static final String FIELD_COUPLE_NICKNAME = "coupleNickname";
    // public static final String FIELD_THEME_SONG = "themeSong"; // Reserved for future

    // Story & Letter Fields
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_CONTENT = "content";
    public static final String FIELD_TIMESTAMP = "timestamp";
    public static final String FIELD_LAST_EDITED_BY = "lastEditedBy";
    public static final String FIELD_VERSION = "version";
}
