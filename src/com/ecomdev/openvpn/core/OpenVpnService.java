package com.ecomdev.openvpn.core;

import android.Manifest.permission;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.VpnService;
import android.os.*;
import android.os.Handler.Callback;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import com.ecomdev.openvpn.activities.DisconnectVPN;
import com.ecomdev.openvpn.activities.LogWindow;
import com.ecomdev.openvpn.R;
import com.ecomdev.openvpn.VpnProfile;
import com.ecomdev.openvpn.core.VpnStatus.ByteCountListener;
import com.ecomdev.openvpn.core.VpnStatus.ConnectionStatus;
import com.ecomdev.openvpn.core.VpnStatus.StateListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;

import static com.ecomdev.openvpn.core.NetworkSpace.*;
import static com.ecomdev.openvpn.core.VpnStatus.ConnectionStatus.*;

public class OpenVpnService extends VpnService implements StateListener, Callback, ByteCountListener {
    public static final String START_SERVICE = "com.ecomdev.openvpn.START_SERVICE";
    public static final String START_SERVICE_STICKY = "com.ecomdev.openvpn.START_SERVICE_STICKY";
    public static final String ALWAYS_SHOW_NOTIFICATION = "com.ecomdev.openvpn.NOTIFICATION_ALWAYS_VISIBLE";
    public static final String DISCONNECT_VPN = "com.ecomdev.openvpn.DISCONNECT_VPN";
    private static final String PAUSE_VPN = "com.ecomdev.openvpn.PAUSE_VPN";
    private static final String RESUME_VPN = "com.ecomdev.openvpn.RESUME_VPN";
    private static final int OPENVPN_STATUS = 1;
    private static boolean mNotificationAlwaysVisible = false;
    private final Vector<String> mDnslist = new Vector<String>();
    private final NetworkSpace mRoutes = new NetworkSpace();
    private final NetworkSpace mRoutesv6 = new NetworkSpace();
    private final IBinder mBinder = new LocalBinder();
    private Thread mProcessThread = null;
    private VpnProfile mProfile;
    private String mDomain = null;
    private CIDRIP mLocalIP = null;
    private int mMtu;
    private String mLocalIPv6 = null;
    private DeviceStateReceiver mDeviceStateReceiver;
    private boolean mDisplayBytecount = false;
    private boolean mStarting = false;
    private long mConnecttime;
    private boolean mOvpn3 = false;
    private OpenVPNManagement mManagement;
    private String mLastTunCfg;
    private String mRemoteGW;

    // From: http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java
    public static String humanReadableByteCount(long bytes, boolean mbit) {
        if (mbit)
            bytes = bytes * 8;
        int unit = mbit ? 1000 : 1024;
        if (bytes < unit)
            return bytes + (mbit ? " bit" : " B");

        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (mbit ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (mbit ? "" : "");
        if (mbit)
            return String.format(Locale.getDefault(), "%.1f %sbit", bytes / Math.pow(unit, exp), pre);
        else
            return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    @Override
    public IBinder onBind(Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(START_SERVICE))
            return mBinder;
        else
            return super.onBind(intent);
    }

    @Override
    public void onRevoke() {
        mManagement.stopVPN();
        endVpnService();
    }

    // Similar to revoke but do not try to stop process
    public void processDied() {
        endVpnService();
    }

    private void endVpnService() {
        mProcessThread = null;
        VpnStatus.removeByteCountListener(this);
        unregisterDeviceStateReceiver();
        ProfileManager.setConntectedVpnProfileDisconnected(this);
        if (!mStarting) {
            stopForeground(!mNotificationAlwaysVisible);

            if (!mNotificationAlwaysVisible) {
                stopSelf();
                VpnStatus.removeStateListener(this);
            }
        }
    }

    private void showNotification(String msg, String tickerText, boolean lowpriority, long when, ConnectionStatus status) {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);


        int icon = getIconByConnectionStatus(status);

        android.app.Notification.Builder nbuilder = new Notification.Builder(this);

        if (mProfile != null)
            nbuilder.setContentTitle(getString(R.string.notifcation_title, mProfile.mName));
        else
            nbuilder.setContentTitle(getString(R.string.notifcation_title_notconnect));

        nbuilder.setContentText(msg);
        nbuilder.setOnlyAlertOnce(true);
        nbuilder.setOngoing(true);
        nbuilder.setContentIntent(getLogPendingIntent());
        nbuilder.setSmallIcon(icon);


        if (when != 0)
            nbuilder.setWhen(when);


        // Try to set the priority available since API 16 (Jellybean)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            jbNotificationExtras(lowpriority, nbuilder);

        if (tickerText != null && !tickerText.equals(""))
            nbuilder.setTicker(tickerText);

        @SuppressWarnings("deprecation")
        Notification notification = nbuilder.getNotification();


        mNotificationManager.notify(OPENVPN_STATUS, notification);
        startForeground(OPENVPN_STATUS, notification);
    }

    private int getIconByConnectionStatus(ConnectionStatus level) {
        switch (level) {
            case LEVEL_CONNECTED:
                return R.drawable.ic_stat_vpn;
            case LEVEL_AUTH_FAILED:
            case LEVEL_NONETWORK:
            case LEVEL_NOTCONNECTED:
                return R.drawable.ic_stat_vpn_offline;
            case LEVEL_CONNECTING_NO_SERVER_REPLY_YET:
            case LEVEL_WAITING_FOR_USER_INPUT:
                return R.drawable.ic_stat_vpn_outline;
            case LEVEL_CONNECTING_SERVER_REPLIED:
                return R.drawable.ic_stat_vpn_empty_halo;
            case LEVEL_VPNPAUSED:
                return android.R.drawable.ic_media_pause;
            case UNKNOWN_LEVEL:
            default:
                return R.drawable.ic_stat_vpn;

        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void jbNotificationExtras(boolean lowpriority,
                                      android.app.Notification.Builder nbuilder) {
        try {
            if (lowpriority) {
                Method setpriority = nbuilder.getClass().getMethod("setPriority", int.class);
                // PRIORITY_MIN == -2
                setpriority.invoke(nbuilder, -2);

                Method setUsesChronometer = nbuilder.getClass().getMethod("setUsesChronometer", boolean.class);
                setUsesChronometer.invoke(nbuilder, true);

            }

            Intent disconnectVPN = new Intent(this, DisconnectVPN.class);
            disconnectVPN.setAction(DISCONNECT_VPN);
            PendingIntent disconnectPendingIntent = PendingIntent.getActivity(this, 0, disconnectVPN, 0);

            nbuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.cancel_connection), disconnectPendingIntent);

            Intent pauseVPN = new Intent(this, OpenVpnService.class);
            if (mDeviceStateReceiver == null || !mDeviceStateReceiver.isUserPaused()) {
                pauseVPN.setAction(PAUSE_VPN);
                PendingIntent pauseVPNPending = PendingIntent.getService(this, 0, pauseVPN, 0);
                nbuilder.addAction(android.R.drawable.ic_media_pause,
                        getString(R.string.pauseVPN), pauseVPNPending);

            } else {
                pauseVPN.setAction(RESUME_VPN);
                PendingIntent resumeVPNPending = PendingIntent.getService(this, 0, pauseVPN, 0);
                nbuilder.addAction(android.R.drawable.ic_media_play,
                        getString(R.string.resumevpn), resumeVPNPending);
            }


            //ignore exception
        } catch (NoSuchMethodException nsm) {
            VpnStatus.logException(nsm);
        } catch (IllegalArgumentException e) {
            VpnStatus.logException(e);
        } catch (IllegalAccessException e) {
            VpnStatus.logException(e);
        } catch (InvocationTargetException e) {
            VpnStatus.logException(e);
        }

    }

    PendingIntent getLogPendingIntent() {
        // Let the configure Button show the Log
        Intent intent = new Intent(getBaseContext(), LogWindow.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent startLW = PendingIntent.getActivity(this, 0, intent, 0);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return startLW;

    }

    synchronized void registerDeviceStateReceiver(OpenVPNManagement magnagement) {
        // Registers BroadcastReceiver to track network connection changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        mDeviceStateReceiver = new DeviceStateReceiver(magnagement);
        registerReceiver(mDeviceStateReceiver, filter);
        VpnStatus.addByteCountListener(mDeviceStateReceiver);
    }

    synchronized void unregisterDeviceStateReceiver() {
        if (mDeviceStateReceiver != null)
            try {
                VpnStatus.removeByteCountListener(mDeviceStateReceiver);
                this.unregisterReceiver(mDeviceStateReceiver);
            } catch (IllegalArgumentException iae) {
                // I don't know why  this happens:
                // java.lang.IllegalArgumentException: Receiver not registered: com.ecomdev.openvpn.NetworkSateReceiver@41a61a10
                // Ignore for now ...
                iae.printStackTrace();
            }
        mDeviceStateReceiver = null;
    }

    public void userPause(boolean shouldBePaused) {
        if (mDeviceStateReceiver != null)
            mDeviceStateReceiver.userPause(shouldBePaused);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null && intent.getBooleanExtra(ALWAYS_SHOW_NOTIFICATION, false))
            mNotificationAlwaysVisible = true;

        VpnStatus.addStateListener(this);
        VpnStatus.addByteCountListener(this);

        if (intent != null && PAUSE_VPN.equals(intent.getAction())) {
            if (mDeviceStateReceiver != null)
                mDeviceStateReceiver.userPause(true);
            return START_NOT_STICKY;
        }

        if (intent != null && RESUME_VPN.equals(intent.getAction())) {
            if (mDeviceStateReceiver != null)
                mDeviceStateReceiver.userPause(false);
            return START_NOT_STICKY;
        }


        if (intent != null && START_SERVICE.equals(intent.getAction()))
            return START_NOT_STICKY;
        if (intent != null && START_SERVICE_STICKY.equals(intent.getAction())) {
            return START_REDELIVER_INTENT;
        }

        assert (intent != null);

        // Extract information from the intent.
        String prefix = getPackageName();
        String[] argv = intent.getStringArrayExtra(prefix + ".ARGV");
        String nativelibdir = intent.getStringExtra(prefix + ".nativelib");
        String profileUUID = intent.getStringExtra(prefix + ".profileUUID");

        mProfile = ProfileManager.get(this, profileUUID);

        String startTitle = getString(R.string.start_vpn_title, mProfile.mName);
        String startTicker = getString(R.string.start_vpn_ticker, mProfile.mName);
        showNotification(startTitle, startTicker,
                false, 0, LEVEL_CONNECTING_NO_SERVER_REPLY_YET);

        // Set a flag that we are starting a new VPN
        mStarting = true;
        // Stop the previous session by interrupting the thread.
        if (mManagement != null && mManagement.stopVPN())
            // an old was asked to exit, wait 1s
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }


        if (mProcessThread != null) {
            mProcessThread.interrupt();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        }
        // An old running VPN should now be exited
        mStarting = false;

        // Start a new session by creating a new thread.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mOvpn3 = prefs.getBoolean("ovpn3", false);
        mOvpn3 = false;


        // Open the Management Interface
        if (!mOvpn3) {

            // start a Thread that handles incoming messages of the managment socket
            OpenVpnManagementThread ovpnManagementThread = new OpenVpnManagementThread(mProfile, this);
            if (ovpnManagementThread.openManagementInterface(this)) {

                Thread mSocketManagerThread = new Thread(ovpnManagementThread, "OpenVPNManagementThread");
                mSocketManagerThread.start();
                mManagement = ovpnManagementThread;
                VpnStatus.logInfo("started Socket Thread");
            } else {
                return START_NOT_STICKY;
            }
        }


        Runnable processThread;
        if (mOvpn3) {

            OpenVPNManagement mOpenVPN3 = instantiateOpenVPN3Core();
            processThread = (Runnable) mOpenVPN3;
            mManagement = mOpenVPN3;


        } else {
            HashMap<String, String> env = new HashMap<String, String>();
            processThread = new OpenVPNThread(this, argv, env, nativelibdir);
        }

        mProcessThread = new Thread(processThread, "OpenVPNProcessThread");
        mProcessThread.start();

        if (mDeviceStateReceiver != null)
            unregisterDeviceStateReceiver();

        registerDeviceStateReceiver(mManagement);


        ProfileManager.setConnectedVpnProfile(this, mProfile);

        return START_NOT_STICKY;
    }

    private OpenVPNManagement instantiateOpenVPN3Core() {
        return null;
    }

    @Override
    public void onDestroy() {
        if (mProcessThread != null) {
            mManagement.stopVPN();

            mProcessThread.interrupt();
        }
        if (mDeviceStateReceiver != null) {
            this.unregisterReceiver(mDeviceStateReceiver);
        }
        // Just in case unregister for state
        VpnStatus.removeStateListener(this);

    }

    private String getTunConfigString() {
        // The format of the string is not important, only that
        // two identical configurations produce the same result
        String cfg = "TUNCFG UNQIUE STRING ips:";

        if (mLocalIP != null)
            cfg += mLocalIP.toString();
        if (mLocalIPv6 != null)
            cfg += mLocalIPv6.toString();

        cfg += "routes: " + TextUtils.join("|", mRoutes.getNetworks(true)) + TextUtils.join("|", mRoutesv6.getNetworks(true));
        cfg += "excl. routes:" + TextUtils.join("|", mRoutes.getNetworks(false)) + TextUtils.join("|", mRoutesv6.getNetworks(false));
        cfg += "dns: " + TextUtils.join("|", mDnslist);
        cfg += "domain: " + mDomain;
        cfg += "mtu: " + mMtu;
        return cfg;
    }

    public ParcelFileDescriptor openTun() {
        Builder builder = new Builder();

        if (mLocalIP == null && mLocalIPv6 == null) {
            VpnStatus.logError(getString(R.string.opentun_no_ipaddr));
            return null;
        }

        if (mLocalIP != null) {
            try {
                builder.addAddress(mLocalIP.mIp, mLocalIP.len);
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.dns_add_error, mLocalIP, iae.getLocalizedMessage());
                return null;
            }
        }

        if (mLocalIPv6 != null) {
            String[] ipv6parts = mLocalIPv6.split("/");
            try {
                builder.addAddress(ipv6parts[0], Integer.parseInt(ipv6parts[1]));
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.ip_add_error, mLocalIPv6, iae.getLocalizedMessage());
                return null;
            }

        }


        for (String dns : mDnslist) {
            try {
                builder.addDnsServer(dns);
            } catch (IllegalArgumentException iae) {
                VpnStatus.logError(R.string.dns_add_error, dns, iae.getLocalizedMessage());
            }
        }


        builder.setMtu(mMtu);


        for (NetworkSpace.ipAddress route : mRoutes.getPositiveIPList()) {
            try {
                builder.addRoute(route.getIPv4Address(), route.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(getString(R.string.route_rejected) + route + " " + ia.getLocalizedMessage());
            }
        }

        for (NetworkSpace.ipAddress route6 : mRoutesv6.getPositiveIPList()) {
            try {
                builder.addRoute(route6.getIPv6Address(), route6.networkMask);
            } catch (IllegalArgumentException ia) {
                VpnStatus.logError(getString(R.string.route_rejected) + route6 + " " + ia.getLocalizedMessage());
            }
        }

        if (mDomain != null)
            builder.addSearchDomain(mDomain);

        VpnStatus.logInfo(R.string.last_openvpn_tun_config);
        VpnStatus.logInfo(R.string.local_ip_info, mLocalIP.mIp, mLocalIP.len, mLocalIPv6, mMtu);
        VpnStatus.logInfo(R.string.dns_server_info, TextUtils.join(", ", mDnslist), mDomain);
        VpnStatus.logInfo(R.string.routes_info_incl, TextUtils.join(", ", mRoutes.getNetworks(true)), TextUtils.join(", ", mRoutesv6.getNetworks(true)));
        VpnStatus.logInfo(R.string.routes_info_excl, TextUtils.join(", ", mRoutes.getNetworks(false)),TextUtils.join(", ", mRoutesv6.getNetworks(false)));
        VpnStatus.logDebug(R.string.routes_debug, TextUtils.join(", ", mRoutes.getPositiveIPList()), TextUtils.join(", ", mRoutesv6.getPositiveIPList()));

        String session = mProfile.mName;
        if (mLocalIP != null && mLocalIPv6 != null)
            session = getString(R.string.session_ipv6string, session, mLocalIP, mLocalIPv6);
        else if (mLocalIP != null)
            session = getString(R.string.session_ipv4string, session, mLocalIP);

        builder.setSession(session);

        // No DNS Server, log a warning
        if (mDnslist.size() == 0)
            VpnStatus.logInfo(R.string.warn_no_dns);

        mLastTunCfg = getTunConfigString();

        // Reset information
        mDnslist.clear();
        mRoutes.clear();
        mRoutesv6.clear();
        mLocalIP = null;
        mLocalIPv6 = null;
        mDomain = null;

        builder.setConfigureIntent(getLogPendingIntent());

        try {
            return builder.establish();
        } catch (Exception e) {
            VpnStatus.logError(R.string.tun_open_error);
            VpnStatus.logError(getString(R.string.error) + e.getLocalizedMessage());
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                VpnStatus.logError(R.string.tun_error_helpful);
            }
            return null;
        }

    }

    public void addDNS(String dns) {
        mDnslist.add(dns);
    }

    public void setDomain(String domain) {
        if (mDomain == null) {
            mDomain = domain;
        }
    }

    public void addRoute (String dest, String mask, String gateway, String device) {
        CIDRIP route = new CIDRIP(dest, mask);
        boolean include = isAndroidTunDevice(device);

        NetworkSpace.ipAddress gatewayIP = new NetworkSpace.ipAddress(new CIDRIP(gateway, 32),false);

        NetworkSpace.ipAddress localNet = new NetworkSpace.ipAddress(mLocalIP,true);
        if (localNet.containsNet(gatewayIP))
            include=true;

        if (gateway!= null &&
                (gateway.equals("255.255.255.255") || gateway.equals(mRemoteGW)))
            include=true;


        if (route.len == 32 && !mask.equals("255.255.255.255")) {
            VpnStatus.logWarning(R.string.route_not_cidr, dest, mask);
        }

        if (route.normalise())
            VpnStatus.logWarning(R.string.route_not_netip, dest, route.len, route.mIp);

        mRoutes.addIP(route, include);
    }

    public void addRoutev6(String network, String device) {
        String[] v6parts = network.split("/");
        boolean included = isAndroidTunDevice(device);

        // Tun is opened after ROUTE6, no device name may be present

        try {
            Inet6Address ip = (Inet6Address) InetAddress.getAllByName(v6parts[0])[0];
            int mask = Integer.parseInt(v6parts[1]);
            mRoutesv6.addIPv6(ip, mask, included);

        } catch (UnknownHostException e) {
            VpnStatus.logException(e);
        }


    }

    private boolean isAndroidTunDevice(String device) {
        return device!=null &&
                (device.startsWith("tun") || "(null)".equals(device) || "vpnservice-tun".equals(device));
    }

    public void setMtu(int mtu) {
        mMtu = mtu;
    }

    public void setLocalIP(CIDRIP cdrip) {
        mLocalIP = cdrip;
    }

    public void setLocalIP(String local, String netmask, int mtu, String mode) {
        mLocalIP = new CIDRIP(local, netmask);
        mMtu = mtu;

        if (mLocalIP.len == 32 && !netmask.equals("255.255.255.255")) {
            // get the netmask as IP
            long netint = CIDRIP.getInt(netmask);
            if (Math.abs(netint - mLocalIP.getInt()) == 1) {
                if ("net30".equals(mode))
                    mLocalIP.len = 30;
                else
                    mLocalIP.len = 31;
            } else {
                VpnStatus.logWarning(R.string.ip_not_cidr, local, netmask, mode);
            }
        }

        if ("p2p".equals(mode))
            mRemoteGW=netmask;
        else
            mRemoteGW=null;

    }

    public void setLocalIPv6(String ipv6addr) {
        mLocalIPv6 = ipv6addr;
    }

    @Override
    public void updateState(String state, String logmessage, int resid, ConnectionStatus level) {
        // If the process is not running, ignore any state,
        // Notification should be invisible in this state
        doSendBroadcast(state, level);
        if (mProcessThread == null && !mNotificationAlwaysVisible)
            return;

        // Display byte count only after being connected

        {
            if (level == LEVEL_WAITING_FOR_USER_INPUT) {
                // The user is presented a dialog of some kind, no need to inform the user
                // with a notifcation
                return;
            } else if (level == LEVEL_CONNECTED) {
                mDisplayBytecount = true;
                mConnecttime = System.currentTimeMillis();
            } else {
                mDisplayBytecount = false;
            }

            // Other notifications are shown,
            // This also mean we are no longer connected, ignore bytecount messages until next
            // CONNECTED
            String ticker = getString(resid);
            showNotification(getString(resid) + " " + logmessage, ticker, false, 0, level);

        }
    }

    private void doSendBroadcast(String state, ConnectionStatus level) {
        Intent vpnstatus = new Intent();
        vpnstatus.setAction("com.ecomdev.openvpn.VPN_STATUS");
        vpnstatus.putExtra("status", level.toString());
        vpnstatus.putExtra("detailstatus", state);
        sendBroadcast(vpnstatus, permission.ACCESS_NETWORK_STATE);
    }

    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        if (mDisplayBytecount) {
            String netstat = String.format(getString(R.string.statusline_bytecount),
                    humanReadableByteCount(in, false),
                    humanReadableByteCount(diffIn / OpenVPNManagement.mBytecountInterval, true),
                    humanReadableByteCount(out, false),
                    humanReadableByteCount(diffOut / OpenVPNManagement.mBytecountInterval, true));

            boolean lowpriority = !mNotificationAlwaysVisible;
            showNotification(netstat, null, lowpriority, mConnecttime, LEVEL_CONNECTED);
        }

    }

    @Override
    public boolean handleMessage(Message msg) {
        Runnable r = msg.getCallback();
        if (r != null) {
            r.run();
            return true;
        } else {
            return false;
        }
    }

    public OpenVPNManagement getManagement() {
        return mManagement;
    }

    public String getTunReopenStatus() {
        String currentConfiguration = getTunConfigString();
        if (currentConfiguration.equals(mLastTunCfg))
            return "NOACTION";
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
            return "OPEN_AFTER_CLOSE";
        else
            return "OPEN_BEFORE_CLOSE";
    }

    public class LocalBinder extends Binder {
        public OpenVpnService getService() {
            // Return this instance of LocalService so clients can call public methods
            return OpenVpnService.this;
        }
    }
}
