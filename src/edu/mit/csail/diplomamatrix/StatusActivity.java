package edu.mit.csail.diplomamatrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.PrintWriter;
import java.util.Map;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class StatusActivity extends Activity implements LocationListener {
	final static private String TAG = "StatusActivity";

	// UI elements
	Button region_button, my_camera_button;
	Button get1_button, get2_button, get3_button, get4_button, get5_button, get6_button;
	TextView opCountTv, successCountTv, failureCountTv;
	TextView idTv, stateTv, regionTv, leaderTv;
	EditText regionText, threadsText;
	ListView msgList;
	ArrayAdapter<String> receivedMessages;
	CameraSurfaceView cameraSurfaceView;

	PowerManager.WakeLock wl = null;
	LocationManager lm;

	// Logging to file
	File myLogFile;
	PrintWriter myLogWriter;

	// Mux
	Mux mux;
	
	// timeout stuff
	final static private long uploadTimeoutPeriod = 6000L;
	final static private long downloadTimoutPeriod = 6000L;
	// areButtonsEnabled is the first line of defense against multi-clicking
	// set to false as soon as a take/get picture button is pressed
	// none of the other buttons can be pressed until it's set true again
	// set to true when progressDialog is dismissed
	private boolean areButtonsEnabled = false;
	// progressDialog is the second line of defense against multi-clicking
	// when shown, disables the rest of the ui, including buttons
	private ProgressDialog progressDialog;
	
	// latency stuff
	long client_upload_start = 0;
	long client_download_start = 0;

	/** Handle messages from various components */
	private final Handler myHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Mux.ACTIVITY_DESTROY:
				logMsg("inside ACTIVITY_DESTROY from the phone itself because its network settings are wrong.");
				StatusActivity.this.onDestroy();
			case Mux.LOG:
				receivedMessages.add((String) msg.obj);
				// Write to file
				if (myLogWriter != null) {
					myLogWriter.println((String) msg.obj);
				}
				break;
			case Mux.LOG_NODISPLAY:
				// Does not display on the list of messages on the phone's screen
				// receivedMessages.add((String) msg.obj);
				// Write to file
				if (myLogWriter != null) {
					myLogWriter.println((String) msg.obj);
				}
				break;
			case Mux.VNC_STATUS_CHANGE:
				// update state of phone: dormant, joining, leader, nonleader
				update();
				break;
			case Mux.CLIENT_STATUS_CHANGE:
				//TODO: count success/failures end to end (client to leader (and to remote and back to leader) and back to client) 
				Map<String, Long> data = (Map<String, Long>) msg.obj;
				opCountTv.setText("ops: " + String.valueOf(data.get("op")));
				successCountTv.setText("successes: "
						+ String.valueOf(data.get("success")));
				failureCountTv.setText("failures: "
						+ String.valueOf(data.get("failure")));
				break;
			case Packet.SERVER_SHOW_NEWPHOTO:
				logMsg("inside Packet.SERVER_SHOW_NEWPHOTO. I'm a leader showing my nonleader/myself client's new photo");
				Packet packet_ssn = (Packet) msg.obj;
				try {
					GetPhotoInfo gpinfo_ssn = _bytesToGetphotoinfo(packet_ssn.getphotoinfo_bytes);

					if (!gpinfo_ssn.isSuccess){
						logMsg("I'm a leader and I FAILED to save my client's new photo");
						CharSequence text = "I'm a leader and I FAILED to save my client's new photo";
						Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
						toast.show();
						
						break;
					}
					logMsg("I'm a leader and I SUCCEEDED in saving my client's new photo");
					
					ImageView image = (ImageView) findViewById(R.id.photoResultView);
					logMsg("now showing in UI the new photo I just saved ... ");
					logMsg("the photo length: " + gpinfo_ssn.photoBytes.length);
					
					// Garbage collect in case VM Heap runs out of memory with decodeByteArray
					System.gc();
					image.setImageBitmap(BitmapFactory.decodeByteArray(gpinfo_ssn.photoBytes, 0, gpinfo_ssn.photoBytes.length));

				} catch (OptionalDataException e) {
					logMsg("SERVER_SHOW_NEWPHOTO byte conversion failed OptionalDataException");
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					logMsg("SERVER_SHOW_NEWPHOTO byte conversion failed ClassNotFoundException");
					e.printStackTrace();
				} catch (IOException e) {
					logMsg("SERVER_SHOW_NEWPHOTO byte conversion failed IOException");
					e.printStackTrace();
				}
				break;
			case Packet.CLIENT_SHOW_REMOTEPHOTO:
				// this is the result of remote region photo request, the reply packet of the leader --> nonleader/leader itself
				// where the nonleader/leader itself requested the remote region
				
				// latency stuff
				long client_download_end = System.currentTimeMillis();
				logMsg("inside Packet.CLIENT_SHOW_REMOTEPHOTOS. Client requested for a photo in a remote region and this is the reply");
				long download_latency = client_download_end - client_download_start;
				if (mux.vncDaemon.mState == VCoreDaemon.LEADER) {
					logMsg("leader download remote photo latency = " + download_latency);
					logMsg("= leader download start " + client_download_start + " ~ stop " + client_download_end);
				} else {
					logMsg("nonleader download remote photo latency = " + download_latency);
					logMsg("= nonleader download start " + client_download_start + " ~ stop " + client_download_end);
				}

				Packet packet_csn = (Packet) msg.obj;
				Bitmap photo_remote;
				try {
					// get the Getphotoinfo from packet
					GetPhotoInfo my_gpinfo3 = _bytesToGetphotoinfo(packet_csn.getphotoinfo_bytes);
					
					// see if it was unsuccessful:
					if (!my_gpinfo3.isSuccess){
						logMsg("FAIL! Client failed to get photo from remote region");
						CharSequence text = "FAIL! Failed to get photo from remote region, try again";
						Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
						toast.show();
					} else { // success
						if (my_gpinfo3.photoBytes == null) {
							logMsg("PHOTO DATA is NULL, perhaps region doesn't have a photo yet");
							CharSequence text = "PHOTO DATA is NULL, perhaps region doesn't have a photo yet";
							Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
							toast.show();
						} else { // success and has photo data!
							
							// process photo
							ImageView image = (ImageView) findViewById(R.id.photoResultView);
							
							// print success!
							logMsg("Success! Client getting photo from remote region, showing photo...");
							CharSequence text = "SUCCESS! Getting photo from remote region succeeded, showing photo ...";
							Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
							toast.show();
							
							logMsg("Remote photo's length: " + my_gpinfo3.photoBytes.length);
							
							// show photo
							// Garbage collect in case VM Heap runs out of memory with decodeByteArray
							System.gc();
							image.setImageBitmap(BitmapFactory.decodeByteArray(my_gpinfo3.photoBytes, 0, my_gpinfo3.photoBytes.length));

						}
					}
					
					// TODO: WHY MUX HANDLER??
					// enable buttons right now, not untill progressdialog timeout
					mux.myHandler.removeCallbacks(buttonsEnableProgressTimeoutR);
					_enableButtons();
					
				} catch (OptionalDataException e) {
					logMsg("CLIENT_SHOW_REMOTEPHOTO: byte conversion failed OptionalDataException");
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					logMsg("CLIENT_SHOW_REMOTEPHOTO: byte conversion failed ClassNotFoundException");
					e.printStackTrace();
				} catch (IOException e) {
					logMsg("CLIENT_SHOW_REMOTEPHOTO: byte conversion failed IOException");
					e.printStackTrace();
				}
				break;
			case Packet.CLIENT_UPLOAD_PHOTO_ACK:
				// ack from region leader for a new photo taken by client (nonleader/leader) 
				// photoBytes is null inside GetPhotoInfo
				
				// latency stuff
				long client_upload_end = System.currentTimeMillis();
				logMsg("inside Packet.CLIENT_UPLOAD_PHOTO_ACK");
				long upload_latency = client_upload_end - client_upload_start;
				if (mux.vncDaemon.mState == VCoreDaemon.LEADER) {
					logMsg("leader upload new photo latency = " + upload_latency);
					logMsg("= leader upload start " + client_upload_start + " ~ stop " + client_upload_end);
				} else {
					logMsg("nonleader upload new photo latency = " + upload_latency);
					logMsg("= nonleader upload start " + client_upload_start + " ~ stop " + client_upload_end);
				}
				
				Packet packet_cupa = (Packet) msg.obj;
				try{
					// get the Getphotoinfo from packet
					GetPhotoInfo my_gpinfo3 = _bytesToGetphotoinfo(packet_cupa.getphotoinfo_bytes);

					// see if it was unsuccessful:
					if (!my_gpinfo3.isSuccess){
						logMsg("FAIL! Client now knows saving photo on its leader failed");
						CharSequence text = "FAIL! Saving photo on leader failed, try again.";
						Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
						toast.show();
					} else { // success
						logMsg("SUCCESS! Client now knows saving photo on its leader succeeded");
						CharSequence text = "SUCCESS! Saving photo on its leader succeeded";
						Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
						toast.setGravity(Gravity.CENTER, 0,0);
						toast.show();
					}
					
					// TODO: MUX.MYHANDLER??
					// enable buttons right now, not until progressdialog timeout
					mux.myHandler.removeCallbacks(buttonsEnableProgressTimeoutR);
					_enableButtons();
					
				} catch (OptionalDataException e) {
					logMsg("CLIENT_UPLOAD_PHOTO_ACK byte conversion failed OptionalDataException");
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					logMsg("CLIENT_UPLOAD_PHOTO_ACK byte conversion failed ClassNotFoundException");
					e.printStackTrace();
				} catch (IOException e) {
					logMsg("CLIENT_UPLOAD_PHOTO_ACK byte conversion failed IOException");
					e.printStackTrace();
				}
				break;
			}
		}
	};

	/** Log message and also display on screen */
	public void logMsg(String msg) {
		msg = String.format("%d: %s", System.currentTimeMillis(), msg);
		receivedMessages.add(msg);
		Log.i(TAG, msg);
		if (myLogWriter != null) {
			myLogWriter.println(msg);
			myLogWriter.flush();
		}
	}

	/** Force an update of the screen views */
	public void update() {
		// Refresh node state, region, and ID, and leader of current region on phone screen
		idTv.setText(String.valueOf(mux.vncDaemon.mId));
		leaderTv.setText(String.valueOf(mux.vncDaemon.leaderId));
		regionTv.setText(String.format("(%d,%d)", mux.vncDaemon.myRegion.x,
				mux.vncDaemon.myRegion.y));

		switch (mux.vncDaemon.mState) {
		case VCoreDaemon.DORMANT:
			stateTv.setText("DORMANT");
			break;
		case VCoreDaemon.JOINING:
			stateTv.setText("JOINING");
			break;
		case VCoreDaemon.LEADER:
			stateTv.setText("LEADER");
			break;
		case VCoreDaemon.NONLEADER:
			stateTv.setText("NONLEADER");
			break;
		case VCoreDaemon.HANDING_OFF:
			stateTv.setText("HANDING_OFF");
			break;
		}
	}
	
	// Runnables
	/** Disable buttons at press of any button (take new pic for upload / region x get for download) */
	private Runnable disableButtonsProgressStartR = new Runnable() {
		public void run() {
			Log.i(TAG, "Inside disableButtonsR");
			areButtonsEnabled = false;
			Log.i(TAG, "areButtonsEnabled --> false");
			progressDialog = ProgressDialog.show(StatusActivity.this, "", "Processing photo get or save to leader ... :)");
		}       
	};  
	
	private void _enableButtons(){
		Log.i(TAG, "Inside _enableButtons");
		progressDialog.dismiss();
		areButtonsEnabled = true;
		Log.i(TAG, "areButtonsEnabled --> true");
	}
	/** Enable buttons again, either when getting reply or timed out */
	private Runnable buttonsEnableProgressTimeoutR = new Runnable() {
		public void run() {
			Log.i(TAG, "inside buttonsEnableProgressTimeoutR. OH NO! Your photo (save/get) request TIMED OUT. Try again later!");
			CharSequence text = "OH NO! Your photo (take/get) request TIMED OUT. Try again later!";
			Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
			toast.show();
			_enableButtons();

		}       
	};     
	
	// check that we can press buttons by
	// 1. areButtonsEnabled is true AND region is inside valid range
	private boolean canPressButton(){
		if (areButtonsEnabled == false){
			logMsg("canPressButton = FALSE because areButtonsEnabled = false");
			CharSequence text = "Can't press button during processing";
			Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
			toast.show();
			return false;
		}
		if (mux.vncDaemon.myRegion.x < Globals.MIN_REGION || mux.vncDaemon.myRegion.x > Globals.MAX_REGION){
			logMsg("canPressButton = false. Can't press button because you're not at a valid region: " + Globals.MIN_REGION + " ~ " + Globals.MAX_REGION + ". You're at " + mux.vncDaemon.myRegion.x);
			CharSequence text = "Can't press button because you're not at a valid region: " + Globals.MIN_REGION + " ~ " + Globals.MAX_REGION + ". You're at " + mux.vncDaemon.myRegion.x;
			Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
			toast.show();
			return false;
		}
		if (mux.vncDaemon.mState != VCoreDaemon.LEADER && mux.vncDaemon.mState != VCoreDaemon.NONLEADER) {
			logMsg("canPressButton = false. state is + " + mux.vncDaemon.mState + " Can't press button because you're in a transient state (JOINING or HANDING_OFF)");
			CharSequence text = "Can't press because you're in a transient state (JOINING or HANDING_OFF), wait a sec ...";
			Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
			toast.show();
			return false;
		}
		logMsg("canPressButton = TRUE");
		return true;
	}

	/**
	 * Android application lifecycle management
	 **/

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		// Buttons
		region_button = (Button) findViewById(R.id.region_button);
		region_button.setOnClickListener(region_button_listener);
		get1_button = (Button) findViewById(R.id.get1_button);
		get1_button.setOnClickListener(get1_button_listener);
		get2_button = (Button) findViewById(R.id.get2_button);
		get2_button.setOnClickListener(get2_button_listener);
		get3_button = (Button) findViewById(R.id.get3_button);
		get3_button.setOnClickListener(get3_button_listener);
		get4_button = (Button) findViewById(R.id.get4_button);
		get4_button.setOnClickListener(get4_button_listener);
		get5_button = (Button) findViewById(R.id.get5_button);
		get5_button.setOnClickListener(get5_button_listener);
		get6_button = (Button) findViewById(R.id.get6_button);
		get6_button.setOnClickListener(get6_button_listener);
		
        //Setup the FrameLayout with the Camera Preview Screen
		cameraSurfaceView = new CameraSurfaceView(this);
        FrameLayout camerapreview = (FrameLayout) findViewById(R.id.CameraPreview); 
        camerapreview.addView(cameraSurfaceView);
      //Setup the 'Take Picture' button to take a picture
        my_camera_button = (Button)findViewById(R.id.cameraPrev_button);
        my_camera_button.setOnClickListener(new OnClickListener() 
        {
                public void onClick(View v) 
                {
                	if (canPressButton()) {
                		// disable button clicks ASAP
                		areButtonsEnabled = false;
                		logMsg("areButtonsEnabled --> false");
            			logMsg("disabling buttons ...");
            			// Disable buttons until timeout is over or received reply
            			// TODO: ASk why is it mux.myHandler?
            			// TODO: DON'T POST DISABLEBUTTONSPROGRESSSTART
            			mux.myHandler.post(disableButtonsProgressStartR);
            			mux.myHandler.postDelayed(buttonsEnableProgressTimeoutR, uploadTimeoutPeriod);
            			
                		logMsg("** Clicked take picture button **");
                		
                        Camera camera = cameraSurfaceView.getCamera();
                        camera.takePicture(null, null, new HandlePictureStorage());
                        
                	} else {
                		logMsg("can't press camera button yet");
                	}
                }
        });
		
		// Text views
        // TODO: Implement this!
		opCountTv = (TextView) findViewById(R.id.opcount_tv);
		successCountTv = (TextView) findViewById(R.id.successcount_tv);
		failureCountTv = (TextView) findViewById(R.id.failurecount_tv);

		regionText = (EditText) findViewById(R.id.region_text);

		// Text views
		idTv = (TextView) findViewById(R.id.id_tv);
		stateTv = (TextView) findViewById(R.id.state_tv);
		regionTv = (TextView) findViewById(R.id.region_tv);
		leaderTv = (TextView) findViewById(R.id.leader_tv);

		msgList = (ListView) findViewById(R.id.msgList);
		receivedMessages = new ArrayAdapter<String>(this, R.layout.message);
		msgList.setAdapter(receivedMessages);
		
		// Get a wakelock to keep everything running
		PowerManager pm = (PowerManager) getApplicationContext()
				.getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
				| PowerManager.ON_AFTER_RELEASE, TAG);
		wl.acquire();

		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		// Setup writing to log file on sd card
		boolean mExternalStorageAvailable = false;
		boolean mExternalStorageWriteable = false;
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// We can read and write the media
			mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			// We can only read the media
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
		} else {
			// Something else is wrong. It may be one of many other states, but
			// all we need to know is we can neither read nor write
			mExternalStorageAvailable = mExternalStorageWriteable = false;
		}

		if (mExternalStorageAvailable && mExternalStorageWriteable) {
			myLogFile = new File(Environment.getExternalStorageDirectory(),
					String.format("csm-%d.txt", System.currentTimeMillis()));
			try {
				myLogWriter = new PrintWriter(myLogFile);
				logMsg("*** Opened log file for writing ***");
			} catch (Exception e) {
				myLogWriter = null;
				logMsg("*** Couldn't open log file for writing ***");
				e.printStackTrace();
			}
		}
		
		// set the network name according to phone build
		logMsg("Android build: " + android.os.Build.MODEL);
		if (android.os.Build.MODEL.equals("SAMSUNG-SGH-I717")){
			Globals.NET_NAME = "eth0";
		} else if (android.os.Build.MODEL.equals("Nexus S")){
			Globals.NET_NAME = "wlan0";
		} else {
			logMsg("DON'T KNOW ANDROID BUILD!, neither 'SAMSUNG-SGH-I717' nor 'Nexus S'");
			onDestroy();
		}
		logMsg("NET_NAME set to: " + Globals.NET_NAME);


		// Start the mux, which will start the entire VNC, CSM, etc stack
		long id = -1;
		Bundle extras = getIntent().getExtras();
		if (extras != null && extras.containsKey("id")) {
			// we're running from within the simulator, so use given id and
			// start benchmark after a delay
			id = Long.valueOf(extras.getString("id"));
			logMsg("Status Activity, getting id = " + String.valueOf(id));
		}
		logMsg("starting Mux with id = " + String.valueOf(id));
		mux = new Mux(id, myHandler);
		mux.start();

		// enable button pressing
		areButtonsEnabled = true;
		logMsg("areButtonsEnabled --> true");
		
		// Watch out for low battery conditions
		BroadcastReceiver receiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				// Battery is low, so hand-off leadership if leader
				mux.vncDaemon.electNewLeader(mux.vncDaemon.myRegion,
						mux.vncDaemon.myRegion);
			}
		};
		IntentFilter inf = new IntentFilter();
		inf.addAction("android.intent.action.BATTERY_LOW");
		this.registerReceiver(receiver, inf);

		logMsg("*** Application started ***");

	} // end OnCreate()

	/**
	 * onResume is is always called after onStart, even if userApp's not
	 * paused
	 */
	@Override
	protected void onResume() {
		logMsg("HI I'm in ONRESUME()");
		super.onResume();
		// update if phone moves 5m ( once GPS fix is acquired )
		// or if 5s has passed since last update
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				Globals.SAMPLING_DURATION, Globals.SAMPLING_DISTANCE, this);
		String logLocationUpdateParameters = String.format(
				"SAMPLING_DISTANCE : %d, SAMPLING_DURATION : %d",
				Globals.SAMPLING_DISTANCE, Globals.SAMPLING_DURATION);
		logMsg(logLocationUpdateParameters);
	}

	@Override
	protected void onPause() {
		logMsg("HI I'm in ONPAUSE()");
		super.onPause();
	}

	@Override
	public void onDestroy() {
		logMsg("inside onDestroy()");
		mux.requestStop();

		myLogWriter.flush();
		myLogWriter.close();

		lm.removeUpdates(this);
		if (wl != null)
			wl.release();
		super.onDestroy();
		
		// close camera
		if (cameraSurfaceView.camera != null ){
			logMsg("closing camera in Status Activity");
			cameraSurfaceView.camera.stopPreview();
			cameraSurfaceView.camera.setPreviewCallback(null);
			cameraSurfaceView.camera.release();
		} else {
			logMsg("no camera to close");
		}
		
		// from: http://stackoverflow.com/a/5036668
		// kill completely for a fresh start every time
		logMsg("close everything else");
		System.runFinalizersOnExit(true);
		System.exit(0);
		
		android.os.Process.killProcess(android.os.Process.myPid());
	}

	/*** UI Callbacks for Buttons, etc. ***/
	// UI callback for "Set Region" button.
	private OnClickListener region_button_listener = new OnClickListener() {
		public void onClick(View v) {
			String strX = regionText.getText().toString();
			if (strX.equals("")){
				logMsg("please input a region");
				CharSequence text = "please input a region";
				Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
				toast.show();
			} else {
				int rX = Integer.parseInt(strX);
				int rY = 0;
				if (rX < Globals.MIN_REGION || rX > Globals.MAX_REGION){
					logMsg("please input a region between " + Globals.MIN_REGION + " ~ " + Globals.MAX_REGION);
					CharSequence text = "please input a region between " + Globals.MIN_REGION + " ~ " + Globals.MAX_REGION;
					Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT);
					toast.show();	
				} else {
					mux.vncDaemon.changeRegion(new RegionKey(rX, rY));
				}
			}
		}
	};

	/***
	 * Location / GPS Stuff adapted from
	 * http://hejp.co.uk/android/android-gps-example/
	 */

	/** Called when a location update is received */
	@Override
	public void onLocationChanged(Location loc) {
		logMsg(".......... GPS onLocationChanged ...... ");
		if (loc != null) {
			//mux.vncDaemon.checkLocation(loc);
			mux.vncDaemon.determineLocation(loc, mux.vncDaemon.myRegion);
		} else {
			logMsg("Null Location");
		}
	}

	@Override
	public void onProviderDisabled(String arg0) { // GPS off
		logMsg("************ GPS turned OFF *************");
	}

	@Override
	public void onProviderEnabled(String arg0) {
		logMsg("************ GPS turned ON *************");
	}

	/** Called upon change in GPS status */
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		logMsg("....... GPS status changed ....... ");
		switch (status) {
		case LocationProvider.OUT_OF_SERVICE:
			logMsg("GPS out of service");
			break;
		case LocationProvider.TEMPORARILY_UNAVAILABLE:
			logMsg("GPS temporarily unavailable");
			break;
		case LocationProvider.AVAILABLE:
			logMsg("GPS available");
			break;
		}
	}

	/**
	 * Camera from CameraSurface Works on both Nexus S and Galaxy Note phones,
	 * because StatusActivity is never paused
	 * 
	 * The photo is not saved on the sdcard.
	 * */
	private class HandlePictureStorage implements PictureCallback {
		@Override
		public void onPictureTaken(byte[] picture, Camera camera) {
			logMsg("inside HandlePictureStorage onPictureTaken()");

			// let the preview work again
			// IS THIS A NEW THREAD??
			cameraSurfaceView.camera.startPreview();
			
			logMsg("Picture successfully taken, ORIG BYTE LENGTH = " + picture.length);

			// must garbage collect here or VM Heap might run out of memory!!
			System.gc();
			Bitmap new_bitmap = _bytesResizeBitmap(picture);
			ImageView image = (ImageView) findViewById(R.id.photoResultView);

			logMsg("Show photo from handle my camera take");

			image.setImageBitmap(new_bitmap);
			sendClientNewpic(new_bitmap);
		}
	}

	protected void sendClientNewpic(Bitmap bitmap){
		logMsg("client making photo packet to send to leader for it to save");
		// Create a Packet to send through Mux to Leader's UserApp
		Packet packet = new Packet(-1, 
				-1,
				Packet.CLIENT_REQUEST,
				Packet.CLIENT_UPLOAD_PHOTO,
				mux.vncDaemon.myRegion,
				mux.vncDaemon.myRegion);
		// Create a GetPhotoInfo to contain origin ID, photo bytes
		GetPhotoInfo my_getphotoinfo = new GetPhotoInfo(mux.vncDaemon.mId, 
				mux.vncDaemon.myRegion.x, 
				mux.vncDaemon.myRegion.x);
	
		try {
			// jpeg compression in bitmapToBytes
			// and save inside GetPhotoInfo
			my_getphotoinfo.photoBytes = _bitmapToBytes(bitmap);
			logMsg("BYTE SIZE AFTER COMPRESSION: " + my_getphotoinfo.photoBytes.length);
			
			// save GetPhotoInfo inside Packet
			packet.getphotoinfo_bytes = _getphotoinfoToBytes(my_getphotoinfo);
		} catch (IOException e) {
			logMsg("sendClientNewpic _bitmapToBytes() or _intToBytes() failed");
			e.printStackTrace();
		}
		// once the nonleader mysteriously stopped executing after the above lines
		// investigate more if this happens again
		logMsg("about to send my pic");
		
		if (mux.vncDaemon.mState == VCoreDaemon.LEADER) {
			logMsg("I'm a leader, upload/save new photo packet going to mux directly");
			// TODO: logMsg("Loopback packet: " +packet.get);
			client_upload_start = System.currentTimeMillis();
			mux.myHandler.obtainMessage(mux.PACKET_RECV, packet).sendToTarget();
		} else if (mux.vncDaemon.mState == VCoreDaemon.NONLEADER) {
			logMsg("I'm a nonleader sending my new photo packet to my leader");
			client_upload_start = System.currentTimeMillis();
			mux.vncDaemon.sendPacket(packet);
		}
		logMsg("end of client send picture method");
	}
	
	protected Bitmap _bytesResizeBitmap(byte [] orig_bytes){
		BitmapFactory.Options options =new BitmapFactory.Options();

		// now we actually produce the bitmap, resized
		options.inJustDecodeBounds=false;
		// hard-code it to a big number
		options.inSampleSize=Globals.JPEG_SAMPLE_SIZE;
		// This decodeByteArray might crash the phone due to VM Heap OutOfMemoryError
		// if the options.inSampleSize is not set to a big number
		// that's why we garbage collect before calling this function and gc other decodeByteArrays to be safe
		// http://stackoverflow.com/questions/6402858/android-outofmemoryerror-bitmap-size-exceeds-vm-budget
		// http://stackoverflow.com/questions/477572/android-strange-out-of-memory-issue-while-loading-an-image-to-a-bitmap-object
		Bitmap new_bitmap =BitmapFactory.decodeByteArray(orig_bytes, 0, orig_bytes.length, options);
		logMsg("Our new height x width: " + new_bitmap.getHeight() + " x " + new_bitmap.getWidth());
		
		return new_bitmap;
	}

	protected int _bitmapBytes(Bitmap bitmap){
		return bitmap.getRowBytes()*bitmap.getHeight();
	}

	public byte[] _bitmapToBytes(Bitmap bmp) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bmp.compress(Bitmap.CompressFormat.JPEG, Globals.COMP_QUALITY, bos);
		// should still be under 65000 bytes
		byte[] bytes = bos.toByteArray();
		return bytes;
	}

	// can't do Java generics because we are serializing
	public static GetPhotoInfo _bytesToGetphotoinfo(byte[] int_bytes)
			throws IOException, ClassNotFoundException {
		ByteArrayInputStream bis = new ByteArrayInputStream(int_bytes);
		ObjectInputStream ois = new ObjectInputStream(bis);
		GetPhotoInfo my_gpinfo = (GetPhotoInfo) ois.readObject();
		ois.close();
		return my_gpinfo;
	}
	
	public byte[] _getphotoinfoToBytes(GetPhotoInfo my_gpinfo) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = new ObjectOutputStream(bos);
		out.writeObject(my_gpinfo);
		out.close();
		byte[] int_bytes = bos.toByteArray();
		bos.close();
		return int_bytes;
	}
	
	/* ############################################### */
	/* ############################################### */
	/* ############################################### */
	/* dumb button listeners */
	private OnClickListener get1_button_listener = new OnClickListener(){
		public void onClick(View v){
			if (canPressButton()) {
				// disable button clicks ASAP
        		areButtonsEnabled = false;
        		logMsg("areButtonsEnabled --> false ");
        		
				logMsg("** Clicked getphotos Button from region 1 **");
				long targetRegion = 1;
				_button_listener_helper(targetRegion);
			} else {
				logMsg("can't press region 1 yet");
			}
		}
	};
	private OnClickListener get2_button_listener = new OnClickListener(){
		public void onClick(View v){
			if (canPressButton()) {
				// disable button clicks ASAP
        		areButtonsEnabled = false;
        		logMsg("areButtonsEnabled --> false ");
        		
				logMsg("** Clicked getphotos Button from region 2 **");
				long targetRegion = 2;
				_button_listener_helper(targetRegion);
			} else {
				logMsg("can't press region 2 yet");
			}
		}
	};
	private OnClickListener get3_button_listener = new OnClickListener(){
		public void onClick(View v){
			if (canPressButton()) {
				// disable button clicks ASAP
        		areButtonsEnabled = false;
        		logMsg("areButtonsEnabled --> false ");
        		
				logMsg("** Clicked getphotos Button from region 3 **");
				long targetRegion = 3;
				_button_listener_helper(targetRegion);
			} else {
				logMsg("can't press region 3 yet");
			}
		}
	};
	private OnClickListener get4_button_listener = new OnClickListener(){
		public void onClick(View v){
			if (canPressButton()) {
				// disable button clicks ASAP
        		areButtonsEnabled = false;
        		logMsg("areButtonsEnabled --> false ");
        		
				logMsg("** Clicked getphotos Button from region 4 **");
				long targetRegion = 4;
				_button_listener_helper(targetRegion);
			} else {
				logMsg("can't press region 4 yet");
			}
		}
	};
	private OnClickListener get5_button_listener = new OnClickListener(){
		public void onClick(View v){
			if (canPressButton()) {
				// disable button clicks ASAP
        		areButtonsEnabled = false;
        		logMsg("areButtonsEnabled --> false ");
        		
				logMsg("** Clicked getphotos Button from region 5 **");
				long targetRegion = 5;
				_button_listener_helper(targetRegion);
			} else {
				logMsg("can't press region 5 yet");
			}
		}
	};
	private OnClickListener get6_button_listener = new OnClickListener(){
		public void onClick(View v){
			if (canPressButton()) {
				// disable button clicks ASAP
        		areButtonsEnabled = false;
        		logMsg("areButtonsEnabled --> false ");
        		
				logMsg("** Clicked getphotos Button from region 6 **");
				long targetRegion = 6;
				_button_listener_helper(targetRegion);
			} else {
				logMsg("can't press region 6 yet");
			}
		}
	};
	private void _button_listener_helper(long targetRegion){
		// Disable buttons until timeout is over, or received reply
		// TODO: WHY IS IT mux.myHandler??
		mux.myHandler.post(disableButtonsProgressStartR);
		mux.myHandler.postDelayed(buttonsEnableProgressTimeoutR, downloadTimoutPeriod);
		
		// Create a Packet to send through Mux to Leader's UserApp
		Packet packet = new Packet(-1, 
				-1,
				Packet.CLIENT_REQUEST,
				Packet.CLIENT_DOWNLOAD_PHOTO,
				mux.vncDaemon.myRegion,
				mux.vncDaemon.myRegion); 
		GetPhotoInfo my_getphotoinfo = new GetPhotoInfo(mux.vncDaemon.mId, 
				mux.vncDaemon.myRegion.x, 
				targetRegion);
		logMsg("I'm the Client, and I'm in region: " + 
				my_getphotoinfo.srcRegion + 
				" nodID = " + my_getphotoinfo.originNodeId);
		try {
			// save GetPhotoInfo inside Packet
			packet.getphotoinfo_bytes = _getphotoinfoToBytes(my_getphotoinfo);
		} catch (IOException e) {
			logMsg("_button_listener_helper _intToBytes() failed");
			e.printStackTrace();
		}
		
		if (mux.vncDaemon.mState == VCoreDaemon.LEADER) {
			logMsg("I'm a leader, my requesting photos packet going to mux directly");
			client_download_start = System.currentTimeMillis();
			mux.myHandler.obtainMessage(mux.PACKET_RECV, packet).sendToTarget();
		} else if (mux.vncDaemon.mState == VCoreDaemon.NONLEADER) {
			logMsg("I'm a nonleader sending my requesting photos packet to my leader");
			client_download_start = System.currentTimeMillis();
			mux.vncDaemon.sendPacket(packet);
		}
		logMsg("StatusActivity sent request to get photos for region " + targetRegion);
	}
}
