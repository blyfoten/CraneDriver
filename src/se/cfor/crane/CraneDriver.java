package se.cfor.crane;

import se.cfor.crane.CranePreferences;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

public class CraneDriver extends Activity {
    /** Menu ID for the command to clear the window. */
    private static final int CLEAR_ID = Menu.FIRST;
    /** Menu ID for the command to toggle fading. */
    private static final int PREFS_ID = Menu.FIRST+1;
    private static final int STEP_MSG = 1;
    private static final int STEP_DELAY = 20;
    private static final int REQUEST_CODE_PREFERENCES = 1;
    private CraneView cView; 
	private Prick crane;
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        

        
        cView = new CraneView(this);
        setContentView(cView);
        cView.requestFocus();
        crane = new Prick();
        
    }
    @Override protected void onResume() {
        super.onResume();
        startStep();
    }

    @Override protected void onPause() {
        super.onPause();
        // Make sure to never run the fading pulse while we are paused or
        // stopped.
        stopStep();
    }
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, CLEAR_ID, 0, "Clear");
        menu.add(0, PREFS_ID, 0, "Options");
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // The preferences returned if the request code is what we had given
        // earlier in startSubActivity
        if (requestCode == REQUEST_CODE_PREFERENCES) {
            // Read a sample value they have set
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            crane.m =Double.valueOf(sharedPref.getString(CranePreferences.KEY_CRANE_MASS, "500"));
            crane.c =Double.valueOf(sharedPref.getString(CranePreferences.KEY_CRANE_AIRRES, "10"));
        }
    }

    
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case CLEAR_ID:
                cView.clear();
                return true;
            case PREFS_ID:
                // When the button is clicked, launch an activity through this intent
                Intent launchPreferencesIntent = new Intent().setClass(this, CranePreferences.class);
                
                // Make it a subactivity so we know when it returns
                startActivityForResult(launchPreferencesIntent, REQUEST_CODE_PREFERENCES);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

	
    /**
     * Start up the pulse to fade the screen, clearing any existing pulse to
     * ensure that we don't have multiple pulses running at a time.
     */
    void startStep() {
        mHandler.removeMessages(STEP_MSG);
        mHandler.sendMessageDelayed(
                mHandler.obtainMessage(STEP_MSG), STEP_DELAY);
    }
    
    /**
     * Stop the pulse to fade the screen.
     */
    void stopStep() {
        mHandler.removeMessages(STEP_MSG);
    }
    
    private Handler mHandler = new Handler() {
        @Override public void handleMessage(Message msg) {
            switch (msg.what) {
                // Upon receiving the step pulse, we have the crane perform a
                // step, the view to repaint and then enqueue a new message to pulse at the desired
                // next time.
                case STEP_MSG: {
                    crane.step();
                	cView.drawDot();
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(STEP_MSG), STEP_DELAY);
                    break;
                }
                default:
                    super.handleMessage(msg);
            }
        }
    };

	
	public class Prick {
		private double x = 40; 		//muspekarens position
		private double y = 40;
		private double dotx = 40;	//prickens position
		private double doty = 40;
		private double velx=0;		//hastighet
		private double vely=0;
		private double accx=0;		//acceleration
		private double accy=0;
		private double fx=0;  		//kraft
		private double fy=0;  
		public double m=1000; 		//massa         lek med dessa för att få till den önskade effekten
		private double c=30; 		//vindmotståndskoefficient 0=inget vindmotstånd
		private Prick polare = null;
				
		public void step() {  // tråden
			if (polare !=null) {
				x = polare.getX();
				y = polare.getY();
			}
			fx = x - dotx;  // räknar ut kraften som skillnaden mellan pricken och muspekaren
			fy = y - doty;
			accx = (fx -velx*velx*c*((velx>0)?1:-1))/m;  // accelerationen = kraften/massan - lite vindmotstånd (brukar vara en kraft som är proportionell mot hastigheten i kvadrat) 
			accy = (fy -vely*vely*c*((vely>0)?1:-1))/m;
			velx += accx;	// hastigheten är ju summan av accelerationen (lite förenklat)
			vely += accy;
			dotx += velx;   // positionen är den summan av den accumulerade hastigheten
			doty += vely;
		}
		public void setCoord(double x, double y) {
			this.x = x;
			this.y = y;
		}
		public double getX() {
			return this.dotx;
		}
		public double getY() {
			return this.doty;
		}
		
	}
	
	public class CraneView extends View {

        private static final int FADE_ALPHA = 0x06;
        private Bitmap mBitmap;
        private Canvas mCanvas;
        private final Rect mRect = new Rect();
        private final Paint mPaint;
        private final Paint mFadePaint;
        private boolean mCurDown;
        private int mCurX;
        private int mCurY;
        private float mCurPressure;
        private float mCurSize;
        private int mCurWidth;

        public CraneView(Context c) {
            super(c);
            mPaint = new Paint();
            mPaint.setAntiAlias(true);
            mPaint.setARGB(255, 255, 255, 255);
            mFadePaint = new Paint();
            mFadePaint.setDither(true);
            mFadePaint.setARGB(FADE_ALPHA, 0, 0, 0);
        }

        public void clear() {
            if (mCanvas != null) {
                mPaint.setARGB(0xff, 0, 0, 0);
                mCanvas.drawPaint(mPaint);
                invalidate();
            }
        }
        
        public void drawDot() {
            if (mCanvas != null) {
                mCurX = (int)crane.getX();
                mCurY = (int)crane.getY();
                mCurWidth = 5;
                int pressureLevel = 255;
                mPaint.setARGB(pressureLevel, 255, 255, 255);
                mCanvas.drawCircle(mCurX, mCurY, mCurWidth, mPaint);
                mRect.set(mCurX-mCurWidth-2, mCurY-mCurWidth-2,
                        mCurX+mCurWidth+2, mCurY+mCurWidth+2);
                invalidate(mRect);
            }
        }
        
        @Override protected void onSizeChanged(int w, int h, int oldw,
                int oldh) {
            int curW = mBitmap != null ? mBitmap.getWidth() : 0;
            int curH = mBitmap != null ? mBitmap.getHeight() : 0;
            if (curW >= w && curH >= h) {
                return;
            }
            
            if (curW < w) curW = w;
            if (curH < h) curH = h;
            
            Bitmap newBitmap = Bitmap.createBitmap(curW, curH,
                                                   Bitmap.Config.RGB_565);
            Canvas newCanvas = new Canvas();
            newCanvas.setBitmap(newBitmap);
            if (mBitmap != null) {
                newCanvas.drawBitmap(mBitmap, 0, 0, null);
            }
            mBitmap = newBitmap;
            mCanvas = newCanvas;
        }
        
        @Override protected void onDraw(Canvas canvas) {
            if (mBitmap != null) {
                canvas.drawBitmap(mBitmap, 0, 0, null);
            }
        }

        @Override public boolean onTrackballEvent(MotionEvent event) {
            boolean oldDown = mCurDown;
            mCurDown = true;
            int N = event.getHistorySize();
            int baseX = mCurX;
            int baseY = mCurY;
            final float scaleX = event.getXPrecision();
            final float scaleY = event.getYPrecision();
            for (int i=0; i<N; i++) {
                //Log.i("TouchPaint", "Intermediate trackball #" + i
                //        + ": x=" + event.getHistoricalX(i)
                //        + ", y=" + event.getHistoricalY(i));
                drawPoint(baseX+event.getHistoricalX(i)*scaleX,
                        baseY+event.getHistoricalY(i)*scaleY,
                        event.getHistoricalPressure(i),
                        event.getHistoricalSize(i));
            }
            //Log.i("TouchPaint", "Trackball: x=" + event.getX()
            //        + ", y=" + event.getY());
            drawPoint(baseX+event.getX()*scaleX, baseY+event.getY()*scaleY,
                    event.getPressure(), event.getSize());
            mCurDown = oldDown;
            return true;
        }
        
        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getAction();
            mCurDown = action == MotionEvent.ACTION_DOWN
                    || action == MotionEvent.ACTION_MOVE;
            crane.setCoord((double)event.getX(), (double)event.getY());
            return true;
        }
        
        private void drawPoint(float x, float y, float pressure, float size) {
            //Log.i("TouchPaint", "Drawing: " + x + "x" + y + " p="
            //        + pressure + " s=" + size);
            mCurX = (int)x;
            mCurY = (int)y;
            mCurPressure = pressure;
            mCurSize = size;
            mCurWidth = (int)(mCurSize*(getWidth()/3));
            if (mCurWidth < 1) mCurWidth = 1;
            if (mCurDown && mBitmap != null) {
                int pressureLevel = (int)(mCurPressure*255);
                mPaint.setARGB(pressureLevel, 255, 255, 255);
                mCanvas.drawCircle(mCurX, mCurY, mCurWidth, mPaint);
                mRect.set(mCurX-mCurWidth-2, mCurY-mCurWidth-2,
                        mCurX+mCurWidth+2, mCurY+mCurWidth+2);
                invalidate(mRect);
            }
        }
    }

}