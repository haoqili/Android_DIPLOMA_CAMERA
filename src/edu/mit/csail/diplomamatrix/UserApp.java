package edu.mit.csail.diplomamatrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import android.graphics.Bitmap;
import android.util.Log;

// only "leader" have this, i.e. the "leader" parts of leaders have UserApp
public class UserApp implements DSMUser {
	final static String TAG = "UserApp";

	private DSMLayer dsm;
	private Mux mux;

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
	// Third part. Original leader (me) gets this emote region's reply 
	// with photo, in case of photo request
	// with ack of success, in case of photo save/upload
	public synchronized void handleDSMReply(Atom reply) {
		origLeaderReceiveTime = System.currentTimeMillis();
		if (!reply.timedOut) {
			logMsg("Now back in orginitator region's leader, precssing handleDSMReply");
			
			// latency stuff
			long latency = origLeaderReceiveTime - origLeaderSendTime;
			logMsg("Going to and from remote region took latency = " + latency);
			logMsg("= orig leader sent packet at " + origLeaderSendTime + " ~ received reply at " + origLeaderReceiveTime);
			
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
					+ " Leader (for Client=" + request_nodeId + 
					") processes remote region's dsm atom reply and will send Packet reply to Originator Client");
			
			// Create a reply packet, note the subtype is temporarily -1 
			Packet reply_packet = new Packet(-1, request_nodeId,
					Packet.SERVER_REPLY, -1,
					mux.vncDaemon.myRegion, new RegionKey(
							request_region, 0));
			
			// Customize the reply packet
			switch (reply.procedure) {
			case SERVER_UPLOAD_PHOTO:
				logMsg("reply packet contains the ACK for UPLOAD_PHOTO");

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
			// For SERVER_UPLOAD_PHOTO: success of the upload
			reply_packet.getphotoinfo_bytes = reply.data;
			
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

		}
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
	// First Part. This from original client (my nonleader/myself) --> me, which is the leader of my region
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
		logMsg("UserApp handling MY region's client request, will send atom packet to REMOTE region's handleDSMRequest");
		switch (packet.subtype) {
		case Packet.CLIENT_UPLOAD_PHOTO:
			logMsg("request is CLIENT_NEW_PHOTO, so send atom packet to myself (remote region = me)");
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
