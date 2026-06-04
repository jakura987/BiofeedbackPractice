package com.intellizondy.biofeedback.network.tcp;

import android.os.SystemClock;

import java.net.Socket;

/**
 * TCP 客户端连接对象。
 *
 * 这个类只保存 socket 连接层的通用信息。
 * deviceId / deviceName / deviceType 可以由上层 Manager 或 Parser 解析后写入。
 * SimpleTcpServer 本身不关心任何业务协议。
 */
public final class ClientConn {

    public final Socket socket;

    public volatile String peer;
    public volatile String deviceId;
    public volatile String deviceName;
    public volatile int deviceType = -1;
    public volatile long connectedAt;

    public ClientConn(Socket socket) {
        this.socket = socket;
        String host = socket.getInetAddress() == null ? "" : socket.getInetAddress().getHostAddress();
        this.peer = host + ":" + socket.getPort();
        this.connectedAt = SystemClock.elapsedRealtime();
    }

    public String getIp() {
        if (socket == null || socket.getInetAddress() == null) {
            return "";
        }
        return socket.getInetAddress().getHostAddress();
    }

    public int getPort() {
        if (socket == null) {
            return -1;
        }
        return socket.getPort();
    }

    public String getDisplayText() {
        String id = isBlank(deviceId) ? "?" : deviceId;
        if (isBlank(deviceName)) {
            return "设备ID[" + id + "] : " + peer;
        }
        return "设备ID[" + id + "] " + deviceName + " : " + peer;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
