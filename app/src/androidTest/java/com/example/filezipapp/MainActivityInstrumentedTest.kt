package com.example.filezipapp

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests, run on an Android emulator or a real/cloud device.
 *
 * These verify the CloudPhone app launches correctly and its core UI
 * (file picker button, zip-name field, create-zip button) is present.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @Test
    fun appContext_hasCorrectPackageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.filezipapp", appContext.packageName)
    }

    @Test
    fun mainActivity_launchesAndShowsCoreViews() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pickButton = activity.findViewById<android.widget.Button>(R.id.btnPickFiles)
                val zipNameEdit = activity.findViewById<android.widget.EditText>(R.id.zipNameEdit)
                val createZipButton = activity.findViewById<android.widget.Button>(R.id.btnCreateZip)

                assertNotNull("Select Files button should exist", pickButton)
                assertNotNull("Zip name field should exist", zipNameEdit)
                assertNotNull("Create Zip button should exist", createZipButton)

                // Create Zip is disabled until files are picked
                assertEquals(false, createZipButton.isEnabled)
            }
        }
    }
}
