package walle.cip.com;


import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {
	SurfaceHolder mHolder;
	private static int W = 720;
	private static int H = 480;
	private Camera mCamera;
	private byte[] buf1 = new byte[W*H*3/2];
	private byte[] buf2 = new byte[W*H*3/2];
	private int count = 0;
	private long last;
	private long cost;
	private long t1;
	private long delta;

	CameraView(Context context) {
		super(context);

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		mCamera = Camera.open();
		try {
			mCamera.setPreviewDisplay(holder);
		} catch (IOException exception) {
			mCamera.release();
			mCamera = null;
			// TODO: add more exception handling logic here
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		// Surface will be destroyed when we return, so stop the preview.
		// Because the CameraDevice object is not a shared resource, it's very
		// important to release it when the activity is paused.
		mCamera.stopPreview();
		mCamera.release();
		mCamera = null;
	}

	@SuppressWarnings("unused")
	private Size getOptimalPreviewSize(List<Size> sizes, int w, int h) {
		final double ASPECT_TOLERANCE = 0.05;
		double targetRatio = (double) w / h;
		if (sizes == null) return null;

		Size optimalSize = null;
		double minDiff = Double.MAX_VALUE;

		int targetHeight = h;

		// Try to find an size match aspect ratio and size
		for (Size size : sizes) {
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
			if (Math.abs(size.height - targetHeight) < minDiff) {
				optimalSize = size;
				minDiff = Math.abs(size.height - targetHeight);
			}
		}

		// Cannot find the one match the aspect ratio, ignore the requirement
		if (optimalSize == null) {
			minDiff = Double.MAX_VALUE;
			for (Size size : sizes) {
				if (Math.abs(size.height - targetHeight) < minDiff) {
					optimalSize = size;
					minDiff = Math.abs(size.height - targetHeight);
				}
			}
		}
		return optimalSize;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// Now that the size is known, set up the camera parameters and begin
		// the preview.
		Camera.Parameters parameters = mCamera.getParameters();

		//List<Size> sizes = parameters.getSupportedPreviewSizes();
		//Size optimalSize = getOptimalPreviewSize(sizes, w, h);
		parameters.setPreviewSize(W, H);
		parameters.setRotation(90);

		mCamera.setParameters(parameters);
		mCamera.setPreviewCallbackWithBuffer(this);
		mCamera.startPreview();
		mCamera.addCallbackBuffer(buf1);
		last = SystemClock.currentThreadTimeMillis();
	}

	public void onPreviewFrame(byte[] arg0, Camera arg1) {

		t1 = java.lang.System.currentTimeMillis();

		if(arg0 == buf1)
			mCamera.addCallbackBuffer(buf2);
		else
			mCamera.addCallbackBuffer(buf1);

		cost = cost + java.lang.System.currentTimeMillis() - t1;
		if(count++ == 100) {
			count =0;
			delta = java.lang.System.currentTimeMillis() - last;
			last = java.lang.System.currentTimeMillis();
			Log.e("Chao", "frame cost="+ cost+", delta="+delta);
			cost = 0;
		}
	}
}
