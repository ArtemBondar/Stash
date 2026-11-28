package org.example.stash

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
fun main(args: Array<String>) {
    val directory = File(System.getProperty("user.home"), ".stash").apply { mkdirs() }
    val database = Room.databaseBuilder<StashDatabase>(File(directory, "stash.db").absolutePath)
        .setDriver(BundledSQLiteDriver()).build()
    val store = RoomCaptureStore(database)
    try {
        application {
            if ("--quick-capture" in args) {
                val session = remember { QuickCaptureSession(store) { text, kind ->
                    Capture(Uuid.random().toString(), text, kind, Clock.System.now().toEpochMilliseconds())
                } }
                Window(
                    onCloseRequest = { if (session.canClose) exitApplication() },
                    title = "Quick capture",
                    state = WindowState(width = 480.dp, height = 320.dp),
                ) { QuickCapture(session, ::exitApplication) }
            } else {
                Window(onCloseRequest = ::exitApplication, title = "Stash") { App(store) }
            }
        }
    } finally { database.close() }
}
