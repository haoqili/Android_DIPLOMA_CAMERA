package edu.mit.csail.diplomamatrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;

import android.graphics.Bitmap;
import android.text.format.Time;
import android.util.Log;

// only "leader" have this, i.e. the "leader" parts of leaders have UserApp
public class UserApp implements DSMUser {
	final static String TAG = "UserApp";

	private DSMLayer dsm;
	private Mux mux;

	// DSM Atoms that can be called
	final static int SERVER_UPLOAD_PHOTO = 10;
	final static int SERVER_REQUEST_PHOTO = 11;

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
	 * originating region.
	 */
	public synchronized void handleDSMReply(Atom reply) {
		if (!reply.timedOut) {
			logMsg(String.format("Procedure %d:%d on %s successful",
					reply.procedure, reply.requestId, reply.srcRegion));

			// deal with finished sr
			// deserialize completed SparseRunner
			SparseRunner sr = null;
				//sr = srFromBytes(reply.data);
				// handle completed SparseRunner
		}
	}

	/**
	 * Handle and reply to a DSM Atom request on the local region. Executed by
	 * the destination region.
	 */
	public synchronized Atom handleDSMRequest(DSMLayer.Block block,
			final Atom request) {
		Atom reply = new Atom(request.requestId, request.procedure,
				Atom.PROC_REPLY, request.dstRegion, request.srcRegion);

		switch (request.procedure) {
		case SERVER_UPLOAD_PHOTO:
			logMsg("Inside UPLOAD_PHOTO!!");
			// run on the leader of the region of phone that took photo
			// should just save the photo data inside the block
			Time now = new Time();
			// TODO: better naming?
			String photoName = mux.vncDaemon.myRegion.x + now.toString();
			try {
				block.lines.put(photoName, request.data); // request.data is the bitmap bytes
				reply.requestSuccess = true;
				logMsg("Upload Photo succeeded");
				
				logMsg("Update in UI through StatusActivity:");
				
	            Packet packet = new Packet(-1, 
  					  -1,
  					  Packet.SERVER_SHOW_NEWPHOTO, // unnecssary actually
  					  -1,
  					  mux.vncDaemon.myRegion,
  					  mux.vncDaemon.myRegion);
				packet.photo_bytes = request.data;
				mux.activityHandler.obtainMessage(Packet.SERVER_SHOW_NEWPHOTO, packet).sendToTarget();
			} catch (Exception e){
				reply.requestSuccess = false;
				logMsg("Upload Photo failed");
				e.printStackTrace();
			}
			break;
		case SERVER_REQUEST_PHOTO:
			// relay the client's download photo request to the getphotos_dest_region
			logMsg("INSIDE SERVER_REQUEST_PHOTO!!!");
			/*try {
				int dest_region = _bytesToInt(request.data);
				logMsg("SEND TO REGION: " + dest_region);
				
				if (dest_region == mux.vncDaemon.myRegion.x){
					// Send reply packet, with data of photo
					Packet packet = new Packet(-1, 
							-1,
							// TODO: 
							Packet._REQUEST,
							Packet.CLIENT_DOWNLOAD_PHOTO,
							mux.vncDaemon.myRegion,
							new RegionKey(dest_region, 0)); 
					packet.getphotos_dest_region = _intToBytes(dest_region);
					mux.myHandler.obtainMessage(mux.PACKET_RECV, packet).sendToTarget();
					
				} else {
					// Send forward packet, to forward to dest_region
					Packet packet = new Packet(-1, 
							-1,
							Packet.CLIENT_REQUEST,
							Packet.CLIENT_DOWNLOAD_PHOTO,
							mux.vncDaemon.myRegion,
							new RegionKey(dest_region, 0)); 
					packet.getphotos_dest_region = _intToBytes(dest_region);
					mux.myHandler.obtainMessage(mux.PACKET_RECV, packet).sendToTarget();
				}
			} catch (IOException e) {
				logMsg("_bytesToInt failed");
				e.printStackTrace();
			}*/
			break;
			
		}

		return reply;
	}
	
	/**
	 * Handle some request from a client (i.e. a non-leader, i.e. the part that doesn't 
	 * know about UserApp or the DIPLOMA layer at all)
	 * 
	 * This handleClientRequest on a "server" comes between the "client" and "DIPLOMA"
	 * The client doesn't know anything about DIPLOMA or UserApp, so this method passes along
	 * the client's request as to end up in handleDSMRequest() where CSM's 
	 * memory "blocks" are provided to be edited or read from.  
	 */
	public synchronized void handleClientRequest(Packet packet){
		switch (packet.subtype) {
		case Packet.CLIENT_UPLOAD_PHOTO:
			logMsg("Inside CLIENT_NEW_PHOTO!!");
			dsm.atomRequest(SERVER_UPLOAD_PHOTO, mux.vncDaemon.myRegion.x, 0, true, packet.photo_bytes);
			break;
		case Packet.CLIENT_DOWNLOAD_PHOTO:
			logMsg("Inside CLIENT_DOWNLOAD_PHOTO");
			dsm.atomRequest(SERVER_REQUEST_PHOTO, mux.vncDaemon.myRegion.x, 0, false, packet.getphotos_dest_region);
			break;
		}
	}
	
	
	public byte[] _bitmapToBytes(Bitmap bmp) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bmp.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG */, bos);
		// TODO: I hope this is still under 65000 bytes
		byte[] bytes = bos.toByteArray();
		return bytes;
	}
	
	public byte[] _intToBytes(int my_int) throws IOException {
	    ByteArrayOutputStream bos = new ByteArrayOutputStream();
	    ObjectOutput out = new ObjectOutputStream(bos);
	    out.writeInt(my_int);
	    out.close();
	    byte[] int_bytes = bos.toByteArray();
	    bos.close();
	    return int_bytes;
	}
	public int _bytesToInt(byte[] int_bytes) throws IOException {
	    ByteArrayInputStream bis = new ByteArrayInputStream(int_bytes);
	    ObjectInputStream ois = new ObjectInputStream(bis);
	    int my_int = ois.readInt();
	    ois.close();
	    return my_int;
	}
}
