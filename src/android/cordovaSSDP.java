package com.scott.plugin;

import org.apache.cordova.*;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;
import cz.msebera.android.httpclient.Header;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;

import java.util.Scanner;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;

public class cordovaSSDP extends CordovaPlugin {

    private static final String TAG = "scott.plugin.cordovaSSDP";
    private JSONArray mDeviceList;
    private Context mContext;

    public cordovaSSDP(Context context){
        mContext = context;
    }

    public static String parseHeaderValue(String content, String headerName) {
        Scanner s = new Scanner(content);
        s.nextLine();
        while (s.hasNextLine()) {
          String line = s.nextLine();
          int index = line.indexOf(':');

          if (index == -1) {
            return null;
          }
          String header = line.substring(0, index);
          if (headerName.equalsIgnoreCase(header.trim())) {
            return line.substring(index + 1).trim();
          }
        }
        return null;
    }

    private void createServiceObjWithXMLData(String url, final JSONObject jsonObj) {

        SyncHttpClient syncRequest = new SyncHttpClient();
        syncRequest.get(mContext.getApplicationContext(), url, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                try {
                    JSONObject device = jsonObj;
                    device.put("xml", new String(responseBody));
                    mDeviceList.put(device);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody,
                                  Throwable error) {
                LOG.e(TAG, responseBody.toString());
            }
        });
    }

    public void search(String service, CallbackContext callbackContext) throws IOException {
        final int SSDP_PORT = 1982;
        //final int SSDP_SEARCH_PORT = 1982;
        final String SSDP_IP = "239.255.255.250";
        int TIMEOUT = 5000;

        DatagramSocket socket = null;

        //InetSocketAddress srcAddress = new InetSocketAddress(SSDP_SEARCH_PORT);
        InetSocketAddress dstAddress = new InetSocketAddress(InetAddress.getByName(SSDP_IP), SSDP_PORT);

        // Clear the cached Device List every time a new search is called
        mDeviceList = new JSONArray();

        // M-Search Packet
        StringBuffer discoveryMessage = new StringBuffer();
        discoveryMessage.append("M-SEARCH * HTTP/1.1\r\n");
        discoveryMessage.append("HOST: " + SSDP_IP + ":" + SSDP_PORT + "\r\n");
        
        discoveryMessage.append("ST:"+service+"\r\n");
        //discoveryMessage.append("ST:ssdp:all\r\n");
        discoveryMessage.append("MAN: \"ssdp:discover\"\r\n");
        discoveryMessage.append("MX: 2\r\n");
        discoveryMessage.append("\r\n");

        System.out.println("Request: " + discoveryMessage.toString() + "\n");

        byte[] discoveryMessageBytes = discoveryMessage.toString().getBytes();
        DatagramPacket discoveryPacket = new DatagramPacket(discoveryMessageBytes, discoveryMessageBytes.length, dstAddress);

        // Send multi-cast packet
        /*MulticastSocket multicast = null;
        try {
            multicast = new MulticastSocket(null);
            multicast.bind(srcAddress);
            multicast.setTimeToLive(4);
            multicast.send(discoveryPacket);
        } finally {
            multicast.disconnect();
            multicast.close();
        }*/

        socket = new DatagramSocket(SSDP_PORT);
        socket.setReuseAddress(true);

        socket.send(discoveryPacket);

        // Create a socket and wait for the response
        //DatagramSocket wildSocket = null;
        DatagramPacket receivePacket;

        try {
            //wildSocket = new DatagramSocket(SSDP_SEARCH_PORT);
            socket.setSoTimeout(TIMEOUT);

            while (true) {
                try {
                    receivePacket = new DatagramPacket(new byte[9216], 9216);
                    socket.receive(receivePacket);

                    //String message = new String(receivePacket.getData());
                    String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

                    try {
                        JSONObject device = new JSONObject();
                        device.put("USN", parseHeaderValue(message, "USN"));
                        device.put("LOCATION", parseHeaderValue(message, "LOCATION"));
                        device.put("ST", parseHeaderValue(message, "ST"));
                        device.put("Server", parseHeaderValue(message, "Server"));
                        mDeviceList.put(device);
                        //createServiceObjWithXMLData(parseHeaderValue(message, "LOCATION"), device);
                    } catch (JSONException e) {
                        System.out.println("* ERROR *: " + e.getMessage() + "\n");
                        e.printStackTrace();
                    }
                } catch (SocketTimeoutException e) {
                    callbackContext.success(mDeviceList);
                    break;
                }
            }
        } finally {
            if (socket != null) {
                socket.disconnect();
                socket.close();
            }
        }
    }
}
