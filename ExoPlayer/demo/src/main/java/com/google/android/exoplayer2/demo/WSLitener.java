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
            int PORT = 8090;
            theReader = r;
            WSServer theServer = new WSServer(new InetSocketAddress(PORT));
            WServerThread wServerThread = new WServerThread(theServer);
            wServerThread.start();
        } catch(Exception e) {
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
                theServer.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private class WSServer extends WebSocketServer{

        private WSServer( InetSocketAddress address ) {
            super( address );
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            // We can check if the conn.getRemoteSocketAddress() is allowed ...
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            // We will not close because we can relaunch or change the current stream
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            if(theReader!=null) {
                try {
                    theReader.read(message);
                    conn.send("OK");
                } catch(Exception e) {
                    conn.send(e.toString());
                }
            }
            else {
                conn.send("No reader");
            }
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            // TODO: Handle errors
        }
    }

}