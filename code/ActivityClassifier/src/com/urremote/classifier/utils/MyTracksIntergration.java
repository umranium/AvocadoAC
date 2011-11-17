/**
 * 
 */
package com.urremote.classifier.utils;

import java.util.List;

import com.google.android.apps.mytracks.content.Sensor;
import com.google.android.apps.mytracks.content.WaypointCreationRequest;
import com.google.android.apps.mytracks.content.WaypointCreationRequest.WaypointType;
import com.google.android.apps.mytracks.services.ITrackRecordingService;
import com.urremote.classifier.common.Constants;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * Class responsible for dealing with the MyTracks application
 * 
 * @author Umran
 * 
 */
public class MyTracksIntergration {

	private static final String MY_TRACKS_SERVICE_PACKAGE = "com.google.android.maps.mytracks";
	private static final String MY_TRACKS_SERVICE_CLASS = "com.google.android.apps.mytracks.services.TrackRecordingService";

	private Context context;
	private ITrackRecordingService service;
	private long currentTrackId;

	/**
	 * Performs necessary tasks when the connection to the service is
	 * established, and after it is disconnected.
	 */
	private final ServiceConnection connection = new ServiceConnection() {

		public void onServiceConnected(ComponentName componentName,
				IBinder iBinder) {
			synchronized (this) {
				service = ITrackRecordingService.Stub.asInterface(iBinder);
				try {
					if (!service.isRecording()) {
						Log.i(Constants.DEBUG_TAG, "Starting new MyTracks Track");
						currentTrackId = service.startNewTrack();
					}
					else {
						Log.i(Constants.DEBUG_TAG, "Found Tracks already recording a Track");
						currentTrackId = service.getRecordingTrackId();
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
		}

		public void onServiceDisconnected(ComponentName componentName) {
			service = null;
		}
	};
	
	public MyTracksIntergration(Context context) {
		this.context = context;
	}

	/**
	 * Attempts to connect to the MyTracks service
	 * 
	 * @return
	 * 	whether the connection was successful or not
	 */
	private boolean connect() {
		try {
			Intent intent = new Intent();
			intent.setComponent(new ComponentName(MY_TRACKS_SERVICE_PACKAGE,
					MY_TRACKS_SERVICE_CLASS));
			if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
				Log.e(Constants.DEBUG_TAG, "Couldn't bind to MyTracks service.");
				return false;
			} else {
				Log.i(Constants.DEBUG_TAG, "Connected to MyTracks service");
				return true;
			}
		} catch (Exception e) {
			Log.e(Constants.DEBUG_TAG, "Error while connecting to MyTracks service", e);
			return false;
		}
	}

	/**
	 * Disconnects from the MyTracks service
	 */
	private void disconnect() {
		if (service!=null) {
			try {
				if (service.isRecording()){
					Log.i(Constants.DEBUG_TAG, "End MyTracks from recording track");
					service.endCurrentTrack();
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
			context.unbindService(connection);
			Log.i(Constants.DEBUG_TAG, "Disconnected from MyTracks service");
			service = null;
		}
	}
	
	/**
	 * 
	 * @return
	 * whether or not the MyTracks service is recording
	 */
	public boolean isRecording() {
		try {
			return service!=null && service.isRecording();
		} catch (RemoteException e) {
			return false;
		}
	}
	
	/**
	 * 
	 */
	public void isConnectedToSensor() {
		try {
			if (service!=null) {
				Sensor.SensorState sensorState = Sensor.SensorState.valueOf(service.getSensorState());
				Log.i("MY TRACKS SENSOR STATE", "MyTracks Sensor State: "+sensorState.toString());
			}
		} catch (RemoteException e) {
		}
	}

	/**
	 * @return
	 * The Id of the currently recording track
	 */
	public long getCurrentTrackId() {
		return currentTrackId;
	}

	/**
	 * Attempts to start recording a new track, if one isn't already recording.
	 * 
	 * @return
	 * Whether or not the attempt was successful
	 */
	public void startRecording() {
		Log.i(Constants.DEBUG_TAG, "Attempting to start MyTracks Recording");
		if (service!=null)
			disconnect();
		connect();
	}

	/**
	 * Stops recording the current track.
	 */
	public void stopRecording() {
		Log.i(Constants.DEBUG_TAG, "Attempting to stop MyTracks Recording");
		disconnect();
	}

	/**
	 * Inserts a marker way-point at the current location on the current track
	 * with the given name and description.
	 * 
	 * @param name
	 * The name to be given to the marker
	 * 
	 * @param description
	 * The description to be given to the marker
	 */
	public void insertMarker(String name, String description) {
		try {
			if (service!=null && service.isRecording()) {
				WaypointCreationRequest request = new WaypointCreationRequest(
						WaypointType.MARKER, name, description, WaypointCreationRequest.DEFAULT_MARKER.getIconUrl());
	//			WaypointCreationRequest request = WaypointCreationRequest.DEFAULT_MARKER;
				service.insertWaypoint(request);
				Log.i(Constants.DEBUG_TAG, "MyTracks way-point marker inserted: "+name+", "+description);
			}
		} catch (RemoteException e) {
			throw new RuntimeException(
					"Error while trying insert MyTracks marker way-point '" + name + "'",
					e);
		}
	}

	/**
	 * Inserts a statistics way-point at the current location on the current track
	 * with the given name and description.
	 * 
	 * @param name
	 * The name to be given to the marker
	 * 
	 * @param description
	 * The description to be given to the marker
	 */
	public void insertStatistics(String name, String description) {
		try {
			if (service!=null && service.isRecording()) {
				WaypointCreationRequest request = new WaypointCreationRequest(
						WaypointType.STATISTICS, name, description, WaypointCreationRequest.DEFAULT_STATISTICS.getIconUrl());
	//			WaypointCreationRequest request = WaypointCreationRequest.DEFAULT_STATISTICS;
				Log.i(Constants.DEBUG_TAG, "MyTracks way-point statistics inserted: "+name+", "+description);
				service.insertWaypoint(request);
			}
		} catch (RemoteException e) {
			throw new RuntimeException(
					"Error while trying insert MyTracks statistics way-point '" + name
							+ "'", e);
		}
	}
	
	public static boolean isMyTracksInstalled(Context context) {
		List<PackageInfo> services = context.getPackageManager().getInstalledPackages(PackageManager.GET_SERVICES);
		for (PackageInfo pack:services) {
			if (MY_TRACKS_SERVICE_PACKAGE.equalsIgnoreCase(pack.packageName)) {
				return true;
			}
		}
		return false;
	}

}
