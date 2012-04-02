package edu.mit.csail.diplomamatrix;

import java.io.File;

import android.location.Location;
import android.os.Environment;


public class Globals {
		// frequently changed constants
		// Nexus S phones use "wlan0", Galaxy Notes use "eth0"
		final static public String NET_NAME = "eth0";
		// cloud server to keep leaders consistent
		//final static public String CSM_SERVER_NAME="128.30.87.130:8212"; //128.30.66.123:5212
        final static public String CSM_SERVER_NAME="hermes5.csail.mit.edu:8212"; //128.30.66.123:5212
        
		// new region calculations
		// road parameters, used to calculate region width
		final static public double PHONE_RANGE_METERS = 60; // diagonal of region
		final static public double ROAD_WIDTH_METERS = 30;
		// to calculate buffer zone
		final static public double REGION_WIDTH_BOUNDARY_METERS = 5;
	
		
		final static public int NTHREADS=1;
        final static public boolean CACHE_ENABLED_ON_START = false;
        final static public double BENCHMARK_READ_DISTRIBUTION_ON_START = 0.9f;
        final static public long BENCHMARK_START_DELAY = 1000L; // milliseconds
        
        final static public int SAMPLING_DURATION=1000;
        final static public int SAMPLING_DISTANCE=1;
        final static public String BROADCAST_ADDRESS="192.168.5.255"; //.255.255 also works

        
        // old region calculations
        final static public int REGION_WIDTH=17; // in meters, 35/2
        final static public int SOUTHEAST_LONG = -7110000;
        final static public int SOUTHEAST_LAT = 4236349;
        // Long is x
        final static public int MAX_X_REGIONS=6;
        final static public int MAX_Y_REGIONS=1;
        final static public int MINIMUM_LONGITUDE=SOUTHEAST_LONG - REGION_WIDTH*MAX_X_REGIONS;
        final static public int MINIMUM_LATITUDE=SOUTHEAST_LAT;
        
        // region constraints, for the UI
        final static public int MIN_REGION = 0;
        final static public int MAX_REGION = MAX_X_REGIONS-1;

        
        final static public int SPARSE_NUM_ITER=100000;
		public static final boolean DEBUG_SKIP_CLOUD = false;

		// photo properties
		final static public int COMP_QUALITY = 10; // 0 - 100, 100 is max quality
		final static String PHOTO_PATH = Environment.getExternalStorageDirectory().getName() + File.separatorChar + "temp_photo.jpg";
		final static int TARGET_SHORT_SIDE = 200;
		final static int TARGET_LONG_SIDE = 240;
		final static String PHOTO_KEY = "diplomaPhotos";
} 

/*
public void testDetermineLocation(double locx, double locy, RegionKey prevRegion){
	// TODO: make this work with Y as well
	// currently determining region only depends on X
	
	logMsg("INSIDE DETERMINELOCATION");
	//logMsg("Loc = " + loc + " Previous Region = " + prevRegion);
	
	//double locx = loc.getLongitude();
	//double locy = loc.getLatitude();
	
	// x-width of a rectangular region
	double region_width = Math.sqrt(Math.pow(Globals.PHONE_RANGE_METERS, 2) - Math.pow(Globals.ROAD_WIDTH_METERS, 2));
			
	// X = Longitude, Y = Latitude
	
	// Converting Latitude and Longitude into meters
	// Latitude: each is 10^-5 degree of lat Y
	final int earth_radius_meters = 6378140; //at equator
	final double location_latitude = 42.365; // angle from location to equator
	double one_lat_to_meters = earth_radius_meters * 2 * Math.PI / (360*100000); // 1.113 meters
	logMsg("one_lat_to_meters = " + one_lat_to_meters);
	double one_long_to_meters = Math.cos(Math.toRadians(location_latitude))*one_lat_to_meters; // 0.822 meters
	logMsg("one_long_to_meters = " + one_long_to_meters);
	
	// Endpoints on (straight) Mass Ave to calculate theta
	final double north_west_loc_long = -71.104888;
	final double north_west_loc_lat = 42.365944;
	final double south_east_loc_long = -71.100005;
	final double south_east_loc_lat = 42.363492;
	double x_diff = Math.abs(south_east_loc_long - north_west_loc_long) * one_long_to_meters; // 401.6m * 10^-5
	logMsg("x_diff = " + x_diff);
	double y_diff = Math.abs(north_west_loc_lat - south_east_loc_lat) * one_lat_to_meters; // 272.9m * 10^-5
	logMsg("y_diff = " + y_diff);
	double theta = Math.atan(y_diff / x_diff); // 0.597 radians or 34.21 degrees
	logMsg("theta = " + theta); 		
	
	// location in respect to south_east point
	double loc_x = (locx - south_east_loc_long) * one_long_to_meters;
	double loc_y = (locy - south_east_loc_lat) * one_lat_to_meters;
	
	// rotational matrix
	double loc_x_rotated = -1 * loc_x * Math.cos(theta) + loc_y * Math.sin(theta); 
	double loc_y_rotated = loc_x * Math.sin(theta) + loc_y * Math.cos(theta);
	
	// find the current region
	// Note: only depending on loc_x_rotated for this experiment
	// TODO: for experiments involving a matrix of regions, add y
	double current_region = (int) Math.floor(loc_x_rotated / region_width);
	logMsg("location pinpoinst to region = " + current_region);
	

	double region_width_boundary = Globals.REGION_WIDTH_BOUNDARY_METERS;
	// check if it's inside boundary of region
	// region_width_boundary is defined as the boundary from the edge of region to edge of boundary
	// i.e. the total boundary length surrounding an edge is 2*this value
	if ( (fractionMod(loc_x_rotated, region_width) < region_width_boundary) ||
	     (fractionMod(region_width - loc_x_rotated, region_width) < region_width_boundary)
		){
		logMsg("location is inside boundary, stay at prev region = " + prevRegion);
	} else { 
		// outside boundary
		
		// check that prev region and new region are next to each other
		RegionKey new_region = new RegionKey((int) current_region, 0);
		if (Math.abs(new_region.x - prevRegion.x) != 1) {
			logMsg("WARNING! Location not changed, trying to jump from region " +
					prevRegion.x + " to region " + new_region.x);
		} else {
			logMsg("location is CHANGED to new region = " + new_region + " from region = " + prevRegion);
			changeRegion(new_region);
		}
	}
	*/
