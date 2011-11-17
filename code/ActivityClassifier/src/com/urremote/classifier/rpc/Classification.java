/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.urremote.classifier.rpc;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.urremote.classifier.common.ActivityNames;
import com.urremote.classifier.common.Constants;
import com.urremote.classifier.db.ActivitiesTable;
import com.urremote.classifier.repository.ActivityQueries;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.format.DateFormat;
import android.util.Log;

/**
 *
 * @author chris
 */
public class Classification implements Parcelable, Comparable<Classification> {
	
	public final static SimpleDateFormat UI_DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");
	
	private String classification;
	private long start;
	private long end;
	private boolean isChecked;
	private Long myTracksId;
	private long lastUpdate;
	private int numberOfBatches;
	private float totalMet;
	private float totalEeAct;
	
	private String uiStartTime;
	private String dbStartTime;
	private String uiEndTime;
	private String dbEndTime;
	private String durationStr="";
	
	private CharSequence niceClassification;
	
	public Classification() {
		init("", 0, 0);
	}
	
	public Classification(String classification, long start, long end) {
		init(classification, start, end);
	}
	
	public void init(String classification, long start, long end) {
		this.classification = classification;
		if (this.classification==null)
			throw new RuntimeException("Invalid classification with classification name as NULL");
		this.start = start;
		this.end = end;
		this.isChecked = false;
		this.myTracksId = null;
		this.lastUpdate = 0;
		this.numberOfBatches = 1;
		this.totalMet = 0.0f;
		this.totalEeAct = 0.0f;
		
		computeDurationStr();
	}
	
	private void computeDurationStr() {
		//        final String duration;
		int length = (int) ((end - start) / 1000);

		if (length < 60) {
			durationStr = "<1 min";
		} else if (length < 60 * 60) {
			durationStr = (length / 60) + " mins";
		} else {
			durationStr = (length / (60 * 60)) + " hrs";
		}
		
		
		Date date = new Date();

		date.setTime(start);
		uiStartTime = UI_DATE_FORMAT.format(date);
		dbStartTime = Constants.DB_DATE_FORMAT.format(date);

		date.setTime(end);
		uiEndTime = UI_DATE_FORMAT.format(date);
		dbEndTime = Constants.DB_DATE_FORMAT.format(date);
	}
	

	public String getDbStartTime(){
		return dbStartTime;
	}
	
	public String getDbEndTime(){
		return dbEndTime;
	}
	
	public String getUiStartTime(){
		return uiStartTime;
	}
	
	public String getUiEndTime(){
		return uiEndTime;
	}
	
	public String getDurationStr(){
		return durationStr;
	}
	
	public String getNiceClassification(){
		return (String) niceClassification;
	}
	
	public int describeContents() {
		return 0;
	}
	
	/**
	 * @param classification the classification to set
	 */
	public void setClassification(String classification) {
		this.classification = classification;
	}
	
	public String getClassification() {
		return classification;
	}

	/**
	 * @param start the start to set
	 */
	public void setStart(long start) {
		this.start = start;
		computeDurationStr();
	}
	
	public void setEnd(long end) {
		this.end = end;
		computeDurationStr();
	}

	public long getEnd() {
		return end;
	}

	public long getStart() {
		return start;
	}
	
	/**
	 * @return the isChecked
	 */
	public boolean isChecked() {
		return isChecked;
	}

	/**
	 * @param isChecked the isChecked to set
	 */
	public void setChecked(boolean isChecked) {
		this.isChecked = isChecked;
	}
	
	/**
	 * @return the lastUpdate
	 */
	public long getLastUpdate() {
		return lastUpdate;
	}

	/**
	 * @param lastUpdate the lastUpdate to set
	 */
	public void setLastUpdate(long lastUpdate) {
		this.lastUpdate = lastUpdate;
	}
	
	
	/**
	 * @return the number Of Batches
	 */
	public int getNumberOfBatches() {
		return numberOfBatches;
	}

	/**
	 * @param numberOfBatches the number Of Batches to set
	 */
	public void setNumberOfBatches(int numberOfBatches) {
		this.numberOfBatches = numberOfBatches;
	}

	/**
	 * @return the total MET
	 */
	public float getTotalMet() {
		return totalMet;
	}

	/**
	 * @param totalMet the total MET to set
	 */
	public void setTotalMet(float totalMet) {
		this.totalMet = totalMet;
	}
	
	/**
	 * @return the total actual energy expenditure (EEact) for the activity
	 */
	public float getTotalEeAct() {
		return totalEeAct;
	}

	/**
	 * @param totalEeAct	the total actual energy expenditure (EEact) for the activity
	 */
	public void setTotalEeAct(float totalEeAct) {
		this.totalEeAct = totalEeAct;
	}
	
	/**
	 * @return the myTracksId
	 */
	public Long getMyTracksId() {
		return myTracksId;
	}
	
	/**
	 * @param myTracksId the myTracksId to set
	 */
	public void setMyTracksId(Long myTracksId) {
		this.myTracksId = myTracksId;
	}

	@Override 
	public String toString() {
		if (ActivityNames.isSystemActivity(classification)) {
			return niceClassification+"";
		}
		else{
			return String.format("%-20s %s for %s", 
					niceClassification+" ("+String.format("%.1f", totalMet/numberOfBatches)+")",
					uiStartTime,
					durationStr);
		}
	}

	public void writeToParcel(Parcel arg0, int arg1) {
		arg0.writeString(classification);
		arg0.writeLong(start);
		arg0.writeLong(end);
	}

	public void withContext(Context context) {
		if (classification==null) {
			throw new RuntimeException("No classification exists");
		}
		
		this.niceClassification = getNiceName(context, classification);
	}
	
	public static String getNiceName(Context context, String classification)
	{
		String name = "activity_" + 
				((classification==null || classification.length() == 0)? 
					"unknown" :
					classification.replace("/", "_").toLowerCase()
					);
	
		//Log.v(Constants.DEBUG_TAG, "Classification derived name: '"+name+"' from: '"+classification+"'");

		int id = context.getResources().getIdentifier(
				name, "string", Constants.DEFAULT_PACKAGE);
		if (id>0)
			return (String) context.getResources().getText(id);
		else {
			throw new RuntimeException("Unrecognized Activity classified as '"+classification+"' ('"+name+"')");
			//return classification;
		}
	}

	public static final Parcelable.Creator<Classification> CREATOR
	= new Parcelable.Creator<Classification>() {

		public Classification createFromParcel(Parcel arg0) {
			final Classification res = new Classification(arg0.readString(), arg0.readLong(), arg0.readLong());
			return res;
		}

		public Classification[] newArray(int arg0) {
			return new Classification[arg0];
		}

	};

	public int compareTo(Classification another) {
		return (int)(this.start-another.start);
	}
	
	public void assignFrom(Classification another) {
		this.classification = another.classification;
		this.start = another.start;
		this.end = another.end;
		this.isChecked = another.isChecked;
		this.myTracksId = another.myTracksId;
		this.lastUpdate = another.lastUpdate;
		this.numberOfBatches = another.numberOfBatches;
		this.totalMet = another.totalMet;
		this.totalEeAct = another.totalEeAct;
		
		computeDurationStr();
	}
	
}
