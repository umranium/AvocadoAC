/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package activity.classifier.rpc;

import activity.classifier.rpc.Classification;

/**
 *
 * @author chris
 */
interface ActivityRecorderBinder {

    boolean isRunning();

    void submitClassification(long sampleTime, String classification);

    List<Classification> getClassifications();
    
    void setWakeLock();
    
    /**
    *	Used by service thread's with no looper.
    */
    void showServiceToast(String message);
    

}
