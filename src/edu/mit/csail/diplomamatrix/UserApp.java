package edu.mit.csail.diplomamatrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
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
	final static int SERVER_UPLOAD_PHOTO = 1;
	final static int REQUESTED_A_PHOTO = 0;

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
	
	/** upload a new picture 
	 * @throws IOException **/
	/*public synchronized void uploadPhoto(Bitmap the_photo) throws IOException {
        Log.i(TAG, "in UserApp's uploadPicture() hqqqqqqqqqqqqqqqqqqq");
		dsm.atomRequest(UPLOAD_PHOTO, mux.vncDaemon.myRegion.x, 0, true, bitmapToBytes(the_photo));
	}*/

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
			try {
				sr = srFromBytes(reply.data);
				// handle completed SparseRunner
			} catch (Exception e) {
				logMsg("Exception deserializing completed SparseRunner!");
				e.printStackTrace();
				return;
			}
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
			} catch (Exception e){
				reply.requestSuccess = false;
				logMsg("Upload Photo failed");
				e.printStackTrace();
			}
			break;
		case REQUESTED_A_PHOTO:
			// reply.data should contain the bytes of the photo
			break;
			
		}

		return reply;
	}
	
	/**
	 * Handle some request from a client (i.e. a non-leader, i.e. the part that doesn't 
	 * know about UserApp or the DIPLOMA layer at all)
	 */
	public synchronized void handleClientRequest(Packet packet){
		switch (packet.subtype) {
		case Packet.CLIENT_UPLOAD_PHOTO:
			logMsg("Inside CLIENT_NEW_PHOTO!!");
			dsm.atomRequest(SERVER_UPLOAD_PHOTO, mux.vncDaemon.myRegion.x, 0, true, packet.photo_bytes);
			break;
		}
	}

	/**
	 * Serialize a Photo to a byte array
	 * 
	 * @throws IOException
	 */
	
	public byte[] _bitmapToBytes(Bitmap bmp) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		bmp.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG */, bos);
		// TODO: I hope this is still under 65000 bytes
		byte[] bytes = bos.toByteArray();
		return bytes;
	}

	/**
	 * Deserialize a Photo from a byte array
	 * 
	 * @throws IOException
	 * @throws ClassNotFoundException
	 * @throws OptionalDataException
	 */
	public SparseRunner srFromBytes(byte[] d) throws OptionalDataException,
			ClassNotFoundException, IOException {
		ByteArrayInputStream bis = new ByteArrayInputStream(d);
		ObjectInputStream ois = new ObjectInputStream(bis);
		SparseRunner a = (SparseRunner) ois.readObject();
		ois.close();
		return a;
	}
}
