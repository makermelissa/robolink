package com.melissagirl.robolink;

import java.io.IOException;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

public class MainActivity extends Activity {
	
    private final String TAG = MainActivity.class.getSimpleName();
	private static final String ACTION_USB_PERMISSION = "com.melissagirl.robolink.USB_PERMISSION";

    private UsbSerialDriver mSerialDevice;
    private UsbManager mUsbManager;

    private PendingIntent mPermissionIntent;
    private boolean mConnected;
    //private int mSwitches;
    private int mFeatures;
    private int mActiveChannels;
    //private int mChannels[] = {0,0,0,0,0,0};
    private String mResponse;

    private TextView mTextViewLog;
    private TextView mTextDevice;
    private TabHost tabHost;
    
    private ScrollView mScrollView;
    
    private final int[] mChannelImageIDs = {
    		R.id.rcImageView1,
			R.id.rcImageView2, 
			R.id.rcImageView3, 
			R.id.rcImageView4,
			R.id.rcImageView5,
			R.id.rcImageView6};
        
    private final int[] mChannelTextIDs = {
    		R.id.rcTextViewValue1,
			R.id.rcTextViewValue2, 
			R.id.rcTextViewValue3, 
			R.id.rcTextViewValue4,
			R.id.rcTextViewValue5,
			R.id.rcTextViewValue6};

    private final int[] mChannelProgressIDs = {
    		R.id.rcProgressBar1,
			R.id.rcProgressBar2, 
			R.id.rcProgressBar3, 
			R.id.rcProgressBar4,
			R.id.rcProgressBar5,
			R.id.rcProgressBar6};
    
    // Arduino Information Commands
    private static final int COMMAND_ACTIVE_CHANNELS = 1;
    private static final int COMMAND_CHANNEL_ACTIVE = 2;
    private static final int COMMAND_CHANNEL_PWM = 3;
    private static final int COMMAND_FEATURES = 4;
    private static final int COMMAND_FEATURE = 5;
    private static final int COMMAND_PING_DISTANCE = 6;
    private static final int COMMAND_SWITCHES = 7;
    private static final int COMMAND_CHANNEL_INFO = 8;

    private static final String ACTIVE_CHANNELS = "ACTIVECHANNELS";
    private static final String CHANNEL_ACTIVE = "CHANNELACTIVE";
    private static final String CHANNEL_PWM = "CHANNELPWM";
    private static final String FEATURES = "FEATURES";
    private static final String FEATURE = "FEATURE";
    private static final String PING_DISTANCE = "PINGDISTANCE";
    private static final String SWITCHES = "SWITCHES";
    private static final String CHANNEL_INFO = "CHANNELINFO";

    private static final int SWITCH_1 = 1;
    private static final int SWITCH_2 = 2;
    private static final int SWITCH_3 = 4;
    private static final int SWITCH_4 = 8;

    // Arduino Features
    private static final int FEATURE_SDCARD = 1;
    private static final int FEATURE_PING = 2;
    private static final int FEATURE_IR = 4;
    private static final int FEATURE_RC = 8;
    private static final int FEATURE_ACCEL = 16;
    private static final int FEATURE_SWITCHES = 32;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {
            log("Runner stopped.", Log.DEBUG);
        }

        @Override
        public void onNewData(final byte[] data) {
        	runOnUiThread(new Runnable() {
                @Override
                public void run() {
                	updateReceivedData(data);
                }
            });
        }
    };

    // Listen to check when a USB Host is connected/disconnected and handle the connection
    BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction(); 
            mTextDevice.setText(action);//textDevice
		    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

			if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
    		    //UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            	mTextDevice.setText("Arduino Disconnected");//textDevice
            	mConnected = false;
            	resetControls();
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				if (!mUsbManager.hasPermission(device)) {
					mTextDevice.setText("Requesting Permission to use Arduino");
					mUsbManager.requestPermission(device, mPermissionIntent);
				}
			} else if (ACTION_USB_PERMISSION.equals(action)) {   
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if(device != null) {
                      //call method to set up device communication
                    	scanDevices();
                   }
                } else {
                	mTextDevice.setText("Permission Denied.");
                }
				// We Should take a look at the device VendorId and ProductId and to determine and store the device type
			    // This way we can access appropriate hardware features available on the Arduino that is currently connected
			    // One idea would be to have the Arduino handle pin specifics and return information that is requested.
			    // We could also query whether a specific feature is enabled like SDCard and Ping/IR Sensors
			}
		}
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        setContentView(R.layout.activity_main);
		mTextViewLog = (TextView) findViewById(R.id.textDebugLog);
		mTextDevice = (TextView) findViewById(R.id.textDevice);
		mScrollView = (ScrollView) findViewById(R.id.logScrollView);
		mConnected = false;
		mResponse = "";
		mFeatures = 0;
		mActiveChannels = 0;
		//mSwitches = 0;

        tabHost=(TabHost)findViewById(R.id.tabhost);
        tabHost.setup();
      
        TabSpec spec1=tabHost.newTabSpec("Overview");
        spec1.setContent(R.id.tab1);
        spec1.setIndicator("Overview");
      
        TabSpec spec2=tabHost.newTabSpec("Servos");
        spec2.setIndicator("Servos");
        spec2.setContent(R.id.tab2);
      
        TabSpec spec3=tabHost.newTabSpec("Log");
        spec3.setContent(R.id.tab3);
        spec3.setIndicator("Log");
        
        tabHost.addTab(spec1);
        tabHost.addTab(spec2);
        tabHost.addTab(spec3);
    }

    public void loop() {
    	new Thread(new Runnable() {
	        @Override
	        public void run() {
	            while(mConnected) {
	            	runCommand(COMMAND_FEATURES);
	            	runCommand(COMMAND_SWITCHES, FEATURE_SWITCHES);
	            	runCommand(COMMAND_CHANNEL_INFO, FEATURE_RC);
	            	runCommand(COMMAND_PING_DISTANCE, FEATURE_PING);
    			}
	        }
	        
	        public void runCommand(int command, int required_feature) {
            	if ((mFeatures & FEATURE_PING) > 0) {
            		runCommand(command);
            	}	        	
	        }
	        
	        public void runCommand(final int command) {
	        	try {
	                Thread.sleep(10);
	            } catch (InterruptedException e) {
	                Log.e(TAG, "Error executing Thread Sleep: " + e.getMessage(), e);
	                Log.e(TAG, "StackTrace: " + e.getStackTrace(), e);
	            }
	            runOnUiThread(new Runnable() {
	                @Override
	                public void run() {
	                	sendCommand(command);
	                }
	            });
	        }
	        
	    }).start();
    }
    
        
    public void onResume(){
    	super.onResume();
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
    	
        scanDevices();
    }
    
    public void onPause(){
    	super.onPause();
    	unregisterReceiver(mUsbReceiver);
        stopIoManager();
        if (mSerialDevice != null) {
            try {
                mSerialDevice.close();
            } catch (IOException e) {
                // Ignore.
            }
            mSerialDevice = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
        
        // We'll need to add a Refresh option to the menu that runs scanDevices();
        
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    	case R.id.action_refresh:
    		scanDevices();
    		log("Refresh selected");
        break;
/*      case R.id.action_settings:
        Toast.makeText(this, "Settings selected", Toast.LENGTH_SHORT)
            .show();
        break;*/

    	default:
    		break;
    	}

    	return true;
    }

    private void scanDevices() {
        mSerialDevice = UsbSerialProber.acquire(mUsbManager);
        log("Resumed, mSerialDevice=" + mSerialDevice, Log.DEBUG);
        if (mSerialDevice == null) {
            mTextDevice.setText("Arduino not connected.");
        } else {
            try {
                mSerialDevice.open();
                mTextDevice.setText("Arduino Connected.");
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                mTextDevice.setText("Error opening device: " + e.getMessage());
                try {
                    mSerialDevice.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                mSerialDevice = null;
                return;
            }
        }
         
        onDeviceStateChange();
    }

    private void sendCommand(int control, int param) {
    	// Based on the command requested,
    	// We should send the command in the format of:
    	// <ACTIVECHANNELS> or <CHANNELPWM:3> or <PINGDISTANCE> or <SWITCHES>
    	// or <SERVOPOS ESC> or <FEATURES> or <FEATURE SDCARD>
    	String command = null;
    	switch(control) {
    	case COMMAND_ACTIVE_CHANNELS: command = ACTIVE_CHANNELS; break;
    	case COMMAND_CHANNEL_ACTIVE: command = CHANNEL_ACTIVE+":"+param; break;
    	case COMMAND_CHANNEL_PWM: command = CHANNEL_PWM+":"+param; break;
    	case COMMAND_FEATURES: command = FEATURES; break;
    	case COMMAND_FEATURE: command = FEATURE+":"+param; break;
    	case COMMAND_PING_DISTANCE: command = PING_DISTANCE; break;
    	case COMMAND_SWITCHES: command = SWITCHES; break;
    	case COMMAND_CHANNEL_INFO: command = CHANNEL_INFO; break;
    		default: return;
    	}
   	
        if (command != null) {
        	command = "<"+command+">"; // Encapsulate the command
        	// Send the command/request
        	// Eventually this will also be used to send overriding commands such as setting servo positions and such       		
            try {
            	log("Sending Command: "+command);
				mSerialDevice.write(command.getBytes(), 10);
		    	log("Command Sent...awaiting response...");
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
    }

    private void sendCommand(int control) {
    	sendCommand(control, -1);
    }		

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            log("Stopping io manager ..", Log.INFO);
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (mSerialDevice != null) {
            log("Starting io manager ..", Log.INFO);
            mSerialIoManager = new SerialInputOutputManager(mSerialDevice, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
    	//HexDump.dumpHexString(array)
        //byte[] hexdata = HexDump.hexStringToByteArray(data);
        String message = decodeData(data);
        String command;
        
        if (message.startsWith("[")) {
        	mResponse = message;
        } else {
        	mResponse += message;
        }

        // Process any chunked commands
    	while(mResponse.contains("][")) {
    		log("mResponse: "+mResponse);
    		log("indexOf: "+mResponse.indexOf("[", 1));
    		command = mResponse.substring(0, mResponse.indexOf("[", 1));
    		log("Chunked Command '"+command+"' found.");
    		handleCommandResponse(command);
    		mResponse = mResponse.substring(mResponse.indexOf("[", 1));
    	}
        
        // We'll wait for a [Connected] message and set a boolean variable mConnected
        // After this we can feel free to write to the Arduino
        if (mResponse.contentEquals("[Connected]")) {
    		mConnected = true;
    		log("Connection Established.", Log.DEBUG);
    		loop();
    	} else if (mResponse.startsWith("[") && mResponse.endsWith("]")) { // Check if String begins with [ and ends with ]
    		handleCommandResponse(mResponse);
    	}
    }
    
    private void handleCommandResponse(String response) {
    	String param;
    	String value;
		log("running handleCommandResponse("+response+")");

		// Strip Brackets Off
    	response = response.substring(1, response.length()-1);

    	if (response.contains("[") || response.contains("]")) {
    		log("Unexpected Response Format: '"+response+"'", Log.WARN);
    		return;
    	}
    	
    	StringTokenizer tokens = new StringTokenizer(response, ":");
    	String command = tokens.nextToken();
    	String data = tokens.nextToken();

		log("'"+response+"'");
		log("mConnected="+(mConnected ? "true" : "false"));
    	if (mConnected) {
    		// mConnected is intended to represent that a serial connection as well
    		// However, this might be removed. It doesn't seem necessary as of yet.
    		log("'"+command+"'");
			if (command.contentEquals(ACTIVE_CHANNELS)) {
		    	// Set Active Channel Lights by bit comparing + Store in global variable
				setActiveChannels(Integer.parseInt(data));
			} else if (command.contentEquals(CHANNEL_INFO)) {
	    		setChannelInfo(data);
			} else if (command.contentEquals(CHANNEL_ACTIVE)) {
		    	StringTokenizer data_tokens = new StringTokenizer(data, ",");
		    	param = data_tokens.nextToken();
		    	value = data_tokens.nextToken();
		    	// To Do: Set Active Channel Light to off/on based on 0/1 response + Store in global variable
		    	setActiveChannel(Integer.parseInt(param), Integer.parseInt(value));
			} else if (command.contentEquals(CHANNEL_PWM)) {
		    	// Set Channel PWM Value and Progress Bar
	    		log("Data: '"+data+"'");
		    	StringTokenizer data_tokens = new StringTokenizer(data, ",");
		    	param = data_tokens.nextToken();
		    	value = data_tokens.nextToken();
	    		log("Param: '"+param+"'");
	    		log("Value: '"+value+"'");
		    	setChannel(Integer.parseInt(param), Integer.parseInt(value));
			} else if (command.contentEquals(FEATURES)) {
		    	// Set Feature Lights by bit comparing features with value
				setFeatures(Integer.parseInt(data));
			} else if (command.contentEquals(FEATURE)) {
		    	// Set Feature Light to off/on based on 0/1 response
		    	StringTokenizer data_tokens = new StringTokenizer(data, ",");
		    	param = data_tokens.nextToken();
		    	value = data_tokens.nextToken();
		    	setFeature(Integer.parseInt(param), Integer.parseInt(value));
			} else if (command.contentEquals(PING_DISTANCE)) {
		    	// To Do: Set Ping String to distance if detected
				setPingLabel(Long.parseLong(data));
			} else if (command.contentEquals(SWITCHES)) {
	    		log("Data: '"+data+"'");
	    		setSwitches(Integer.parseInt(data));
			}

    	}
        mScrollView.smoothScrollTo(0, mTextViewLog.getBottom());
    }
    
    private void setSwitches(int value) {
    	// Set Switch Values by bit comparing
		Switch switch1 = (Switch) findViewById(R.id.switch1);
		Switch switch2 = (Switch) findViewById(R.id.switch2);
		Switch switch3 = (Switch) findViewById(R.id.switch3);
		Switch switch4 = (Switch) findViewById(R.id.switch4);
		switch1.setChecked((value & SWITCH_1) > 0);
		switch2.setChecked((value & SWITCH_2) > 0);
		switch3.setChecked((value & SWITCH_3) > 0);
		switch4.setChecked((value & SWITCH_4) > 0);
		//mSwitches = value;
    }

    private void setChannelInfo(String data) {
    	StringTokenizer channel_tokens = new StringTokenizer(data, "|");
    	String data_token;
    	StringTokenizer data_tokens;
    	int channel, value;
    	while (channel_tokens.hasMoreTokens()) {
    		data_token = channel_tokens.nextToken();
    		data_tokens = new StringTokenizer(data_token, ",");
    		channel = Integer.parseInt(data_tokens.nextToken());
    		value = Integer.parseInt(data_tokens.nextToken());
        	setChannel(channel, value);
    	}
    }
    
    private void setFeature(int feature, int value) {
    	if (value == 1) {
    		mFeatures |= feature;
    	} else if (value == 0) {
    		mFeatures &= feature;
    	}
    	setFeatures(mFeatures);
    }

    private void setFeatures(int value) {
		mFeatures = value;
    	// Set Feature Lights by bit comparing
    	ImageView ivSwitches = (ImageView) findViewById(R.id.featureImageViewSwitches);
    	ImageView ivPing = (ImageView) findViewById(R.id.featureImageViewPing);
    	ImageView ivRC = (ImageView) findViewById(R.id.featureImageViewRC);
    	ImageView ivSDCard = (ImageView) findViewById(R.id.featureImageViewSDCard);
    	ImageView ivIR = (ImageView) findViewById(R.id.featureImageViewIR);
    	ImageView ivAccelerometer = (ImageView) findViewById(R.id.featureImageViewAccelerometer);
    	ivSwitches.setImageResource(((mFeatures & FEATURE_SWITCHES) > 0) ? R.drawable.ic_led_green_on : R.drawable.ic_led_red_on);
    	ivPing.setImageResource(((mFeatures & FEATURE_PING) > 0) ? R.drawable.ic_led_green_on : R.drawable.ic_led_red_on);
    	ivRC.setImageResource(((mFeatures & FEATURE_RC) > 0) ? R.drawable.ic_led_green_on : R.drawable.ic_led_red_on);
    	ivSDCard.setImageResource(((mFeatures & FEATURE_SDCARD) > 0) ? R.drawable.ic_led_green_on : R.drawable.ic_led_red_on);
    	ivIR.setImageResource(((mFeatures & FEATURE_IR) > 0) ? R.drawable.ic_led_green_on : R.drawable.ic_led_red_on);
    	ivAccelerometer.setImageResource(((mFeatures & FEATURE_ACCEL) > 0) ? R.drawable.ic_led_green_on : R.drawable.ic_led_red_on);
    }

    private void setActiveChannel(int channel, int value) {
    	if (value == 1) {
    		mActiveChannels |= channel;
    	} else if (value == 0) {
    		mActiveChannels &= channel;
    	}
    	setActiveChannels(mActiveChannels);
    }
    
    private void setActiveChannels(int value) {
		log("Active Channel Value: "+value);    			
    	ImageView currentLED;
    	boolean isActive;
    	int resource_id;
    	for(int i=0; i<6; i++) {
    		resource_id = mChannelImageIDs[i];
    		log("Resource ID for Channel "+(i+1)+": "+resource_id, Log.VERBOSE);    			
    		if (resource_id > 0) {
    			currentLED = (ImageView) findViewById(resource_id);
    			if (currentLED != null) {
    				isActive = (value & 1 << i) > 0;
    				currentLED.setImageResource(isActive ? R.drawable.ic_led_green_on : R.drawable.ic_led_red_on);
    			} else {
        			log("findViewById("+resource_id+") returned null", Log.DEBUG);    			
    			}
    		} else {
    			log("Resource Not found for 'rcImageView"+(i+1)+"'", Log.DEBUG);    			
    		}
    	}
    	mActiveChannels = value;

		//for(int i=1; i<=6; i++) requestPWN(i);
    }
    
    /*private void requestPWN(int channel) {
    	if (channel < 1 || channel > 6) {
    		log("Channel "+channel+" not found in requestPWN().");
    		return;
    	}
    	if (isActive(channel)) {
        	log("Sending PWM Channel Request.");
    		sendCommand(COMMAND_CHANNEL_PWM, channel);
    	} else {
    		log("Channel "+channel+" not active.");
    	}
    }*/
    
    private void setPingLabel(long distance) {
    	TextView ping_label = (TextView) findViewById(R.id.textViewPingDistance);
    	if (distance == 0) {
    		ping_label.setText(R.string.ping_value);
    	} else {
    		ping_label.setText(R.string.ping_detected);
    		//ping_label.append((distance / 100)+"m.");
    		ping_label.append(" "+distance+"cm.");
    	}
    }

    private void setChannel(int channel, int value) {
    	boolean isActive = (value >= 600 && value <= 2400); 
    	if (channel < 1 || channel > 6) {
    		log("Channel "+channel+" not found in setChannel().", Log.WARN);
    		return;
    	}
   	
		int channel_resource_id = mChannelTextIDs[channel-1];
		if (channel_resource_id == 0) {
    		log("TextView Resource ID for Channel "+channel+" not found.", Log.DEBUG);    			
    		return;
		}
		TextView currentChannel = (TextView) findViewById(channel_resource_id); 
		if (currentChannel == null) {
    		log("Resource Not found for rcTextViewValue"+channel+".", Log.DEBUG);    			
    		return;
		}   		
		int progress_resource_id = mChannelProgressIDs[channel-1];
		if (progress_resource_id == 0) {
    		log("ProgressBar Resource ID for Channel "+channel+" not found.", Log.DEBUG);    			
    		return;
		}
		ProgressBar currentPB = (ProgressBar) findViewById(progress_resource_id);
 		if (currentPB == null) {
    		log("Resource Not found for rcProgressBar"+channel+".", Log.DEBUG);    			
    		return;
		}   		

 		if (isActive) {
    		log("Channel "+channel+" has a value of "+value+"ms.");    			
    		currentChannel.setText(value+"ms");
			if (value > 2000) value = 2000;
			if (value < 1000) value = 1000;
    		int percent = (int) Math.floor((value - 1000) / 10);
    		if (percent < 0 || percent > 100) log(percent + " percent for Channel "+channel+" is not a valid value.");
			currentPB.setProgress(percent);
		} else {
    		currentChannel.setText(R.string.default_time);
			currentPB.setProgress(0);
		}
 		
 		int resource_id = mChannelImageIDs[channel-1];
		if (resource_id == 0) {
			log("Resource Not found for 'rcImageView"+(channel)+"'", Log.DEBUG);
			return;
		}

		ImageView currentLED = (ImageView) findViewById(resource_id);
		if (currentLED != null) {
			currentLED.setImageResource(isActive ? R.drawable.ic_led_green_on : R.drawable.ic_led_red_on);
		} else {
			log("findViewById("+resource_id+") returned null", Log.DEBUG);    			
		}
    }
    
    private void log(String message, int type) {
    	//message += "\n";
    	String tag = "RoboLink";
    	String htmltext;
    	switch (type) {
    		case Log.DEBUG: Log.d(tag, message); 
    			htmltext = "<font color=\"#0000cc\">"+message+"</font><br>"; // Blue
    			mTextViewLog.append(Html.fromHtml(htmltext)); break;
    		case Log.ERROR: Log.e(tag, message);
    			htmltext = "<font color=\"#ff0000\">"+message+"</font><br>"; // Red
				mTextViewLog.append(Html.fromHtml(htmltext)); break;
    		case Log.INFO: Log.i(tag, message); 
    			htmltext = "<font color=\"#008800\">"+message+"</font><br>"; // Green
    			mTextViewLog.append(Html.fromHtml(htmltext)); break;
    		case Log.WARN: Log.w(tag, message); 
    			htmltext = "<font color=\"#ff8800\">"+message+"</font><br>"; // Orange
    			mTextViewLog.append(Html.fromHtml(htmltext)); break;
    		case Log.VERBOSE: Log.v(tag, message); break;
    		default: Log.i(tag, message); break;
    	}
    }
    
    private void log(String message) {
    	log(message, Log.VERBOSE);
    }
    /*
    private boolean isActive(int channel) {
    	if (channel < 1 || channel > 6) {
    		log("Channel "+channel+" not found in isActive().");
    		return false;
    	}
    	boolean isActive = (mActiveChannels & 1 << (channel - 1)) > 0;
    	if (isActive) {
    		log("Channel "+channel+" is active.");
    	} else {
    		log("Channel "+channel+" is not active.");
    	}
		return isActive;
    }
    */
    
    private void resetControls() {
    	
    	
    	// Set All Switches to Off
		setSwitches(0);
    
		// Set All RC Channel Lights to off
		setActiveChannels(0);
		
		// Set All RC Channel Values and Progress Bars to 0
		for(int i=1; i<=6; i++) setChannel(i, 0);
		
		// Set All Feature Lights to off
		setFeatures(0);
		
		// Set Ping Sensor Value to 0
		setPingLabel(0);
    }
    
    private String decodeData(byte[] data) {
        StringBuilder result = new StringBuilder();
        
        byte[] line = new byte[256];
        int lineIndex = 0;
        
        for (int i = 0 ; i < data.length ; i++) {
            // Copy up to every 256 bytes to the buffer
        	// If we hit the end, append and restart
            if (lineIndex == 256) {
                for (int j = 0 ; j < 256 ; j++) {
                    if (line[j] >= ' ' && line[j] <= '~') {
                        result.append(new String(line, j, 1));
                    } else {
                        result.append(".");
                    }
                }
                lineIndex = 0;
            }
            
            byte b = data[i];
            line[lineIndex++] = b;
        }
        
        
        
        if (lineIndex != 256) {
            for (int i = 0 ; i < lineIndex ; i++) {
                if (line[i] >= ' ' && line[i] <= '~') {
                    result.append(new String(line, i, 1));
                } else {
                    result.append(".");
                }
            }
        }
        
        return result.toString();
    }
}