package com.eluqen.sensorlock;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;

public final class LocalShellClient {
    private static final String PREFS = "shell_bridge";
    private static final String KEY_PORT = "loopback_port";
    private static final String KEY_TOKEN = "token";

    private LocalShellClient() {}

    public static int getPort(Context context) {
        android.content.SharedPreferences sp =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int value = sp.getInt(KEY_PORT, -1);
        if (value >= 20000 && value <= 60000) return value;

        value = randomPort();
        sp.edit().putInt(KEY_PORT, value).commit();
        return value;
    }

    public static String getToken(Context context) {
        android.content.SharedPreferences sp =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String value = sp.getString(KEY_TOKEN, null);
        if (value != null && !value.isEmpty()) return value;

        value = randomHex(24);
        sp.edit().putString(KEY_TOKEN, value).commit();
        return value;
    }

    public static void rotateIdentity(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_PORT, randomPort())
                .putString(KEY_TOKEN, randomHex(24))
                .commit();
    }

    public static boolean ping(Context context) {
        try {
            return "PONG".equals(request(context, "PING", 700));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String request(Context context, String command, int timeoutMs)
            throws Exception {
        int port = getPort(context);
        String token = getToken(context);

        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), port), timeoutMs);
            socket.setSoTimeout(timeoutMs);

            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            writer.println(token + "|" + command);
            String response = reader.readLine();
            if (response == null) throw new Exception("No local bridge response");
            return response;
        } finally {
            try { socket.close(); } catch (Throwable ignored) {}
        }
    }

    private static int randomPort() {
        return 32000 + new SecureRandom().nextInt(16000);
    }

    private static String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        new SecureRandom().nextBytes(data);
        StringBuilder out = new StringBuilder(data.length * 2);
        for (byte b : data) {
            out.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return out.toString();
    }
}
