package edu.mit.csail.diplomamatrix;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.util.Log;

import com.google.gson.Gson;

/**
 * Some cloud / server that we talk to over 3G / cellular data with JSON
 */
public class Cloud {
	final static String TAG = "VNC:Cloud";
	final static String hostname = Globals.CSM_SERVER_NAME;

	// Status codes
	final static int CR_ERROR = 13;
	final static int CR_OKAY = 12;
	final static int CR_NOCSM = 11;
	final static int CR_CSM = 10;

	public class CloudResponse extends Object {
		public int status;
		public long leader;
		public long modified_time;
		public String csm_data;
	}

	public CloudResponse takeLeadership(RegionKey region, long id) {
		long time = System.currentTimeMillis();
		InputStream data = makeRequest(String.format("http://" + hostname
				+ "/take/%d/%d/%d/%d/", region.x, region.y, id, time));
		if (data == null)
			return null;
		Reader r = new InputStreamReader(data);
		Gson gson = new Gson();

		CloudResponse csmR = null;
		try {
			csmR = gson.fromJson(r, CloudResponse.class);
		} catch (Exception e) {
			Log.e(TAG,
					"Exception in deserializing cloud response: "
							+ e.getMessage());
		}
		return csmR;
	}

	public CloudResponse releaseLeadership(RegionKey region, long id) {
		long time = System.currentTimeMillis();
		InputStream data = makeRequest(String.format("http://" + hostname
				+ "/release/%d/%d/%d/%d/", region.x, region.y, id, time));
		Log.i(TAG, "in Cloud releaseLeadership");
		Log.i(TAG, data.toString());
		Reader r = new InputStreamReader(data);
		Gson gson = new Gson();
		CloudResponse cloudR = null;
		try {
			cloudR = gson.fromJson(r, CloudResponse.class);
		} catch (Exception e) {
			Log.e(TAG,
					"Exception in deserializing cloud response: "
							+ e.getMessage());
		}
		return cloudR;
	}

	/** Make an HTTP GET request to the cloud */
	public InputStream makeRequest(String url) {
		InputStream data = null;
		try {
			URI uri = new URI(url);
			HttpGet method = new HttpGet(uri);
			DefaultHttpClient httpClient = new DefaultHttpClient();
			HttpResponse response = httpClient.execute(method);
			data = response.getEntity().getContent();
			Log.i(TAG, String.format("Request executed: %s", url));
		} catch (Exception e) {
			Log.e(TAG, e.toString());
		}
		return data;
	}

	/** Upload CSM state data to cloud via HTTP POST */
	public CloudResponse uploadState(RegionKey region, long id,
			String csmDataAsString) {
		long time = System.currentTimeMillis();

		String url = String.format("http://" + hostname
				+ "/upload/%d/%d/%d/%d/", region.x, region.y, id, time);

		CloudResponse cloudR = null;
		try {
			HttpPost method = new HttpPost(new URI(url));

			// Log.i(TAG, "Adding JSON CSM data to POST: " + csmDataAsString);
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
			nameValuePairs.add(new BasicNameValuePair("csm_data",
					csmDataAsString));
			method.setEntity(new UrlEncodedFormEntity(nameValuePairs));

			Log.i(TAG, "Executing request: " + url);
			DefaultHttpClient httpClient = new DefaultHttpClient();
			HttpResponse response = httpClient.execute(method);
			InputStream data = response.getEntity().getContent();
			Reader r = new InputStreamReader(data);
			Gson gson = new Gson();
			cloudR = gson.fromJson(r, CloudResponse.class);
		} catch (Exception e) {
			Log.e(TAG, e.toString());
			cloudR = new CloudResponse();
			cloudR.status = CR_ERROR;
		}
		return cloudR;
	}
}
