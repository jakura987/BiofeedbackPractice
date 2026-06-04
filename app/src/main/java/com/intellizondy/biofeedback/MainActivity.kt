package com.intellizondy.biofeedback

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.intellizondy.biofeedback.databinding.ActivityMainBinding
import com.intellizondy.biofeedback.network.tcp.LocalIpHelper
import com.intellizondy.biofeedback.network.tcp.SimpleTcpServer
import com.intellizondy.biofeedback.network.tcp.WifiTcpServerManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var wifiTcpServerManager: WifiTcpServerManager

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val ipList = LocalIpHelper.listPrivateIPv4()


//        wifiTcpServerManager.setOnReceiveListener{ peer, data ->
//            Timber.tag("TCP_TEST").w(
//                "收到数据 peer=%s len=%d hex=%s text=%s",
//                peer,
//                data.size,
//                SimpleTcpServer.toHex(data, data.size),
//                data.toString(Charsets.UTF_8)
//            )
//
//            //收到什么 就回一段ack
//            val reply = "ACK from Android\r\n".toByteArray(Charsets.UTF_8)
//            wifiTcpServerManager.sendToPeer(peer, reply)
//        }

        wifiTcpServerManager.ensureStarted(8883)

        binding.tvIp.text = """
            TCP Server 已启动

            端口：8883

            请电脑依次尝试：
            $ipList
        """.trimIndent()




    }

    override fun onDestroy() {
        wifiTcpServerManager.ensureStopped()
        super.onDestroy()

    }
}
