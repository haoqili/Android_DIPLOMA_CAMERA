package edu.mit.csail.diplomamatrix;

import java.io.IOException;

public interface DSMUser {
	Atom handleDSMRequest(DSMLayer.Block b, Atom c) throws IOException, ClassNotFoundException;

	void handleDSMReply(Atom c);
}