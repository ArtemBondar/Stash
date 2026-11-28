package org.example.stash

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

fun main() {
    val directory = File(System.getProperty("user.home"), ".stash").apply { mkdirs() }
    val database = Room.databaseBuilder<StashDatabase>(File(directory, "stash.db").absolutePath)
        .setDriver(BundledSQLiteDriver()).build()
    val store = RoomCaptureStore(database)
    try {
        application { Window(onCloseRequest = ::exitApplication, title = "Stash") { App(store) } }
    } finally { database.close() }
}
