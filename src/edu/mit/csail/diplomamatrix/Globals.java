package edu.mit.csail.diplomamatrix;


public class Globals {
        final static public boolean CACHE_ENABLED_ON_START = false;
        final static public double BENCHMARK_READ_DISTRIBUTION_ON_START = 0.9f;
        final static public long BENCHMARK_START_DELAY = 1000L; // milliseconds
        final static public String CSM_SERVER_NAME="128.30.66.123:5212";
        final static public int MINIMUM_LATITUDE=128898;
        final static public int MINIMUM_LONGITUDE=10384948;
        final static public int MAX_X_REGIONS=10;
        final static public int MAX_Y_REGIONS=0;
        final static public int SAMPLING_DURATION=1000;
        final static public int SAMPLING_DISTANCE=1;
        final static public int REGION_WIDTH=180;
        final static public String BROADCAST_ADDRESS="192.168.5.255"; //.255.255 also works
        
        final static public int SPARSE_NUM_ITER=100000;
		public static final boolean DEBUG_SKIP_CLOUD = true;
} 
