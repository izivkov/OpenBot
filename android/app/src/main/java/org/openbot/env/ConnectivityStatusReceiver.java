package org.openbot.env;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.Formatter;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.json.JSONException;
import org.json.JSONObject;

public class ConnectivityStatusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        final ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();

        if (activeNetworkInfo != null) {
            WifiManager mgr = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
            int wifiIP = mgr.getConnectionInfo().getIpAddress();
            String myIp = android.text.format.Formatter.formatIpAddress(wifiIP);

            BotToControllerEventBus.emitEvent(createStatus("IP_ADDRESS", myIp));
        }
    }

    protected JSONObject createStatus(String name, String value) {
        try {
            return new JSONObject().put("status", new JSONObject().put(name, value));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new JSONObject();
    }

}