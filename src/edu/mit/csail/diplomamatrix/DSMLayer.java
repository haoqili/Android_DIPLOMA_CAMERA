package edu.mit.csail.diplomamatrix;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DSMLayer implements Serializable {
	private static final long serialVersionUID = 5L;

	// Attributes
	public boolean active = false;
	public boolean cacheEnabled;
	public boolean synced;
	public boolean forwardingEnabled;
	RegionKey region;
	public Map<RegionKey, Block> blocks;

	public class Block implements Serializable {
		private static final long serialVersionUID = 5L;

		public Map<String, byte[]> lines;

		Block() {
			this.lines = new ConcurrentHashMap<String, byte[]>();
		}

		Block(Block other) {
			this.lines = new ConcurrentHashMap<String, byte[]>(other.lines);
		}
	}

	// Prevent forwarding loops
	// map source region to an incrementing long
	private long nextOpNonce = 0;
	transient private Map<RegionKey, Set<Long>> forwardedPackets;

	// References to other components
	// Do not serialize these
	transient private Mux mux;
	transient private VCoreDaemon vncDaemon;
	transient public UserApp userApp;

	transient private Runnable pendingRequestsRetryCheckR;

	private static final long pendingRequestsRetryCheckPeriod = 12000;
	private static final long pendingRequestsRetryTimeoutPeriod = 13000;

	// CSM procedure retry and at-most-once queues
	// Requester side
	private Map<RegionKey, Long> nextRequestId;
	private Map<RegionKey, Map<Long, Atom>> pendingRequests;
	// Procedure servicer side
	private Map<RegionKey, Map<Long, Atom>> sentReplies;

	// INSO
	// Write updater / HOME side
	private long globalOrder; // my monotonically increasing write update order
	private Map<Long, Atom> sentWriteUpdates; // Store for retries
	private Map<Long, Set<RegionKey>> acksNotReceived; // who hasn't ack'd?
	// Acknowledger / other side
	private Map<RegionKey, Long> remoteOrders;
	private Map<RegionKey, Map<Long, Atom>> writeBuffer;

	/** Enable CSM cache */
	public synchronized void enableCaching() {
		logMsg("*** CSM Caching Enabled ***");
		this.cacheEnabled = true;
	}

	/** Disable CSM cache */
	public synchronized void disableCaching() {
		logMsg("*** CSM Caching Disabled ***");
		this.cacheEnabled = false;
	}

	/** CSM-UserApp Interface - make a procedure call request */
	public synchronized long atomRequest(int p, long rx, long ry,
			boolean write, byte[] d) {
		if (!active)
			return -1L;

		// Ensure request is uniquely identified by region-region pair and an id
		RegionKey dstRegion = new RegionKey(rx, ry);
		Long id = nextRequestId.get(dstRegion);

		if (id == null) {
			id = 0L;
		}
		nextRequestId.put(dstRegion, id + 1);
		Atom request = new Atom(id, p, Atom.PROC_REQUEST, region,
				new RegionKey(rx, ry));
		request.isWrite = write;
		request.data = d;

		// Include most recent completed request, so remote HOME can cull logs
		request.lowestPendingRequestId = this.lowestPendingRequestId(dstRegion);
		if (request.lowestPendingRequestId < 0)
			request.lowestPendingRequestId = request.requestId;

		logMsg(String.format("Sending %s", request));
		pendingRequests.get(request.dstRegion).put(request.requestId, request);

		// handle cached read-only procedure requests
		if (this.cacheEnabled && !request.dstRegion.equals(this.region)
				&& request.isWrite == false
				&& request.type == Atom.PROC_REQUEST
				&& this.blocks.get(request.dstRegion) != null) {
			logMsg(String.format("Local cache hit on read-only %s", request));
			Atom reply = userApp.handleDSMRequest(
					this.blocks.get(request.dstRegion), request);
			this.dispatchCSMOp(reply);
		} else {
			this.dispatchCSMOp(request);
		}
		return id;
	}

	/**
	 * This function receives CSMOps destined for remote and local regions. It
	 * ensures at-most-once execution by filtering out repeated CSMOps and
	 * orders operations for INSO.
	 */
	public synchronized void handleCSMOp(final Atom op) {
		if (!active || userApp == null) {
			return; // don't do anything until fully started
		}

		// handle writes and non-cacheable procedures or non-cached procedures
		if (op.dstRegion.equals(this.region) || op.broadcast) {
			switch (op.type) {
			case Atom.PROC_REQUEST:
				this.cullSentReplies(op.srcRegion, op.lowestPendingRequestId);

				if (sentReplies.get(op.srcRegion).containsKey(op.requestId)) { // at-most-once
					Atom reply = sentReplies.get(op.srcRegion).get(
							op.requestId);

					logMsg(String.format("Received DUPLICATE %s, replying %s",
							op, reply));

					this.dispatchCSMOp(reply);
					return;
				} else {
					if (!this.blocks.containsKey(this.region)) {
						this.blocks.put(this.region, new Block());
					}
					Atom reply = userApp.handleDSMRequest(
							this.blocks.get(region), op);
					logMsg(String.format("Received %s, replying %s", op, reply));
					sentReplies.get(reply.dstRegion)
							.put(reply.requestId, reply);
					this.dispatchCSMOp(reply);

					if (this.cacheEnabled && op.isWrite) {
						logMsg("Sending out write updates with threadgroup");
						// send out WRITE_UPDATE with broadcast flag
						Atom update = new Atom(-1, -1, Atom.WRITE_UPDATE,
								region, new RegionKey(-2, -2));
						update.broadcast = true;
						update.block = new Block(blocks.get(region));
						update.order = globalOrder;
						globalOrder++;

						// Save for later WRITE_UPDATE_REQUESTS / retries
						sentWriteUpdates.put(update.order, update);
						Set<RegionKey> allOtherRegions = new HashSet<RegionKey>();
						for (long x = 0; x <= vncDaemon.maxRx; x++) {
							for (long y = 0; y <= vncDaemon.maxRy; y++) {
								allOtherRegions.add(new RegionKey(x, y));
							}
						}
						// remove ourself
						allOtherRegions.remove(region);
						acksNotReceived.put(update.order, allOtherRegions);
						dispatchCSMOp(update);
						/*
						 * writeUpdateThread = new Thread( new
						 * ThreadGroup("writeUpdateGroup").getParent(),
						 * this.writeUpdateR, "writeUpdateThread", 256L);
						 * writeUpdateThread.start();
						 */
					}
				}
				break;
			case Atom.PROC_REPLY: // pass to userserver
				// ignore if not in pending request queue, duplicate reply
				if (pendingRequests.get(op.srcRegion).containsKey(op.requestId)) {
					logMsg(String.format("Received %s, handing to UserApp",
							op));
					pendingRequests.get(op.srcRegion).remove(op.requestId);
					userApp.handleDSMReply(op);
				} else {
					logMsg(String.format("Received DUPLICATE %s", op));
				}
				break;
			case Atom.WRITE_UPDATE: // update cache and send back an ack
				// don't apply our own updates, though
				logMsg(String.format("Received %s", op));
				if (this.cacheEnabled && !op.srcRegion.equals(this.region)) {
					if (!this.remoteOrders.containsKey(op.srcRegion)) {
						// first write_update heard from this remote region
						logMsg(String.format(
								"first WRITE_UPDATE from %s is order %d",
								op.srcRegion, op.order));
						this.remoteOrders.put(op.srcRegion, op.order);
					}

					// ignore if < next order expected; odd stack overflow err
					long nextOrder = this.remoteOrders.get(op.srcRegion);
					if (op.order >= nextOrder) {
						this.writeBuffer.get(op.srcRegion).put(op.order, op);

						// send back ack
						Atom ack = new Atom(-1, -1, Atom.WRITE_UPDATE_ACK,
								this.region, op.srcRegion);
						ack.order = op.order;
						this.dispatchCSMOp(ack);

						// try to apply write updates
						checkWriteBuffer(op.srcRegion);
					}
				}
				break;
			case Atom.WRITE_UPDATE_ACK:
				if (this.cacheEnabled) {
					logMsg(String.format("Received %s", op));
					// remove op.srcRegion from List of waiting-for-ack
					if (this.acksNotReceived.containsKey(op.order)) {
						this.acksNotReceived.get(op.order).remove(op.srcRegion);
						if (this.acksNotReceived.get(op.order).size() <= 0) {
							// if waiting-for-ack List is empty, it's complete,
							// so remove from List of pending updates
							logMsg("WRITE_UPDATE:" + op.order
									+ " completed on all other regions.");
							this.acksNotReceived.remove(op.order);
							this.sentWriteUpdates.remove(op.order);
						}
					}
				}
				break;
			case Atom.WRITE_UPDATE_REQUEST: // resend the write_update
				if (this.cacheEnabled) {
					logMsg(String.format("Received %s", op));
					Atom updateToResend = this.sentWriteUpdates.get(op.order);
					if (updateToResend != null) {
						logMsg(String.format("Sending requested %s", op));
						this.dispatchCSMOp(updateToResend);
					} else {
						logMsg(String
								.format("Can't resend requested update, not found in sentWriteupdates"));
					}
				}
				break;
			}
		} else if (forwardingEnabled) {
			if (!this.forwardedPackets.containsKey(op.srcRegion)) {
				this.forwardedPackets.put(new RegionKey(op.srcRegion),
						new HashSet<Long>());
			}

			// Don't forward an op more than once
			if (!this.forwardedPackets.get(op.srcRegion).contains(op.nonce)) {
				// Forward on towards destination, x-y routing
				if (op.dstRegion.x == region.x && op.srcRegion.y < region.y
						&& region.y < op.dstRegion.y) {
					logMsg("Forwarding Atom to remote region.");
					this.forwardedPackets.get(op.srcRegion).add(op.nonce);
					this.dispatchCSMOp(op);
				} else if (op.srcRegion.x < region.x
						&& region.x < op.dstRegion.x) {
					logMsg("Forwarding Atom to remote region.");
					this.forwardedPackets.get(op.srcRegion).add(op.nonce);
					this.dispatchCSMOp(op);
				} else if (op.broadcast) {
					logMsg("Forwarding Atom because it's broadcast.");
					this.forwardedPackets.get(op.srcRegion).add(op.nonce);
					this.dispatchCSMOp(op);
				}
			} else {
				logMsg("Received Atom already forwarded, ignoring...");
			}
		}
	}

	/** go through buffered write updates and apply the ones we can */
	private void checkWriteBuffer(RegionKey r) {
		long nextOrder = this.remoteOrders.get(r);

		logMsg(String
				.format("Applying write updates from %s, remote order = %d, write buffer contains %s",
						r, nextOrder, this.writeBuffer.get(r).keySet()));

		if (this.writeBuffer.get(r).containsKey(nextOrder)) {
			// apply next update
			Atom nextUpdate = this.writeBuffer.get(r).get(nextOrder);
			this.blocks.put(r, new Block(nextUpdate.block));
			this.writeBuffer.get(r).remove(nextOrder);

			// and increment remote order expected
			this.remoteOrders.put(r, ++nextOrder);

			logMsg(String.format("Applied WRITE_UPDATE:%d from %s",
					nextUpdate.order, r));
			checkWriteBuffer(r); // recursively check next expected order
		} else if (this.writeBuffer.get(r).size() > 0) {
			// doesn't contain next order, but other updates are waiting
			// so request the missing order
			logMsg(String.format("Missing WRITE_UPDATE:%d from %s, requesting",
					nextOrder, r));
			Atom retryRequest = new Atom(-1, -1, Atom.WRITE_UPDATE_REQUEST,
					this.region, r);
			retryRequest.order = nextOrder;
			this.dispatchCSMOp(retryRequest);
		}
	}

	private void cullSentReplies(RegionKey rk, long lowestPendingRequestId) {
		Map<Long, Atom> regionSentReplies = this.sentReplies.get(rk);
		Iterator<Entry<Long, Atom>> it = regionSentReplies.entrySet()
				.iterator();
		while (it.hasNext()) {
			Map.Entry<Long, Atom> pairs = (Map.Entry<Long, Atom>) it.next();
			long id = pairs.getValue().requestId;
			if (id < lowestPendingRequestId)
				it.remove();
		}
		logMsg("removed replies before id " + lowestPendingRequestId
				+ " from sentReplies of size " + sentReplies.get(rk).size());
	}

	private long lowestPendingRequestId(RegionKey rk) {
		long lowestId = -1;
		Map<Long, Atom> regionPendingRequests = this.pendingRequests.get(rk);
		for (long id : regionPendingRequests.keySet()) {
			if (lowestId == -1 || id < lowestId)
				lowestId = id;
		}
		return lowestId;
	}

	/** DSMLayer constructor */
	public DSMLayer(VCoreDaemon vnc, RegionKey r, boolean cachen) {
		this.vncDaemon = vnc;
		this.cacheEnabled = cachen;
		this.forwardingEnabled = false; // for matrix mult bench, all in range
		this.synced = false;

		logMsg("*** Starting CSM Layer ***");
		if (this.cacheEnabled) {
			logMsg("*** CSM Layer starting with cache enabled ***");
		} else {
			logMsg("*** CSM Layer starting with cache disabled ***");
		}
		if (this.forwardingEnabled) {
			logMsg("*** CSM Layer starting with forwarding enabled ***");
		} else {
			logMsg("*** CSM Layer starting with forwarding disabled ***");
		}

		this.region = new RegionKey(r);

		this.blocks = new ConcurrentHashMap<RegionKey, Block>();
		for (long x = 0; x <= this.vncDaemon.maxRx; x++) {
			for (long y = 0; y <= this.vncDaemon.maxRy; y++) {
				this.blocks.put(new RegionKey(x, y), new Block());
			}
		}

		this.nextRequestId = new ConcurrentHashMap<RegionKey, Long>();

		this.pendingRequests = new ConcurrentHashMap<RegionKey, Map<Long, Atom>>();
		for (long x = 0; x <= this.vncDaemon.maxRx; x++) {
			for (long y = 0; y <= this.vncDaemon.maxRy; y++) {
				this.pendingRequests.put(new RegionKey(x, y),
						new ConcurrentHashMap<Long, Atom>());
			}
		}

		this.sentReplies = new ConcurrentHashMap<RegionKey, Map<Long, Atom>>();
		for (long x = 0; x <= this.vncDaemon.maxRx; x++) {
			for (long y = 0; y <= this.vncDaemon.maxRy; y++) {
				this.sentReplies.put(new RegionKey(x, y),
						new ConcurrentHashMap<Long, Atom>());
			}
		}

		// INSO / SRCC
		this.writeBuffer = new ConcurrentHashMap<RegionKey, Map<Long, Atom>>();
		for (long x = 0; x <= this.vncDaemon.maxRx; x++) {
			for (long y = 0; y <= this.vncDaemon.maxRy; y++) {
				this.writeBuffer.put(new RegionKey(x, y),
						new ConcurrentHashMap<Long, Atom>());
			}
		}

		this.sentWriteUpdates = new ConcurrentHashMap<Long, Atom>();
		this.acksNotReceived = new ConcurrentHashMap<Long, Set<RegionKey>>();
		this.remoteOrders = new ConcurrentHashMap<RegionKey, Long>();
	}

	public synchronized void start(Mux m, VCoreDaemon v) {
		this.mux = m;
		this.vncDaemon = v;

		logMsg("DSMLayer starting");
		this.userApp = new UserApp(m, this);
		this.userApp.start();

		this.active = true;

		this.forwardedPackets = new HashMap<RegionKey, Set<Long>>();
		for (long x = 0; x <= this.vncDaemon.maxRx; x++) {
			for (long y = 0; y <= this.vncDaemon.maxRy; y++) {
				this.forwardedPackets.put(new RegionKey(x, y),
						new HashSet<Long>());
			}
		}

		// Setup timeout watchdog timer
		pendingRequestsRetryCheckR = new Runnable() {
			@Override
			public void run() {
				for (RegionKey rk : pendingRequests.keySet()) {
					Map<Long, Atom> rPendingRequests = pendingRequests.get(rk);

					Iterator<Entry<Long, Atom>> it = rPendingRequests
							.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry<Long, Atom> pairs = (Map.Entry<Long, Atom>) it
								.next();
						Atom request = pairs.getValue();
						long timeSinceRequest = System.currentTimeMillis()
								- request.timestamp;

						if (timeSinceRequest > 3 * pendingRequestsRetryTimeoutPeriod) {
							// it.remove(); // remove in reply handling instead
							Atom reply = new Atom(request.requestId,
									request.procedure, Atom.PROC_REPLY,
									request.dstRegion, request.srcRegion);
							reply.timedOut = true;
							logMsg("Request timed out, send failure " + reply);
							sentReplies.get(reply.dstRegion).put(
									reply.requestId, reply);
							dispatchCSMOp(reply);
						} else if (timeSinceRequest > pendingRequestsRetryTimeoutPeriod) {
							logMsg("Retrying " + request);
							dispatchCSMOp(request);
						}
					}
				}
				vncDaemon.myHandler.postDelayed(this,
						pendingRequestsRetryCheckPeriod);
			}
		};
		vncDaemon.myHandler.postDelayed(pendingRequestsRetryCheckR,
				pendingRequestsRetryCheckPeriod);
	}

	public synchronized void init() {
		this.userApp.init();
	}

	public synchronized void stop() {
		this.active = false;
		vncDaemon.myHandler.removeCallbacks(pendingRequestsRetryCheckR);
		if (this.userApp != null)
			this.userApp.stop();
		this.userApp = null;
		logMsg("DSMLayer stopped");
		return;
	}

	/** Log message to device display and to Android log. */
	private void logMsg(String msg) {
		vncDaemon.logMsg(msg);
	}

	/** Send a Atom to the Mux, which will loopback or broadcast as necessary */
	private void dispatchCSMOp(Atom op) {
		logMsg("Dispatching Atom " + op);
		op.nonce = nextOpNonce++;
		mux.myHandler.obtainMessage(Mux.CSM_SEND, op).sendToTarget();
	}
}