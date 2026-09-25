package com.eluqen.sensorlock;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.android.AdbMdns;
import io.github.muntashirakon.adb.android.AndroidUtils;

public class PairingReceiver extends BroadcastReceiver {
    static final String DISCOVER="com.eluqen.sensorlock.DISCOVER";
    static final String PAIR="com.eluqen.sensorlock.PAIR";
    static final String CODE="pair_code", CH="pairing_v5";
    static final int ID=5051;
    static final String PREFS="pairing_state", HOST="host", PORT="port";
    static final String SECURE_PERMISSION="android.permission.WRITE_SECURE_SETTINGS";

    @Override public void onReceive(final Context c, Intent i) {
        if (alreadyConnected(c)) {
            cancelPairingNotifications(c);
            return;
        }

        if (DISCOVER.equals(i.getAction())) {
            final PendingResult pr=goAsync();
            progress(c,text(c,R.string.pair_finding_title),text(c,R.string.pair_keep_open));
            new Thread(new Runnable(){ public void run(){ discover(c,pr); }}).start();
            return;
        }
        if (!PAIR.equals(i.getAction())) return;

        Bundle b=RemoteInput.getResultsFromIntent(i);
        CharSequence x=b==null?null:b.getCharSequence(CODE);
        final String code=x==null?"":x.toString().trim();
        if (!code.matches("\\d{6}")) {
            pairInput(c,text(c,R.string.pair_invalid_code_title),text(c,R.string.pair_invalid_code_text));
            return;
        }

        SharedPreferences sp=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        final int port=sp.getInt(PORT,-1);
        if (port<1) {
            ready(c,text(c,R.string.pair_port_missing_title),text(c,R.string.pair_port_missing_text));
            return;
        }

        final PendingResult pr=goAsync();
        progress(c,text(c,R.string.pair_pairing_title),text(c,R.string.pair_keep_open));

        new Thread(new Runnable(){ public void run(){
            AbsAdbConnectionManager m=null;
            boolean authenticated=false;
            try {
                m=AdbConnectionManager.getInstance(c);
                String localHost=AndroidUtils.getHostIpAddress(c);
                if (!m.pair(localHost,port,code)) {
                    throw new Exception("ADB rejected the pairing request.");
                }

                boolean alreadyGranted =
                        c.checkSelfPermission(SECURE_PERMISSION)==PackageManager.PERMISSION_GRANTED;

                progress(c,text(c,R.string.pair_finishing_title),
                        text(c,R.string.pair_finishing_text));

                m.setThrowOnUnauthorised(true);
                Throwable first=null;
                try {
                    if (!m.connectTls(c,5000)) {
                        throw new Exception("Local ADB did not connect.");
                    }
                } catch (Throwable t) {
                    first=t;
                    try { m.disconnect(); } catch (Throwable ignored) {}
                    Thread.sleep(300);
                    if (!m.connectTls(c,8000)) {
                        throw new Exception("Local ADB did not connect after retry. First="+safe(first));
                    }
                }

                if (!alreadyGranted) {
                    runShell(m,
                            "shell:pm grant " + c.getPackageName() +
                            " android.permission.WRITE_SECURE_SETTINGS; echo SENSOR_LOCK_GRANT_DONE");

                    if (c.checkSelfPermission(SECURE_PERMISSION)!=PackageManager.PERMISSION_GRANTED) {
                        Thread.sleep(150);
                    }
                    if (c.checkSelfPermission(SECURE_PERMISSION)!=PackageManager.PERMISSION_GRANTED) {
                        throw new Exception("Android did not grant WRITE_SECURE_SETTINGS after local ADB pairing.");
                    }
                }

                SensorController.pairingVerified(c);
                authenticated=true;

                SensorController.startLocalBridgeViaManager(c,m);
                if (!SensorController.verifyLocalBridgeFunctional(c)) {
                    throw new Exception("Local bridge functional verification failed");
                }
                SensorController.pairingCompleted(c);

                c.getSharedPreferences("sensor_lock_state",Context.MODE_PRIVATE).edit()
                        .putString("last_message","")
                        .apply();

                try { m.disconnect(); } catch (Throwable ignored) {}
                SensorController.cleanupNow(c);
                clearPairingNotifications(c);
            } catch(Throwable t) {
                try { if(m!=null) m.disconnect(); } catch(Throwable ignored) {}
                if (c.checkSelfPermission(SECURE_PERMISSION)==PackageManager.PERMISSION_GRANTED) {
                    SensorController.cleanupNow(c);
                }

                if (authenticated) {
                    cancelPending(c);
                    SensorController.bridgeStartFailedAfterPairing(c);
                } else {
                    pairInput(c,text(c,R.string.pair_failed_title),text(c,R.string.operation_failed));
                }
            } finally {
                pr.finish();
            }
        }}).start();
    }

    static void discover(final Context c, PendingResult pr) {
        AdbMdns mdns=null;
        try {
            final AtomicReference<InetAddress> host=new AtomicReference<InetAddress>();
            final AtomicInteger port=new AtomicInteger(-1);
            final CountDownLatch latch=new CountDownLatch(1);

            mdns=new AdbMdns(c.getApplicationContext(),AdbMdns.SERVICE_TYPE_TLS_PAIRING,
                new AdbMdns.OnAdbDaemonDiscoveredListener() {
                    @Override public void onPortChanged(InetAddress a,int p) {
                        if(a!=null && p>0){ host.set(a); port.set(p); latch.countDown(); }
                    }
                });

            mdns.start();
            if(!latch.await(20,TimeUnit.SECONDS) || port.get()<1) {
                throw new Exception("No pairing port found. Keep 'Pair device with pairing code' open and try FIND PAIRING PORT again.");
            }

            if (alreadyConnected(c)) {
                cancelPending(c);
                return;
            }

            c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                    .putString(HOST,host.get()==null?null:host.get().getHostAddress())
                    .putInt(PORT,port.get())
                    .apply();

            pairInput(c,text(c,R.string.pair_found_title),
                    text(c,R.string.pair_found_text));
        } catch(Throwable t) {
            if (alreadyConnected(c)) {
                cancelPending(c);
            } else {
                ready(c,text(c,R.string.pair_discovery_failed),text(c,R.string.pair_port_missing_text));
            }
        } finally {
            if(mdns!=null) try{ mdns.stop(); }catch(Throwable ignored){}
            pr.finish();
        }
    }

    static boolean start(Context c) {
        if (alreadyConnected(c)) {
            cancelPairingNotifications(c);
            return false;
        }

        cancelPairingNotifications(c);
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();
        ready(c,text(c,R.string.pair_start_title),
                text(c,R.string.pair_start_text));
        return true;
    }

    static void cancelPairingNotifications(Context c) {
        NotificationManager nm = get(c);
        if (nm != null) {
            nm.cancel(ID);
        }
    }

    static void clearPairingNotifications(Context c) {
        NotificationManager nm = get(c);
        if (nm != null) {
            nm.cancel(ID);
            nm.cancel(ID + 1);
        }
    }

    static void cancelPending(Context c) {
        cancelPairingNotifications(c);
    }

    static boolean alreadyConnected(Context c) {
        return SensorController.isPairingVerified(c)
                && SensorController.isConnectionHealthyNow(c);
    }

    static PendingIntent pi(Context c,String action,int req,boolean mutable){
        Intent i=new Intent(c,PairingReceiver.class).setAction(action);
        int f=PendingIntent.FLAG_UPDATE_CURRENT;
        if(Build.VERSION.SDK_INT>=31) {
            f|=mutable?PendingIntent.FLAG_MUTABLE:PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getBroadcast(c,req,i,f);
    }

    static void ready(Context c,String title,String text){
        channel(c);
        Notification.Action a=new Notification.Action.Builder(
                android.R.drawable.ic_menu_search,
                text(c,R.string.pair_find_port),
                pi(c,DISCOVER,11,false)).build();

        get(c).notify(ID,new Notification.Builder(c,CH)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(false)
                .addAction(a)
                .build());
    }

    static void pairInput(Context c,String title,String text){
        channel(c);
        RemoteInput ri=new RemoteInput.Builder(CODE)
                .setLabel(text(c,R.string.pair_input_label))
                .build();

        Notification.Action a=new Notification.Action.Builder(
                android.R.drawable.ic_menu_send,
                text(c,R.string.pair_enter_code),
                pi(c,PAIR,12,true))
                .addRemoteInput(ri)
                .build();

        get(c).notify(ID,new Notification.Builder(c,CH)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(a)
                .build());
    }

    static void progress(Context c,String title,String text){
        channel(c);
        get(c).notify(ID,new Notification.Builder(c,CH)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0,0,true)
                .build());
    }

    static void result(Context c,String title,String text){
        channel(c);
        get(c).notify(ID,new Notification.Builder(c,CH)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build());
    }

    static NotificationManager get(Context c){
        return (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    static void channel(Context c){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel ch=new NotificationChannel(
                    CH,text(c,R.string.pair_channel_name),NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(text(c,R.string.pair_channel_desc));
            ch.setSound(null,null);
            ch.enableVibration(false);
            get(c).createNotificationChannel(ch);
        }
    }

    static String runShell(AbsAdbConnectionManager manager,String destination) throws Exception {
        AdbStream stream=manager.openStream(destination);
        InputStream is=stream.openInputStream();
        ByteArrayOutputStream os=new ByteArrayOutputStream();
        byte[] buf=new byte[2048];

        while(true){
            int n;
            try { n=is.read(buf); }
            catch(Throwable t){ break; }
            if(n>0) os.write(buf,0,n); else break;
            if(stream.isClosed()) break;
        }

        try { stream.close(); } catch(Throwable ignored){}
        return new String(os.toByteArray(),"UTF-8");
    }

    static String text(Context c,int resId){
        return LanguageManager.wrap(c).getString(resId);
    }

    static String safe(Throwable t){
        if(t==null) return "unknown";
        String m=t.getMessage();
        return m==null?t.toString():m;
    }
}
