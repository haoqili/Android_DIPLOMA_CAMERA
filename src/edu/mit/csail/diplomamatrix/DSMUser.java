package edu.mit.csail.diplomamatrix;

public interface DSMUser {
	Atom handleDSMRequest(DSMLayer.Block b, Atom c);

	void handleDSMReply(Atom c);
}