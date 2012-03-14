package edu.mit.csail.diplomamatrix;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import android.os.Handler;
import android.util.Log;

public class NetworkThread extends Thread {
	private static final String TAG = "NetworkThread";

	private Handler muxHandler;

	// UDP over IPv4 Networking
	private static final int MAX_PACKET_SIZE = 110592; // bytes
	private static final int PORT = 6666;
	private DatagramSocket mySocket;
	private boolean socketOK = true;
	private InetAddress myBcastIPAddress;
	private InetAddress myIPAddress;
	
	/** Log message to device display and to Android log. */
	public void logMsg(String line) {
		line = String.format("%d: %s", System.currentTimeMillis(), line);
		muxHandler.obtainMessage(Mux.LOG_NODISPLAY, line).sendToTarget();
		Log.i(TAG, line);
	}

	/** NetworkThread constructor */
	public NetworkThread(Handler h) {
		muxHandler = h;

		// Determine broadcast IP address
		try {
			myBcastIPAddress = getBroadcastAddress();
			// Log.i(TAG, "My Broadcast IP address:" + myBcastIPAddress);
		} catch (Exception e) {
			Log.e(TAG, "Cannot get my broadcast IP address" + e.toString());
			closeSocket();
			return;
		}

		// Determine local IP address
		myIPAddress = null;
		try {
			NetworkInterface intf = NetworkInterface.getByName("eth0"); // eth0 //wlan0
			for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
					.hasMoreElements();) {
				InetAddress inetAddress = enumIpAddr.nextElement();
				if (!inetAddress.isLoopbackAddress()) {
					myIPAddress = inetAddress;
				}
			}
			if (myIPAddress == null) {
			    Log.e(TAG, "no addresses bound to barnacle hqqqqqqqqqqqqqqqqqq ");
				throw new Exception("no addresses bound to eth0");
			}
		} catch (Exception e) {
			Log.e(TAG, "can't determine local IP address: " + e.toString());
			closeSocket();
			return;
		}
        

        /* HaoQi download from: http://www.droidnova.com/get-the-ip-address-of-your-device,304.html
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                		enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        myIPAddress =  inetAddress;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "can't determine local IP address: " + e.toString());
            closeSocket();
            return;
        }*/


		restartSocket();

		Log.i(TAG, "started, local IP address:" + myIPAddress);
	}

	/** Close the socket before exiting the application */
	public void closeSocket() {
		mySocket.close();
	}

	/** Start of r */
	public void restartSocket() {
		if (mySocket != null && !mySocket.isClosed())
			mySocket.close();

		// Create UDP socket for receiving packets
		try {
			mySocket = new DatagramSocket(PORT);
			mySocket.setBroadcast(true);

			Log.i(TAG, String.format(
					"Initial socket buffer sizes: %d receive, %d send",
					mySocket.getReceiveBufferSize(),
					mySocket.getSendBufferSize()));

			mySocket.setReceiveBufferSize(MAX_PACKET_SIZE);
			mySocket.setSendBufferSize(MAX_PACKET_SIZE);

			Log.i(TAG, String.format(
					"Set socket buffer sizes to: %d receive, %d send",
					mySocket.getReceiveBufferSize(),
					mySocket.getSendBufferSize()));
		} catch (Exception e) {
			Log.e(TAG, "Cannot open socket" + e.getMessage());
			socketOK = false;
			return;
		}
	}

	/** If not socketOK, then receive loop thread will stop */
	boolean socketIsOK() {
		return socketOK;
	}

	/** Thread's receive loop for UDP packets */
	@Override
	public void run() {
		byte[] receiveData = new byte[MAX_PACKET_SIZE];

		while (socketOK) {
			DatagramPacket dPacket = new DatagramPacket(receiveData,
					receiveData.length);
			try {
				mySocket.receive(dPacket);
			} catch (IOException e) {
				Log.e(TAG, "Exception on mySocket.receive: " + e.getMessage());
				socketOK = false;
				continue;
			}

			// filter out our own UDP broadcasts
			if (dPacket.getAddress().equals(myIPAddress))
				continue;
			
			logMsg("Received UDP payload: " + dPacket.getLength());

			// Decode and send packet back to mux
			muxHandler.obtainMessage(Mux.PACKET_RECV, readPacket(dPacket))
					.sendToTarget();
		} // end while(socketOK)
	} // end run()

	/**
	 * Read back a VirtualNode network packet.
	 * 
	 * @throws Exception
	 */
	private synchronized Packet readPacket(DatagramPacket dgram) {
		Packet vnpIn = null;
		ByteArrayInputStream bis = new ByteArrayInputStream(dgram.getData());
		ObjectInputStream ois = null;
		try {
			ois = new ObjectInputStream(bis);
		} catch (IOException e) {
			Log.e(TAG, "error on new ObjectInputStream: " + e.getMessage());
			return null;
		}
		try {
			vnpIn = (Packet) ois.readObject();
		} catch (ClassNotFoundException e) {
			Log.e(TAG,
					"ClassNotFoundException reading object from ois: "
							+ e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			Log.e(TAG, "IOException reading object from ois: " + e.getClass()
					+ ": " + e.getMessage());
		}
		try {
			ois.close();
		} catch (IOException e) {
			Log.e(TAG, "error closing ois: " + e.getMessage());
		}
		return vnpIn;
	}

	/** Send a VirtualNode network packet. */
	public synchronized void sendPacket(Packet vnpOut) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			ObjectOutput out = new ObjectOutputStream(bos);
			out.writeObject(vnpOut);
			out.close();
			byte[] data = bos.toByteArray();
			logMsg("Sending UDP payload: " + data.length);
			sendData(data);
		} catch (IOException e) {
			Log.e(TAG, "error sending packet:" + e.getMessage());
			e.printStackTrace();
		}
	}

	/** Send an UDP packet to the broadcast address */
	private void sendData(byte[] sendData) throws IOException {
		mySocket.send(new DatagramPacket(sendData, sendData.length,
				myBcastIPAddress, PORT));
	}

	/** Calculate the broadcast IP we need to send the packet along. */
	private InetAddress getBroadcastAddress() throws IOException {
		return InetAddress.getByName(Globals.BROADCAST_ADDRESS);
	}

	/** Return our stored local IP address. */
	public synchronized InetAddress getLocalAddress() {
		return myIPAddress;
	}
}
