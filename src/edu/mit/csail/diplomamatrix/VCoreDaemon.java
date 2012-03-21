package edu.mit.csail.diplomamatrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.util.ArrayList;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import edu.mit.csail.diplomamatrix.Cloud.CloudResponse;

public class VCoreDaemon extends Thread {
	final static private String TAG = "VCoreDaemon";

	public boolean cacheEnabled = Globals.CACHE_ENABLED_ON_START;

	// Constants
	private static final int regionWidth = Globals.REGION_WIDTH; // ~meters
	// lat / long * 10^5, e.g. 103.77900 becomes 10377900
	private static final int minLatitude = Globals.MINIMUM_LATITUDE;
	private static final int minLongitude = Globals.MINIMUM_LONGITUDE;

	// Time periods
	private final static long cloudHearbeatPeriod = 20 * 60 * 1000;
	private final static long heartbeatPeriod = 30000;
	private final static long stateRequestedTimeoutPeriod = 10000;

	private final static long electCandidatePeriod = 400;
	private final static long newLeaderAckTimeoutPeriod = 1000;
	private final static long oldLeaderReleasePeriod = 2000;

	private final static long leaderRequestRetryPeriod = 300;
	private final static long leaderRequestTimeoutPeriod = 1000;

	// Daemon states
	final static int DORMANT = 0; // out of active area
	final static int JOINING = 1; // waiting to join region
	final static int LEADER = 2; // leader of region
	final static int NONLEADER = 3; // non-leader of region following a leader

	// Attributes
	int mState;
	long mId, leaderId, lastHeartbeatTime;
	long maxRx, maxRy;
	RegionKey myRegion;
	// Location loc;

	// Leader-related
	RegionKey leaderRegion;
	ArrayList<Candidate> candidates; // Potential new leaders for old region

	// References to other components
	Handler myHandler;
	Mux mux;
	Cloud myCloud;
	DSMLayer csm = null;

	/** Enable CSM cache */
	public synchronized void enableCaching() {
		this.cacheEnabled = true;
		if (csm != null)
			csm.enableCaching();
	}

	/** Disable CSM cache */
	public synchronized void disableCaching() {
		this.cacheEnabled = false;
		if (csm != null)
			csm.disableCaching();
	}

	/** VCoreDaemon constructor */
	public VCoreDaemon(Mux m, long id, long initRx, long initRy, long maxRx_,
			long maxRy_) {
		this.mux = m;

		mId = id;
		maxRx = maxRx_;
		maxRy = maxRy_;
		lastHeartbeatTime = -1;

		mState = JOINING;
		myRegion = new RegionKey(initRx, initRy);
		String line = String
				.format("Started VCoreDaemon with parameters maxRx = %d , maxRY= %d, minLongitude = %d , minLatitude = %d, regionWidth =%d",
						maxRx, maxRy, minLongitude, minLatitude, regionWidth);
		logMsg(line);
	}

	/** Log message to device display and to Android log. */
	public void logMsg(String line) {
		line = String.format("%d: %s", System.currentTimeMillis(), line);
		mux.myHandler.obtainMessage(Mux.LOG, line).sendToTarget();
		Log.i(TAG, line);
	}

	/** Send a packet through the network thread */
	public void sendPacket(Packet p) {
		logMsg("inside sendPacket(Packet p)");
		mux.myHandler.obtainMessage(Mux.PACKET_SEND, p).sendToTarget();
	}
	
	/** Periodic: check-in with cloud by taking leadership again every  20 min */
	private Runnable cloudHeartbeatR = new Runnable() {
		public void run() {
			if (mState == LEADER) {
				logMsg("leader to cloud hearbeat...");
				if (!Globals.DEBUG_SKIP_CLOUD) {
					CloudResponse csmR = myCloud.takeLeadership(myRegion, mId);
				}
			} 
			myHandler.postDelayed(this, cloudHearbeatPeriod);
		}       
	};      


	/** Periodic: check for LEADER again if still in JOINING state */
	private Runnable stateRequestedTimeoutR = new Runnable() {
		public void run() {
			if (mState == JOINING) {
				logMsg("been in JOINING state for a while now, start over");
				stateTransition(JOINING, myRegion, null);
			}
		}
	};

	/** Periodic: check for heartbeats if NONLEADER, or send them if LEADER */
	private Runnable heartbeatR = new Runnable() {
		public void run() {
			if (mState == LEADER) {
				sendPacket(new Packet(mId, -1, Packet.VNC_MSG,
						Packet.HEARTBEAT, myRegion, myRegion));
			} else if (mState == NONLEADER) {
				if (lastHeartbeatTime < System.currentTimeMillis() - 3
						* heartbeatPeriod) {
					stateTransition(JOINING, myRegion, null);
				}
			} else if (mState == JOINING) {
				// stateTransition(JOINING, myRegion, null);
			}
			myHandler.postDelayed(this, heartbeatPeriod);
		}
	};

	/** Didn't hear a LEADER_REPLY, retry */
	private Runnable leaderRequestRetryR = new Runnable() {
		public void run() {
			if (mState == JOINING) {
				logMsg("sending LEADER_REQUEST");
				sendPacket(new Packet(mId, -1, Packet.VNC_MSG,
						Packet.LEADER_REQUEST, myRegion, myRegion));
				myHandler.postDelayed(leaderRequestRetryR,
						leaderRequestRetryPeriod);
			}
		}
	};

	/** Didn't hear a LEADER_REPLY, so take action */
	private Runnable leaderRequestTimeoutR = new Runnable() {
		public void run() {
			myHandler.removeCallbacks(leaderRequestRetryR);
			logMsg("LEADER_REQUEST timed out");
			if (mState == JOINING) {
				stateTransition(LEADER, myRegion, null);
			}
		}
	};

	/**
	 * Randomly pick a candidate with a matching state version, or if no such
	 * candidate exists, randomly pick a candidate and send it the latest state
	 */
	private Runnable electCandidateR = new Runnable() {
		public void run() {
			if (mState != LEADER)
				return; // only elect a new candidate if we're a leader

			if (candidates.size() == 0) {
				// stop servicing CSM requests so we remain consistent
				csm.stop();
				long t1 = System.currentTimeMillis();
				String json = csmGetJson(csm);
				if (!Globals.DEBUG_SKIP_CLOUD){
					myCloud.uploadState(leaderRegion, mId, json);
					CloudResponse cr = myCloud.releaseLeadership(leaderRegion, mId);
					if (cr == null || cr.status != Cloud.CR_OKAY) {
						logMsg("Error releasing leadership. Retrying...");
						cr = myCloud.releaseLeadership(leaderRegion, mId);
					}
				}
				long t2 = System.currentTimeMillis();
				logMsg(String
						.format("old region empty, uploaded state to cloud in %dms as json %d bytes",
								t2 - t1, json.getBytes().length));

				// transition to new state if in bounds, otherwise go dormant
				stateTransition(JOINING, myRegion, null);
			} else {
				// TODO pick candidate with same CSM data / state
				Candidate elected = candidates.get(0);
				Packet electionReply = new Packet(mId, elected.id,
						Packet.VNC_MSG, Packet.LEADER_CONFIRM, myRegion,
						elected.region);

				// add state data to packet // TODO if hashes !match
				try {
					// stop servicing requests so we remain consistent
					csm.stop();
					electionReply.csm_hash = csmGetHash(csm);
					electionReply.csm_data = csmGetBytes(csm);
				} catch (IOException e) {
					logMsg("Exception adding CSM data to packet: "
							+ e.getMessage());
					electionReply.csm_hash = null;
					electionReply.csm_data = null;
				}

				// send reply and wait for ack
				logMsg("sending LEADER_CONFIRM to node " + elected.id);
				sendPacket(electionReply);
				myHandler.postDelayed(newLeaderAckTimeoutR,
						newLeaderAckTimeoutPeriod);
			}
		}
	};

	/** if we (leader) don't hear ack from newly elected leader, upload state */
	private Runnable newLeaderAckTimeoutR = new Runnable() {
		public void run() {
			if (mState != LEADER)
				return;

			csm.stop(); // stop servicing CSM requests to remain consistent
			// upload CSM state to cloud and release leadership
			String json = csmGetJson(csm);
			long t1 = System.currentTimeMillis();
			if (!Globals.DEBUG_SKIP_CLOUD){
				myCloud.uploadState(leaderRegion, mId, json);
				CloudResponse cr = myCloud.releaseLeadership(leaderRegion, mId);
				if (cr == null || cr.status != Cloud.CR_OKAY) {
					logMsg("Error releasing leadership. Retrying...");
					cr = myCloud.releaseLeadership(leaderRegion, mId);
				}
			}
			long t2 = System.currentTimeMillis();
			logMsg(String
					.format("no LEADER_CONFIRM_ACK, uploaded state to cloud in %dms, json %d bytes",
							t2 - t1, json.getBytes().length));

			// start afresh in new region
			stateTransition(JOINING, myRegion, null);
		}
	};

	/** Handle a Packet */
	private void handlePacket(final Packet vnp) {
		if (vnp.dst != -1 && vnp.dst != mId) {
			return; // ignore if not addressed to broadcast or to this node
		}

		switch (vnp.type) {
		case Packet.VNC_MSG:
			switch (vnp.subtype) {
			case Packet.HEARTBEAT: // update heartbeat deadline
				if (vnp.dstRegion.equals(myRegion) && vnp.src == leaderId)
					lastHeartbeatTime = System.currentTimeMillis();
				break;
			case Packet.LEADER_REQUEST:
				// respond with csm data if we're LEADER of the relevant region
				if (mState == LEADER && vnp.dstRegion.equals(leaderRegion)) {
					logMsg("heard LEADER_REQUEST from node " + vnp.src
							+ ", responding LEADER_REPLY");
					Packet reply = new Packet(mId, vnp.src, Packet.VNC_MSG,
							Packet.LEADER_REPLY, myRegion, vnp.srcRegion);
					// attach csm data
					try {
						reply.csm_hash = csmGetHash(csm);
						reply.csm_data = csmGetBytes(csm);
					} catch (IOException e) {
						logMsg("Exception attaching CSM data to reply: "
								+ e.getMessage());
					}
					sendPacket(reply);
				}
				break;
			case Packet.LEADER_REPLY:
				myHandler.removeCallbacks(leaderRequestTimeoutR);
				myHandler.removeCallbacks(leaderRequestRetryR);
				// follow leader if in same region, and not already following
				if (mState == JOINING && vnp.dstRegion.equals(myRegion)) {
					logMsg("heard LEADER_REPLY from " + vnp.src);
					stateTransition(NONLEADER, myRegion, vnp);
				} else if (mState == NONLEADER && vnp.src != leaderId
						&& vnp.dstRegion.equals(myRegion)) {
					logMsg("heard LEADER_REPLY from new leader " + vnp.src);
					stateTransition(NONLEADER, myRegion, vnp);
				}
				break;
			case Packet.LEADER_ELECT:
				if (mState == NONLEADER && vnp.dstRegion.equals(myRegion)) {
					logMsg("heard LEADER_ELECT from node " + vnp.src
							+ ", responding LEADER_NOMINATE");

					// send LEADER_NOMINATE (including CSM hash)
					// to leaving leader, who is now in a different region
					Packet leaderElectReply = new Packet(mId, vnp.src,
							Packet.VNC_MSG, Packet.LEADER_NOMINATE, myRegion,
							myRegion);
					// leaderElectReply.csm_hash = csmUnits.hashCode();
					sendPacket(leaderElectReply);
				}
				break;
			case Packet.LEADER_NOMINATE:
				// check that we -were- leader of that region
				if (mState == LEADER && vnp.dstRegion.equals(leaderRegion)) {
					logMsg("received LEADER_NOMINATE from " + vnp.src
							+ ", saving as candidate");
					// add candidate to list of candidates
					candidates.add(new Candidate(vnp.src, vnp.dstRegion, ""
							.getBytes())); // store TODO vnp.csm_hash
				}
				break;
			case Packet.LEADER_CONFIRM:
				if (mState == NONLEADER && vnp.dstRegion.equals(myRegion)) {
					logMsg("received LEADER_CONFIRM from " + vnp.src
							+ ", replying LEADER_CONFIRM_ACK");
					sendPacket(new Packet(mId, vnp.src, Packet.VNC_MSG,
							Packet.LEADER_CONFIRM_ACK, myRegion, vnp.dstRegion));
					// delay so old leader has time to release leadership
					logMsg("waiting to give old leader time to release");
					myHandler.postDelayed(new Runnable() {
						public void run() {
							stateTransition(LEADER, myRegion, vnp);
						}
					}, oldLeaderReleasePeriod);
				}
				break;
			case Packet.LEADER_CONFIRM_ACK:
				if (mState == LEADER && vnp.dstRegion.equals(leaderRegion)) {
					myHandler.removeCallbacks(newLeaderAckTimeoutR);
					// cloudUploadState(leaderRegionX, leaderRegionY); // backup
					long t1 = System.currentTimeMillis();
					if (!Globals.DEBUG_SKIP_CLOUD){
						CloudResponse cr = myCloud.releaseLeadership(leaderRegion,
								mId);
						if (cr == null || cr.status != Cloud.CR_OKAY) {
							logMsg("Error releasing leadership. Retrying...");
							cr = myCloud.releaseLeadership(leaderRegion, mId);
						}
					}
					long t2 = System.currentTimeMillis();
					logMsg(String
							.format("recv LEADER_CONFIRM_ACK from %d, released leadership to cloud in %dms",
									vnp.src, t2 - t1));

					// transition to new state
					stateTransition(JOINING, myRegion, null);
				}
			}
		}

	}

	private void stateTransition(int targetState, RegionKey targetRegion,
			Packet vnp) {
		Log.i("VCoreDaemon.java", "inside stateTransition ......................................................................................");
		// Cancel outstanding leader requests, retries, etc.
		myHandler.removeCallbacks(leaderRequestRetryR);
		myHandler.removeCallbacks(leaderRequestTimeoutR);
		myHandler.removeCallbacks(stateRequestedTimeoutR);

		// If out of bounds, don't do anything and just wait
		if (myRegion.x > maxRx || myRegion.y > maxRy || myRegion.x < 0
				|| myRegion.y < 0) {
			Log.i("..... VCoreDaemon.java", "out of bounds");
			logMsg(String.format("region (%d, %d) out of bounds, dormant",
					myRegion.x, myRegion.y));

			this.mState = DORMANT;
			this.myRegion = new RegionKey(targetRegion);
			this.leaderId = -1;
			this.leaderRegion = new RegionKey(-1, -1);
			this.lastHeartbeatTime = -1;

		} else if (targetState == JOINING) {
			Log.i("..... VCoreDaemon.java", "targetState = JOINING");
			this.mState = targetState;
			this.myRegion = new RegionKey(targetRegion);
			this.leaderId = -1;
			this.leaderRegion = new RegionKey(-1, -1);
			this.lastHeartbeatTime = -1;

			// Otherwise ask leader (if any) if we can join
			myHandler.post(leaderRequestRetryR);
			myHandler.postDelayed(leaderRequestTimeoutR,
					VCoreDaemon.leaderRequestTimeoutPeriod);
			myHandler.postDelayed(stateRequestedTimeoutR,
					VCoreDaemon.stateRequestedTimeoutPeriod); // in case
		} else if (targetState == LEADER) {
			Log.i("..... VCoreDaemon.java", "targetState = LEADER");
			// Ask cloud if we can become the leader

			Cloud.CloudResponse csmR = null;

			if (!Globals.DEBUG_SKIP_CLOUD) {
				csmR = myCloud.takeLeadership(myRegion, mId);
				if (csmR == null || csmR.status == Cloud.CR_ERROR) {
					logMsg("cloud rejected leadership request or request failed, wait to retry");
					myHandler.postDelayed(stateRequestedTimeoutR,
							VCoreDaemon.stateRequestedTimeoutPeriod);
					return; // Cancel becoming leader
				} else if (csmR.status == Cloud.CR_CSM) {
					logMsg("cloud accepted leadership request, returned csm bytes size "
							+ csmR.csm_data.length());
				} else if (csmR.status == Cloud.CR_NOCSM) {
					logMsg("cloud accepted leadership request, no csm data included.");
				} else {
					logMsg("cloud accepted leadership request, unexpected status, assuming no csm data included.");
				}
			}

			// Update self attributes
			this.mState = LEADER;
			this.myRegion = new RegionKey(targetRegion);
			this.leaderRegion = new RegionKey(targetRegion);
			this.leaderId = mId;

			// Reset the CSM layer
			if (csm != null) {
				Log.i("..... VCoreDaemon.java", "targetState = LEADER and csm is not null");
				csm.stop();
			}if (false) { // TODO if (vnp.csm_hash != csm.getHash()) { // use own
				Log.i("..... VCoreDaemon.java", "targetState = LEADER and (false)");
				logMsg("continuing with csm data from local replica");
			} else {
				Log.i("..... VCoreDaemon.java", "targetState = LEADER and create new DSMLayer !!!! :D:D:D:D:D");
				csm = new DSMLayer(this, targetRegion, this.cacheEnabled);
				// if triggered by a packet (e.g. leader handoff), try that data
				if (vnp != null && vnp.csm_data != null) {
					try {
						csm = csmLoadBytes(vnp.csm_data);
						csm.synced = true;
						csm.start(mux, this);
						logMsg("loaded csm data from old leader");
					} catch (Exception e) {
						csm = new DSMLayer(this, targetRegion,
								this.cacheEnabled);
						csm.synced = true;
						csm.start(mux, this);
						csm.init();
						logMsg("error loading csm data from old leader"
								+ e.getMessage());
					}
				} else if (csmR != null && csmR.status == Cloud.CR_CSM) { // try
																			// the
																			// cloud
																			// data
					csm = csmLoadJson(csmR.csm_data);
					csm.synced = true;
					csm.start(mux, this);
					logMsg("loaded csm data from cloud");
				} else {
					csm.start(mux, this);
					csm.init();
				}
			}

			// initNeighbors();

			// Notify NONLEADERs in region that I'm leader now
			Packet reply = new Packet(mId, -1, Packet.VNC_MSG,
					Packet.LEADER_REPLY, myRegion, myRegion);
			try {
				// reply.csm_hash = csmGetHash(csm);
				reply.csm_data = csmGetBytes(csm);
			} catch (IOException e) {
				logMsg("error attaching CSM data to reply: " + e.getMessage());
			}
			sendPacket(reply);

			logMsg(String.format("now fully up as LEADER (id=%d) of %s", mId,
					myRegion));
		} else if (targetState == NONLEADER) {
			Log.i("..... VCoreDaemon.java", "targetState = NONLEADER");
			// Update self attributes
			mState = NONLEADER;
			leaderId = vnp.src;
			// initNeighbors();

			// Reset the CSM layer
			if (csm != null)
				csm.stop();
			csm = null;

			/*
			 * if (vnp != null && vnp.csm_data != null) { try { // load the CSM
			 * data from the LEADER csm = new DSMLayer(this, targetRegion); csm
			 * = csmLoadBytes(vnp.csm_data); csm.synced = true; csm.start(mux,
			 * this); logMsg("loaded CSM data from leader"); } catch (Exception
			 * e) { csm = new DSMLayer(this, targetRegion); csm.synced = true;
			 * csm.start(mux, this); csm.init();
			 * logMsg("couldn't load CSM data from leader: " + e.getMessage());
			 * } }
			 */

			logMsg(String.format(
					"now NONLEADER (id=%d) following LEADER (id=%d) in %s",
					mId, leaderId, myRegion));
		}

		mux.myHandler.obtainMessage(Mux.VNC_STATUS_CHANGE).sendToTarget();
		Log.i("..... VCoreDaemon.java", "ends xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
		return;
	}

	/** Called when location has changed, or periodically */
	public synchronized void checkLocation(Location loc) {
		if (loc == null)
			return;

		// round to 5th decimal place (approx 1 meter at equator)
		long mX = Math.round((loc.getLongitude() * 100000) - minLongitude);
		long mY = Math.round((loc.getLatitude() * 100000) - minLatitude);
		logMsg(String.format(
				"GPS lat,long: %f,%f mapping to cartesian x,y: %d,%d",
				loc.getLongitude(), loc.getLatitude(), mX, mY));

		// Determine what region we're in now
		// and if we've entered a new region since last check, take action
		/*long rx = mX / regionWidth;
		long ry = mY / regionWidth;
		RegionKey newRegion = new RegionKey(rx, ry);
		if (!newRegion.equals(myRegion))
			changeRegion(newRegion);*/
		long rx = Math.round(mX / regionWidth);
		//long ry = mY / regionWidth;
		RegionKey newRegion = new RegionKey(rx, 0);
		if (!newRegion.equals(myRegion))
			changeRegion(newRegion);
	}
	/*
	 * Region 0 starts at south-east point and 
	 * increments one by one north-west-wards along Mass Ave.
	 */
	public void determineLocation(Location loc, RegionKey prevRegion){
		// TODO: make this work with Y as well
		// currently determining region only depends on X
		
		logMsg("INSIDE DETERMINELOCATION");
		logMsg("Loc = " + loc + " Previous Region = " + prevRegion);
		
		double locx = loc.getLongitude();
		double locy = loc.getLatitude();		
		double power = 100000;
		
		// x-width of a rectangular region
		double region_width = Math.sqrt(Math.pow(Globals.PHONE_RANGE_METERS, 2) - Math.pow(Globals.ROAD_WIDTH_METERS, 2));
		logMsg("GPS x/long:" + locx + ", GPS y/lat: " + locy + ". Region width in x: " + region_width);

		// X = Longitude, Y = Latitude
		
		// Converting Latitude and Longitude into meters
		// Latitude: each is 10^-5 degree of lat Y
		final int earth_radius_meters = 6378140; //at equator
		final double location_latitude = 42.365; // angle from location to equator
		double one_lat_to_meters = earth_radius_meters * 2 * Math.PI / (360*power); // 1.113 meters
		//logMsg("one_lat_to_meters = " + one_lat_to_meters);
		double one_long_to_meters = Math.cos(Math.toRadians(location_latitude))*one_lat_to_meters; // 0.822 meters
		//logMsg("one_long_to_meters = " + one_long_to_meters);
		
		// Endpoints on (straight) Mass Ave to calculate theta
		/*final double north_west_loc_long = -71.104888;
		final double north_west_loc_lat = 42.365944;
		final double south_east_loc_long = -71.100005;
		final double south_east_loc_lat = 42.363492;
		*/
		final double north_west_loc_long = -71.093881;
		final double north_west_loc_lat = 42.359644;
		final double south_east_loc_long = -71.092894;
		final double south_east_loc_lat = 42.357741;
		double x_diff = Math.abs(south_east_loc_long - north_west_loc_long) * one_long_to_meters * power; // 401.6m
		//logMsg("x_diff = " + x_diff);
		double y_diff = Math.abs(north_west_loc_lat - south_east_loc_lat) * one_lat_to_meters * power; // 272.9m
		//logMsg("y_diff = " + y_diff);
		double theta = Math.atan(y_diff / x_diff); // 0.597 radians or 34.21 degrees
		//logMsg("theta = " + theta); 		
		
		// location in respect to south_east point
		double loc_x = (locx - south_east_loc_long) * one_long_to_meters * power;
		double loc_y = (locy - south_east_loc_lat) * one_lat_to_meters * power;
		logMsg("unrotated x, y: " + loc_x + ", " + loc_y);
		
		// rotational matrix
		double loc_x_rotated = -1 * loc_x * Math.cos(theta) + loc_y * Math.sin(theta); 
		double loc_y_rotated = loc_x * Math.sin(theta) + loc_y * Math.cos(theta);
		logMsg("rotated x, y: " + loc_x_rotated + ", " + loc_y_rotated);
		
		// find the current region
		// Note: only depending on loc_x_rotated for this experiment
		// TODO: for experiments involving a matrix of regions, add y
		double current_region = (int) Math.floor(loc_x_rotated / region_width);
		logMsg("location PINPOINTS to region = " + current_region + ", previous " + prevRegion.x);
		
 
		double region_width_boundary = Globals.REGION_WIDTH_BOUNDARY_METERS;
		// check if it's inside boundary of region
		// region_width_boundary is defined as the boundary from the edge of region to edge of boundary
		// i.e. the total boundary length surrounding an edge is 2*this value
		if ( (fractionMod(loc_x_rotated, region_width) < region_width_boundary) ||
		     (fractionMod(region_width - loc_x_rotated, region_width) < region_width_boundary)
			){
			logMsg("location is INSIDE BOUNDARY, stay at prev region = " + prevRegion);
		} else { 
			// outside boundary
			
			// check that prev region and new region are next to each other
			RegionKey new_region = new RegionKey((int) current_region, 0);
			if (Math.abs(new_region.x - prevRegion.x) > 1) {
				logMsg("Location CHANGED, but changed > 1 regions, so location is changed, trying to jump from region " +
						prevRegion.x + " to region " + new_region.x);
			} else if  (Math.abs(new_region.x - prevRegion.x) == 0) {
				logMsg("stay at region " + prevRegion.x);
			}
			else {
				logMsg("location CHANGED TO NEW region = " + new_region + " from region = " + prevRegion);
				changeRegion(new_region);
			}
		}
	}	
	private double fractionMod(double a, double b){
		double quotient = Math.floor(a/b);
		return a-quotient*b;
	}

	public synchronized void electNewLeader(RegionKey oldRegion,
			RegionKey newRegion) {
		logMsg(String.format("broadcasting LEADER_ELECT to old %s", oldRegion));
		candidates = new ArrayList<Candidate>();
		sendPacket(new Packet(mId, -1, Packet.VNC_MSG, Packet.LEADER_ELECT,
				newRegion, oldRegion));

		// wait to hear replies before selecting a node to be new leader
		myHandler.postDelayed(electCandidateR, electCandidatePeriod);
	}

	/* Called upon moving from one current region to a new one, rx, ry */
	public synchronized void changeRegion(RegionKey newRegion) {
		if (newRegion.equals(myRegion)) // hasn't changed?
			return;

		RegionKey oldRegion = new RegionKey(myRegion);
		myRegion = new RegionKey(newRegion);

		logMsg(String.format("moving from region %s, to %s", oldRegion,
				newRegion));

		// take actions necessary for LEADER leaving a region
		if (mState == LEADER && oldRegion.equals(leaderRegion)) {
			electNewLeader(oldRegion, newRegion);
		} else if (mState != LEADER) { // if not leader
			stateTransition(JOINING, myRegion, null);
		}

		mux.myHandler.obtainMessage(Mux.REGION_CHANGE, myRegion).sendToTarget();
		mux.myHandler.obtainMessage(Mux.VNC_STATUS_CHANGE).sendToTarget();
	}

	/**
	 * Represents a Neighboring region; used by VCoreDaemon
	 */
	public class Neighbor {
		// neighbor status states
		final static int INVALID = 0;
		final static int ACTIVE = 1;

		public int status;
		public long deadline; // heartbeat
		public RegionKey region;

		public Neighbor(RegionKey r, long deadline) {
			this.region = new RegionKey(r);
			this.status = INVALID;
			this.deadline = deadline;
		}
	}

	/**
	 * Represents a candidate node who could be the new leader of region; used
	 * by VCoreDaemon during leader hand-off process
	 */
	public class Candidate {
		public long id;
		public RegionKey region;
		public byte[] csm_hash;

		public Candidate(long id_, RegionKey region_, byte[] csm_hash_) {
			this.id = id_;
			this.region = region_;
			this.csm_hash = csm_hash_;
		}
	}

	private void processMessage(Message msg) {
		switch (msg.what) {
		case Mux.PACKET_RECV:
			handlePacket((Packet) msg.obj);
			break;
		}
	}

	private void onStart() {
		Log.i("VCoreDaemon.java", "onStart() about to stateTransition.........................................................................");
		logMsg("started, mId = " + mId);
		myCloud = new Cloud();
		stateTransition(JOINING, myRegion, null);

		// Start forever recurring events
		myHandler.postDelayed(heartbeatR, heartbeatPeriod);
	}

	private void onStop() {
		myHandler.removeCallbacks(heartbeatR);
		myHandler.removeCallbacks(electCandidateR);
		myHandler.removeCallbacks(newLeaderAckTimeoutR);
		myHandler.removeCallbacks(stateRequestedTimeoutR);
		myHandler.removeCallbacks(leaderRequestTimeoutR);

		if (mState == LEADER) {
			// stop servicing CSM requests so we remain consistent
			csm.stop();

			if (!Globals.DEBUG_SKIP_CLOUD) {
				String json = csmGetJson(csm);
				Cloud.CloudResponse upResponse = myCloud.uploadState(
						leaderRegion, mId, json);
				if (upResponse.status == Cloud.CR_OKAY) {
					logMsg("onStop uploaded state to cloud, json size: "
							+ json.getBytes().length);
				} else {
					logMsg("onStop Upload error: " + upResponse.status);
				}
				myCloud.releaseLeadership(leaderRegion, mId);
				logMsg("onStop released leadership to cloud");
			} else {
				logMsg("onStop skipping release leadership to cloud");
			}
		}

		if (csm != null)
			csm.stop();
	}

	@Override
	public void run() {
		// Prepare looper and handler on current thread
		Looper.prepare();
		myHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				processMessage(msg);
			}
		};
		onStart();
		Looper.loop();
		onStop();
		Log.i(TAG, "Loop ended, vncDaemon thread exiting");
	}

	/** Exit after all queued tasks are done. */
	public synchronized void requestStop() {
		myHandler.post(new Runnable() {
			@Override
			public void run() {
				Log.i(TAG, "Stop request encountered.");
				Looper.myLooper().quit();
			}
		});
	}

	/** Everything pertaining to being a leader of a region */
	public class Leader {
		public RegionKey region;
		public byte[] csm_data;
		public String csm_hash;
	}

	/** VNC-CSM interface - serialize CSM data to JSON */
	public synchronized String csmGetJson(DSMLayer c) {
		logMsg("serializing DSMLayer to JSON");
		Gson gson = new GsonBuilder().enableComplexMapKeySerialization()
				.create();
		String json = gson.toJson(c, DSMLayer.class);
		// Log.d(TAG, "serialized to: " + json);
		return json;
	}

	/** VNC-CSM interface - deserialize CSM data from JSON */
	public synchronized DSMLayer csmLoadJson(String json) {
		Gson gson = new GsonBuilder().enableComplexMapKeySerialization()
				.create();
		DSMLayer c = gson.fromJson(json, DSMLayer.class);
		// logMsg("deserialized from: " + json);
		return c;
	}

	/**
	 * VNC-CSM interface - deserialize from byte array
	 * 
	 * @throws IOException
	 */
	public synchronized byte[] csmGetBytes(DSMLayer c) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = new ObjectOutputStream(bos);
		out.writeObject(c);
		out.close();
		byte[] bytes = bos.toByteArray();
		bos.close();
		return bytes;
	}

	/**
	 * VNC-CSM interface - serialize to byte array
	 * 
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws OptionalDataException
	 */
	public synchronized DSMLayer csmLoadBytes(byte[] d)
			throws OptionalDataException, ClassNotFoundException, IOException {
		ByteArrayInputStream bis = new ByteArrayInputStream(d);
		ObjectInputStream ois = new ObjectInputStream(bis);
		DSMLayer c = (DSMLayer) ois.readObject();
		ois.close();
		return c;
	}

	/** VNC-CSM interface - get hash */
	public synchronized byte[] csmGetHash(DSMLayer csm) {
		// SHA-1 JNI?
		return null;
	}
}
