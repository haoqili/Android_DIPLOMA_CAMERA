package edu.mit.csail.diplomamatrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.util.Random;

import android.util.Log;

public class UserApp implements DSMUser {
	final static String TAG = "JGF";

	private DSMLayer dsm;
	private Mux mux;

	// DSM Atoms that can be called
	final static int WORK_AND_BARRIER = 0;

	long kernelStartTime, kernelStopTime;
	long runStartTime, runStopTime;

	int barrier_count = 0;

	// App-specific stuff
	public int nthreads = Globals.NTHREADS; // number of threads to execute in the program
	public double ytotal = 0.0;
	public double global_yt[]; // this is very randomly coupled, this is the
								// shared output variable t split across threads

	private int size; // size of the sparse matrix, small medium and large
	private static final long RANDOM_SEED = 10101010;
	// generating entries in the random vector.

	private static final int datasizes_M[] = { 1000, 50000, 100000, 500000,
			1000000 };
	// number of rows in the matrix
	private static final int datasizes_N[] = { 1000, 50000, 100000, 500000,
			1000000 };
	// number of columns in the matrix
	private static final int datasizes_nz[] = { 2000, 250000, 500000, 2500000,
			5000000 };
	// number of non zero elements in the matrix

	// private static final int datasizes_M[] = { 50000, 100000, 500000, 1000000
	// };
	// number of rows in the matrix
	// private static final int datasizes_N[] = { 50000, 100000, 500000, 1000000
	// };
	// number of columns in the matrix
	// private static final int datasizes_nz[] = { 250000, 500000, 2500000,
	// 5000000 };
	// number of non zero elements in the matrix

	// number of iterations to compute the benchmark. I guess it's for
	// averaging. but it makes no sense to me. TODO: Check this.

	Random R = new Random(RANDOM_SEED);

	double[] x;
	double[] y;
	double[] val;
	int[] col;
	int[] row;
	int[] lowsum;
	int[] highsum;

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

	/** Start the benchmark (from master) **/
	public synchronized void startBenchmark() {
        Log.i(TAG, "in UserApp's startBenchmark() hqqqqqqqqqqqqqqqqqqq");
		logMsg(String.format("Starting benchmark with %d threads...", nthreads));
		JGFrun(0);
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
			try {
				sr = srFromBytes(reply.data);
				// handle completed SparseRunner
				handleCompletedSparseRunner(sr);
			} catch (Exception e) {
				logMsg("Exception deserializing completed SparseRunner!");
				e.printStackTrace();
				return;
			}
		}
	}

	public synchronized void handleCompletedSparseRunner(SparseRunner sr) {
		// combine worker's results into main result matrix
		for (int i = lowsum[sr.id]; i < highsum[sr.id]; i++) {
			global_yt[row[i]] = sr.yt[row[i]];
		}

		// Barrier here
		barrier_count++;
		if (barrier_count == nthreads) {
			barrier_count = 0;
			int nz = val.length;

			// Sum results for fast validation
			for (int i = 0; i < nz; i++) {
				ytotal += global_yt[row[i]];
			}

			kernelStopTime = System.currentTimeMillis();

			JGFvalidate(); // check result
			JGFtidyup(); // cleanup

			runStopTime = System.currentTimeMillis();
			Log.d(TAG,
					String.format("JGFrun finished in %dms", runStopTime
							- runStartTime));

			Log.d(TAG,
					String.format("All slaves finished in %dms", kernelStopTime
							- kernelStartTime));
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
		case WORK_AND_BARRIER: // multiply worker / my portion of the matrix
			Log.i("UserApp.java", "multiply worker / my portion of the matrix %%%%%%%%%%%%%%");
			logMsg("Received work request!");
			// Deserialize SparseRunner
			long startTime = System.currentTimeMillis();
			SparseRunner sr = null;
			try {
				sr = srFromBytes(request.data);
				Thread th = new Thread(sr);
				th.start();
				// th.join makes sure th is finished
				// see http://cnapagoda.blogspot.com/2010/01/thread-join-method.html
				th.join();
				reply.data = srToBytes(sr);
				reply.requestSuccess = true;
				long stopTime = System.currentTimeMillis();
				logMsg(String.format("Work finished in %d ms!", stopTime
						- startTime));
			} catch (Exception e) {
				this.logMsg("Exception deserializing / running / serializing SparseRunner!");
				e.printStackTrace();
				reply.requestSuccess = false;
				logMsg("Work failed!");
			}
			break;
		}

		return reply;
	}

	/* JGF STUFF */

	public void JGFsetsize(int size) {
		this.size = size;

	}

	/*
	 * JGFinitialise stores the matrix in Sparse Row format It isn't really
	 * smart, it ends up storing it as Sparse Rows with a separate row and
	 * column matrix.
	 */
	public void JGFinitialise() {

		x = RandomVector(datasizes_N[size], R); // the vector , this makes
												// sense.
		y = new double[datasizes_M[size]]; // what is this ?

		val = new double[datasizes_nz[size]];
		col = new int[datasizes_nz[size]];
		row = new int[datasizes_nz[size]];

		int[] ilow = new int[nthreads];
		int[] iup = new int[nthreads];
		int[] sum = new int[nthreads + 1];
		lowsum = new int[nthreads + 1];
		highsum = new int[nthreads + 1];
		int[] rowt = new int[datasizes_nz[size]];
		int[] colt = new int[datasizes_nz[size]];
		double[] valt = new double[datasizes_nz[size]];
		int sect;

		// This portion below makes sense I guess now.
		for (int i = 0; i < datasizes_nz[size]; i++) {

			// generate random row index (0, M-1)
			row[i] = Math.abs(R.nextInt()) % datasizes_M[size];

			// generate random column index (0, N-1)
			col[i] = Math.abs(R.nextInt()) % datasizes_N[size];

			val[i] = R.nextDouble();

		}

		// reorder arrays for parallel decomposition

		// divide the number of rows by number of threads.
		sect = (datasizes_M[size] + nthreads - 1) / nthreads;

		for (int i = 0; i < nthreads; i++) {
			ilow[i] = i * sect;
			iup[i] = ((i + 1) * sect) - 1;
			if (iup[i] > datasizes_M[size])
				iup[i] = datasizes_M[size];
		}

		for (int i = 0; i < datasizes_nz[size]; i++) {
			for (int j = 0; j < nthreads; j++) {
				if ((row[i] >= ilow[j]) && (row[i] <= iup[j])) {
					sum[j + 1]++;
				}
			}
		}

		// compute the number of elements in each section.

		for (int j = 0; j < nthreads; j++) {
			for (int i = 0; i <= j; i++) {
				lowsum[j] = lowsum[j] + sum[j - i]; // summing backwards I guess
													// ?
				highsum[j] = highsum[j] + sum[j - i];// summing backwards I
														// guess ?
			}
		}

		// compute the number of elements upto and including the current
		// section.

		for (int i = 0; i < datasizes_nz[size]; i++) {
			for (int j = 0; j < nthreads; j++) {
				if ((row[i] >= ilow[j]) && (row[i] <= iup[j])) {
					rowt[highsum[j]] = row[i];
					colt[highsum[j]] = col[i];
					valt[highsum[j]] = val[i];
					highsum[j]++;
				}
			}
		}

		// wonder what is going on here ?

		// The above code: run through all the non zero elements first.
		// Then, for each section in the matrix, check which section belongs to
		// .
		for (int i = 0; i < datasizes_nz[size]; i++) {
			row[i] = rowt[i];
			col[i] = colt[i];
			val[i] = valt[i];
		}

	}

	public void JGFkernel() {
		int nz = val.length;
		global_yt = y;

		System.gc();

		logMsg("Starting benchmark, nthreads = " + nthreads);

		kernelStartTime = System.currentTimeMillis();

		// Send pieces of work to each slave as a DIPLOMA Atom
		for (int i = nthreads - 1; i >= 0; i--) {
			SparseRunner sr = new SparseRunner(global_yt, i, val, row, col, x,
					Globals.SPARSE_NUM_ITER, nz, lowsum, highsum);
			byte[] b = null;
			try {
				Log.d(TAG,
						String.format(
								"Serializing SparseRunner into Atom for region %d,0...",
								i));
				b = srToBytes(sr);
			} catch (Exception e) {
				e.printStackTrace();
			}

			Log.d(TAG, "Sending Atom with payload bytes: " + b.length);
			dsm.atomRequest(WORK_AND_BARRIER, i, 0, false, b);

			b = null;
			sr = null;
		}
	}

	/**
	 * Serialize an Atom to a byte array
	 * 
	 * @throws IOException
	 */
	public byte[] srToBytes(SparseRunner a) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = new ObjectOutputStream(bos);
		out.writeObject(a);
		out.close();
		byte[] bytes = bos.toByteArray();
		bos.close();
		return bytes;
	}

	/**
	 * Deserialize an Atom from a byte array
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

	public void JGFvalidate() {

		double refval[] = { 147.78973120096038, 75.02484945753453,
				150.0130719633895, 749.5245870753752, 900.0092 }; // random
																	// field for
																	// validation.
		double dev = Math.abs(ytotal - refval[size]);
		if (dev > 1.0e-10) {
			Log.d(TAG, "Validation failed");
			Log.d(TAG, "ytotal = " + ytotal + "  " + dev + "  " + size);
		} else {
			Log.d(TAG, "Validation succeeded");
			Log.d(TAG, "ytotal = " + ytotal + "  " + dev + "  " + size);
		}

	}

	public void JGFtidyup() {
		System.gc();
	}

	public void JGFrun(int size) {
        Log.i(TAG, "in UserApp's JGFrun() start hqqqqqqqqqqqqqqqqqqq");
		runStartTime = System.currentTimeMillis();

		JGFsetsize(size);
		JGFinitialise(); // matrix balancing
		JGFkernel(); // calls test() in master.java; spawn slave tasks,
						// distribute to slaves, collect/join/barrier at end
        Log.i(TAG, "in UserApp's JGFrun() end  hqqqqqqqqqqqqqqqqqqq");
	}

	private static double[] RandomVector(int N, java.util.Random R) {
		double A[] = new double[N];

		for (int i = 0; i < N; i++)
			A[i] = R.nextDouble() * 1e-6;

		return A;
	}
}
