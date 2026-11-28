package org.example.stash

import androidx.compose.ui.window.ComposeUIViewController
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
fun ViewController() = ComposeUIViewController {
    val directory = NSFileManager.defaultManager.URLForDirectory(NSDocumentDirectory, NSUserDomainMask, null, true, null)!!.path!!
    val database = androidx.compose.runtime.remember {
        Room.databaseBuilder<StashDatabase>("$directory/stash.db").setDriver(BundledSQLiteDriver()).build()
    }
    androidx.compose.runtime.DisposableEffect(database) { onDispose { database.close() } }
    val store = androidx.compose.runtime.remember(database) { RoomCaptureStore(database) }
    App(store)
}
