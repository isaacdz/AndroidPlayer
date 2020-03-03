package com.google.android.exoplayer2.demo;

import org.java_websocket.*;
import org.java_websocket.server.*;
import org.java_websocket.handshake.*;
import java.net.*;

class WSListener {

    /**
     * This is the interface to be used to read the messages
     */
    interface Reader {
        void read (String txt) throws Exception ;
    }

    private Reader theReader = null;

    WSListener(Reader r) {
        // We will handle any exception to avoid problems inside the activity
        try {
            android.util.Log.v("ADF","ADF WSListener start...");
            int PORT = 8090;
            theReader = r;
            WSServer theServer = new WSServer(new InetSocketAddress(PORT));
            WServerThread wServerThread = new WServerThread(theServer);
            wServerThread.start();
            android.util.Log.v("ADF","ADF WSListener started");
        } catch(Exception e) {
            android.util.Log.v("ADF","ADF WSListener EX");
            e.printStackTrace();
        }
    }

    private class WServerThread extends Thread {

        private WSServer theServer;

        WServerThread(WSServer s) {
            theServer = s;
        }

        @Override
        public void run() {
            try {
                android.util.Log.v("ADF","ADF WSServer start...");
                theServer.run();
                android.util.Log.v("ADF","ADF WSServer started");
            } catch (Exception e) {
                android.util.Log.v("ADF","ADF WSServer EX");
                e.printStackTrace();
            }
        }

    }

    private class WSServer extends WebSocketServer{

        private WSServer( InetSocketAddress address ) {
            super( address );
        }

        @Override
        public void onStart() {
            android.util.Log.v("ADF","ADF WSServer onStart");
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            // We can check if the conn.getRemoteSocketAddress() is allowed ...
            android.util.Log.v("ADF","ADF WSServer onOpen");
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            // We will not close because we can relaunch or change the current stream
            android.util.Log.v("ADF","ADF WSServer onClose");
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            android.util.Log.v("ADF","ADF WSServer onMessage ---");
            if(theReader!=null) {
                try {
                    theReader.read(message);
                    conn.send("OK: Received message from client");
                } catch(Exception e) {
                    conn.send(e.toString());
                }
            }
            else {
                android.util.Log.v("ADF","ADF WSServer onMessage. No reader!!");
                conn.send("No reader");
            }
            android.util.Log.v("ADF","ADF WSServer onMessage +++");
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            // TODO: Handle errors
            android.util.Log.v("ADF","ADF WSServer onError EX:" + ex);
        }
    }

}