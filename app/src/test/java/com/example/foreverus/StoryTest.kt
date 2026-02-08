package com.example.foreverus

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryTest {

    @Test
    fun getTimestamp_returnsTimestamp() {
        val story = Story()
        val ts = Timestamp(1678886400L, 0)
        story.timestamp = ts

        val resultTimestamp = story.getTimestamp()
        
        assertEquals(ts, resultTimestamp)
    }

    @Test
    fun getIsEdited_returnsTrueIfVersionGreaterThanZero() {
        val story = Story()
        story.version = 1L
        assertTrue(story.getIsEdited())
    }

    @Test
    fun getIsEdited_returnsFalseIfVersionIsZero() {
        val story = Story()
        story.version = 0L
        assertFalse(story.getIsEdited())
    }
}
