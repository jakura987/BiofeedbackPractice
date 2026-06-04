package com.intellizondy.biofeedback.network.tcp;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 通用 TCP Server。
 *
 * 职责边界：
 * 1. 只负责 ServerSocket / Socket 的连接、读取、发送、关闭。
 * 2. 不解析业务协议，不判断 5A A5，不处理身份包，不发控制事件。
 * 3. 收到原始 byte[] 后，通过 Listener 回调给上层 Manager / Parser。
 *
 * 适合复用到不同项目中。
 */
public final class SimpleTcpServer {

    private static final String TAG = "SimpleTcpServer";

    public interface Listener {
        default void onClientConnected(String peer) {
        }

        default void onClientDisconnected(String peer) {
        }

        void onReceive(String peer, byte[] data, int length);
    }

    public static final class Config {
        /**
         * 每次 socket read 的 buffer 大小。
         */
        public int bufferSize = 4096;

        /**
         * 是否允许同一个 IP 多连接。
         *
         * false：同 IP 新连接进来时，踢掉旧连接，适合“一台设备只保留一个连接”的设备场景。
         * true ：同 IP 多连接共存，适合调试工具或一个 IP 下多个客户端场景。
         */
        public boolean allowSameIpMultiConnection = false;

        /**
         * 最大客户端数量。
         * <= 0 表示不限制。
         */
        public int maxClients = 0;

        public Config() {
        }

        public Config(int bufferSize, boolean allowSameIpMultiConnection, int maxClients) {
            this.bufferSize = bufferSize;
            this.allowSameIpMultiConnection = allowSameIpMultiConnection;
            this.maxClients = maxClients;
        }
    }

    private final int port;
    private final Listener listener;
    private final Config config;

    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final CopyOnWriteArrayList<ClientConn> clients = new CopyOnWriteArrayList<>();

    private volatile boolean running = false;
    private volatile ServerSocket serverSocket;

    public SimpleTcpServer(int port, Listener listener) {
        this(port, listener, new Config());
    }

    public SimpleTcpServer(int port, Listener listener, Config config) {
        this.port = port;
        this.listener = listener;
        this.config = config == null ? new Config() : config;
    }

    public synchronized boolean start() {
        if (running) {
            Log.i(TAG, "start() ignored: already running");
            return false;
        }

        running = true;

        pool.execute(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        });

        return true;
    }

    private void acceptLoop() {
        try {
            Log.i(TAG, "binding port " + port + " ...");

            ServerSocket ss = new ServerSocket();
            ss.setReuseAddress(true);
            ss.bind(new InetSocketAddress(port));

            serverSocket = ss;

            Log.i(TAG, "server started on port " + port);

            while (running) {
                Log.i(TAG, "accept() waiting...");

                Socket socket;
                try {
                    socket = ss.accept();
                } catch (IOException e) {
                    if (running) {
                        Log.w(TAG, "accept() error", e);
                    }
                    break;
                }

                prepareSocket(socket);

                ClientConn conn = new ClientConn(socket);
                Log.i(TAG, "accepted " + conn.peer);

                if (!canAcceptNewClient(conn)) {
                    closeSocketQuietly(socket);
                    continue;
                }

                if (!config.allowSameIpMultiConnection) {
                    removeOldClientsWithSameIp(conn);
                }

                clients.add(conn);
                safeOnClientConnected(conn.peer);

                Log.i(TAG, "client count=" + clients.size());

                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        handle(conn);
                    }
                });
            }
        } catch (IOException e) {
            if (running) {
                Log.w(TAG, "accept loop end", e);
            } else {
                Log.i(TAG, "accept loop closed");
            }
        } catch (Throwable t) {
            Log.e(TAG, "accept loop unexpected error", t);
        } finally {
            running = false;
            closeServerSocketQuietly();
            Log.i(TAG, "server stopped (loop exit)");
        }
    }

    private void prepareSocket(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
        } catch (Exception e) {
            Log.w(TAG, "prepareSocket error", e);
        }
    }

    private boolean canAcceptNewClient(ClientConn conn) {
        int max = config.maxClients;
        if (max <= 0) return true;
        if (clients.size() < max) return true;

        Log.w(TAG, "reject client: maxClients=" + max + " reached, peer=" + conn.peer);
        return false;
    }

    /**
     * 同 IP 新连接进来时，踢掉旧连接。
     */
    private void removeOldClientsWithSameIp(ClientConn newConn) {
        if (newConn == null) return;

        String newIp = newConn.getIp();
        if (isBlank(newIp)) return;

        for (ClientConn old : clients) {
            if (old == null || old == newConn) continue;
            if (!newIp.equals(old.getIp())) continue;

            Log.w(TAG, "same ip reconnect: remove old " + old.peer + " -> keep new " + newConn.peer);

            closeConn(old);
        }
    }

    /**
     * 每连上一个客户端，就会跑一个对应的 handle() 线程来收它发来的数据。
     */
    private void handle(ClientConn conn) {
        Socket socket = conn.socket;
        final String peer = conn.peer;

        Log.i(TAG, "handle() enter: " + peer);

        try {
            InputStream input = socket.getInputStream();
            byte[] buffer = new byte[Math.max(1, config.bufferSize)];

            while (running && !socket.isClosed()) {
                int n = input.read(buffer);

                if (n == -1) {
                    Log.w(TAG, "peer EOF: " + peer);
                    break;
                }

                if (n == 0) {
                    continue;
                }

                byte[] copy = new byte[n];
                System.arraycopy(buffer, 0, copy, 0, n);

                Log.d(TAG, "recv peer=" + peer
                        + " len=" + n
                        + " head=" + toHex(copy, Math.min(n, 16)));

                try {
                    if (listener != null) {
                        listener.onReceive(peer, copy, copy.length);
                    }
                } catch (Throwable t) {
                    // 上层协议解析异常不应该导致 socket 直接断开。
                    Log.e(TAG, "listener error, keep socket alive, peer=" + peer, t);
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "conn io error [" + peer + "]", e);
        } catch (Throwable t) {
            Log.e(TAG, "conn unexpected error [" + peer + "]", t);
        } finally {
            Log.i(TAG, "handle() leave: " + peer);
            closeConn(conn);
            safeOnClientDisconnected(peer);
            Log.i(TAG, "client removed, left=" + clients.size());
        }
    }

    /**
     * 广播发送到所有已连接客户端。
     * 返回 true 表示至少一个客户端发送成功。
     */
    public synchronized boolean send(byte[] data) {
        if (data == null || data.length == 0) {
            Log.w(TAG, "send() ignored: empty data");
            return false;
        }

        if (clients.isEmpty()) {
            Log.w(TAG, "send() failed: no active client");
            return false;
        }

        boolean anyOk = false;

        for (ClientConn conn : clients) {
            boolean ok = sendToConn(conn, data);
            anyOk = anyOk || ok;
        }

        Log.i(TAG, "send() complete, anyOk=" + anyOk + ", clientsLeft=" + clients.size());

        return anyOk;
    }

    /**
     * 单播发送到指定 peer。
     */
    public synchronized boolean sendTo(String peer, byte[] data) {
        if (isBlank(peer)) {
            Log.w(TAG, "sendTo failed: empty peer");
            return false;
        }

        if (data == null || data.length == 0) {
            Log.w(TAG, "sendTo failed: empty data, peer=" + peer);
            return false;
        }

        Log.d(TAG, "sendTo target=" + peer
                + " len=" + data.length
                + " head=" + toHex(data, Math.min(data.length, 16)));

        ClientConn target = null;
        for (ClientConn conn : clients) {
            if (conn != null && peer.equals(conn.peer)) {
                target = conn;
                break;
            }
        }

        if (target == null) {
            Log.w(TAG, "sendTo failed: peer not found, target=" + peer + ", clients=" + getClientPeers());
            return false;
        }

        return sendToConn(target, data);
    }

    private boolean sendToConn(ClientConn conn, byte[] data) {
        if (conn == null || conn.socket == null) {
            return false;
        }

        Socket socket = conn.socket;

        try {
            if (socket.isClosed() || !socket.isConnected()) {
                closeConn(conn);
                return false;
            }

            OutputStream out = socket.getOutputStream();
            out.write(data);
            out.flush();

            Log.d(TAG, "sent to " + conn.peer + " len=" + data.length);
            return true;
        } catch (IOException e) {
            Log.w(TAG, "send error to " + conn.peer + ", remove client", e);
            closeConn(conn);
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "send unexpected error to " + conn.peer, t);
            closeConn(conn);
            return false;
        }
    }

    public synchronized void stop() {
        running = false;

        closeServerSocketQuietly();

        for (ClientConn conn : new ArrayList<>(clients)) {
            closeConn(conn);
        }

        clients.clear();
        pool.shutdownNow();

        Log.i(TAG, "stop() done");
    }

    public synchronized boolean closePeer(String peer) {
        ClientConn target = null;

        for (ClientConn conn : clients) {
            if (conn != null && peer.equals(conn.peer)) {
                target = conn;
                break;
            }
        }

        if (target == null) {
            return false;
        }

        closeConn(target);

        Log.i(TAG, "closePeer: " + peer + ", left=" + clients.size());
        return true;
    }

    public boolean isRunning() {
        return running;
    }

    public List<String> getClientPeers() {
        ArrayList<String> out = new ArrayList<>();

        for (ClientConn conn : clients) {
            if (conn == null || conn.socket == null) continue;

            Socket socket = conn.socket;
            if (!socket.isClosed() && socket.isConnected() && !isBlank(conn.peer)) {
                out.add(conn.peer);
            }
        }

        return out;
    }

    public List<String> getClientDisplayTexts() {
        ArrayList<String> out = new ArrayList<>();

        for (ClientConn conn : clients) {
            if (conn == null || conn.socket == null) continue;

            Socket socket = conn.socket;
            if (!socket.isClosed() && socket.isConnected() && !isBlank(conn.peer)) {
                out.add(conn.getDisplayText());
            }
        }

        return out;
    }

    /**
     * 返回当前连接快照。
     * 注意：这是浅拷贝列表，ClientConn 对象本身仍然是同一个引用。
     */
    public List<ClientConn> getClientSnapshot() {
        return new ArrayList<>(clients);
    }

    private void closeConn(ClientConn conn) {
        if (conn == null) return;
        clients.remove(conn);
        closeSocketQuietly(conn.socket);
    }

    private void closeServerSocketQuietly() {
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        } finally {
            serverSocket = null;
        }
    }

    private void closeSocketQuietly(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
    }

    private void safeOnClientConnected(String peer) {
        try {
            if (listener != null) {
                listener.onClientConnected(peer);
            }
        } catch (Throwable t) {
            Log.e(TAG, "onClientConnected listener error, peer=" + peer, t);
        }
    }

    private void safeOnClientDisconnected(String peer) {
        try {
            if (listener != null) {
                listener.onClientDisconnected(peer);
            }
        } catch (Throwable t) {
            Log.e(TAG, "onClientDisconnected listener error, peer=" + peer, t);
        }
    }

    public static String toHex(byte[] data, int length) {
        if (data == null || length <= 0) {
            return "";
        }

        int safeLen = Math.min(length, data.length);
        StringBuilder sb = new StringBuilder(safeLen * 3);

        for (int i = 0; i < safeLen; i++) {
            sb.append(String.format("%02X", data[i] & 0xFF));
            if (i + 1 < safeLen) {
                sb.append(' ');
            }
        }

        return sb.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
