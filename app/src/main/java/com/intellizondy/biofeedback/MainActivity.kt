package com.intellizondy.biofeedback

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

//import com.intellizon.biofeedbacktest.BuildConfig

import com.intellizondy.biofeedback.R
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

//        if (BuildConfig.DEBUG) {
//            Timber.plant(Timber.DebugTree())
//        }
//        Timber.d("version name: ${BuildConfig.VERSION_NAME}")

//        findViewById<Button>(R.id.blueToothConnection).setOnClickListener {
//            Toast.makeText(this@MainActivity, "blueToothConnect", Toast.LENGTH_SHORT).show()
//        }


    }

    override fun onDestroy() {
        super.onDestroy()

    }
}
