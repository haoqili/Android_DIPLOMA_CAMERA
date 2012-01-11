package edu.mit.csail.diplomamatrix;
import java.io.Serializable;
import java.util.Arrays;

public class Atom implements Serializable {
	private static final long serialVersionUID = 6L;

	// Types
	final static int PROC_REQUEST = 10;
	final static int PROC_REPLY = 11;
	final static int WRITE_UPDATE = 12;
	final static int WRITE_UPDATE_ACK = 13;
	final static int WRITE_UPDATE_REQUEST = 14;

	// attributes to be serialized
	public long timestamp;
	public long requestId, lowestPendingRequestId;
	public int type;
	public int procedure;
	public RegionKey srcRegion, dstRegion;
	public boolean broadcast;
	public boolean requestSuccess; // did remote region complete the procedure?
	public boolean isWrite;
	public boolean timedOut; // did the request time-out / never heard reply?
	public byte[] data = null;
	public long nonce; // to prevent forwarding loop

	// INSO
	public long order;
	public DSMLayer.Block block;

	public Atom(long id_, int p, int t, RegionKey src, RegionKey dst) {
		this.timestamp = System.currentTimeMillis();
		this.requestId = id_;
		this.lowestPendingRequestId = -2;
		this.type = t;
		this.procedure = p;
		this.srcRegion = new RegionKey(src);
		this.dstRegion = new RegionKey(dst);
		this.broadcast = false;
		this.requestSuccess = true; // assume true for at-most-once execution
		this.timedOut = false;
		this.isWrite = true; // for safety, assume is write requiring UPDATE
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (broadcast ? 1231 : 1237);
		result = prime * result + Arrays.hashCode(data);
		result = prime * result
				+ ((dstRegion == null) ? 0 : dstRegion.hashCode());
		result = prime * result + (isWrite ? 1231 : 1237);
		result = prime
				* result
				+ (int) (lowestPendingRequestId ^ (lowestPendingRequestId >>> 32));
		result = prime * result + procedure;
		result = prime * result + (int) (requestId ^ (requestId >>> 32));
		result = prime * result + (requestSuccess ? 1231 : 1237);
		result = prime * result
				+ ((srcRegion == null) ? 0 : srcRegion.hashCode());
		result = prime * result + type;
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Atom other = (Atom) obj;
		if (broadcast != other.broadcast)
			return false;
		if (!Arrays.equals(data, other.data))
			return false;
		if (dstRegion == null) {
			if (other.dstRegion != null)
				return false;
		} else if (!dstRegion.equals(other.dstRegion))
			return false;
		if (isWrite != other.isWrite)
			return false;
		if (lowestPendingRequestId != other.lowestPendingRequestId)
			return false;
		if (procedure != other.procedure)
			return false;
		if (requestId != other.requestId)
			return false;
		if (requestSuccess != other.requestSuccess)
			return false;
		if (srcRegion == null) {
			if (other.srcRegion != null)
				return false;
		} else if (!srcRegion.equals(other.srcRegion))
			return false;
		if (type != other.type)
			return false;
		return true;
	}

	@Override
	public String toString() {
		switch(this.type) {
		case PROC_REQUEST:
			return String.format("PROC_REQUEST %d:%d %s->%s", this.procedure, this.requestId,
					this.srcRegion, this.dstRegion);
		case PROC_REPLY:
			return String.format("PROC_REPLY %d:%d %s->%s", this.procedure, this.requestId,
					this.srcRegion, this.dstRegion);
		case WRITE_UPDATE:
			return String.format("WRITE_UPDATE %d %s->%s", this.order,
					this.srcRegion, this.dstRegion);
		case WRITE_UPDATE_ACK:
			return String.format("WRITE_UPDATE_ACK %d %s->%s", this.order,
					this.srcRegion, this.dstRegion);
		case WRITE_UPDATE_REQUEST:
			return String.format("WRITE_UPDATE_REQUEST %d %s->%s", this.order,
					this.srcRegion, this.dstRegion);
		}
		return String.format("UNKNOWN %d:%d %s->%s", this.procedure, this.requestId,
				this.srcRegion, this.dstRegion);
	}
}
