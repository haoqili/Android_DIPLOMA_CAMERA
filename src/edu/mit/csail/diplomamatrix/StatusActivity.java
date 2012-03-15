package edu.mit.csail.diplomamatrix;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.PrintWriter;
import java.util.Map;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class StatusActivity extends Activity implements LocationListener {
	final static private String TAG = "StatusActivity";
	private static final int CAMERA_PIC_REQUEST = 111;

	// UI elements
	Button camera_button, region_button, my_camera_button;
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

	/** Handle messages from various components */
	private final Handler myHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case Mux.LOG:
				receivedMessages.add((String) msg.obj);
				// Write to file
				if (myLogWriter != null) {
					myLogWriter.println((String) msg.obj);
				}
				break;
			case Mux.LOG_NODISPLAY:
				// receivedMessages.add((String) msg.obj);
				// Write to file
				if (myLogWriter != null) {
					myLogWriter.println((String) msg.obj);
				}
				break;
			case Mux.VNC_STATUS_CHANGE:
				update();
				break;
			case Mux.CLIENT_STATUS_CHANGE:
				Map<String, Long> data = (Map<String, Long>) msg.obj;
				opCountTv.setText("ops: " + String.valueOf(data.get("op")));
				successCountTv.setText("successes: "
						+ String.valueOf(data.get("success")));
				failureCountTv.setText("failures: "
						+ String.valueOf(data.get("failure")));

				boolean requestOutstanding = data.get("request_oustanding") == 1L;
				break;
			case Packet.SERVER_SHOW_NEWPHOTO:
				Log.i(TAG, "inside Packet.SERVER_SHOW_NEWPHOTO");
				Packet packet = (Packet) msg.obj;
				Bitmap new_photo;
				try {
					new_photo = _bytesToBitmap(packet.photo_bytes);
					ImageView image = (ImageView) findViewById(R.id.photoResultView);
					logMsg("Show photo from server_show_newphoto");
					image.setImageBitmap(new_photo);
				} catch (OptionalDataException e) {
					Log.i(TAG, "_bytesToBitmap failed");
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					Log.i(TAG, "_bytesToBitmap failed");
					e.printStackTrace();
				} catch (IOException e) {
					Log.i(TAG, "_bytesToBitmap failed");
					e.printStackTrace();
				}
				break;
			case Packet.CLIENT_SHOW_NEWPHOTOS:
				Log.i(TAG, "inside Packet.CLIENT_SHOW_NEWPHOTOS");
				Packet pack = (Packet) msg.obj;
				Bitmap photo_one;
				try {
					if (pack.photo_bytes == null) {
						logMsg("PHOTO DATA is NULL, perhaps region doesn't have a photo yet");
					} else {
						photo_one = _bytesToBitmap(pack.photo_bytes);
						ImageView image = (ImageView) findViewById(R.id.photoResultView);
						logMsg("Show photo from client_show_newphotos");
						image.setImageBitmap(photo_one);
					}
				} catch (OptionalDataException e) {
					Log.i(TAG, "_bytesToBitmap failed");
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					Log.i(TAG, "_bytesToBitmap failed");
					e.printStackTrace();
				} catch (IOException e) {
					Log.i(TAG, "_bytesToBitmap failed");
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
		}
	}

	/** Force an update of the screen views */
	public void update() {
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
		}
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
		camera_button = (Button) findViewById(R.id.camera_button);
		//camera_button.setOnClickListener(camera_button_listener);
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
                        Camera camera = cameraSurfaceView.getCamera();
                        camera.takePicture(null, null, new HandlePictureStorage());
                }
        });

		// Text views
		opCountTv = (TextView) findViewById(R.id.opcount_tv);
		successCountTv = (TextView) findViewById(R.id.successcount_tv);
		failureCountTv = (TextView) findViewById(R.id.failurecount_tv);

		regionText = (EditText) findViewById(R.id.region_text);
		//threadsText = (EditText) findViewById(R.id.threads_text);

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
			}
		}

		// Start the mux, which will start the entire VNC, CSM, etc stack
		long id = -1;
		Bundle extras = getIntent().getExtras();
		if (extras != null && extras.containsKey("id")) {
			// we're running from within the simulator, so use given id and
			// start benchmark after a delay
			id = Long.valueOf(extras.getString("id"));
			Log.i("Status Activity, getting id = ", String.valueOf(id));
		}
		Log.i(TAG, "about to start mux hqqqqqqqqqqqqqqqqqqq");
		Log.i("                id = ", String.valueOf(id));
		mux = new Mux(id, myHandler);
		mux.start();
		Log.i(TAG, "end to start mux hqqqqqqqqqqqqqq11qqqqq");

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
		mux.requestStop();

		myLogWriter.flush();
		myLogWriter.close();

		lm.removeUpdates(this);
		if (wl != null)
			wl.release();
		super.onDestroy();
	}

	/*** UI Callbacks for Buttons, etc. ***/
	private OnClickListener region_button_listener = new OnClickListener() {
		public void onClick(View v) {
			int rX = Integer.parseInt(regionText.getText().toString());
			int rY = 0;
			mux.vncDaemon.changeRegion(new RegionKey(rX, rY));
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
			// TODO: better location:
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
	 * Camera from Intent stuff 
	 * It launches camera by pausing StatusActivity and 
	 * returns to onActivityResult
	 * 
	 * We save the photo at Globals.PHOTO_PATH (in the sdcard) and
	 * retrieve the photo from there inside onActivityResult
	 * 
	 * ----
	 * 
	 * This works fine on Nexus S phones, but
	 * on Galaxy Note phones, Mux is killed and have to be restarted
	 * before and after the camera intent, causing a
	 * "Cannot open socketAddress already in use" error
	 * 
	 * **/
	private OnClickListener camera_button_listener = new OnClickListener(){
		public void onClick(View v){
			Log.i(TAG, "#################");
			Log.i(TAG, "clicked Camera button");
			Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

			// credit: http://stackoverflow.com/questions/1910608/android-action-image-capture-intent
			File _photoFile = new File(Globals.PHOTO_PATH);
			try {
				if(_photoFile.exists() == false) {
					_photoFile.getParentFile().mkdirs();
					_photoFile.createNewFile();
				}
			} catch (IOException e) {
				Log.e(TAG, "Could not create file.", e);
			}
			Log.i(TAG + " photo path: ", Globals.PHOTO_PATH);

			Uri _fileUri = Uri.fromFile(_photoFile);
			cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, _fileUri);
			// start the Intent:
			startActivityForResult(cameraIntent, CAMERA_PIC_REQUEST);
		}
	};
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != Activity.RESULT_OK) {
			logMsg("Taking picture failed. Try again!");
			return;
		}
		if (requestCode == CAMERA_PIC_REQUEST) {
			Log.i(TAG, "Camera Handling results!");

			
			logMsg("Display on screen:");
			ImageView image = (ImageView) findViewById(R.id.photoResultView);

			Bitmap new_bitmap = _getAndResizeBitmap();
			logMsg("Show photo from camera intent result");
			image.setImageBitmap(new_bitmap);
			logMsg("GETANDRESIZE BITMAP Original SIZE: " + _bitmapBytes(new_bitmap));
			sendClientNewpic(new_bitmap);
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
			// let the preview work again
			cameraSurfaceView.camera.startPreview();
			
			logMsg("Picture successfully taken, ORIG BYTE LENGTH = " + picture.length);
			try {
				Bitmap orig_bitmap = _bytesToBitmap(picture);
				Bitmap new_bitmap = _bytesResizeBitmap(picture, orig_bitmap);
				ImageView image = (ImageView) findViewById(R.id.photoResultView);
				
				logMsg("Show photo from handle my camera take");
				image.setImageBitmap(new_bitmap);
				sendClientNewpic(new_bitmap);
			} catch (OptionalDataException e) {
				Log.i(TAG, "_bytesToBitmap failed");
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				Log.i(TAG, "_bytesToBitmap failed");
				e.printStackTrace();
			} catch (IOException e) {
				Log.i(TAG, "_bytesToBitmap failed");
				e.printStackTrace();
			}
		}
	}

	protected void sendClientNewpic(Bitmap bitmap){
		logMsg("client making photo packet to send to leader");
		Log.i(TAG, "111111111");
		// Create a Packet to send through Mux to Leader's UserApp
		Packet packet = new Packet(-1, 
				-1,
				Packet.CLIENT_REQUEST,
				Packet.CLIENT_UPLOAD_PHOTO,
				mux.vncDaemon.myRegion,
				mux.vncDaemon.myRegion);
		Log.i(TAG, "222222222");
		try {
			// jpeg compression in bitmapToBytes
			packet.photo_bytes = _bitmapToBytes(bitmap);
			logMsg("BYTE SIZE AFTER COMPRESSION: " + packet.photo_bytes.length);
		} catch (IOException e) {
			Log.i(TAG, "_bitmapToBytes() failed");
			e.printStackTrace();
		}
		Log.i(TAG, "3333333333");
		if (mux.vncDaemon.mState == VCoreDaemon.LEADER) {
			Log.i(TAG, "I'm a leader, photo packet going to mux directly");
			mux.myHandler.obtainMessage(mux.PACKET_RECV, packet).sendToTarget();
		} else if (mux.vncDaemon.mState == VCoreDaemon.NONLEADER) {
			Log.i(TAG, "I'm not a leader, photo packet send out");
			mux.vncDaemon.sendPacket(packet);
		}
		Log.i(TAG, "44444444444");
	}
	
	protected Bitmap _getAndResizeBitmap(){
		BitmapFactory.Options options =new BitmapFactory.Options();
		// first we don't produce an actual bitmap, but just probe its dimensions
		options.inJustDecodeBounds = true;
		Bitmap bitmap = BitmapFactory.decodeFile(Globals.PHOTO_PATH, options);
		int h, w;
		if (options.outHeight > options.outWidth){
			h = (int) Math.ceil(options.outHeight/(float) Globals.TARGET_SHORT_SIDE);
			w = (int) Math.ceil(options.outWidth/(float) Globals.TARGET_LONG_SIDE);
		} else {
			w = (int) Math.ceil(options.outHeight/(float) Globals.TARGET_SHORT_SIDE);
			h = (int) Math.ceil(options.outWidth/(float) Globals.TARGET_LONG_SIDE);
		}
		if(h>1 || w>1){
			options.inSampleSize = (h > w) ? h : w;
		}
		// now we actually produce the bitmap, resized
		options.inJustDecodeBounds=false;
		bitmap =BitmapFactory.decodeFile(Globals.PHOTO_PATH, options);
		logMsg("Our new height x width: " + bitmap.getHeight() + " x " + bitmap.getWidth());
		
		return bitmap;
	}
	
	protected Bitmap _bytesResizeBitmap(byte [] orig_bytes, Bitmap orig_bitmap){
		BitmapFactory.Options options =new BitmapFactory.Options();
		int h, w;
		if (orig_bitmap.getHeight() > orig_bitmap.getWidth()){
			h = (int) Math.ceil(orig_bitmap.getHeight()/(float) Globals.TARGET_SHORT_SIDE);
			w = (int) Math.ceil(orig_bitmap.getWidth()/(float) Globals.TARGET_LONG_SIDE);
		} else {
			w = (int) Math.ceil(orig_bitmap.getHeight()/(float) Globals.TARGET_SHORT_SIDE);
			h = (int) Math.ceil(orig_bitmap.getWidth()/(float) Globals.TARGET_LONG_SIDE);
		}
		if(h>1 || w>1){
			options.inSampleSize = (h > w) ? h : w;
		}
		// now we actually produce the bitmap, resized
		options.inJustDecodeBounds=false;
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
	public Bitmap _bytesToBitmap(byte[] photo_bytes) throws OptionalDataException,
	ClassNotFoundException, IOException {
		return BitmapFactory.decodeByteArray(photo_bytes, 0, photo_bytes.length);
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
			Log.i(TAG, "#################");
			Log.i(TAG, "clicked getphotos Button from region 1");
			long targetRegion = 1;
			_button_listener_helper(targetRegion);
		}
	};
	private OnClickListener get2_button_listener = new OnClickListener(){
		public void onClick(View v){
			Log.i(TAG, "#################");
			Log.i(TAG, "clicked getphotos Button from region 2");
			long targetRegion = 2;
			_button_listener_helper(targetRegion);
		}
	};
	private OnClickListener get3_button_listener = new OnClickListener(){
		public void onClick(View v){
			Log.i(TAG, "#################");
			Log.i(TAG, "clicked getphotos Button from region 3");
			long targetRegion = 3;
			_button_listener_helper(targetRegion);
		}
	};
	private OnClickListener get4_button_listener = new OnClickListener(){
		public void onClick(View v){
			Log.i(TAG, "#################");
			Log.i(TAG, "clicked getphotos Button from region 4");
			long targetRegion = 4;
			_button_listener_helper(targetRegion);
		}
	};
	private OnClickListener get5_button_listener = new OnClickListener(){
		public void onClick(View v){
			Log.i(TAG, "#################");
			Log.i(TAG, "clicked getphotos Button from region 5");
			long targetRegion = 5;
			_button_listener_helper(targetRegion);
		}
	};
	private OnClickListener get6_button_listener = new OnClickListener(){
		public void onClick(View v){
			Log.i(TAG, "#################");
			Log.i(TAG, "clicked getphotos Button from region 6");
			long targetRegion = 6;
			_button_listener_helper(targetRegion);
		}
	};
	private void _button_listener_helper(long targetRegion){
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
			packet.getphotoinfo_bytes = _getphotoinfoToBytes(my_getphotoinfo);
		} catch (IOException e) {
			Log.i(TAG, "_intToBytes() failed");
			e.printStackTrace();
		}
		if (mux.vncDaemon.mState == VCoreDaemon.LEADER) {
			Log.i(TAG, "I'm a leader, requesting photos packet going to mux directly");
			mux.myHandler.obtainMessage(mux.PACKET_RECV, packet).sendToTarget();
		} else if (mux.vncDaemon.mState == VCoreDaemon.NONLEADER) {
			Log.i(TAG, "I'm not a leader, requesting photos packet send out");
			mux.vncDaemon.sendPacket(packet);
		}
		Log.i(TAG, "Done with Get photos button for region " + targetRegion);
	}
}
