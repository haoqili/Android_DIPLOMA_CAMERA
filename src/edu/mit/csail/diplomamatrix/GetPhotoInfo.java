package edu.mit.csail.diplomamatrix;

import java.io.Serializable;

/*
 * Save some properties about the photo request
 * So that we always know the origin of request
 */
public class GetPhotoInfo implements Serializable  {
	public long originNodeId, srcRegion, destRegion;
	public byte[] photoBytes;
	public boolean isSuccess;
	
	public GetPhotoInfo(long originNodeId_, long srcRegion_, long destRegion_){
		originNodeId = originNodeId_;
		srcRegion = srcRegion_;
		destRegion = destRegion_;
	}
}
