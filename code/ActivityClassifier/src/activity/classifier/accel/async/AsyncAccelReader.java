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

package activity.classifier.accel.async;

import activity.classifier.accel.SampleBatch;
import activity.classifier.accel.sync.SyncAccelReader;
import activity.classifier.exception.HardwareFaultException;

/**
 * Interface implemented by classes which can sample accelerometer data.
 * 
 * Edit by Umran:
 * An asynchronous accelerometer reader is capable of running at a different
 * frequency from the system's sampling frequency. The sampler obtains the
 * latest available data by calling assign sample. This could be a problem
 * since the reader might be obtaining more samples than required (over sampling)
 * or obtaining less samples hence providing out-dated values to the sampler
 * (under sampling). In addition, more resources are used in order to maintain
 * states of both the sampler and the reader. But the {@link SyncAccelReader} is
 * currently not functioning as hoped.
 * 
 * @author chris
 */
public interface AsyncAccelReader {

    void startSampling()  throws HardwareFaultException;

    void stopSampling();

    void assignSample(SampleBatch batch) throws HardwareFaultException;

}
