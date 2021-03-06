/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.urremote.classifier.rpc;

import com.urremote.classifier.rpc.Classification;

/**
 *
 * @author chris
 */
interface ActivityRecorderBinder {

    boolean isRunning();
    
    void submitClassification(long sampleTime, String classification, double eeAct, double met);

    List<Classification> getClassifications();
    
    //void setWakeLock();
    
    /**
    *	Used by service thread's with no looper.
    */
    void showServiceToast(String message);
    
    //public boolean ForceCalibration();
    
    //public boolean isCalibrationForced();
    
    boolean isHardwareNotificationOn();
    
    void handleHardwareFaultException(String title, String msg);
    
    void cancelHardwareNotification();

}
