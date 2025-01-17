package hw.dt83.udpchat;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;

@SuppressLint("CutPasteId")
public class MainActivity extends Activity {
	static final String LOG_TAG = "UDPchat";
	private static final int LISTENER_PORT = 50003;
	private static final int BUF_SIZE = 1024;
	private ContactManager contactManager;
	private String displayName;
	private boolean STARTED = false;
	private boolean IN_CALL = false;
	private boolean LISTEN = false;
	
	public final static String EXTRA_CONTACT = "hw.dt83.udpchat.CONTACT";
	public final static String EXTRA_IP = "hw.dt83.udpchat.IP";
	public final static String EXTRA_DISPLAYNAME = "hw.dt83.udpchat.DISPLAYNAME";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		Log.i(LOG_TAG, "UDPChat started");
		
		// START BUTTON
		// Pressing this buttons initiates the main functionality
		final Button btnStart = findViewById(R.id.buttonStart);
		btnStart.setOnClickListener(v -> {
			Log.i(LOG_TAG, "Start button pressed");
			STARTED = true;

			EditText displayNameText = findViewById(R.id.editTextDisplayName);
			displayName = displayNameText.getText().toString();

			displayNameText.setEnabled(false);
			btnStart.setEnabled(false);

			TextView text = findViewById(R.id.textViewSelectContact);
			text.setVisibility(View.VISIBLE);

			Button updateButton = findViewById(R.id.buttonUpdate);
			updateButton.setVisibility(View.VISIBLE);

			Button callButton = findViewById(R.id.buttonCall);
			callButton.setVisibility(View.VISIBLE);

			ScrollView scrollView = findViewById(R.id.scrollView);
			scrollView.setVisibility(View.VISIBLE);

			contactManager = new ContactManager(displayName, getBroadcastIp());
			startCallListener();
		});
		
		// UPDATE BUTTON
		// Updates the list of reachable devices
		final Button btnUpdate = findViewById(R.id.buttonUpdate);
		btnUpdate.setOnClickListener(v -> updateContactList());
		
		// CALL BUTTON
		// Attempts to initiate an audio chat session with the selected device
		final Button btnCall = findViewById(R.id.buttonCall);
		btnCall.setOnClickListener(v -> {
			RadioGroup radioGroup = findViewById(R.id.contactList);
			int selectedButton = radioGroup.getCheckedRadioButtonId();
			if(selectedButton == -1) {
				// If no device was selected, present an error message to the user
				Log.w(LOG_TAG, "Warning: no contact selected");
				final AlertDialog alert = new AlertDialog.Builder(MainActivity.this).create();
				alert.setTitle("Oops");
				alert.setMessage("You must select a contact first");
				alert.setButton(-1, "OK", (dialog, which) -> alert.dismiss());
				alert.show();
				return;
			}
			// Collect details about the selected contact
			RadioButton radioButton = findViewById(selectedButton);
			String contact = radioButton.getText().toString();
			InetAddress ip = contactManager.getContacts().get(contact);
			IN_CALL = true;

			// Send this information to the MakeCallActivity and start that activity
			Intent intent = new Intent(MainActivity.this, MakeCallActivity.class);
			intent.putExtra(EXTRA_CONTACT, contact);
			String address = ip != null ? ip.toString() : "null";
			address = address.substring(1);
			intent.putExtra(EXTRA_IP, address);
			intent.putExtra(EXTRA_DISPLAYNAME, displayName);
			startActivity(intent);
		});
	}
	
	private void updateContactList() {
		// Create a copy of the HashMap used by the ContactManager
		HashMap<String, InetAddress> contacts = contactManager.getContacts();
		// Create a radio button for each contact in the HashMap
		RadioGroup radioGroup = findViewById(R.id.contactList);
		radioGroup.removeAllViews();
		
		for(String name : contacts.keySet()) {
			RadioButton radioButton = new RadioButton(getBaseContext());
			radioButton.setText(name);
			radioButton.setTextColor(Color.BLACK);
			radioGroup.addView(radioButton);
		}
		
		radioGroup.clearCheck();
	}
	
	private InetAddress getBroadcastIp() {
		// Function to return the broadcast address, based on the IP address of the device
		try {
			WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
			WifiInfo wifiInfo = wifiManager.getConnectionInfo();
			int ipAddress = wifiInfo.getIpAddress();
			String addressString = toBroadcastIp(ipAddress);
			return InetAddress.getByName(addressString);
		} catch(UnknownHostException e) {
			Log.e(LOG_TAG, "UnknownHostException in getBroadcastIP: " + e);
			return null;
		}
		
	}
	
	private String toBroadcastIp(int ip) {
		// Returns converts an IP address in int format to a formatted string
		return (ip & 0xFF) + "." +
				((ip >> 8) & 0xFF) + "." +
				((ip >> 16) & 0xFF) + "." +
				"255";
	}
	
	private void startCallListener() {
		// Creates the listener thread
		LISTEN = true;
		Thread listener = new Thread(() -> {
			try {
				// Set up the socket and packet to receive
				Log.i(LOG_TAG, "Incoming call listener started");
				DatagramSocket socket = new DatagramSocket(LISTENER_PORT);
				socket.setSoTimeout(1000);
				byte[] buffer = new byte[BUF_SIZE];
				DatagramPacket packet = new DatagramPacket(buffer, BUF_SIZE);
				while(LISTEN) {
					// Listen for incoming call requests
					try {
						Log.i(LOG_TAG, "Listening for incoming calls");
						socket.receive(packet);
						String data = new String(buffer, 0, packet.getLength());
						Log.i(LOG_TAG, "Packet received from "+ packet.getAddress() +" with contents: " + data);
						String action = data.substring(0, 4);
						// Received an invalid request
						if(action.equals("CAL:")) {
							// Received a call request. Start the ReceiveCallActivity
							String address = packet.getAddress().toString();
							String name = data.substring(4, packet.getLength());

							Intent intent = new Intent(MainActivity.this, ReceiveCallActivity.class);
							intent.putExtra(EXTRA_CONTACT, name);
							intent.putExtra(EXTRA_IP, address.substring(1));
							IN_CALL = true;
							//LISTEN = false;
							//stopCallListener();
							startActivity(intent);
						} else Log.w(LOG_TAG, packet.getAddress() + " sent invalid message: " + data);
					} catch(Exception e) {
						throw new RuntimeException(e);
					}
				}
				Log.i(LOG_TAG, "Call Listener ending");
				socket.disconnect();
				socket.close();
			} catch(SocketException e) {
				Log.e(LOG_TAG, "SocketException in listener " + e);
			}
		});
		listener.start();
	}
	
	private void stopCallListener() {
		// Ends the listener thread
		LISTEN = false;
	}
	
	@Override
	public void onPause() {
		
		super.onPause();
		if(STARTED) {
			contactManager.bye(displayName);
			contactManager.stopBroadcasting();
			contactManager.stopListening();
			//STARTED = false;
		}
		stopCallListener();
		Log.i(LOG_TAG, "App paused!");
	}
	
	@Override
	public void onStop() {
		super.onStop();
		Log.i(LOG_TAG, "App stopped!");
		stopCallListener();
		if(!IN_CALL) finish();
	}
	
	@Override
	public void onRestart() {
		super.onRestart();
		Log.i(LOG_TAG, "App restarted!");
		IN_CALL = false;
		STARTED = true;
		contactManager = new ContactManager(displayName, getBroadcastIp());
		startCallListener();
	}
}
