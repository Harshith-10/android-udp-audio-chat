package hw.dt83.udpchat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class ReceiveCallActivity extends Activity {
	private static final String LOG_TAG = "ReceiveCall";
	private static final int BROADCAST_PORT = 50002;
	private static final int BUF_SIZE = 1024;
	private String contactIp;
	private boolean LISTEN = true;
	private boolean IN_CALL = false;
	private AudioCall call;
	
	@SuppressLint({"SetTextI18n", "CutPasteId"})
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_receive_call);
		
		Intent intent = getIntent();
		String contactName = intent.getStringExtra(MainActivity.EXTRA_CONTACT);
		contactIp = intent.getStringExtra(MainActivity.EXTRA_IP);
		
		TextView textView = findViewById(R.id.textViewIncomingCall);
		textView.setText("Incoming call: " + contactName);
		
		final Button endButton = findViewById(R.id.buttonEndCall1);
		endButton.setVisibility(View.INVISIBLE);
		
		startListener();
		
		// ACCEPT BUTTON
		Button acceptButton = findViewById(R.id.buttonAccept);
		acceptButton.setOnClickListener(v -> {
			try {
				// Accepting call. Send a notification and start the call
				sendMessage("ACC:");
				InetAddress address = InetAddress.getByName(contactIp);
				Log.i(LOG_TAG, "Calling " + address);
				IN_CALL = true;
				call = new AudioCall(address);
				call.startCall();
				// Hide the buttons as they're not longer required
				Button accept = findViewById(R.id.buttonAccept);
				accept.setEnabled(false);

				Button reject = findViewById(R.id.buttonReject);
				reject.setEnabled(false);

				endButton.setVisibility(View.VISIBLE);
			} catch(UnknownHostException e) {
				Log.e(LOG_TAG, "UnknownHostException in acceptButton: " + e);
			} catch(Exception e) {
				Log.e(LOG_TAG, "Exception in acceptButton: " + e);
			}
		});
		
		// REJECT BUTTON
		Button rejectButton = findViewById(R.id.buttonReject);
		rejectButton.setOnClickListener(v -> {
			// Send a reject notification and end the call
			sendMessage("REJ:");
			endCall();
		});
		
		// END BUTTON
		endButton.setOnClickListener(v -> endCall());
	}
	
	private void endCall() {
		// End the call and send a notification
		stopListener();
		if(IN_CALL) call.endCall();
		sendMessage("END:");
		finish();
	}
	
	private void startListener() {
		// Creates the listener thread
		LISTEN = true;
		Thread listenThread = new Thread(() -> {
			try {
				Log.i(LOG_TAG, "Listener started!");
				DatagramSocket socket = new DatagramSocket(BROADCAST_PORT);
				socket.setSoTimeout(1500);
				byte[] buffer = new byte[BUF_SIZE];
				DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
				while(LISTEN) {
					try {
						Log.i(LOG_TAG, "Listening for packets");
						socket.receive(packet);
						String data = new String(buffer, 0, packet.getLength());
						Log.i(LOG_TAG, "Packet received from "+ packet.getAddress() +" with contents: " + data);
						String action = data.substring(0, 4);
						// End call notification received. End call
						// Invalid notification received.
						if(action.equals("END:")) endCall();
						else Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
					} catch(IOException e) {

						Log.e(LOG_TAG, "IOException in Listener " + e);
					}
				}
				Log.i(LOG_TAG, "Listener ending");
				socket.disconnect();
				socket.close();
			} catch(SocketException e) {
				Log.e(LOG_TAG, "SocketException in Listener " + e);
				endCall();
			}
		});
		listenThread.start();
	}
	
	private void stopListener() {
		// Ends the listener thread
		LISTEN = false;
	}
	
	private void sendMessage(final String message) {
		// Creates a thread for sending notifications
		Thread replyThread = new Thread(() -> {
			try {
				InetAddress address = InetAddress.getByName(contactIp);
				byte[] data = message.getBytes();
				DatagramSocket socket = new DatagramSocket();
				DatagramPacket packet = new DatagramPacket(data, data.length, address, BROADCAST_PORT);
				socket.send(packet);
				Log.i(LOG_TAG, "Sent message( " + message + " ) to " + contactIp);
				socket.disconnect();
				socket.close();
			} catch(UnknownHostException e) {
				Log.e(LOG_TAG, "Failure. UnknownHostException in sendMessage: " + contactIp);
			} catch(SocketException e) {
				Log.e(LOG_TAG, "Failure. SocketException in sendMessage: " + e);
			} catch(IOException e) {
				Log.e(LOG_TAG, "Failure. IOException in sendMessage: " + e);
			}
		});
		replyThread.start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.receive_call, menu);
		return true;
	}
}