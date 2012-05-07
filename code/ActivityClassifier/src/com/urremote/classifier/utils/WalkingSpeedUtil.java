package com.urremote.classifier.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import com.urremote.classifier.common.Constants;

import android.util.Log;

public class WalkingSpeedUtil {
	
	private static final String DEBUG  = "WalkingSpeed";
	
	private static final int DATA_V_DIM = 2;
	private static final float MIN_UPPER_BAND = 1.1f;	// m/s^2
	private static final float MIN_PEAK_HEIGHT = Constants.GRAVITY*0.25f;	// m/s^2
	private static final int BUFFER_LEN = Constants.MAXIMUM_SUPPORTED_SAMPLES_PER_BATCH;
	
	//	minimum and maximum, step frequencies for walking
	private static final float MAX_WALKING_STEP_FREQUENCY = 2.5f;	// Hz
	private static final float MIN_WALKING_STEP_FREQUENCY = 1.4f;	// Hz
	
	//	minimum and maximum, time between peaks
	private static final long MIN_WALKING_DURATION_BTWN_PEAKS = (long)(1000.0f / MAX_WALKING_STEP_FREQUENCY);	// milli-seconds
	private static final long MAX_WALKING_DURATION_BTWN_PEAKS = (long)(1000.0f / MIN_WALKING_STEP_FREQUENCY);	// milli-seconds
	
	private static final int MIN_WALKING_ZERO_CROSSINGS = 2;
	private static final int MAX_WALKING_ZERO_CROSSINGS = 8;
	
	//	maximum allowable sampling delay (in milli-seconds) 2 sampling periods @ 20Hz
	private static final long MAX_SAMPLING_INTERVAL = 50*2;	// milliseconds
	
	private String printOutputFolder;
	private SimpleDateFormat dateFormat;
	private PrintStream printStream;
	
	private int count;
	private Sample[] samples = new Sample[BUFFER_LEN];
	private Sample[] allPeaksFoundBuffer = new Sample[BUFFER_LEN];
	private SampleRef[] allPeaksFoundAccelSorted = new SampleRef[BUFFER_LEN];
	private Sample[] filteredPeaksBuffer = new Sample[BUFFER_LEN];
	private int peaksFound;
	private int stepsFound;
	private float walkingHeight;
	private float walkingSpeed;
	private int maxContigiousSteps;
	
	private class Sample {
		int index;	//	index in the samples array
		long timeStamp;	//	absolute time sample was taken
		int zeroCrossings;
		float accel;
		float vel;
		float dist;
		
		@Override
		public String toString() {
			return index+", t="+(((double)(timeStamp-samples[0].timeStamp))/1000.0);
		}
	}
	
	private class SampleRef {
		Sample sample;
		int arrayIndex;
	}
	
	private Comparator<SampleRef> peakAccelDecrComparator = new Comparator<SampleRef>() {
		public int compare(SampleRef object1, SampleRef object2) {
			return -Double.compare(object1.sample.accel, object2.sample.accel);
		}
	};
	
	public WalkingSpeedUtil(String printOutputFolder, SimpleDateFormat dateFormat) {
		this.printOutputFolder = printOutputFolder;
		this.dateFormat = dateFormat;
		
		for (int i=0; i<BUFFER_LEN; ++i) {
			samples[i] = new Sample(); 
			allPeaksFoundAccelSorted[i] = new SampleRef();
		}
	}
	
	public int getPeaksFound() {
		return peaksFound;
	}
	
	public int getStepsFound() {
		return stepsFound;
	}

	public int getMaxContigiousSteps() {
		return maxContigiousSteps;
	}
	
	public float getWalkingHeight() {
		return walkingHeight;
	}
	
	public float getWalkingSpeed() {
		return walkingSpeed;
	}
	
	
	private void log(String msg) {
		if (Constants.OUTPUT_DEBUG_INFO) {
			if (this.printStream!=null)
				this.printStream.println(msg);
			else
				Log.d(DEBUG, msg);
		}
	}
	
	public void processData(long[] timeStamps, float[][] rotatedData, float[] rotatedDataMeans, int count)
	{
		Date samplingTime = new Date(timeStamps[0]);
		String fname = dateFormat.format(samplingTime);
		fname = fname.replaceAll("\\W+", "_");
		try {
			this.printStream = new PrintStream(new File(printOutputFolder + File.separator + fname + ".txt"));
		} catch (FileNotFoundException ex) {
			Log.e(DEBUG, "Error openning file: "+fname);
		}
		
		this.stepsFound = 0;
		this.walkingHeight = 0;
		this.walkingSpeed = 0;
		this.maxContigiousSteps = 0;
		
		log("Processing Walking");
		
		this.count = Math.min(count, BUFFER_LEN);
		{
			int zeroCrossings = 0;
			for (int i=0; i<this.count; ++i) {
				Sample sample = samples[i];

				sample.index = i;
				sample.timeStamp = timeStamps[i];
				sample.accel = rotatedData[i][DATA_V_DIM] - rotatedDataMeans[DATA_V_DIM];

				if (i>0 && (sample.accel>0.0)!=(samples[i-1].accel>0.0)) {
					++zeroCrossings;
				}

				sample.zeroCrossings = zeroCrossings;

				log(i+","+sample.timeStamp+","+sample.accel);
			}
		}
		
		filterAndSortPeaks();
		
		log("Peaks Found="+peaksFound);
		
		if (peaksFound>1) {
			
			int contigiousSteps = 0;
			boolean lastWasAStep = false;
			
			for (int i=0; i<peaksFound; ++i) {
				boolean thisIsAStep = false;
				
				Sample peak = filteredPeaksBuffer[i];
				log("Peak["+i+"]: "+peak);
				
				if (peak.accel<MIN_PEAK_HEIGHT) {
					log("\t\tPeak is smaller than expected minimum size ("+peak.accel+"<"+MIN_PEAK_HEIGHT+")");
				} else {
					//	find next peak
					Sample nextPeak = null;
					for (int j=1; j<peaksFound; ++j) {
						if (filteredPeaksBuffer[j-1].index==peak.index) {
							nextPeak = filteredPeaksBuffer[j];
							break;
						}
					}

					log("\tNext Peak "+nextPeak);

					if (nextPeak!=null) {
						long stepDuration = nextPeak.timeStamp-peak.timeStamp;
						if (stepDuration<MIN_WALKING_DURATION_BTWN_PEAKS ||
								stepDuration>MAX_WALKING_DURATION_BTWN_PEAKS) {
							nextPeak = null;
							log("\t\tStep-duration ("+stepDuration+"ms) not within required limits ["+MIN_WALKING_DURATION_BTWN_PEAKS+"ms.."+MAX_WALKING_DURATION_BTWN_PEAKS+"ms]!");
						}
					}

					if (nextPeak!=null) {
						int zeroCrossings = nextPeak.zeroCrossings - peak.zeroCrossings;
						log("\t\tzero-corsisngs="+zeroCrossings);
						if (zeroCrossings<MIN_WALKING_ZERO_CROSSINGS || zeroCrossings>MAX_WALKING_ZERO_CROSSINGS) {
							log("\t\tZero crossings ("+zeroCrossings+") out of required range ["+MIN_WALKING_ZERO_CROSSINGS+".."+MAX_WALKING_ZERO_CROSSINGS+"]");
							nextPeak = null;
						}
					}

					if (nextPeak!=null && nextPeak.accel<MIN_PEAK_HEIGHT) {
						log("\t\tNext peak is smaller than expected minimum size ("+nextPeak.accel+"<"+MIN_PEAK_HEIGHT+")");
						nextPeak = null;
					}

					//	check from 2 samples before first peak
					if (peak.index>2) {
						peak = samples[peak.index-2];
					}
					if (nextPeak!=null && nextPeak.index<count-2) {
						nextPeak = samples[nextPeak.index+2];
					}

					if (nextPeak!=null) {
						if (hasOkTiming(peak.index, nextPeak.index)) {
							log("\tIntegrating from "+peak+".."+nextPeak);

							integrateAccel(peak.index, nextPeak.index);

		//					log("\t\tVelocities:");
		//					for (int k=peak.index; k<=nextPeak.index; ++k) {
		//						log("\t\t\t"+samples[k]+" "+samples[k].accel+" >> "+samples[k].vel);
		//					}

							integrateVel(peak.index, nextPeak.index);

		//					log("\t\tDistances:");
		//					for (int k=peak.index; k<=nextPeak.index; ++k) {
		//						log("\t\t\t"+samples[k]+" "+samples[k].vel+" >> "+samples[k].dist);
		//					}

							float h = computeWalkingHeight(peak.index, nextPeak.index);

							log("\tFound Height="+h);

							if (!Float.isNaN(h)) {
								thisIsAStep = true;
								++this.stepsFound;
								this.walkingHeight += h;
	//							//	find highest walking height
	//							if (h>this.walkingHeight) {
	//								this.walkingHeight = h;
	//							}
							}
						} else {
							log("\t\tRange from "+peak+".."+nextPeak+" has timing problems");
						}
					}
				}
				
				if (thisIsAStep) {
					if (lastWasAStep) {
						if (contigiousSteps==0)
							contigiousSteps = 1;
						
						++contigiousSteps;
						
						if (contigiousSteps>maxContigiousSteps) {
							maxContigiousSteps = contigiousSteps;
						}
					}
				} else {
					contigiousSteps = 0;
				}
				
				lastWasAStep = thisIsAStep;
				
			}
			
			if (stepsFound>1) {
				//	get mean walking height
				this.walkingHeight /= this.stepsFound;
				this.walkingSpeed = this.walkingHeight / 0.038f;
			}
		}
		
		log("Final Height Found = "+this.walkingHeight);
		log("Final Speed  Found = "+this.walkingSpeed);
		
		if (this.printStream!=null) {
			this.printStream.close();
			this.printStream = null;
		}
	}
	
	private void filterAndSortPeaks()
	{
		int numberFound = 0;
		
		boolean isInUpperBand = false;	//	is the signal in the upper band
		boolean wasInUpperBand = false;	// was the signal in the upper band previously
		boolean firstPeakOfUpperBandGroup = true;	// first peak, since the signal went to the upper band
		
		log("Finding Upper-band Group Highest Peaks");
		for (int i=1; i<this.count-1; ++i) {
			isInUpperBand = samples[i].accel>=MIN_UPPER_BAND;
			
			//	check if it's a peak in the upper band
			if (	isInUpperBand
					&&
					(samples[i].accel-samples[i-1].accel)>0.0f
					&&
					(samples[i+1].accel-samples[i].accel)<=0.0f
				)
			{
				log("\t\tUpper Band Peak: "+samples[i]);
				
				//	check if this is the first peak of a group in the upper band
				if (firstPeakOfUpperBandGroup) {
					log("\tFirst Peak of Group: "+samples[i]);
					
					//	add a new peak
					allPeaksFoundBuffer[numberFound] = samples[i];
					++numberFound;
					
					firstPeakOfUpperBandGroup = false;
				} else {
					//	we expect, if everything goes right, a previous peak was detected
					//		unless something wrong happens
					assert(numberFound>0);
					
					//	check if this peak is higher than previously found peak
					if (samples[i].accel>allPeaksFoundBuffer[numberFound-1].accel) {
						log("\tReplacement Peak of Group: "+samples[i]);
						
						//	set this peak to the new highest peak of this group
						allPeaksFoundBuffer[numberFound-1] = samples[i];
					}
				}
			}
			
			if (wasInUpperBand && !isInUpperBand)
				firstPeakOfUpperBandGroup = true;
			wasInUpperBand = isInUpperBand;
		}
		
		log("Total Peaks Found: "+numberFound);
		
		if (numberFound>0) {
//			log("Removing smaller peaks");
//			//	leaving only required peaks
//			for (int i=0; i<numberFound; ++i) {
//				allPeaksFoundAccelSorted[i].sample = allPeaksFoundBuffer[i];
//				allPeaksFoundAccelSorted[i].arrayIndex = i;
//			}
//			
//			Arrays.sort(allPeaksFoundAccelSorted, 0, numberFound, peakAccelDecrComparator);
//			int numberRequired = Math.min(numberFound, MAX_NUMBER_OF_WALKING_STEPS);
//			log("\tRequired peaks "+numberRequired);
//			for (int i=numberRequired; i<numberFound; ++i) {
//				int index = allPeaksFoundAccelSorted[i].arrayIndex;
//				log("\t\tclearing "+allPeaksFoundBuffer[index]);
//				allPeaksFoundBuffer[index] = null;
//			}
			
			log("Removing peaks that are too close");
			
			int highestPeak = 0;
			for (int i=1; i<numberFound; ++i) {
				if (allPeaksFoundBuffer[i].accel>allPeaksFoundBuffer[highestPeak].accel) {
					highestPeak = i; 
				}
			}
			log("Highest Peak = "+allPeaksFoundBuffer[highestPeak]);
			
			for (int majorDir=1; majorDir>=-1; majorDir-=2) {
				log("\tFiltering "+((majorDir>0)?"Rightwards":"Leftwards"));
				
				int currentPeak = highestPeak;
				while (currentPeak>=0 && currentPeak<numberFound && allPeaksFoundBuffer[currentPeak]!=null)
				{
					log("\t\tCurrent Peak = "+allPeaksFoundBuffer[currentPeak]);
					
					for (int minorDir=-1; minorDir<=1; minorDir+=2) {
						int finalDir = minorDir * majorDir;
						
						//	don't do reverse dir, for the highest peak
						if (currentPeak==highestPeak && minorDir<0)
							continue;
						
						log("\t\t\tRemoving all peaks too close on the "+((finalDir>0)?"right":"left"));
						
						int nearbyPeak = currentPeak+finalDir;
						int maxNearbyPeak = -1;
						while (nearbyPeak>=0 && nearbyPeak<numberFound && allPeaksFoundBuffer[nearbyPeak]!=null)
						{
							log("\t\t\t\tProcessing "+allPeaksFoundBuffer[nearbyPeak]);
						
							long dist = allPeaksFoundBuffer[currentPeak].timeStamp -
										allPeaksFoundBuffer[nearbyPeak].timeStamp;
							if (dist<0) dist = -dist;
							
							//	peak is too close
							if (dist<MIN_WALKING_DURATION_BTWN_PEAKS) {
								log("\t\t\t\t\tRemoving "+allPeaksFoundBuffer[nearbyPeak]+" (too close)");
								allPeaksFoundBuffer[nearbyPeak] = null;
							}
							else
								//	peak is too far.. we've gone too far
								if (dist>MAX_WALKING_DURATION_BTWN_PEAKS) {
									log("\t\t\t\t\tReached peak that is too far");
									break;
								}
								//	peak is around the location were we expect it
								else {
									//	lets find the largest peak in that gap
									if (maxNearbyPeak<0 || 
											allPeaksFoundBuffer[nearbyPeak].accel>allPeaksFoundBuffer[maxNearbyPeak].accel) {
										if (maxNearbyPeak>=0) {
											log("\t\t\t\t\tRemoving "+allPeaksFoundBuffer[maxNearbyPeak]+" (new peak found)");
											allPeaksFoundBuffer[maxNearbyPeak] = null;
										}
										maxNearbyPeak = nearbyPeak;
										log("\t\t\t\t\tNew highest peak "+allPeaksFoundBuffer[maxNearbyPeak]);
									} else {
										log("\t\t\t\t\tRemoving "+allPeaksFoundBuffer[nearbyPeak]+" (is not peak)");
										allPeaksFoundBuffer[nearbyPeak] = null;
									}
								}
							
							//	find next peak
							do {
								nearbyPeak += finalDir;
							} while (nearbyPeak>=0 && nearbyPeak<numberFound &&
									allPeaksFoundBuffer[nearbyPeak]==null);
						}
						
						
					}
					
					//	find next peak
					do {
						currentPeak += majorDir;
					} while (currentPeak>=0 && currentPeak<numberFound &&
							allPeaksFoundBuffer[currentPeak]==null);
				}
			}
			
		}
		
		log("Peaks after filtering:");
		int filteredNumber = 0;
		for (int i=0; i<numberFound; ++i) {
			if (allPeaksFoundBuffer[i]!=null) {
				log("\t"+allPeaksFoundBuffer[i]);
				filteredPeaksBuffer[filteredNumber] = allPeaksFoundBuffer[i];
				++filteredNumber;
			}
		}
		
		peaksFound = filteredNumber;
		
	}
	
	private boolean hasOkTiming(int peakFrom, int peakTo)
	{
		long timeDiff = 0;
		
		for (int i=peakFrom+1; i<=peakTo; ++i) {
			Sample prevData = samples[i-1];
			Sample currData = samples[i];
			
			timeDiff = currData.timeStamp-prevData.timeStamp;
			
			if (timeDiff>MAX_SAMPLING_INTERVAL) {
				log("\t\t\tFound delay="+timeDiff);
				return false;
			}
		}
		
		return true;
	}
	
	private void integrateAccel(int peakFrom, int peakTo)
	{
		double timeDiff = 0.0;
		double heightDiff = 0.0;
		double velocity = 0.0;
		double sum = 0.0;
		double count = 0.0;
		
		samples[peakFrom].vel = 0.0f;
		sum = 0.0;
		count = 1.0;
		
		for (int i=peakFrom+1; i<=peakTo; ++i) {
			Sample prevData = samples[i-1];
			Sample currData = samples[i];
			
			timeDiff = ((double)(currData.timeStamp-prevData.timeStamp))/1000.0;
			heightDiff = ((double)(currData.accel+prevData.accel))/2.0;
			velocity += timeDiff*heightDiff;
			
			sum += velocity;
			count += 1.0;
			
			currData.vel = (float)velocity;
		}
		
		float mean = (float)(sum / count);
		
		for (int i=peakFrom; i<=peakTo; ++i) {
			Sample currData = samples[i];
			currData.vel -= mean;
		}
		
	}
	
	private void integrateVel(int peakFrom, int peakTo)
	{
		double timeDiff = 0.0;
		double heightDiff = 0.0;
		double distance = 0.0;
		double sum = 0.0;
		double count = 0.0;
		
		samples[peakFrom].dist = 0.0f;
		sum = 0.0;
		count = 1.0;
		
		for (int i=peakFrom+1; i<=peakTo; ++i) {
			Sample prevData = samples[i-1];
			Sample currData = samples[i];
			timeDiff = ((double)(currData.timeStamp-prevData.timeStamp))/1000.0;
			heightDiff = ((double)(currData.vel+prevData.vel))/2.0;
			distance += timeDiff*heightDiff;
			
			sum += distance;
			count += 1.0;
			
			currData.dist = (float)distance;
		}
		
		float mean = (float)(sum / count);
		
		for (int i=peakFrom; i<=peakTo; ++i) {
			Sample currData = samples[i];
			currData.dist -= mean;
		}
		
	}
	
	private float computeWalkingHeight(int peakFrom, int peakTo)
	{
		Sample firstMinima = null, maxima = null, lastMinima = null;
		boolean hadAnError = false;
		
		for (int i=peakFrom; i<=peakTo; ++i) {
			Sample prevData = (i>peakFrom)?samples[i-1]:null;
			Sample currData = samples[i];
			Sample nextData = (i<peakTo)?samples[i+1]:null;
			
			//	is this a minima, and no maxima has been found
			if (	(prevData==null || currData.dist-prevData.dist<=0.0f) &&
					(nextData==null || nextData.dist-currData.dist>0.0f) && 
					maxima==null) {
				log("\t\tprob first minima="+currData);
				
				if (lastMinima!=null) {
					log("\t\terror: last minima found");
					hadAnError = true;
					break;
				}
				
				if (firstMinima==null || firstMinima.dist>currData.dist)
					firstMinima = currData;
			} else
				//	is this a minima, and a maxima has been found
				if (	(prevData==null || currData.dist-prevData.dist<0.0f) &&
						(nextData==null || nextData.dist-currData.dist>=0.0f) && 
						maxima!=null) {
					log("\t\tprob last minima="+currData);
					
					if (firstMinima==null) {
						log("\t\terror: first minima not found yet");
						hadAnError = true;
						break;
					}
					
					if (lastMinima==null || lastMinima.dist>currData.dist)
						lastMinima = currData;
				} else
					//	if this is a maxima
					if ((prevData!=null && currData.dist-prevData.dist>0.0f) &&
							(nextData!=null && nextData.dist-currData.dist<=0.0f)) {
						log("\t\tprob maxima="+currData);
						
						//	if we already found a last minima,
						//		or haven't found a first minima
						//		something went wrong
						if (firstMinima==null) {
							log("\t\terror: first minima not found yet");
							hadAnError = true;
							break;
						}
						if (lastMinima!=null) {
							log("\t\terror: last minima already found");
							hadAnError = true;
							break;
						}
						
						if (maxima==null || maxima.dist<currData.dist)
							maxima = currData;
					}
		}
		
		if (!hadAnError) {
			log("\tfirst minima="+firstMinima);
			log("\tmaxima="+maxima);
			log("\tlast minima="+lastMinima);
		}
		
		if (!hadAnError &&firstMinima!=null && maxima!=null && lastMinima!=null) {
			/*
			 *       /\
			 *   b / /  \
			 *   /   /h   \
			 * /A    /      \ a
			 * -------        \
			 *      c -----     \
			 *             ------
			 *             
			 *   where h joins c at a right angle
			 */
			
			//	absolute heights of Mx, Fi Mn, La Mn
			double hMx = maxima.dist;
			double hFiMn = firstMinima.dist;
			double hLaMn = lastMinima.dist;
			
			//	time between Mx -> FiMn, Mx -> La Mn, Fi Mn -> LaMn
			double tmMxFiMn = (maxima.timeStamp-firstMinima.timeStamp)/1000.0;
			double tmMxLaMn = (lastMinima.timeStamp-maxima.timeStamp)/1000.0;
			double tmFiMnLaMn = (lastMinima.timeStamp-firstMinima.timeStamp)/1000.0;
			
			//	height between Mx -> FiMn, Mx -> La Mn, Fi Mn -> LaMn
			double hMxFiMn = Math.abs(hMx-hFiMn);
			double hMxLaMn = Math.abs(hMx-hLaMn);
			double hFiMnLaMn = Math.abs(hFiMn-hLaMn);
			
			//	distance from Mx to La Mn
			double a = Math.sqrt(hMxLaMn*hMxLaMn + tmMxLaMn*tmMxLaMn);
			//	distance from Mx to Fi Mn
			double b = Math.sqrt(hMxFiMn*hMxFiMn + tmMxFiMn*tmMxFiMn);
			//	distance from Fi Mn to La Mn
			double c = Math.sqrt(hFiMnLaMn*hFiMnLaMn + tmFiMnLaMn*tmFiMnLaMn);
			
			//	i just love this formula!!!
			double cosAngleA = (b*b + c*c - a*a) / (2*b*c);
			double sinAngleA = Math.sqrt(1 - cosAngleA*cosAngleA);
			
			//	and we finally arrive at h!
			double h = sinAngleA * b;
			
//			return (Math.abs(maxima.dist-firstMinima.dist) +
//					Math.abs(maxima.dist-lastMinima.dist))/2.0f;
			return (float)h;
		} else {
			return Float.NaN;
		}
	}
	
}
