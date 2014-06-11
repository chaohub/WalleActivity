package walle.cip.com;

import java.util.ArrayList;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class Navigator implements SensorEventListener {
	private SensorManager mSensorManager;
	private Sensor mOrientationSensor;
	private Context mContext;

	private int SWEEPWIDTH = 30;
	private double mThreshold = 20;
	private double adjustRatio = 1.0f;
	public double mx = 0, my = 0;
	private int mAzimuth = 0;
	public int mPitch = 0;
	private ArrayList<WayPoint> mRecordPath;
	private ArrayList<WayPoint> mPath;
	public Mode mMode;
	private int mId = 0;
	private static final String TAG = "WalleNav";
	private int mSteps = 0;
	
	enum Mode {
		Stop, Manual, Record, Follow, Test
	}

	public class WayPoint {
		public double dist;
		public int angle;
		public double x, y;
		public int mode;

		public WayPoint() {
		}

		public WayPoint(double mx, double my) {
			this.x = mx;
			this.y = my;
			this.angle = 0;
			this.mode = 0;
		}
		
		public WayPoint(double x, double y, int a, int mode) {
			this.x = x;
			this.y = y;
			this.angle = a;
			this.mode = mode;
		}
	}

	public Navigator(Context c) {
		mContext = c;
		mSensorManager = (SensorManager) mContext
				.getSystemService(Context.SENSOR_SERVICE);
		mOrientationSensor = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ORIENTATION);
		mSensorManager.registerListener(this, mOrientationSensor,
				SensorManager.SENSOR_DELAY_NORMAL);
		mPath = new ArrayList<WayPoint>();
		mMode = Mode.Stop;
	}

	public float getOrientation() {
		return mAzimuth;
	}

	public synchronized double move(int step) {		
		double ret = 0;
		WayPoint wp0;
		switch (mMode) {
		case Stop:
			ret = 361;	// Stay with current direction
			break;
		case Manual:
			ret = 0;	// Stay with current direction
			break;
		case Record:
			mSteps += step;
			if (mSteps > 30) {
				mRecordPath.add(new WayPoint(mx, my));
				mSteps = 0;
			}
			break;
		case Test:
		case Follow:
			mx += step * Math.sin(Math.toRadians(mAzimuth));
			my += step * Math.cos(Math.toRadians(mAzimuth));
			
			wp0 = mPath.get(mId);
			double dist = FindDistance(wp0.x, wp0.y, mx, my);
			float angle = (float) Math.toDegrees(Math.atan2(wp0.x - mx, wp0.y - my));
			ret = ((angle - mAzimuth) * adjustRatio) % 360;
			if (ret < -180) ret += 360;
			else if (ret > 180) ret -=360;
			
			if ((dist < 5) || ((dist < mThreshold) && ((ret < -90) || (ret > 90)))) {  //if yes, we reach current waypoint, go the next one
				mId++;
				if (mId >= mPath.size()) {
					mMode = Mode.Stop;
					Log.d(TAG, "Path ended. Dist=" + dist);
					ret = 1080;
				}
				else {
					wp0 = mPath.get(mId);
					dist = FindDistance(wp0.x, wp0.y, mx, my);
					angle = (float) Math.toDegrees(Math.atan2(wp0.x - mx, wp0.y - my));
					ret = ((angle - mAzimuth) * adjustRatio) % 360;
					if (ret < -180) ret += 360;
					else if (ret > 180) ret -=360;
				}
			}
			
			//ret = ret;
			Log.d(TAG, "mAngle=" + mAzimuth + ", turn=" + ret + ", (" + mx +", " + my + "), (" + wp0.x + ", " + wp0.y + ")");
			break;
		default:
			break;
		}
		
		return ret;
	}

	private double FindDistance(double x1, double y1, double x2, double y2) {
		double x = x1 - x2;
		double y = y1 - y2;
		return Math.sqrt(x * x + y * y);
	}
	// Calculate the distance between
	// point pt and the segment p1 --> p2.
	@SuppressWarnings("unused")
	private double FindDistanceToSegment(double x, double y, double x1, double y1, double x2, double y2)
	{
	    double dx = x2 - x1;
	    double dy = y2 - y1;
	    // Calculate the t that minimizes the distance.
	    double t = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy);

	    // See if this represents one of the segment's
	    // end points or a point in the middle.
	    if (t < 0) {
	        dx = x - x1;
	        dy = y - y1;
	    }
	    else if (t > 1) {
	        dx = x - x2;
	        dy = y - y2;
	    }
	    else {
	        dx = x - (x1 + t * dx);
	        dy = y - ( y1 + t * dy);
	    }

	    return Math.sqrt(dx * dx + dy * dy);
	}
	
	public synchronized void setMode(Mode mod) {
		if (mMode == Mode.Record) {
			//TODO: Save the recorded path
		}

		mMode = mod;

		switch (mMode) {
		case Record:
			mRecordPath.clear();
		case Follow:
			mId = 0;
			break;
		case Test:
			break;
		default:
			break;
		}
	}

	public synchronized void pattern(int shape, float size) {
		mPath.clear();
		mId = 0;
		switch (shape) {
		case 0:	// Straight line
			mPath.add(new WayPoint(mx, my));
			mPath.add(new WayPoint(mx, my + size));
			break;
		case 1: // Square
			mPath.add(new WayPoint(mx, my));
			mPath.add(new WayPoint(mx, my + size));
			mPath.add(new WayPoint(mx + size, my + size));
			mPath.add(new WayPoint(mx + size, my));
			mPath.add(new WayPoint(mx, my));
			break;
		case 2: // circle
			for (int i = 0; i < 360; i+=10) {
				mPath.add(new WayPoint((float)(mx + size * Math.sin(Math.toRadians(i))), my + (float)(size * Math.cos(Math.toRadians(i)))));
			}
			mPath.add(new WayPoint(mx, my));
			break;
		case 3: // zigzag square
			mPath.clear();
			for (int i = 0; i <= size; i +=SWEEPWIDTH) {
				mPath.add(new WayPoint(mx + i, my));
				mPath.add(new WayPoint(mx + i, my + size));
			}
			mPath.add(new WayPoint(mx, my));
			break;
		case 4:	// swirl
			mPath.clear();
			for (int i = 0; i <= size/2; i +=SWEEPWIDTH) {
				mPath.add(new WayPoint(mx - i, my - i));
				mPath.add(new WayPoint(mx - i, my + i));
				mPath.add(new WayPoint(mx + i, my + i));
				if (i + SWEEPWIDTH > size/2)
					mPath.add(new WayPoint(mx + i, my - i));
				else
					mPath.add(new WayPoint(mx + i, my - i - SWEEPWIDTH));
			}
			mPath.add(new WayPoint(mx, my));
			break;
		default:
			break;
		}
	}

	public void home() {
		mPath.clear();
		mId = 0;
		mPath.add(new WayPoint(0, 0));
	}
	
	@Override
	public void onAccuracyChanged(Sensor arg0, int arg1) {
		// TODO Auto-generated method stub
	}

	@Override
	public void onSensorChanged(SensorEvent arg0) {
		mAzimuth = Math.round(arg0.values[0]);
		mPitch = Math.round(arg0.values[1]);	//up or down angle
		// float roll_angle = arg0.values[2];	//left and right angle
	}

}
