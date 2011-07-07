/*
 * Copyright (c) 2009-2010 Chris Smith
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package activity.classifier.accel.sync;

import activity.classifier.accel.SampleBatch;

/**
 * Interface implemented by classes which can sample accelerometer data.
 * 
 * Edit by Umran:
 * An synchronous accelerometer reader obtains samples from the accelerometer
 * at the frequency of the accelerometer. Hence new data is inserted only
 * when it is available.
 * 
 * This the synchronous accelerometer reader is currently not working.
 * 
 * @author chris
 */
public interface SyncAccelReader {

	/**
	 * 
	 * @param interSampleDelay
	 * The delay between samples in ms.
	 * 
	 * @param currentBatch
	 * The batch to save the samples to.
	 * 
	 */
    void startSampling(int interSampleDelay, SampleBatch currentBatch, Runnable finishedRunnable);

    void stopSampling();

}
