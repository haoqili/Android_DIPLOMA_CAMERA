package edu.mit.csail.diplomamatrix;

import java.io.File;

import android.location.Location;
import android.os.Environment;


public class Globals {
		// frequently changed constants
		final static public int JPEG_SAMPLE_SIZE = 12; // if too low, will cause Nexus S out of memory!
		// TODO: CHANGE TURNNING SKIP CLOUD TO FALSE!!!!!!!!!!!!!!!!!!!!
		
		// cloud server to keep leaders consistent
		//final static public String CSM_SERVER_NAME="128.30.87.130:8212"; //128.30.66.123:5212
        final static public String CSM_SERVER_NAME="18.111.1.239:8212"; //128.30.66.123:5212
        //final static public String CSM_SERVER_NAME="hermes5.csail.mit.edu:8212"; //128.30.66.123:5212

		// region calculations
        // 21 meters is the max linearly on 77 Mass Ave, 
        // ideally we want REGION_WIDTH to span 2 regions, but 10.5 meters is too short
        static public double REGION_WIDTH=20; // This can be changed from UI
		final static public double ROAD_WIDTH_METERS = 30;
		static public double HYSTERESIS = 0; // can be changed from UI 
		
		// to calculate start point & road angle:
		// Endpoints on (straight) Mass Ave to calculate theta
		/* Central Mass Ave
		final static public double NW_LONG = -71.104888;
		final static public double NW_LAT = 42.365944;
		final static public double SE_LONG = -71.100005;
		final static public double SE_LAT = 42.363492;
		*/
		
		// 77 Mass Ave
		final static public double NW_LONG = -71.093881;
		final static public double NW_LAT = 42.359644;
		final static public double SE_LONG = -71.092894;
		final static public double SE_LAT = 42.357741;
	
		
		final static public int NTHREADS=1;
        final static public boolean CACHE_ENABLED_ON_START = false;
        final static public double BENCHMARK_READ_DISTRIBUTION_ON_START = 0.9f;
        final static public long BENCHMARK_START_DELAY = 1000L; // milliseconds
        
        final static public int SAMPLING_DURATION=1000;
        final static public int SAMPLING_DISTANCE=1;
        final static public String BROADCAST_ADDRESS="192.168.5.255"; //.255.255 also works

        
        // region constraints, for the UI
        final static public int MAX_REGION = 5;
        final static public int MAX_Y_REGIONS=1;
        
        final static public int SPARSE_NUM_ITER=100000;

		// photo properties
		final static public int COMP_QUALITY = 10; // 0 - 100, 100 is max quality
		final static String PHOTO_PATH = Environment.getExternalStorageDirectory().getName() + File.separatorChar + "temp_photo.jpg";
		final static String PHOTO_KEY = "diplomaPhotos";
		
		// Nexus S phones use "wlan0", Galaxy Notes use "eth0"
		public static boolean DEBUG_SKIP_CLOUD = false; // TODO: SET TO false
		static public String NET_NAME = "bad"; // set in StatusActivity
		
} 