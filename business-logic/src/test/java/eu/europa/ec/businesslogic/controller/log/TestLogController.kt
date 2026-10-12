/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.businesslogic.controller.log

import android.content.Context
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import timber.log.Timber
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TestLogController {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val logsDir: File
        get() = File(context.filesDir, "logs")

    private val loggedText: String
        get() = logsDir.listFiles().orEmpty()
            .filter { it.name.endsWith(".txt") }
            .joinToString(separator = "\n") { it.readText() }

    @Before
    fun before() {
        Timber.uprootAll()
        logsDir.deleteRecursively()
    }

    @After
    fun after() {
        Timber.uprootAll()
    }

    @Test
    fun `Given logged messages, When the log files are deleted, Then they are gone and later messages go to a new file`() {
        val controller = LogControllerImpl(context = context, tag = "TAG")
        controller.i { "before the reset" }

        controller.deleteLogFiles()
        controller.i { "after the reset" }

        assertFalse(loggedText.contains("before the reset"))
        assertTrue(loggedText.contains("after the reset"))
    }

    @Test
    fun `Given the log files were deleted, When they are deleted again, Then logging goes on`() {
        val controller = LogControllerImpl(context = context, tag = "TAG")
        controller.i { "first" }

        controller.deleteLogFiles()
        controller.deleteLogFiles()
        controller.i { "second" }

        assertFalse(loggedText.contains("first"))
        assertTrue(loggedText.contains("second"))
    }
}
