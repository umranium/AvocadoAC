/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package aus.csiro.justin.sensorlogger.rpc;

/**
 *
 * @author chris
 */
interface SensorLoggerBinder {

    /**
     * Sets the state of the sensor binder application.
     *
     * 1 - introduction (not running)
     * 2 - countdown phase
     * 3 - collection phase
     * 4 - analysis phase
     * 5 - results
     * 6 - uploading/uploaded
     * 7 - upload complete
     * 8 - finished
     */
    void setClassfication(String strClss);
    
    void setLocation(String strClss);
    
    void setComment(String strClss);
    
     
    void setState(int state);
	
	void setIndex(int index);
	
    void submitClassification(String classification);

    void submit();

    void submitWithCorrection(String correction,String loc,String cmnt);

    String getClassification();

    int getState();

	int getIndex();

    int getCountdownTime();

}
