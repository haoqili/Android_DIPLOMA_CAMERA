package edu.mit.csail.diplomamatrix;

import java.io.File;
import java.io.PrintWriter;
import java.util.Map;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class StatusActivity extends Activity implements LocationListener {
	final static private String TAG = "StatusActivity";

	// UI elements
	Button bench_button, cache_button, region_button, threads_button;
	TextView opCountTv, successCountTv, failureCountTv;
	TextView idTv, stateTv, regionTv, leaderTv;
	EditText regionText, threadsText;
	ListView msgList;
	ArrayAdapter<String> receivedMessages;

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
		bench_button = (Button) findViewById(R.id.bench_button);
		bench_button.setOnClickListener(bench_button_listener);
		cache_button = (Button) findViewById(R.id.cache_button);
		cache_button.setOnClickListener(cache_button_listener);
		region_button = (Button) findViewById(R.id.region_button);
		region_button.setOnClickListener(region_button_listener);
		threads_button = (Button) findViewById(R.id.threads_button);
		threads_button.setOnClickListener(threads_button_listener);

		// Text views
		opCountTv = (TextView) findViewById(R.id.opcount_tv);
		successCountTv = (TextView) findViewById(R.id.successcount_tv);
		failureCountTv = (TextView) findViewById(R.id.failurecount_tv);

		regionText = (EditText) findViewById(R.id.region_text);
		threadsText = (EditText) findViewById(R.id.threads_text);

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
		Log.i(TAG, "end to start mux hqqqqqqqqqqqqqq11qqqqq");
		mux.start();
		
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
	private OnClickListener bench_button_listener = new OnClickListener() {
		public void onClick(View v) {
		    Log.i(TAG, "about to start benchmark hqqqqqqqqqqqqqqqqqqq");
		    if (mux == null){
		    	Log.i("bench button null 1:", "mux == null. :(:(:(:(:(:(:(");
		    } else {
		    	if (mux.vncDaemon == null){
		    		Log.i("bench button null 2:", "mux.vncDaemon == null. :(:(:(");
		    	} else {
		    		if (mux.vncDaemon.csm == null){
		    			Log.i("bench button null 3:", "mux.vncDaemon.csm == null :(:(:(:(");
		    		} else {
		    			if (mux.vncDaemon.csm.userApp == null) {
		    				Log.i("bench button null 4:", "mux.vncDaemon.csm.userApp == null :(:(:(:(");
		    			} else {
		    				Log.i("bench button null 5:", ":):):):)");
		    			}
		    		}
		    	}
		    }
			if (mux == null || mux.vncDaemon == null
					|| mux.vncDaemon.csm == null
					|| mux.vncDaemon.csm.userApp == null) {
		        Log.i(TAG, ":( mux not right, can't start benchmark hqqqqqqqqqqqqqqqqqqq");
				return;
            }
			logMsg("*** benchmark starting ***");
			mux.vncDaemon.csm.userApp.startBenchmark();
			update();
		}
	};

	private OnClickListener cache_button_listener = new OnClickListener() {
		// toggle caching in vncDaemon (which will toggle in DSMLayer)
		public void onClick(View v) {
			if (mux.vncDaemon.cacheEnabled) {
				cache_button.setText("Cache is OFF");
				mux.vncDaemon.disableCaching();
			} else {
				cache_button.setText("Cache is ON");
				mux.vncDaemon.enableCaching();
			}
		}
	};

	private OnClickListener region_button_listener = new OnClickListener() {
		public void onClick(View v) {
			int rX = Integer.parseInt(regionText.getText().toString());
			int rY = 0;
			mux.vncDaemon.changeRegion(new RegionKey(rX, rY));
		}
	};

	private OnClickListener threads_button_listener = new OnClickListener() {
		public void onClick(View v) {
			int nthreads = Integer.parseInt(threadsText.getText().toString());
			if (mux.vncDaemon.csm != null
					&& mux.vncDaemon.csm.userApp != null)
				mux.vncDaemon.csm.userApp.nthreads = nthreads;
		}
	};

	/***
	 * Location / GPS Stuff adapted from
	 * http://hejp.co.uk/android/android-gps-example/
	 */

	/** Called when a location update is received */
	@Override
	public void onLocationChanged(Location loc) {

		if (loc != null) {
			mux.vncDaemon.checkLocation(loc);
		} else {
			logMsg("Null Location");
		}
	}

	@Override
	public void onProviderDisabled(String arg0) {
	}

	@Override
	public void onProviderEnabled(String arg0) {
	}

	/** Called upon change in GPS status */
	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
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
}
