package org.example.stash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

class MainActivity : ComponentActivity() {
    private val database by lazy {
        Room.databaseBuilder<StashDatabase>(applicationContext, getDatabasePath("stash.db").absolutePath)
            .setDriver(BundledSQLiteDriver()).build()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(androidx.compose.runtime.remember(database) { RoomCaptureStore(database) }) }
    }
    override fun onDestroy() {
        super.onDestroy()
        database.close()
    }
}
