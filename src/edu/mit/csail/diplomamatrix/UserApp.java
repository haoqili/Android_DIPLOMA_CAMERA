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
	final static int SERVER_UPLOAD_PHOTO = 10;
	final static int SERVER_GET_PHOTO = 11;
	final static int PHOTO_TO_CLIENT = 12;

	long kernelStartTime, kernelStopTime;
	long runStartTime, runStopTime;

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
		logMsg("UserApp - MatMult initialized and waiting for requests.");
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
	 * originating region. from leader to non-leader
	 */
	public synchronized void handleDSMReply(Atom reply) {
		if (!reply.timedOut) {

			switch (reply.procedure) {
			// handle a reply to our SERVER_GET_PHOTO request
			case SERVER_GET_PHOTO:
				// we now need to send the photo back to the client
				logMsg("got photo from remote region, returning to requesting client");
				// first, figure out the mID of client to send to
				GetPhotoInfo my_gpinfo2;
				try {
					my_gpinfo2 = _bytesToGetphotoinfo(reply.data);
					long request_nodeId = my_gpinfo2.originNodeId;
					long request_region = my_gpinfo2.srcRegion;
					int subtype = Packet.CLIENT_SHOW_NEWPHOTOS;
					
					logMsg("send photo packet to original CLIENT:");
					logMsg("Client is in region: " + request_region
							+ " nodID = " + request_nodeId);
					Packet packet = new Packet(-1, request_nodeId,
							Packet.SERVER_REPLY, subtype,
							mux.vncDaemon.myRegion, new RegionKey(
									request_region, 0));
					packet.photo_bytes = my_gpinfo2.photoBytes;
					if (request_nodeId == mux.vncDaemon.mId) {
						// if this node is leader, go directly to its StatusActivity
						// because phones filter out packets to itself
						logMsg("I'm a leader and I was the photo requester (id = "
								+ request_nodeId + ") about to display photo");
						mux.activityHandler.obtainMessage(subtype, packet)
								.sendToTarget();
					} else {
						logMsg("I'm a leader, but I was not the photo requester, " +
								"sending photo back to original requester id = " + request_nodeId);
						// forward the packet to its non-client, the photo requester
						mux.vncDaemon.sendPacket(packet);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
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
	public synchronized Atom handleDSMRequest(DSMLayer.Block block,
			final Atom request) throws IOException, ClassNotFoundException {
		Atom reply = new Atom(request.requestId, request.procedure,
				Atom.PROC_REPLY, request.dstRegion, request.srcRegion);

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
				byte[] new_photo_bytes = request.data;
				ArrayList<byte[]> photolist = null;
				if (!block.lines.containsKey(Globals.PHOTO_KEY)) {
					photolist = new ArrayList<byte[]>();
				} else {
					byte[] orig_photolist_bytes = block.lines
							.get(Globals.PHOTO_KEY);
					photolist = _bytesToArraylist(orig_photolist_bytes);
				}
				// add this newest photo bytes
				photolist.add(new_photo_bytes);
				// make photolist back to byte[]
				byte[] new_photolist_bytes = _arraylistToBytes(photolist);
				// set it into block.lines
				block.lines.put(Globals.PHOTO_KEY, new_photolist_bytes);

				logMsg("Upload Photo succeeded");

				// display this newly added photo on this leader phone
				logMsg("Update in leader UI through StatusActivity:");
				Packet packet = new Packet(-1, -1, -1, -1,
						mux.vncDaemon.myRegion, mux.vncDaemon.myRegion);
				packet.photo_bytes = new_photo_bytes;
				mux.activityHandler.obtainMessage(Packet.SERVER_SHOW_NEWPHOTO,
						packet).sendToTarget();

				// TODO: actually send an acknowledgment back to sender
				reply.requestSuccess = true;
			} catch (Exception e) {
				reply.requestSuccess = false;
				logMsg("Upload Photo failed");
				e.printStackTrace();
			}
			break;

		case SERVER_GET_PHOTO:
			// relay the client's download photo request to the
			// getphotos_dest_region
			logMsg("INSIDE SERVER_GET_PHOTO!!!");
			GetPhotoInfo my_gpinfo = _bytesToGetphotoinfo(request.data);
			long dest_region = my_gpinfo.destRegion;
			long src_region = my_gpinfo.srcRegion;
			if (dest_region != mux.vncDaemon.myRegion.x) {
				logMsg("RELAYED TO WRONG SERVER!" + mux.vncDaemon.myRegion.x
						+ " instead of dest_region: " + dest_region);
				break;
			}
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
			}
			// Unnecessary self loop, but we don't care about this little
			// overhead
			if (dest_region == src_region) {
				logMsg("dst_region == src_region = " + src_region);
				logMsg(" 1 self to self atomRequest");
			}

			// relay the photo data to the src_region leader, with data of photo
			/*
			 * dsm.atomRequest(PHOTO_TO_CLIENT, src_region, 0, false,
			 * _getphotoinfoToBytes(my_gpinfo));
			 */
			reply.data = _getphotoinfoToBytes(my_gpinfo);
			break;

		}

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
		switch (packet.subtype) {
		case Packet.CLIENT_UPLOAD_PHOTO:
			logMsg("Inside CLIENT_NEW_PHOTO!!");
			dsm.atomRequest(SERVER_UPLOAD_PHOTO, mux.vncDaemon.myRegion.x, 0,
					true, packet.photo_bytes);
			break;
		case Packet.CLIENT_DOWNLOAD_PHOTO:
			logMsg("Inside CLIENT_DOWNLOAD_PHOTO, figure out where to forward packet");
			GetPhotoInfo my_gpinfo = _bytesToGetphotoinfo(packet.getphotoinfo_bytes);
			long dest_region = my_gpinfo.destRegion;
			logMsg("Sending to region: " + dest_region);

			// this atom Request is going to forward this to the destination
			// region
			dsm.atomRequest(SERVER_GET_PHOTO, dest_region, 0, false,
					packet.getphotoinfo_bytes);
			break;
		}
	}

	public byte[] _bitmapToBytes(Bitmap bmp) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bmp.compress(Bitmap.CompressFormat.PNG, 0 /* ignored for PNG */, bos);
		// TODO: I hope this is still under 65000 bytes
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
