package edu.mit.csail.diplomamatrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

import android.graphics.Bitmap;
import android.util.Log;

// only "leader" have this, i.e. the "leader" parts of leaders have UserApp
public class UserApp implements DSMUser {
	final static String TAG = "UserApp";

	private DSMLayer dsm;
	private Mux mux;

	// global variables for runnables
	// Use a HashMap to keep track of replies since 
	// other replies might pop in between final_ack sent and receive 
	// The Packet is the reply packet
	HashMap<Integer, Packet> replyPacketMap;
	// This hashmap keeps track of all the sendingReplyRunnables
	HashMap<Integer, Runnable> replyRepeatingRMap;
	// This hashmap keeps track of all the timeouts so we can delete them when received ack
	// We don't have to delete TimoutRunnables but for better logging, let's track them
	HashMap<Integer, Runnable> replyTimeoutRMap;
	
	// reply counter stuff
	private int replyCounter = 0; // keeping track of unique replies
	private final static long sendingRepliesPeriod = 300;
	// will keep sending replies to client UNTIL
	//    1. heard Final Leg Ack from client 
	// OR 2. sendingRepliesTimeoutPeriod reached
	private final static long sendingRepliesTimeoutPeriod = 1000;
	
	// request counter stuff
	private ArrayList<Integer> processedRequests = null;

	// DSM Atoms that can be called
	final static int SERVER_UPLOAD_PHOTO = 10; // when a client (nonleader/myself) of my region takes a new photo. The photo is uploaded to me to be saved.
	final static int SERVER_GET_PHOTO = 11; // for retrieving a photo from another region

	long origLeaderSendTime, origLeaderReceiveTime; // for recording the roundtrip time in the original client (nonleader/myself)'s region leader

	// App-specific stuff

	/** Log message to device display and to Android log. */
	public void logMsg(String line) {
		line = String.format("%d: %s", System.currentTimeMillis(), line);
		mux.myHandler.obtainMessage(Mux.LOG, line).sendToTarget();
		Log.i(TAG, line);
	}

	/** UserApp constructor */
	public UserApp(Mux m, DSMLayer c) {
		this.mux = m;
		this.dsm = c;
		
		// Initialize ProcessRequests
		processedRequests = new ArrayList<Integer>();
		// Initialize reply maps
		replyPacketMap = new HashMap<Integer, Packet>();
		replyRepeatingRMap = new HashMap<Integer, Runnable>();
		replyTimeoutRMap = new HashMap<Integer, Runnable>();
	}

	/** Called only upon empty DSMLayer invocation in region. */
	public void init() {
		logMsg("UserApp for Camera Diploma initialized and waiting for requests.");
	}

	/** Called every time upon starting / resuming userApp in a region */
	public void start() {
		logMsg("UserApp started.");
	}

	/** Called upon stopping userApp in a region, e.g. switch leader */
	public void stop() {
		logMsg("UserApp stopped.");
	}

	public synchronized void startBenchmark() {
		logMsg("UserApp created successfully");
	}

	/**
	 * Handle a DSM Atom reply from a remote region. Executed by the source /
	 * originating region. At the end, it sends the final leg from leader (me!) to non-leader
	 */
	// Third part. Original leader (me) gets this remote region's reply 
	// with photo, in case of photo request
	// with ack of success, in case of photo save/upload
	public synchronized void handleDSMReply(Atom reply) {
		origLeaderReceiveTime = System.currentTimeMillis();
		if (!reply.timedOut) {
			logMsg("inside handleDSMReply. Now back in orginitator leader of region " + mux.vncDaemon.myRegion.x);
			
			// latency stuff
			long latency = origLeaderReceiveTime - origLeaderSendTime;
			logMsg("Going to and from remote region took latency = " + latency);
			logMsg("orig leader sent packet at " + origLeaderSendTime + " ~ received reply at " + origLeaderReceiveTime);
			
			GetPhotoInfo my_gpinfo2 = null;
			long request_nodeId = 666;
			long request_region = 666;
			
			try {
				// First, figure out the mID of client to send to 
				// from remote leader's reply.data's GetPhotoInfo
				my_gpinfo2 = _bytesToGetphotoinfo(reply.data);
				request_nodeId = my_gpinfo2.originNodeId;
				request_region = my_gpinfo2.srcRegion;
				
			} catch (IOException e1) {
				logMsg("FAIL remote region's dsm atom reply data to Originator Region "
						+ " could not be converted into GetPhotoInfo. IOException");
				e1.printStackTrace();
			} catch (ClassNotFoundException e1) {
				logMsg("FAIL remote region's dsm atom reply data to Originator Region "
						+ " could not be converted into GetPhotoInfo. ClassNotFoundException");
				e1.printStackTrace();
			}
			
			logMsg("Originator Region=" + request_region 
					+ "'s Leader (for Client=" + request_nodeId + 
					") processes remote region's dsm atom reply and will send Packet reply to Originator Client");
			
			// Create a reply packet, note the subtype is temporarily -1 
			Packet reply_packet = new Packet(mux.vncDaemon.mId, request_nodeId,
					Packet.SERVER_REPLY, -1,
					mux.vncDaemon.myRegion, new RegionKey(
							request_region, 0));

			// increment replyCounter
			replyCounter += 1;
			logMsg("change leader replyCounter to: "+replyCounter);
			// because the leader has to keep track of different client's reply acks
			reply_packet.replyCounter = ((int)mux.vncDaemon.mId)*100000 + replyCounter;

			// Customize the reply packet
			switch (reply.procedure) {
			case SERVER_UPLOAD_PHOTO:
				logMsg("reply packet contains the success info for UPLOAD_PHOTO");

				reply_packet.subtype = Packet.CLIENT_UPLOAD_PHOTO_ACK;
				break;
				
			case SERVER_GET_PHOTO:
				// handle a reply to our SERVER_GET_PHOTO request
				logMsg("reply packet contains the newest photo from the remote region");

				reply_packet.subtype = Packet.CLIENT_SHOW_REMOTEPHOTO;
				break;
			}
			
			// fill the packet's GetPhotoInfo with remote leader's reply.data's GetPhotoInfo
			// reply.data is GetPhotoInfo bytes containing
			// For SERVER_GET_PHOTO: photoBytes of the newest remote photo 
			// For SERVER_UPLOAD_PHOTO: successfulness of the upload
			reply_packet.getphotoinfo_bytes = reply.data;
			
			
			//TODO: marker
			// put the reply with the counter in HashMap 
			// it's like a global, so the reply_packet can be sent repeatedly
			replyPacketMap.put(reply_packet.replyCounter, reply_packet);
			
			// keep sending replies every sendingRepliesPeriod UNTIL
			//    1. got Final Leg Ack from client
			// OR 2. sendingRepliesTimeoutPeriod reached
			sendReplies(reply_packet.replyCounter);
		}
		// Cannot handle the case of timedOut to tell nonclient that I (the leader)
		// failed to reach the remote region so it (the nonclient) doesn't have to
		// wait until its timeout ends. This is because the reply (without its 
		// reply.data that contains GetPhotoInfo) cannot distinguish among its nonclients.
		// So even if a nonclient hears a PROC REPLY of FAILURE, it can't be sure
		// whether that PROC REPLY was a response to its request or some other 
		// nonleader in the region's request
	}
	
	// keep sending replies every sendingRepliesPeriod UNTIL
	//    1. got Final Leg Ack from client
	// OR 2. sendingRepliesTimeoutPeriod reached
	private void sendReplies(int replyCount_) {
		logMsg("inside sendReplies of replyCount = " + replyCount_);
		
		// ReplyRepeating Runnable:
		// reply Runnable (repeats)
		Runnable sendReplyRepeatingRunnable = createReplyRepeatingRunnable(replyCount_);
		// put Runnable in HashMap
		replyRepeatingRMap.put(replyCount_, sendReplyRepeatingRunnable);
		// post the sendReplyRepeatingRunnable
		mux.vncDaemon.myHandler.post(sendReplyRepeatingRunnable);

		// Timeout Runnable:
		// timeout Runnable to stop the reply Runnable
		Runnable sendReplyTimeoutR = createReplyTimeoutR(replyCount_);
		// put Runnable in HashMap
		replyTimeoutRMap.put(replyCount_, sendReplyTimeoutR);
		// post timeout runnable
		mux.vncDaemon.myHandler.postDelayed(sendReplyTimeoutR, sendingRepliesTimeoutPeriod);
	}
	
	// TODO: marker
	// Called when 
	//    1. inside handleClientRequest when heard Final Leg Ack from client 
	// OR 2. inside sendReplyTimeoutR when sendingRepliesTimeoutPeriod reached
	private void deleteSendReplyRepeatingR(int replyCount_){
		if (replyRepeatingRMap.containsKey(replyCount_)){
			logMsg("deleting the key's associated reply_REPEATING_RMap runnable for replyCount "
					+ replyCount_);
			Runnable sendReplyRepeatingRunnable_mine = replyRepeatingRMap.get(replyCount_);
			mux.vncDaemon.myHandler.removeCallbacks(sendReplyRepeatingRunnable_mine);
			// delete from hashmap
			replyRepeatingRMap.remove(replyCount_);
		} else {
			logMsg("the key's associated reply_REPEATING_ RMap runnable ALREADY deleted for replyCount "
					+ replyCount_);
		}
		
	}
	
	private Runnable createReplyTimeoutR(final int replyCount_){
		Runnable sendReplyTimeoutR = new Runnable(){
			public void run(){
				logMsg("inside sendReplyTimeoutR");
				deleteSendReplyRepeatingR(replyCount_);
			}
		};
		return sendReplyTimeoutR;
	}
	
	private Runnable createReplyRepeatingRunnable(final int replyCount_) {
		Runnable sendReplyRepeatingRunnable = new Runnable() {
			public void run(){	
				logMsg("=======================");
				logMsg("inside sendReplyRepeatingRunnable for replyCount = " + replyCount_);
				
				// get the packet to send
				Packet reply_packet = replyPacketMap.get(replyCount_);
				long request_nodeId = reply_packet.dst;
				
				logMsg("Leader about to send REPLY packet, number: " + reply_packet.replyCounter
						+ " type: " + reply_packet.subtype
	    				+ " Leader in region: " + reply_packet.srcRegion + 
	    				" to Client nodID: " + request_nodeId);
				
				
				// Send reply packet to originator client (the final leg)
				if (request_nodeId == mux.vncDaemon.mId) {
					// if this node is leader, go directly to mux
					// because phones filter out packets to itself
					logMsg("I (the leader) was also the originator client (id = "
							+ request_nodeId + ") so I hand the packet to my mux directly, without UDP");
					mux.activityHandler.obtainMessage(reply_packet.subtype, reply_packet)
					.sendToTarget();
				} else {
					logMsg("I (the leader) was not the originator client (which id = "
							+ request_nodeId + ") so I use UDP to send packet back to my nonleader");
					mux.vncDaemon.sendPacket(reply_packet);
				}
	    		logMsg("=== Finished one round of sending REPLY Packet =======");

				mux.vncDaemon.myHandler.postDelayed(this, sendingRepliesPeriod);
			}
		};
		return sendReplyRepeatingRunnable;
	}

	/**
	 * Handle and reply to a DSM Atom request on the local region. Executed by
	 * the destination region.
	 * 
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	// Second Part. Remote leader region, where stuff actually gets done
	public synchronized Atom handleDSMRequest(DSMLayer.Block block,
			final Atom request) throws IOException, ClassNotFoundException {
		logMsg("inside handleDSMRequest. At requests's remote leader of region " + mux.vncDaemon.myRegion.x);
		// reply goes to the *originator* client (leader or nonleader), in the original region
		Atom reply = new Atom(request.requestId, request.procedure,
				Atom.PROC_REPLY, request.dstRegion, request.srcRegion);
		
		GetPhotoInfo my_gpinfo = _bytesToGetphotoinfo(request.data);
		
		// reply is not successful by default
		my_gpinfo.isSuccess = false;

		switch (request.procedure) {
		case SERVER_UPLOAD_PHOTO:
			logMsg("Inside UPLOAD_PHOTO!!");
			// run on the leader of the region of phone that took photo
			// should just save the photo data inside the block
			/**
			 * The structure we have to save is a HashMap<String --> byte[]>
			 * We're going to have Globals.PHOTO_KEY --> byte[] of
			 * ArrayList<byte []> of photo bytes. This way, we can query the
			 * latest x photos as well as getting the ith photo
			 */
			// TODO: check the max byte[] of HashMap
			try {
				byte[] new_photo_bytes = my_gpinfo.photoBytes;
				
				// get photolist ArrayList from saved byte [] block data
				ArrayList<byte[]> photolist = null;
				if (!block.lines.containsKey(Globals.PHOTO_KEY)) {
					photolist = new ArrayList<byte[]>();
					photolist.add(new_photo_bytes);
				} else {
					byte[] orig_photolist_bytes = block.lines
							.get(Globals.PHOTO_KEY);
					photolist = _bytesToArraylist(orig_photolist_bytes);
				
					// TODO: revert to photolist.add(new_photo_bytes) for saving many photos
					// set this newest photo to the first element of photolist
					photolist.set(0, new_photo_bytes);
				}
				// make photolist back to byte[]
				byte[] new_photolist_bytes = _arraylistToBytes(photolist);
				// set it into block.lines
				block.lines.put(Globals.PHOTO_KEY, new_photolist_bytes);

				// Edit GetPhotoInfo by setting setting the upload to be successful
				my_gpinfo.isSuccess = true;
				logMsg("my_gpinfo.isSuccess is now (should be true): " + my_gpinfo.isSuccess);
				
				// display this newly added photo on this leader phone
				// ------- loopback from here to my StatusActivity for UI display
				logMsg("Region leader successfully uploaded a new photo taken by a client in region. " +
						"Region leader now display the new photo on its screen (through StatusActivity)");
				Packet packet = new Packet(-1, -1, -1, -1,
						mux.vncDaemon.myRegion, mux.vncDaemon.myRegion);
				// set photo bytes to be displayed on Region Leader (me) of new photo taken by a client
				packet.getphotoinfo_bytes = _getphotoinfoToBytes(my_gpinfo);
				// display new photo on leader
				mux.activityHandler.obtainMessage(Packet.SERVER_SHOW_NEWPHOTO,
						packet).sendToTarget();
				// ------ end loopback
				
				// now make the reply packet smaller by taking out the photoBytes inside my_gpinfo
				my_gpinfo.photoBytes = null;

			} catch (Exception e) {
				logMsg("Remote region FAILED to Upload Photo");
				logMsg("my_gpinfo.isSuccess is (should be false): " + my_gpinfo.isSuccess);
				e.printStackTrace();
			}
			break;

		case SERVER_GET_PHOTO:
			// relay the client's download photo request to the
			// getphotos_dest_region
			logMsg("INSIDE SERVER_GET_PHOTO!!!");
			long dest_region = my_gpinfo.destRegion;
			long src_region = my_gpinfo.srcRegion;
			if (dest_region != mux.vncDaemon.myRegion.x) { // should never get here, mux should filter this
				logMsg("RELAYED TO WRONG SERVER!" + mux.vncDaemon.myRegion.x
						+ " instead of dest_region: " + dest_region);
				break;
			}
			
			// Edit GetPhotoInfo by adding in remote photo into its photoBytes
			// TODO: MAKE IT GET ith Photo, currently just sending newest photo
			if (block.lines.get(Globals.PHOTO_KEY) == null) {
				// this leader doesn't have any photos yet
				my_gpinfo.photoBytes = null;
				logMsg("I don't have any photos yet");
			} else {
				// this leader has a photo
				byte[] photolist_bytes = block.lines.get(Globals.PHOTO_KEY);
				ArrayList<byte[]> photolist = _bytesToArraylist(photolist_bytes);

				byte[] latest_photo_bytes = photolist.get(photolist.size() - 1);

				my_gpinfo.photoBytes = latest_photo_bytes;
				// Edit GetPhotoInfo by setting setting the photo retrieval to be successful
				my_gpinfo.isSuccess = true;
			}
			// Unnecessary self loop, but we don't care about this little
			// overhead
			if (dest_region == src_region) {
				logMsg("dst_region == src_region = " + src_region);
				logMsg(" 1 self to self atomRequest");
			}

			break;

		}

		// the modified GetPhotoInfo is added to the reply, which will be passed to originator client
		reply.data = _getphotoinfoToBytes(my_gpinfo);
		return reply;
	}

	/**
	 * Handle some request from a client (i.e. a non-leader, i.e. the part that
	 * doesn't know about UserApp or the DIPLOMA layer at all)
	 * 
	 * This handleClientRequest on a "server" comes between the "client" and
	 * "DIPLOMA" The client doesn't know anything about DIPLOMA or UserApp, so
	 * this method passes along the client's request as to end up in
	 * handleDSMRequest() where CSM's memory "blocks" are provided to be edited
	 * or read from.
	 * 
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	// First Part/leg. This from original client (my nonleader/myself) --> me, which is the leader of my region
	public synchronized void handleClientRequest(Packet packet)
			throws IOException, ClassNotFoundException {
		if ((packet.dstRegion.x != mux.vncDaemon.myRegion.x)
				&& (packet.dstRegion.y != mux.vncDaemon.myRegion.y)) {
			// discard requests from non-leaders that are not from the same
			// region
			logMsg("pack.dstRegion: " + packet.dstRegion + " muxmyRegion: "
					+ mux.vncDaemon.myRegion);
			logMsg("handleClientRequest discarding packet to reg: "
					+ packet.dstRegion);
			return;
		}
		logMsg("inside handleClientRequest. Originator leader of region " + mux.vncDaemon.myRegion.x);
		// also discard packets that are already-processed
		if (processedRequests.contains(packet.requestCounter)){
			logMsg("discarding repeated requestCounter="+packet.requestCounter
					+ ", but still send an ack back");
		
		} else {
			logMsg("got new request, requestCounter = " + packet.requestCounter);
			switch (packet.subtype) {
			case Packet.CLIENT_UPLOAD_PHOTO:
				logMsg("request is CLIENT_UPLOAD_PHOTO, so send atom packet to myself (remote region = me)");
				origLeaderSendTime = System.currentTimeMillis();
				dsm.atomRequest(SERVER_UPLOAD_PHOTO, mux.vncDaemon.myRegion.x, 0,
						true, packet.getphotoinfo_bytes);
				break;
			case Packet.CLIENT_DOWNLOAD_PHOTO:
				logMsg("request is CLIENT_DOWNLOAD_PHOTO, figure out where (remote region) to forward packet");
				// Packet.getphotoinfo_bytes unchanged, just retrieving info of destination region
				GetPhotoInfo my_gpinfo = _bytesToGetphotoinfo(packet.getphotoinfo_bytes);
				long dest_region = my_gpinfo.destRegion;
				logMsg("Sending to region: " + dest_region);

				// this atom Request is going to forward this to the destination region
				origLeaderSendTime = System.currentTimeMillis();
				dsm.atomRequest(SERVER_GET_PHOTO, dest_region, 0, false,
						packet.getphotoinfo_bytes);
				break;
			case Packet.CLIENT_FINAL_LEG_ACK:
				logMsg("request is CLIENT_FINAL_LEG_ACK with replyCounter of " + packet.replyCounter);
				// Yay the leader got the ack from its client, the last leg succeeded
				int replyC = packet.replyCounter;
				logMsg("Yay the last leg succeeded. Removing reply runnables ...");
				// remove the sendReply
				deleteSendReplyRepeatingR(packet.replyCounter);
				// remove sendReplyTimeoutR
				if (replyTimeoutRMap.containsKey(replyC)){
					logMsg("deleting the key's associated reply_TIMEOUT_RMap runnable for replyCount "
							+ replyC);
					Runnable sendReplyTimeoutR_mine = replyTimeoutRMap.get(replyC);
					mux.vncDaemon.myHandler.removeCallbacks(sendReplyTimeoutR_mine);
					// delete from hashmap
					replyTimeoutRMap.remove(replyC);
				} else {
					logMsg("the key's associated reply_TIMEOUT_RMap runnable ALREADY deleted for replyCount "
							+ replyC);
				}
				logMsg("do not send ack for received final_leg_ack");
				return;  // should not send ack for this received final_leg_ack
			}
		}

		// note down this newly processed request
		logMsg("Note down new request by adding requestCounter=" + packet.requestCounter + " to HashMap processedRequests");
		processedRequests.add(packet.requestCounter);
		
		// TODO: marker
		logMsg("Make FirstLeg Ack regardless of new or already-processed requests");
		// send ack ONCE to client to let it know that this First Leg is complete
		// so that the client can resend if it doesn't hear this ack
		// since we send it only ONCE, no need to send it repeatedly runnable
		//							   nor have the need of a reply counter, as it's targeted to one client
		// Create an ack packet:
		Packet reply_packet = new Packet(-1, packet.src,
				Packet.SERVER_REPLY, Packet.SERVER_FIRST_LEG_ACK,
				mux.vncDaemon.myRegion, mux.vncDaemon.myRegion);
		
		// Send ack packet to originator client
		if (packet.src == mux.vncDaemon.mId) {
			// if this node is leader, go directly to mux
			// because phones filter out packets to itself
			logMsg("sending a First Leg ack to myself, becaues I (the leader) was also the originator client (id = "
					+ mux.vncDaemon.mId + ")");
			mux.activityHandler.obtainMessage(reply_packet.subtype, reply_packet)
			.sendToTarget();
		} else {
			logMsg("The leader sending a First Leg ack to originator client (which id = "
					+ packet.src + ")");
			mux.vncDaemon.sendPacket(reply_packet);
		}
		
	}

	public byte[] _bitmapToBytes(Bitmap bmp) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bmp.compress(Bitmap.CompressFormat.PNG, 0 /* ignored for PNG */, bos);
		// I hope this is still under 65000 bytes
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

	public static byte[] _getphotoinfoToBytes(GetPhotoInfo my_gpinfo)
			throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = new ObjectOutputStream(bos);
		out.writeObject(my_gpinfo);
		out.close();
		byte[] int_bytes = bos.toByteArray();
		bos.close();
		return int_bytes;
	}

	public ArrayList<byte[]> _bytesToArraylist(byte[] int_bytes)
			throws IOException, ClassNotFoundException {
		ByteArrayInputStream bis = new ByteArrayInputStream(int_bytes);
		ObjectInputStream ois = new ObjectInputStream(bis);
		@SuppressWarnings("unchecked")
		ArrayList<byte[]> my_arrlist = (ArrayList<byte[]>) ois.readObject();
		ois.close();
		return my_arrlist;
	}

	public byte[] _arraylistToBytes(ArrayList<byte[]> my_arrlist)
			throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = new ObjectOutputStream(bos);
		out.writeObject(my_arrlist);
		out.close();
		byte[] int_bytes = bos.toByteArray();
		bos.close();
		return int_bytes;
	}

}
