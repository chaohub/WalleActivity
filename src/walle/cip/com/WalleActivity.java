package walle.cip.com;

import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import org.apache.http.conn.util.InetAddressUtils;

import walle.cip.com.Navigator.Mode;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.ToggleButton;

public class WalleActivity extends IOIOActivity implements OnInitListener, OnSeekBarChangeListener {
	public TextView mLocalTV, mIoioTV, mStatusTV;
	public ImageView iView;
	public ToggleButton mToggleButton1;
	// private CameraView mPreview;
	private Navigator mNav;
	private Driver mDrv;
	/** Called when the activity is first created. */

	public EditText mServerET;
	public EditText mCommandET;
	
	// designate a port
	public static final int SERVERPORT = 8080;

	private Handler handler = new Handler();

	private ServerSocket mServerSocket;
	private Thread mServerThread;
	private String mToServer = null;
	private String mToClient = null;
	private boolean mServerOn = false;
	private String mServerIpAddress = "";
	private String mLocalIpAddress;
	private PrintWriter mSendBuf;
	private BufferedReader mReceiveBuf;
	private TextToSpeech tts;
	private boolean mBusy = false;
	private static final String TAG = "WalleActivity";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// mPreview = new CameraView(this);
		Log.d(TAG, "create 0");
		setContentView(R.layout.main);

		mLocalTV = (TextView) findViewById(R.id.textView1);
		mIoioTV = (TextView) findViewById(R.id.textView2);
		mStatusTV = (TextView) findViewById(R.id.textView3);
		mToggleButton1 = (ToggleButton) findViewById(R.id.toggleButton1);
		mToggleButton1.setEnabled(true);
		mToggleButton1.setChecked(true);
		mServerET = (EditText) findViewById(R.id.server_status);
		mCommandET = (EditText) findViewById(R.id.editText1);
		mCommandET.setText("move:0 100");
		iView = (ImageView) findViewById(R.id.imageView1);
		tts = new TextToSpeech(this, this);
		mNav = new Navigator(this.getApplicationContext());
		setSever();		//start server by default

		SeekBar bar = (SeekBar)findViewById(R.id.seekBar1); // make seekbar object
		bar.setProgress(50);
        bar.setOnSeekBarChangeListener(this); // set seekbar listener.

		// this is the view on which you will listen for touch events
		iView.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				float x = (int) event.getX();
				float y = (int) event.getY();
				Rect vr = new Rect();
				v.getDrawingRect(vr);


				if (event.getAction() == MotionEvent.ACTION_UP) {
					if (!mServerOn ) {
						sendCmd("stop:");
					} else {
						stop();
					}
				}
				else if (vr.contains((int)x, (int)y)) {
					mLocalTV.setText("X=" + event.getX() + ", Y=" + event.getY());
					//normalize x and y for different screen resolution
					x = (x - vr.centerX())*2/vr.width();
					y = (y - vr.centerY())*2/vr.height();
					if (!mServerOn) {
						sendCmd("direction:" + x + " " + y);
					} else {
						direction(x, y);
					}
				}
				return true;
			}
		});
		
		Log.d(TAG, "create done");
	}

	public void onToggleClicked(View view) {
		// Is the toggle on?
		boolean on = ((ToggleButton) view).isChecked();
		if (mDrv.connected)
			mToClient = "IOIO board connected";
		else
			mToClient = "IOIO board not connected";

		if (on) {
			setSever();
		} else {
			setClient();
			
			sendCmd("status:1");
			String status = receiveCmd();
			mIoioTV.setText(status);
		}
	}

	private String receiveCmd() {
		if (mReceiveBuf != null) {
			try {
				return mReceiveBuf.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}

	//Start client socket. Client is not a thread
	private void setClient() {
		try {
			mServerIpAddress = mServerET.getText().toString();
			if (!mServerIpAddress.equals(mLocalIpAddress)) {
				// if server address != local address, kill the local sever
				// Set the signal to let server kill itself.
				Log.d(TAG, "mServerThread stop");
				mServerOn = false;
				mServerThread.interrupt();
			}
			
			InetAddress serverAddr = InetAddress
					.getByName(mServerIpAddress);
			Log.d(TAG, "client thread start");
			Socket socket = new Socket(serverAddr, SERVERPORT);
			mSendBuf = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(socket.getOutputStream())), true);
			mReceiveBuf = new BufferedReader(
					new InputStreamReader(socket.getInputStream()));
			
			//update the UI to show the server-client connected.
			mStatusTV.setText("Connected to Walle.");
			sendCmd("speak: remote connected");

		} catch (Exception e) {
			// socket and stream buffer failed. reset.
			Log.e(TAG, "Server: Error", e);
		}

	}

	// Start server thread and socket. Server is a thread
	private void setSever() {
		Log.d(TAG, "Start server");
		mServerThread = new Thread(new ServerThread());

		try {
			Log.d(TAG, "ServerSocket start");
			mLocalIpAddress = getLocalIpAddress();
			if (mLocalIpAddress == null) {
				Log.e(TAG, "No network.");
				return;
			}
			InetAddress serverAddr = InetAddress.getByName(mLocalIpAddress);
			if (mServerSocket != null) mServerSocket.close();
			mServerSocket = new ServerSocket(SERVERPORT, 50, serverAddr);
			mServerET.setText(mLocalIpAddress);
			mServerOn = true;
			mServerThread.start();
			Log.d(TAG, "serverThread alive");
		} catch (Exception e) {
			Log.e(TAG, "Server: Error", e);
		}

		// stop client
		if (mSendBuf != null) {
			mSendBuf.close();
			mSendBuf = null;
		}
	}

	public void onSendClicked(View v) {
		if (mSendBuf != null) {
			mSendBuf.println(mCommandET.getText().toString());
			mSendBuf.flush();
		}
	}

	@Override
	public void onBackPressed() {
		Log.i("WalleActivity", "exit 1");

		if (mSendBuf != null) {
			mSendBuf.close();
			mSendBuf = null;
		}
		
		if (mServerThread != null) {
			mServerThread.interrupt();
			try {
				if (mServerSocket != null) mServerSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Log.e(TAG, "Exit failed", e);
				e.printStackTrace();
			}
		}
		mServerSocket = null;
		mServerThread = null;
		if (mDrv != null) {
			mDrv.reset();
			// mDrv = null;		//this will cause an exception
		}

		if (tts != null) {
			tts.shutdown();
			tts = null;
		}

		Log.i("WalleActivity", "exit 4");
		finish();
		return;
	}

	@Override
	protected IOIOLooper createIOIOLooper() {
		mDrv = new Driver(mNav);
		if (mDrv.connected)
			mStatusTV.setText("IOIO board connected");
		return mDrv;
	}

	public class ServerThread implements Runnable {

		public void run() {
			try {
				if (mLocalIpAddress != null && mServerSocket != null) {

					while (mServerOn) {
						Thread.yield();
						// listen to incoming clients
						Socket client = mServerSocket.accept();
						Log.d(TAG, "Socket connected");
						//client.setSoTimeout(60000);		//10s timeout

						try {
							BufferedReader in = new BufferedReader(
									new InputStreamReader(
											client.getInputStream()));

							PrintWriter out = new PrintWriter(new BufferedWriter(
									new OutputStreamWriter(client.getOutputStream())), true);

							while ((mToServer = in.readLine()) != null) {
								Log.d(TAG, "Get line: " + mToServer);
								
								if (mToServer.contains("status")) {
									if (mDrv.connected)
										mToClient = "IOIO board connected";
									else
										mToClient = "IOIO board not connected";
									out.println(mToClient);
								}
								
								// skip commands to prevent too many threads
								if ((mBusy) && !mToServer.contains("stop")) continue;
								
								mBusy = true;
								handler.post(new Runnable() {
									@Override
									public void run() {
										String[] commandParse = mToServer.toLowerCase()
												.split(":");
										commandParse[0] = commandParse[0].trim();
										if (commandParse.length > 1)
											commandParse[1] = commandParse[1].trim();
										if (commandParse[0].contains("direction")) {
											String[] dataToParse = commandParse[1]
													.split(" ");
											float x = Float.parseFloat(dataToParse[0]);
											float y = Float.parseFloat(dataToParse[1]);
											direction(x, y);
										} else if (commandParse[0].contains("speak")) {
											speak(commandParse[1]);
										} else if (commandParse[0].contains("move")) {
											autoMove(commandParse[1]);
										} else if (commandParse[0].contains("turn")) {
											int a = Integer.parseInt(commandParse[1]);
											mDrv.turnHead(a);
										} else if (commandParse[0].contains("mow")) {
											int m = Integer.parseInt(commandParse[1]);
											mDrv.mower = m==1? true:false;
										} else if (commandParse[0].contains("stop")) {
											stop();
										} else if (commandParse[0].contains("test")) {
											int m = Integer.parseInt(commandParse[1]);
											test(m);
										} 
										mStatusTV.setText(mToServer);
										mBusy = false;
									}
								});
							}
							Log.d(TAG, "Socket Disconnect");
							client.close();
						} catch (Exception e) {
							handler.post(new Runnable() {
								@Override
								public void run() {
									mStatusTV
									.setText("Connection interrupted. Please reconnect the remote.");
									mDrv.reset();
								}
							});
							e.printStackTrace();
						}

						Log.d(TAG, "Socket Disconnect");
					}
				} else {
					handler.post(new Runnable() {
						@Override
						public void run() {
							mServerET
							.setText("Couldn't open server socket.");
						}
					});
				}
			} catch (Exception e) {
				handler.post(new Runnable() {
					@Override
					public void run() {
						mServerET.setText("Error");
						mDrv.reset();
					}
				});
				e.printStackTrace();
			}
		}
	}

	// gets the ip address of your phone's network
	private String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				if (!(intf.getName().contains("wlan") || intf.getName()
						.contains("eth")))
					continue;
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					String ipv4;
					if (!inetAddress.isLoopbackAddress()
							&& InetAddressUtils
							.isIPv4Address(ipv4 = inetAddress
							.getHostAddress())) {
						Log.d(TAG, " " + ipv4);
						return ipv4;
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
		return null;
	}

	private void direction(float x, float y) {
		mNav.mMode = Mode.Manual;
		float angle = (float) Math.toDegrees(Math.atan2(x, -y));
		float r = (float) Math.sqrt(x * x + y * y);

		Log.d(TAG, "angle: " + angle + ", speed: " + r);
		mDrv.drive(angle, r);
	}

	private void speak(String words) {
		tts.speak(words, TextToSpeech.QUEUE_FLUSH, null);
	}

	private void autoMove(String cmd) {
		String[] commandParse = cmd.toLowerCase()
				.split(" ");
		commandParse[0] = commandParse[0].trim();
		int p = Integer.parseInt(commandParse[0]);
		
		int m = 100;
		if (commandParse.length > 1) {
			commandParse[1] = commandParse[1].trim();
			m = Integer.parseInt(commandParse[1]);
		}
		
		mNav.pattern(p, m);
		mNav.setMode(Navigator.Mode.Follow);
	}

	private void stop() {
		Log.d(TAG, "stop()");
		mNav.setMode(Navigator.Mode.Stop);
		//mDrv.reset();
	}

	private void test(int m) {
		mNav.pattern(0, m);
		mNav.setMode(Navigator.Mode.Test);
		new Thread(new Runnable() {
	        public void run() {
	        	mDrv.simulate();
	        }
	    }).start();
	}

	@Override
	public void onInit(int arg0) {
		// TODO Auto-generated method stub
		// This is the needed by speech engine
	}

	private void sendCmd(String cmd) {
		if (mSendBuf != null) {
			mSendBuf.println(cmd);
			mSendBuf.flush();
		}
	}

	@Override
	public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
		// TODO Auto-generated method stub
		String cmd = "turn:" + arg1;
		sendCmd(cmd);
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		// TODO Auto-generated method stub
		
	}
}