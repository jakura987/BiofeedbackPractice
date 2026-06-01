package com.intellizondy.biofeedback.network

import android.health.connect.datatypes.units.Length
import android.util.Log
import androidx.resourceinspection.annotation.Attribute.IntMap
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class SimpleTcpServer(
    private val port: Int,
    private val listener: Listener
){

    interface Listener{
        fun onClientConnected(peer: String)
        fun onClientDisconnected(peer: String)
        fun onReceive(peer: String, data: ByteArray, length: Int)
    }

    //给多线程共享变量用的
    @Volatile
    private var running = false

    private var server: ServerSocket ? = null



}