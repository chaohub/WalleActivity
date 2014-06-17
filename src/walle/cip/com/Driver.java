package walle.cip.com;

import walle.cip.com.Navigator.Mode;
import android.util.Log;
import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;

class Driver extends BaseIOIOLooper {

	static final String TAG = "WalleDrv";

	// ioio settings
	private PwmOutput E1, E2, E3, P1, BL;
	private DigitalOutput L1, L2, L3, L4, L5, L6;
	private AnalogInput mWheelIn;

	// Wheel encoder variables
	int samples = 0;
	int overflow = 0;
	boolean lastState = false;
	private static final float AVERAGE = 0.965f;
	private static final float THRETHOLD = 0.005f;
	private static final int POLLPERIOD = 50;	// IOIO board poll every 50ms
	private int mNumNoMove = 0;
	
	// Analog driving settings
	public static final float CUT_OFF_DRIVE = 0.2f;
	public static final float MAX_DRIVE = 1.0f;
	public static final float LR_RATIO = 1.0f;
	public static final float TURN_RATIO = 2.0f;
	public boolean leftReverse;
	public boolean rightReverse;
	public float leftDrive;
	public float rightDrive;
	public boolean connected = false;
	public boolean mower = false;
	private float mSpeed;
	private float turn = 0.075f;
	private byte eye = 0;
	
	private Navigator mNav;


	public Driver(Navigator nav) {
		mNav = nav;
		reset();
	}

	@Override
	public void setup() throws ConnectionLostException {
		try {
			// driving motor control
			E2 = ioio_.openPwmOutput(28, 256); // right wheel drive strength
			E1 = ioio_.openPwmOutput(27, 256); // left wheel drive strength
			L1 = ioio_.openDigitalOutput(21, false); // left wheel forward
			L2 = ioio_.openDigitalOutput(22, false); // left wheel backward
			L3 = ioio_.openDigitalOutput(23, false); // right wheel forward
			L4 = ioio_.openDigitalOutput(24, false); // right wheel backward
			
			// mower motor control
			E3 = ioio_.openPwmOutput(30, 256); // right wheel drive strength
			L5 = ioio_.openDigitalOutput(25, false); // right wheel forward
			L6 = ioio_.openDigitalOutput(26, false); // right wheel backward
			E3.setDutyCycle(1.0f);
			
			// Encoding wheel
			// pin 45 white wire, pin 46 green wire
			mWheelIn = ioio_.openAnalogInput(44);
			mWheelIn.setBuffer(256); // allow buffer 64 samples at most

			// Head control
			P1 = ioio_.openPwmOutput(29, 50); // left wheel drive strength
		
			// eye led
			BL = ioio_.openPwmOutput(31, 256); // right wheel drive strength

			connected = true;
			Log.w(TAG, "IOIO connection success");

		} catch (ConnectionLostException e) {
			Log.e(TAG, "IOIO connection lost");
			throw e;
		}
	}

	@Override
	public void loop() throws ConnectionLostException {
		float angle = 0;
		int steps = 0;
		
		steps = wheelEncoder();
		if (/*mNav.mMode == Mode.Manual ||*/ mNav.mMode == Mode.Follow || mNav.mMode == Mode.Test) {
			angle = (float)mNav.move(steps);
			drive(angle, 0.5f);
		}

		if (mNav.mMode == Mode.Stop)
			reset();
		else
			checkLimit();

		// Handle the case when the robot is blocked
		if (isBlocked(steps)) {
			// TODO: add more complex reaction in case of blocked
			revert();
			mNav.setMode(Navigator.Mode.Stop);
		}
		
		motor_control();
		
		P1.setDutyCycle(turn);
		
		if ((leftDrive != 0)|| (rightDrive != 0) || (steps != 0))
			Log.i(TAG, "Direction=" + mNav.getOrientation() +", Turn=" + angle + ", step=" + steps
					+ ", L=" + leftDrive + ", R=" + rightDrive + " overflow="+ overflow);

		blink();
		mower();
		
		try {
			// This defines the looping frequency of ioio board
			Thread.sleep(POLLPERIOD);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void revert() {
		// TODO Auto-generated method stub
		leftDrive = -leftDrive;
		rightDrive = -rightDrive;
	}

	public void reset() {
		leftReverse = false;
		rightReverse = false;
		leftDrive = 0.0f;
		rightDrive = 0.0f;
	}

	synchronized public void drive(float angle, float speed) {
		float m = 1.0f;
		if (speed > 1.0f) speed = 1.0f;
		
		angle = angle % 360;
		if (angle < 0) angle += 360;
		
		angle = angle / 90.0f;
		if (angle >= 0 && angle <= 1.5) {
			//forward right turn
			leftDrive = 1.0f;
			rightDrive = (float) (1.0 - angle * m);
		}
		else if (angle > 1.5 && angle <= 2) {
			//backward right turn
			leftDrive = -1.0f;
			rightDrive = (float) (1.0 - angle * m);			
		}
		else if (angle > 2 && angle < 2.5) {
			//backward left turn
			leftDrive = (float) (angle * m - 3.0);
			rightDrive = -1.0f;			
		}
		else if (angle >= 2.5 && angle < 4) {
			//forward left turn
			leftDrive = (float) (angle * m - 3.0);
			rightDrive = 1.0f;			
		}
		
		leftDrive = leftDrive * speed;
		leftReverse = leftDrive < 0 ? true : false;
	
		rightDrive = rightDrive * speed;
		rightReverse = rightDrive < 0 ? true : false;
		
		//Log.d(TAG, "a=" + angle + ", L=" + leftDrive +", R=" + rightDrive);
	}

	private void checkLimit() {
		// Force the drive strength from -1.0 to 1.0
		if (leftDrive > 1.0f) leftDrive = 1.0f;
		if (leftDrive < -1.0f) leftDrive = -1.0f;
		if (rightDrive > 1.0f) rightDrive = 1.0f;		
		if (rightDrive < -1.0f) rightDrive = -1.0f;
		
		//no differetial drive, don't turn too sharp
		if (leftDrive * rightDrive < 0) {
			if (Math.abs(leftDrive) > Math.abs(rightDrive)) {
				rightDrive = leftDrive * 0.5f;
			}
			else {
				leftDrive = rightDrive * 0.5f;
			}
		}
		else {
			//TODO: make the turn less sharp in same direction
		}
		
		// if in differential drive mode, limit the drive strength to protect
		// the track
		float total = Math.abs(leftDrive) + Math.abs(rightDrive);
		if ((leftDrive * rightDrive < 0) && (total > 1.0f)) {
			leftDrive = leftDrive / total;
			rightDrive = rightDrive / total;
		}
		
		// rule 1, if drive strength is too little, cut it off
		if (Math.abs(leftDrive) + Math.abs(rightDrive) < 0.3f) {
			leftDrive = 0;
			rightDrive = 0;
		}
		//Log.d(TAG, "L=" + leftDrive +", R=" + rightDrive);
	}

	private void motor_control() {
		try {
			// control motor
			E1.setDutyCycle(Math.abs(leftDrive));
			L1.write(!leftReverse);
			L2.write(leftReverse);
			E2.setDutyCycle(Math.abs(rightDrive));
			L3.write(rightReverse);
			L4.write(!rightReverse);
		} catch (ConnectionLostException e) {
			Log.e(TAG, "ioio connection lost");
		} catch (Exception e) {
			Log.e(TAG, "No ioio connection");
		}
	}
	
	private boolean isBlocked(int steps) {
//		if (steps == 0)
//			if (leftDrive + rightDrive > 0.3)
//				mNumNoMove ++;
//		else
//			mNumNoMove = 0;
//
//		if (mNumNoMove >= 10) {
//			Log.i(TAG, "Walle is blocked.");
//			return true;
//		}
//		
//		if ((mNav.mPitch > 120) || (mNav.mPitch < 60)) {
//			Log.i(TAG, "Walle tilt too much.");
//			return true;
//		}
		
		return false;
	}
	
	private int wheelEncoder() {
		int steps = 0;
		String sap = "";
		try {
			samples = mWheelIn.available();
			overflow = mWheelIn.getOverflowCount();
			for (int i = 0; i < samples; i++) {
				float sample;

				sample = mWheelIn.readBuffered();
				sap += ", " + sample;
				if (sample > AVERAGE + THRETHOLD) {
					if (!lastState) {
						lastState = true;
						steps++;
					}
				} else if (sample < AVERAGE - THRETHOLD) {
					if (lastState) {
						lastState = false;
						steps++;
					}
				}
			}
			// check if the move is forward or backward
			steps = (leftDrive + rightDrive) >= 0 ? steps : -steps;
			Log.d(TAG, "samples = " + samples + ", over=" + overflow + sap);
		} catch (ConnectionLostException e) {
			Log.e(TAG, "IOIO connection lost");
			steps = 0;
		} catch (InterruptedException e) {
			Log.e(TAG, "IOIO interupt exveption");
			//e.printStackTrace();
			steps = 0;
		} catch (Exception e) {
			//No connection, fake move
			steps = 2;
		}
		
		mSpeed = mSpeed * 0.9f + steps * 0.1f;
		return steps;			
	}
	
	// simulate navigation for testing
	public boolean simulate() {
		for (int i = 0; i < 100; i++) {
			try {
				loop();
				Thread.sleep(POLLPERIOD);
			} catch (ConnectionLostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return true;
	}
	
	// turn head left or right
	public void turnHead(int f) {
		turn = 0.035f + f/100.0f * 0.075f;
		if (turn < 0.035) turn = 0.0375f;
		else if (turn > 0.1125) turn = 0.1125f;
	}
	
	public void blink() {
		eye += 12;
		float b = eye/256.0f;
		try {
			BL.setDutyCycle(b);
Log.d(TAG, "blink = " + b);
		} catch (ConnectionLostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void mower() {
		try {
			if (mower && (leftDrive + rightDrive > 0.5)) {
				L5.write(true);
				L6.write(false);
			}
			else {
				L5.write(true);
				L6.write(true);			
			}
		} catch (ConnectionLostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
		}
	}
}
