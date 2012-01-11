package edu.mit.csail.diplomamatrix;
import java.io.Serializable;

public class Packet implements Serializable {
	private static final long serialVersionUID = 6L;

	// Packet header types
	final static int VNC_MSG = 1;
	final static int CSM_MSG = 2;
	final static int APP_MSG = 3;

	// VNC_MSG subtypes
	final static int LEADER_REQUEST = 0; // Who's the leader?
	final static int LEADER_REPLY = 1; // I'm the leader!
	final static int HEARTBEAT = 2; // I'm the leader, and I'm still alive!
	final static int LEADER_ELECT = 3; // I'm leaving, who wants to be leader?
	final static int LEADER_NOMINATE = 4; // Pick me as the leader!
	final static int LEADER_CONFIRM = 5; // Okay, you can be the leader!
	final static int LEADER_CONFIRM_ACK = 6; // Yay, I'm the leader now!

	// attributes to be serialized
	public int type;
	public long timestamp;
	
	// forwarding
	public long nonce; // TODO
	
	// VNC stuff
	public int subtype;
	public long src, dst;
	public RegionKey srcRegion, dstRegion;
	public byte[] csm_hash = null;
	public byte[] csm_data = null;

	// CSM request
	public Atom csm_op = null;
	
	// APP stuff
	// public UserOp user_op = null;
	
	/** Construct Packet with values */
	public Packet(long src_, long dst_, int type_, int subtype_,
			RegionKey srcRegion_, RegionKey dstRegion_) {
		type = type_;
		subtype = subtype_;
		timestamp = System.currentTimeMillis();
		srcRegion = srcRegion_;
		dstRegion = dstRegion_;
		src = src_;
		dst = dst_;
	}

	/** String representation, for debugging */
	public String toString() {
		return src + "->" + dst + ": type " + type + "-" + subtype + " region "
				+ srcRegion.toString() + "->" + dstRegion.toString();
	}
}
