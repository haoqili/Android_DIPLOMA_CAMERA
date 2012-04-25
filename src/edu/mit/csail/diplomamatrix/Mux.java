package edu.mit.csail.diplomamatrix;

import java.io.IOException;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class Mux extends Thread {
	private final static String TAG = "Mux";

	private final static long maxRx = Globals.MAX_REGION;
	private final static long maxRy = Globals.MAX_Y_REGIONS;

	private long nodeId;

	Handler myHandler;
	Handler activityHandler;

	// Mux message types
	protected final static int ACTIVITY_DESTROY = 666666;
	protected final static int LOG_NODISPLAY = 27;
	protected final static int LOG = 3;
	protected final static int PACKET_RECV = 4;
	protected final static int PACKET_SEND = 5;
	protected final static int CSM_SEND = 19;
	protected final static int VNC_SEND = 18;
	// protected final static int APP_SEND = 17;
	protected final static int VNC_STATUS_CHANGE = 6;
	protected final static int REGION_CHANGE = 7;
	protected final static int CLIENT_STATUS_CHANGE = 8;

	// Components TODO make private
	private NetworkThread netThread;
	public VCoreDaemon vncDaemon;

	/** Mux constructor */
	public Mux(long id, Handler a) {
		nodeId = id;
		activityHandler = a;
	}

	/** Log message to device display and to Android log. */
	public void logMsg(String line) {
		line = String.format("%d: %s", System.currentTimeMillis(), line);
		activityHandler.obtainMessage(Mux.LOG, line).sendToTarget();
		Log.i(TAG, line);
	}

	/**
	 * Take action on message sent to my handler.
	 * 
	 * @throws ClassNotFoundException
	 * @throws IOException
	 */
	private void processMessage(Message msg) throws IOException,
			ClassNotFoundException {
		switch (msg.what) {
		case Mux.PACKET_RECV:
			Packet vnp = (Packet) msg.obj;
			if (vnp == null) { // due to some error in networkthread loop
				logMsg("Received null packet...");
				return;
			}
			switch (vnp.type) {
			case Packet.VNC_MSG:
				// logMsg("Network -> VNC");
				vncDaemon.myHandler.sendMessage(Message.obtain(msg));
				break;
			case Packet.CSM_MSG:
			    logMsg("GOT CSM_Msg");
				if (vncDaemon.mState == VCoreDaemon.LEADER
						&& vncDaemon.csm != null)
					vncDaemon.csm.handleCSMOp(vnp.csm_op);
				break;

			case Packet.CLIENT_REQUEST:
				logMsg("Inside mux Packet.CLIENT_REQUEST");
				// send request to UserApp to upload photo
				if ((vncDaemon.mState == VCoreDaemon.LEADER) &&
					(vncDaemon.myRegion.x == vnp.srcRegion.x) &&
					(vncDaemon.myRegion.y == vnp.srcRegion.y)	) {

					logMsg("I'm the leader of requesting client, about to process Packet.CLIENT_REQUEST in userApp");
					if (vncDaemon == null) {
						logMsg("vncDaemon is null tee hee");
					}
					if (vncDaemon.csm == null){
						logMsg("vncDaemon.csm is null laa laa");
					}
					if (vncDaemon.csm.userApp == null){
						logMsg("vncDaemon.csm.userApp is null");
					}
					if (vnp == null){
						logMsg("vnp is null");
					}
					assert(vncDaemon.csm.userApp != null);
					vncDaemon.csm.userApp.handleClientRequest(vnp);
				} else if (vncDaemon.mState == VCoreDaemon.NONLEADER) {
					// non leaders can be from both my region and
					// neighboring regions
					logMsg("Nonleader does nothing for Packet.CLIENT_REQUEST");
				}
				break;
			case Packet.SERVER_REPLY:
				logMsg("Inside mux Packet.SERVER_REPLY");
				// check that it's the original photo requester
				if (vnp.dst == nodeId) {
					logMsg("I have the photo requester id = " + nodeId
							+ " about to display photo or receive upload ack");
					activityHandler.obtainMessage(vnp.subtype, vnp)
							.sendToTarget();
				} else {
					logMsg("Ignoring SERVER_REPLY since it's not for me  " + nodeId);
				}
				break;
			} // end switch(vnp.type)

			break;

		case Mux.PACKET_SEND:
			// logMsg("VNC -> Network"); // Only VCoreDaemon uses PACKET_SEND
			this.netThread.sendPacket((Packet) msg.obj);
			break;
		case CSM_SEND:
			Atom op = (Atom) msg.obj;
			// outgoing to network if leader. otherwise, mute and buffer
			Packet p = new Packet(-1, -1, Packet.CSM_MSG, -1, op.srcRegion,
					op.dstRegion);
			p.csm_op = op;
			if (vncDaemon.mState == VCoreDaemon.LEADER) {
				// logMsg("CSM -> Network");
				logMsg("I got a CSM packet at a leader \n");
				this.netThread.sendPacket(p);
				vncDaemon.csm.handleCSMOp(op);
			} else if (vncDaemon.mState == VCoreDaemon.NONLEADER) {
				// logMsg("CSM -> Network (muted, buffered)");
				// TODO csmOpBuffer.add(op)
			}

			break;
		case Mux.LOG:
			activityHandler.sendMessage(Message.obtain(msg));
			break;
		case Mux.LOG_NODISPLAY:
			activityHandler.sendMessage(Message.obtain(msg));
			break;
		case Mux.VNC_STATUS_CHANGE:
			activityHandler.sendMessage(Message.obtain(msg));
			break;
		case Mux.REGION_CHANGE:
			activityHandler.sendMessage(Message.obtain(msg));
			break;
		case Mux.CLIENT_STATUS_CHANGE: // TODO remove?
			activityHandler.sendMessage(Message.obtain(msg));
			break;
		}
	}

	/** Stuff to do right before we enter the run loop. */
	private void onStart() {
		Log.d(TAG, "Mux started");

		// Start the network thread and ensure it's running
		netThread = new NetworkThread(myHandler);
		if (!netThread.socketIsOK()) {
			Log.e(TAG, "Cannot start server: socket not ok.");
			logMsg("FATAL!! Cannot start server: socket not ok. shut down/destroy activity");
			// quit application
			activityHandler.obtainMessage(ACTIVITY_DESTROY).sendToTarget();
			return; // quit out
		}
		netThread.start();
		if (netThread.getLocalAddress() == null) {
			Log.e(TAG, "Couldn't get my IP address.");
			logMsg("FATAL!! Couldn't get my IP address. shut down/destroy activity");
			// quit application
			activityHandler.obtainMessage(ACTIVITY_DESTROY).sendToTarget();
			return; // quit out
		}

		if (nodeId < 0) {
			nodeId = 1000 * netThread.getLocalAddress().getAddress()[2]
					+ netThread.getLocalAddress().getAddress()[3];
			Log.i("Mux.java's nodeId is", String.valueOf(nodeId));
			// nodeId = netThread.getLocalAddress().getAddress()[3]; // lastoct
		}

		// Start outside active region
		long initRx = -1;
		long initRy = -1;

		Log.i(TAG, "starting vncDaemon ........");
		vncDaemon = new VCoreDaemon(this, nodeId, initRx, initRy, maxRx, maxRy);
		vncDaemon.start();
		Log.i(TAG, "vncDaemon started");
	}

	/** Stuff to do right BEFORE exiting the run loop. */
	private void onRequestStop() {
		vncDaemon.requestStop();
		while (vncDaemon.isAlive()) {
			Log.d(TAG, "Waiting for VCoreDaemon to stop...");
			try {
				sleep(1000L);
			} catch (Exception e) {
				Log.d(TAG, "Exception: " + e.getLocalizedMessage());
			}
		}
	}

	/**
	 * Stuff to do right AFTER exiting the run loop (when it has finished
	 * processing remaining tasks / messages / runnables)
	 **/
	private void onStop() {

		netThread.closeSocket();
		while (netThread.isAlive()) {
			Log.d(TAG, "Waiting for NetworkThread to stop...");
			try {
				sleep(1000L);
			} catch (Exception e) {
				Log.d(TAG, "Exception: " + e.getLocalizedMessage());
			}
		}
	}

	/** Exit after all queued tasks are done. */
	public synchronized void requestStop() {
		myHandler.post(new Runnable() {
			@Override
			public void run() {
				Log.i(TAG, "Stop request encountered.");
				onRequestStop();
				Looper.myLooper().quit();
			}
		});
	}

	/** Thread's run method */
	@Override
	public void run() {
		// Prepare looper and handler on current thread
		Log.i("Mux.java", "run() beginning -------------");
		Looper.prepare();
		myHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				try {
					processMessage(msg);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		onStart(); // Start up
		Looper.loop();
		onStop();
		Log.i(TAG, "Thread exiting");
	}
}
