package com.intellizondy.biofeedback.network.tcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 获取ipv4地址
 */
public final class LocalIpHelper {

    private LocalIpHelper() {}

    /**
     * 列出本机的私网 IPv4（含接口名）
     * @return
     */
    public static String listPrivateIPv4() {
        List<String> hits = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (nif.isLoopback()) continue; // 跳过 lo
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address) {
                        String ip = a.getHostAddress();
                        if (isPrivateIPv4(ip)) {
                            hits.add(nif.getName() + " -> " + ip);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // 若 Java API 没扫到，尝试 shell 兜底（不要求 root，失败再试 su）
        if (hits.isEmpty()) {
            String shell = tryParseIpAddrShell();
            if (shell != null && !shell.isEmpty()) return shell;
        }

        if (hits.isEmpty()) {
            return "(未找到私网 IPv4，常见网关可试 192.168.43.1 / 192.168.49.1)";
        }
        Collections.sort(hits, (l, r) -> prio(l) - prio(r));
        StringBuilder sb = new StringBuilder();
        for (String s : hits) sb.append(s).append('\n');
        return sb.toString().trim();
    }

    /** 返回一个“最可能的网关候选 IP”（列表中的第一项 IP），失败返回 null */
    public static String getBestGatewayCandidate() {
        String list = listPrivateIPv4();
        if (list == null || list.isEmpty() || list.startsWith("(")) return null;
        String firstLine = list.split("\n")[0];
        int arrow = firstLine.indexOf("->");
        if (arrow > 0) {
            return firstLine.substring(arrow + 2).trim();
        }
        return null;
    }

    /** 私网判断：10/8, 172.16–31/12, 192.168/16 */
    private static boolean isPrivateIPv4(String ip) {
        if (ip == null) return false;
        if (ip.startsWith("10.")) return true;
        if (ip.startsWith("192.168.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * /** 接口优先级：ap/softap* > br* > wlan* /swlan > 其他
     * @param line
     * @return
     */
    private static int prio(String line) {
        String n = line.split(" ")[0]; // 接口名
        if (n.startsWith("ap") || n.startsWith("softap")) return 0;
        if (n.startsWith("br")) return 1;
        if (n.startsWith("wlan")) return 2;
        if (n.startsWith("swlan")) return 3;
        return 9;
    }

    /** 尝试解析 `ip addr show` 输出；先普通，再 su */
    public static String tryParseIpAddrShell() {
        String out = exec("ip addr show");
        if (out == null || out.isEmpty()) out = exec("su -c ip addr show");
        if (out == null || out.isEmpty()) return null;

        List<String> lines = new ArrayList<>();
        String[] arr = out.split("\n");
        String currentIf = null;
        for (String line : arr) {
            line = line.trim();
            if (line.matches("\\d+:\\s*\\S+:.*")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String rest = line.substring(colon + 1).trim();
                    int colon2 = rest.indexOf(':');
                    currentIf = (colon2 > 0 ? rest.substring(0, colon2).trim() : rest.split("\\s+")[0]);
                }
            } else if (line.startsWith("inet ") && currentIf != null && !"lo".equals(currentIf)) {
                String[] toks = line.split("\\s+");
                if (toks.length >= 2) {
                    String cidr = toks[1];
                    String ip = cidr.split("/")[0];
                    if (isPrivateIPv4(ip)) {
                        lines.add(currentIf + " -> " + ip);
                    }
                }
            }
        }
        Collections.sort(lines, (l, r) -> prio(l) - prio(r));
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    public static String exec(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder(); String s;
            while ((s = br.readLine()) != null) sb.append(s).append('\n');
            br.close();
            p.waitFor();
            return sb.toString();
        } catch (Exception ignored) {}
        return null;
    }
}