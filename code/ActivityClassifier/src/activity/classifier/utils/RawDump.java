package activity.classifier.utils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import activity.classifier.accel.SampleBatch;
import activity.classifier.common.Constants;
import android.text.format.DateFormat;
import android.util.Log;

public class RawDump {
	
	private static final String PATH_DUMP_FOLDER = Constants.PATH_SD_CARD_APP_LOC + File.separator + "RawDump";
	private static final long DURATION_KEEP_DUMPS = 24*60*60*1000L;	// 24hrs
	private static final long INTERVAL_DELETE_DUMPS = 10*60*1000L;	//	10min
	
	private File dumpFolder;
	private ArrayList<String> dumpFiles;
	private long lastDeleteDump = 0;
	
	public RawDump() {
		this.dumpFolder = new File(PATH_DUMP_FOLDER);
		
		if (!this.dumpFolder.exists() && !this.dumpFolder.mkdirs())
			throw new RuntimeException("Unable to create folder:"+PATH_DUMP_FOLDER);
		
		//	fetch all required files in the folder
		File[] files = this.dumpFolder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				return filename.matches("\\d\\d\\d\\d_\\d\\d_\\d\\d_\\d\\d_\\d\\d_\\d\\d\\.txt");
			}
		});
		
		//	add to list
		this.dumpFiles = new ArrayList<String>(files.length);
		for (File f:files)
			this.dumpFiles.add(f.getName());
		
		//	sort list
		if (!this.dumpFiles.isEmpty())
			Collections.sort(dumpFiles, comparebyDate);
	}
	
	private void deleteOldDumps(long currentTime) {
		if (currentTime-lastDeleteDump<INTERVAL_DELETE_DUMPS) {
			return;
		}
		
		long deleteCutPoint = currentTime - DURATION_KEEP_DUMPS;
		String fname = timeToFName(deleteCutPoint);
		
		//	assume list is sorted
		int searchResult = Collections.binarySearch(dumpFiles, fname, comparebyDate);
		
		int index;	// index of last item to delete
		if (searchResult>=0) {	// search success
			index = searchResult;
		} else {
			index = -(searchResult+1) - 1;
		}
		
		while (index>=0) {
			String delFName = dumpFiles.get(0);
			File delF = new File(dumpFolder, delFName);
			if (!delF.delete()) {
				Log.e(Constants.DEBUG_TAG, "Unable to delete dump file "+delF.getAbsolutePath());
				break;
			} else {
				Log.i(Constants.DEBUG_TAG, "Deleted dump file "+delF.getAbsolutePath());
			}
			dumpFiles.remove(0);
			--index;
		}
		
		lastDeleteDump = currentTime;
	}
	
	public void dumpRawData(SampleBatch batch)
	{
		try {
			Log.i(Constants.DEBUG_TAG, "Dumping raw-data to file");
			
			File newF = new File(dumpFolder, timeToFName(batch.sampleTime));
			if (!newF.exists()) {
				newF.createNewFile();
			}
			dumpFiles.add(newF.getName());
			
			PrintStream out = new PrintStream(newF);
			
			int len = batch.getSize();
			for (int i=0; i<len; ++i) {
				out.print(i+","+batch.timeStamps[i]);
				for (int j=0; j<Constants.ACCEL_DIM; ++j) {
					out.print(","+batch.data[i][j]);
				}
				out.println();
			}
			
			out.close();
			
			deleteOldDumps(batch.sampleTime);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static String timeToFName(long time) {
		return new StringBuffer(DateFormat.format("yyyy_MM_dd_kk_mm_ss", time)).toString() + ".txt";
	}
	
	private static int[] fNameToIntArray(String fname) {
		int posDot = fname.lastIndexOf('.');
		if (posDot>=0)
			fname = fname.substring(0, posDot);
		String[] fields = fname.split("_");
		if (fields.length<6)
			return null;
		
		int year = Integer.parseInt(fields[0]);
		int mon = Integer.parseInt(fields[1]);
		int day = Integer.parseInt(fields[2]);
		int hour = Integer.parseInt(fields[3]);
		int min = Integer.parseInt(fields[4]);
		int sec = Integer.parseInt(fields[5]);
		
		return new int[] {year,mon,day,hour,min,sec};
	}
	
//	private static long fNameToTime(String fname) {
//		
//		int[] intArray = fNameToIntArray(fname);
//		
//		if (intArray==null)
//			return -1;
//		
//		Date dt = new Date(
//				intArray[0]-1900,	// years since 1900
//				intArray[1]-1,		// mon: 0..11
//				intArray[2],		// day: 1..31
//				intArray[3],		// hour: 0..23
//				intArray[4],		// min: 0..59
//				intArray[5]			// sec: 0..59
//				         );
//		
//		return dt.getTime();
//	}

	private static int compareFnames(String fname1, String fname2) {
		int[] a, b;
		
		a = fNameToIntArray(fname1);
		b = fNameToIntArray(fname2);
		
		for (int i=0; i<6; ++i) {
			if (a[i]<b[i])
				return -1;
			if (b[i]<a[i])
				return 1;
		}
		
		return 0;
	}
	
	private static Comparator<String> comparebyDate = new Comparator<String>() {
		
		@Override
		public int compare(String object1, String object2) {
			return compareFnames(object1, object2);
		}
	};;;
	
}
