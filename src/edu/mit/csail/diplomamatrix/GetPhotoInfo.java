package edu.mit.csail.diplomamatrix;

import java.io.Serializable;

/*
 * Save some properties about the photo request
 * So that we always know the origin of request
 */
public class GetPhotoInfo implements Serializable  {
	// set in client request, info saved for leader reply
	public long originNodeId, srcRegion, destRegion;
	public int requestCtr;
	// set in leader reply
	public byte[] photoBytes;
	public boolean isSuccess;
	
	public GetPhotoInfo(long originNodeId_, long srcRegion_, long destRegion_, int requestCounter_){
		originNodeId = originNodeId_;
		srcRegion = srcRegion_;
		destRegion = destRegion_;
		requestCtr = requestCounter_;
	}
}
