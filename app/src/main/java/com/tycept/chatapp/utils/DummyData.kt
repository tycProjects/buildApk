package com.tycept.chatapp.utils

import com.tycept.chatapp.models.Contact
import com.tycept.chatapp.models.Message

object DummyData {

    fun contacts(): MutableList<Contact> = mutableListOf(
        Contact(1, "Maria Chen", "Sounds good, see you then! 🙌", "2m", "#1877F2", true, 2),
        Contact(2, "Deo Alvarez", "Bro the build finally passed 🔥", "14m", "#F02849", true, 0),
        Contact(3, "Priya Nair", "Sent the zip, check your email", "1h", "#31A24C", false, 1),
        Contact(4, "Jonas Weber", "Haha true true", "3h", "#F7B928", false, 0),
        Contact(5, "Team Tycept", "New APK ready to test ✅", "5h", "#8B5CF6", true, 5),
        Contact(6, "Amara Obi", "Let's ship it tonight", "1d", "#F5533D", false, 0)
    )

    fun conversation(contactName: String): MutableList<Message> {
        val now = System.currentTimeMillis()
        return mutableListOf(
            Message("Yo, did the new build go through?", false, now - 500_000),
            Message("Just kicked it off, watching the progress bar now 👀", true, now - 460_000),
            Message("It auto-detected everything, no config needed", true, now - 455_000),
            Message("Let's gooo. Send the apk when it's done", false, now - 300_000),
            Message("On it. Should be like a minute out", true, now - 280_000)
        )
    }
}
