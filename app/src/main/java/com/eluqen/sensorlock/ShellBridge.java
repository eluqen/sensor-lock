package com.eluqen.sensorlock;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShellBridge {
    private static final Pattern MIC =
            Pattern.compile("sensor=1[\\s\\S]*?state_type=(\\d+)");
    private static final Pattern CAM =
            Pattern.compile("sensor=2[\\s\\S]*?state_type=(\\d+)");

    private static final int COMMAND_TIMEOUT_MS = 3500;
    private static final ExecutorService CLIENTS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sensorlock-bridge-client");
        t.setDaemon(true);
        return t;
    });

    private ShellBridge() {}

    public static void main(String[] args) throws Exception {
        if (args == null || args.length < 2) return;

        final int port = Integer.parseInt(args[0]);
        final String token = args[1];

        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));

        while (true) {
            final Socket client = server.accept();
            CLIENTS.execute(() -> {
                try {
                    handle(client, token);
                } catch (Throwable ignored) {
                } finally {
                    try { client.close(); } catch (Throwable ignored) {}
                }
            });
        }
    }

    private static void handle(Socket socket, String token) throws Exception {
        socket.setSoTimeout(4500);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        String line = reader.readLine();
        if (line == null) return;

        String prefix = token + "|";
        if (!line.startsWith(prefix)) {
            writer.println("ERR auth");
            return;
        }

        String command = line.substring(prefix.length());

        try {
            if ("PING".equals(command)) {
                writer.println("PONG");
                return;
            }

            if ("BLOCK".equals(command)) {
                setPrivacy(true);
                writer.println(readState());
                return;
            }

            if ("ALLOW".equals(command)) {
                setPrivacy(false);
                writer.println(readState());
                return;
            }

            if ("STATE".equals(command)) {
                writer.println(readState());
                return;
            }

            if ("SELFTEST".equals(command)) {
                PrivacyValues current = readValues();
                setOne("camera", current.cameraBlocked);
                setOne("microphone", current.microphoneBlocked);
                writer.println(readState());
                return;
            }

            if ("STOP".equals(command)) {
                writer.println("OK stopping");
                writer.flush();
                System.exit(0);
                return;
            }

            writer.println("ERR unknown");
        } catch (Throwable t) {
            writer.println("ERR command");
        }
    }

    private static void setPrivacy(boolean blocked) throws Exception {
        setOne("camera", blocked);
        setOne("microphone", blocked);
    }

    private static void setOne(String sensor, boolean blocked) throws Exception {
        run("/system/bin/cmd sensor_privacy " +
                (blocked ? "enable" : "disable") + " 0 " + sensor,
                COMMAND_TIMEOUT_MS);
    }

    private static String readState() throws Exception {
        PrivacyValues values = readValues();
        return "STATE mic=" + (values.microphoneBlocked ? 1 : 0) +
                " cam=" + (values.cameraBlocked ? 1 : 0);
    }

    private static PrivacyValues readValues() throws Exception {
        String raw = run("/system/bin/dumpsys sensor_privacy", COMMAND_TIMEOUT_MS);
        Integer mic = find(MIC, raw);
        Integer cam = find(CAM, raw);
        if (mic == null || cam == null) throw new Exception("Invalid sensor privacy state");
        return new PrivacyValues(mic == 1, cam == 1);
    }

    private static Integer find(Pattern pattern, String raw) {
        Matcher matcher = pattern.matcher(raw == null ? "" : raw);
        if (!matcher.find()) return null;
        try { return Integer.parseInt(matcher.group(1)); }
        catch (Throwable ignored) { return null; }
    }

    private static String run(String command, long timeoutMs) throws Exception {
        Process process = new ProcessBuilder("/system/bin/sh", "-c", command)
                .redirectErrorStream(true)
                .start();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread collector = new Thread(() -> {
            try {
                InputStream input = process.getInputStream();
                byte[] buffer = new byte[2048];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) != -1) {
                    int keep = Math.min(read, Math.max(0, 131072 - total));
                    if (keep > 0) {
                        output.write(buffer, 0, keep);
                        total += keep;
                    }
                }
            } catch (Throwable ignored) {
            }
        }, "sensorlock-shell-output");
        collector.setDaemon(true);
        collector.start();

        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroy();
            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(500, TimeUnit.MILLISECONDS);
            }
            throw new Exception("shell timeout");
        }

        try { collector.join(300); } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        int code = process.exitValue();
        String text = output.toString("UTF-8");
        if (code != 0) throw new Exception("shell exit=" + code + " " + text);
        return text;
    }

    private static final class PrivacyValues {
        final boolean microphoneBlocked;
        final boolean cameraBlocked;

        PrivacyValues(boolean microphoneBlocked, boolean cameraBlocked) {
            this.microphoneBlocked = microphoneBlocked;
            this.cameraBlocked = cameraBlocked;
        }
    }
}
