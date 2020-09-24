package de.oth.emi.studienarbeit2020;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.BitmapFactory.Options;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * @see SensorManager
 * @see SensorEvent
 * @see Sensor
 * @author Tobias Schotter
 */


public class AccelerometerPlayActivity extends AppCompatActivity implements View.OnTouchListener {

    private SimulationView mSimulationView;
    private SensorManager mSensorManager;
    private PowerManager mPowerManager;
    private WindowManager mWindowManager;
    private Display mDisplay;
    private WakeLock mWakeLock;

    private static final String sub_topic = "sensor/data";
    private static final String pub_topic = "mqtt/message";
    private int qos = 0;
    private String data;
    private String clientId;
    private MemoryPersistence persistence = new MemoryPersistence();
    private MqttClient client;
    private String TAG = AccelerometerPlayActivity.class.getSimpleName();

    public static final String SHARED_PREFS = "sharedPrefs";
    public static final String NEW_IP = "newIP";
    private String newIP;
    private String defaultIP = "192.168.178.20";
    private String BROKER;

    private float circleX;
    private float circleY;
    private float circleR = 100;
    private Paint brush = new Paint();
    private boolean DoDrawCircle = false;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        loadData();
        //Standartisierung des Protokolls sowie des Ports für einfachere Benutzereingabe
        BROKER = "tcp://"+newIP+":1883";


        // Get an instance of the SensorManager
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // Get an instance of the PowerManager
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);

        // Get an instance of the WindowManager
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mDisplay = mWindowManager.getDefaultDisplay();

        // Create a bright wake lock
        mWakeLock = mPowerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, getClass()
                .getName());

        // instantiate our simulation view and set it as the activity's content
        mSimulationView = new SimulationView(this);
        mSimulationView.setOnTouchListener(this);
        // Sets background to welt.jpg
        mSimulationView.setBackgroundResource(R.drawable.welt);
        setContentView(mSimulationView);

    }

    /**
     * Called when the activity is first created and creates an Options-menu
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }


    /**
     * OnClickEvent for every element inside the menu (only 1 Element inside this menu)
     * @param item a MenuItem Selected
     * @return the selected Item
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()){
            case R.id.ipadress:
                inputDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Help-function for sharedPreferences. Gets called inside onCreate()
     */
    public void loadData() {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        newIP = sharedPreferences.getString(NEW_IP, "192.168.178.20");
    }


    /**
     * Input-dialog for custom ip-address, realised with an AlertDialog
     * Input gets saved inside SharedPreferences
     */
    public void inputDialog(){
        final EditText input = new EditText(this);

        input.setHint("new Broker IP-Address");

        new AlertDialog.Builder(this)
                .setTitle("MQTT Broker")
                .setMessage("Example: " + defaultIP)
                .setView(input)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString(NEW_IP, input.getText().toString());
                        editor.apply();
                        recreate();
                    }
                })
                .setNegativeButton("ABBRECHEN", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                    }
                })
                .show();
    }

    /**
     * Method called on Resume of the activity
     */
    @Override
    protected void onResume() {

        super.onResume();
        /**
         * when the activity is resumed, we acquire a wake-lock so that the
         * screen stays on, since the user will likely not be fiddling with the
         * screen or buttons.
         */
        mWakeLock.acquire();
        connect();
        subscribe();

        /**
         * Start the simulation
         */
        mSimulationView.startSimulation();
    }

    /**
     * Method called on Pause of the activity
     */
    @Override
    protected void onPause() {

        super.onPause();
        /**
         * When the activity is paused, we make sure to stop the simulation,
         * release our sensor resources and wake locks
         */

        // Stop the simulation
        mSimulationView.stopSimulation();
        Disconnect();

        // and release our wake-lock
        mWakeLock.release();
    }

    /**
     * Method called when the user touches the screen to create a circle at the position of the touch
     * @param v The view the TouchEvent has been dispatched to.
     * @param event MotionEvent: The Object containing full information about the event.
     * @return True if the listener has consumed the event, false otherwise.
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int Aktion = event.getAction();

        if(Aktion == MotionEvent.ACTION_DOWN) {
            circleX = (int) event.getX();
            circleY = (int) event.getY();
            DoDrawCircle = true;
            return true;
        }
        return false;
    }

    /**
     * Class for Simulation of the visuals.
     * Stores all parameters and logic for the particles
     * All following methods are inside this class
     */
    class SimulationView extends FrameLayout implements SensorEventListener {
        // diameter of the balls in meters
        private static final float sBallDiameter = 0.004f;
        private static final float sBallDiameter2 = sBallDiameter * sBallDiameter;

        private final int mDstWidth;
        private final int mDstHeight;

        private Sensor mAccelerometer;
        private long mLastT;

        private float mXDpi;
        private float mYDpi;
        private float mMetersToPixelsX;
        private float mMetersToPixelsY;
        private float mXOrigin;
        private float mYOrigin;
        private float mSensorX;
        private float mSensorY;
        private float mHorizontalBound;
        private float mVerticalBound;
        private final ParticleSystem mParticleSystem;
        /**
         * Each of our particle holds its previous and current position, its
         * acceleration. for added realism each particle has its own friction
         * coefficient.
         */
        class Particle extends View {
            private float mPosX = (float) Math.random();
            private float mPosY = (float) Math.random();
            private float mVelX;
            private float mVelY;

            public Particle(Context context) {
                super(context);
            }

            public Particle(Context context, AttributeSet attrs) {
                super(context, attrs);
            }

            public Particle(Context context, AttributeSet attrs, int defStyleAttr) {
                super(context, attrs, defStyleAttr);
            }

            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            public Particle(Context context, AttributeSet attrs, int defStyleAttr,
                            int defStyleRes) {
                super(context, attrs, defStyleAttr, defStyleRes);
            }

            /**
             * Computes physics of given particles
             * @param sx
             * @param sy
             * @param dT
             */
            public void computePhysics(float sx, float sy, float dT) {

                final float ax = -sx/5;
                final float ay = -sy/5;

                mPosX += mVelX * dT + ax * dT * dT / 2;
                mPosY += mVelY * dT + ay * dT * dT / 2;

                mVelX += ax * dT;
                mVelY += ay * dT;
            }

            /**
             * Resolving constraints and collisions with the Verlet integrator
             * can be very simple, we simply need to move a colliding or
             * constrained particle in such way that the constraint is
             * satisfied.
             */
            public void resolveCollisionWithBounds() {
                final float xmax = mHorizontalBound;
                final float ymax = mVerticalBound;
                final float x = mPosX;
                final float y = mPosY;
                if (x > xmax) {
                    mPosX = xmax;
                    mVelX = 0;
                } else if (x < -xmax) {
                    mPosX = -xmax;
                    mVelX = 0;
                }
                if (y > ymax) {
                    mPosY = ymax;
                    mVelY = 0;
                } else if (y < -ymax) {
                    mPosY = -ymax;
                    mVelY = 0;
                }
            }
        }

        /**
         * A particle system is just a collection of particles
         */
        class ParticleSystem {
            public int NUM_PARTICLES = 6;
            private Particle mBalls[] = new Particle[NUM_PARTICLES];
            private int actualCount = NUM_PARTICLES;

            ParticleSystem() {
                /*
                 * Initially our particles have no speed or acceleration
                 */
                for (int i = 0; i < actualCount; i++) {
                    mBalls[i] = new Particle(getContext());
                    mBalls[i].setBackgroundResource(R.drawable.virus);
                    mBalls[i].setLayerType(LAYER_TYPE_HARDWARE, null);
                    addView(mBalls[i], new ViewGroup.LayoutParams(mDstWidth, mDstHeight));
                }
            }

            /**
             * Update the position of each particle in the system using the
             * Verlet integrator.
             */
            private void updatePositions(float sx, float sy, long timestamp) {
                final long t = timestamp;
                if (mLastT != 0) {
                    final float dT = (float) (t - mLastT) / 1000.f /** (1.0f / 1000000000.0f)*/;
                    final int count = actualCount;
                    for (int i = 0; i < count; i++) {
                        Particle ball = mBalls[i];
                        ball.computePhysics(sx, sy, dT);
                    }
                }
                mLastT = t;
            }

            /**
             * Performs one iteration of the simulation. First updating the
             * position of all the particles and resolving the constraints and
             * collisions.
             */
            public void update(float sx, float sy, long now) {
                /**
                 * update the system's positions
                 */
                updatePositions(sx, sy, now);

                /**
                 * We do no more than a limited number of iterations
                 */
                final int NUM_MAX_ITERATIONS = 10;

                /**
                 * Resolve collisions, each particle is tested against every
                 * other particle for collision. If a collision is detected the
                 * particle is moved away using a virtual spring of infinite
                 * stiffness.
                 */
                boolean more = true;
                final int count = actualCount;
                for (int k = 0; k < NUM_MAX_ITERATIONS && more; k++) {
                    more = false;
                    for (int i = 0; i < count; i++) {
                        Particle curr = mBalls[i];
                        for (int j = i + 1; j < count; j++) {
                            Particle ball = mBalls[j];
                            float dx = ball.mPosX - curr.mPosX;
                            float dy = ball.mPosY - curr.mPosY;
                            float dd = dx * dx + dy * dy;
                            /**
                             * Check for collisions
                             */
                            if (dd <= sBallDiameter2) {
                                /**
                                 * add a little bit of entropy, after nothing is
                                 * perfect in the universe.
                                 */
                                dx += ((float) Math.random() - 0.5f) * 0.0001f;
                                dy += ((float) Math.random() - 0.5f) * 0.0001f;
                                dd = dx * dx + dy * dy;
                                // simulate the spring
                                final float d = (float) Math.sqrt(dd);
                                final float c = (0.5f * (sBallDiameter - d)) / d;
                                final float effectX = dx * c;
                                final float effectY = dy * c;
                                curr.mPosX -= effectX;
                                curr.mPosY -= effectY;
                                ball.mPosX += effectX;
                                ball.mPosY += effectY;
                                more = true;
                            }
                        }
                        curr.resolveCollisionWithBounds();
                    }
                }
            }

            /**
             * Returns count of current particles
             * @return Count of particles
             */
            public int getParticleCount() {
                return actualCount;
            }

            /**
             * Gets X position of current particle
             * @param i Current iteration
             * @return x Position
             */
            public float getPosX(int i) {
                return mBalls[i].mPosX;
            }

            /**
             * Gets Y position of current particle
             * @param i Current iteration
             * @return y Position
             */
            public float getPosY(int i) {
                return mBalls[i].mPosY;
            }
        }

        /**
         * Starts simulation
         */
        public void startSimulation() {
            /**
             * It is not necessary to get accelerometer events at a very high
             * rate, by using a slower rate (SENSOR_DELAY_UI), we get an
             * automatic low-pass filter, which "extracts" the gravity component
             * of the acceleration. As an added benefit, we use less power and
             * CPU resources.
             */
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
        }

        /**
         * Stops simulation
         */
        public void stopSimulation() {
            mSensorManager.unregisterListener(this);
        }

        public SimulationView(Context context) {
            super(context);
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            mXDpi = metrics.xdpi;
            mYDpi = metrics.ydpi;
            mMetersToPixelsX = mXDpi / 0.0254f;
            mMetersToPixelsY = mYDpi / 0.0254f;

            /**
             * rescale the ball so it's about 0.5 cm on screen
             */
            mDstWidth = (int) (sBallDiameter * mMetersToPixelsX + 0.5f);
            mDstHeight = (int) (sBallDiameter * mMetersToPixelsY + 0.5f);
            mParticleSystem = new ParticleSystem();

            Options opts = new Options();
            opts.inDither = true;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            /**
             * compute the origin of the screen relative to the origin of
             * the bitmap
             */
            mXOrigin = (w - mDstWidth) * 0.5f;
            mYOrigin = (h - mDstHeight) * 0.5f;
            mHorizontalBound = ((w / mMetersToPixelsX - sBallDiameter) * 0.5f);
            mVerticalBound = ((h / mMetersToPixelsY - sBallDiameter) * 0.5f);
        }

        /**
         * Method that gets x and y coordinates from external remote control
         * and changes X and Y coordinates of particles
         * @param event
         */
        @Override
        public void onSensorChanged(SensorEvent event) {
            float x, y;
            String[] result;
            if (data != null) {
                /**
                 * Split the Data in 2 parts
                 */
                result = data.split(",");
                x = Float.parseFloat(result[0]);
                y = Float.parseFloat(result[1]);
                /**
                 * Changes to signs of x and y to make the controls more natural
                 */
                mSensorX = -x;
                mSensorY = y;
            }
        }

        /**
         * Method that actually draws the particles and circle onto the screen
         * Also contains logic when the particles and onTouch circle intersect each other.
         * This logic could be taken out of this function if u wanted to clean up the structure of this method.
         * @param canvas handles what to draw
         */
        @Override
        protected void onDraw(Canvas canvas) {
            float DistanceParticleCircle;
            brush.setColor(Color.BLACK);

            final ParticleSystem particleSystem = mParticleSystem;
            final long now = System.currentTimeMillis();
            final float sx = mSensorX;
            final float sy = mSensorY;

            particleSystem.update(sx, sy, now);
            final float xc = mXOrigin;
            final float yc = mYOrigin;
            final float xs = mMetersToPixelsX;
            final float ys = mMetersToPixelsY;


            for (int i = 0; i < particleSystem.actualCount; i++) {

                float x = xc + particleSystem.getPosX(i) * xs;
                float y = yc - particleSystem.getPosY(i) * ys;


                if(DoDrawCircle == true){

                    /**
                     * Radius in pixel (Variable sballDiameter is the Diameter in meter)
                     */
                    float particleR = (float)(mDstWidth/2);

                    /**
                     * Compute distance between both circles.
                     * Add particle Radius to particle X and Y coordinates to get the true center of the particles
                     */
                    float radiusSum = (((circleR) + (particleR)) * ((circleR) + (particleR)));
                    DistanceParticleCircle = (((x + particleR) - (circleX)) * ((x+ particleR) - (circleX))) + (((y+ particleR - circleY)) * ((y + particleR) - circleY));

                    /**
                     * Test if distance between circles is less than combined radius.
                     * If it is then circles intersect each other
                     */
                    if(DistanceParticleCircle <= radiusSum) {
                        /**
                         * Visual removal of particle
                         */
                        removeView(particleSystem.mBalls[i]);

                        /**
                         * logical removal of particle
                         */
                        for(int n = i; n < particleSystem.actualCount - 1; n++) {
                            particleSystem.mBalls[n] = particleSystem.mBalls[n+1];
                        }
                        particleSystem.actualCount -= 1;
                        publish("Partikelcount:" + String.valueOf(particleSystem.actualCount));
                    }
                }
                /**
                 * After removal of all particles -> recreate the activity
                 */
                if(particleSystem.actualCount == 0) {
                    recreate();
                }
                particleSystem.mBalls[i].setTranslationX(x);
                particleSystem.mBalls[i].setTranslationY(y);
            }

            //Condition to not draw circle at initial start of the app
            if(DoDrawCircle == true) {
                canvas.drawCircle(circleX, circleY, circleR, brush);
            }
            //and make sure to redraw asap
            invalidate();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }

    /**
     * Method to connect to broker for remote controlling
     */
    private void connect () {
        try {
            clientId = MqttClient.generateClientId();
            client = new MqttClient(BROKER, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            Log.d(TAG, "Connecting to broker: " + BROKER);
            client.connect(connOpts);
            Log.d(TAG, "Connected with broker: " + BROKER);
        } catch (MqttException me) {
            Log.e(TAG, "Reason: " + me.getReasonCode());
            Log.e(TAG, "Message: " + me.getMessage());
            Log.e(TAG, "localizedMsg: " + me.getLocalizedMessage());
            Log.e(TAG, "cause: " + me.getCause());
            Log.e(TAG, "exception: " + me);
        }
    }

    /**
     * Method that handles subscribing.
     * To get needed information for remote controlling of particles
     */
    private void subscribe() {
        try {
            client.subscribe(sub_topic, qos, new IMqttMessageListener() {
                @Override
                public void messageArrived(String topic, MqttMessage msg) throws Exception {
                    String message = new String(msg.getPayload());
                    Log.d(TAG, "Message with topic " + topic + " arrived: " + message);
                    //Zuweisung der Data
                    data = message;
                }
            });
            Log.d(TAG, "subscribed to topic " + sub_topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method for publishing a message
     * @param msg Message that gets parsed while publishing
     */
    private void publish(String msg) {
        MqttMessage message = new MqttMessage(msg.getBytes());
        message.setQos(qos);
        try {
            client.publish(pub_topic, message);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to disconnect from the broker
     */
    private void Disconnect() {
        try {
            client.unsubscribe(sub_topic);
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
        try {
            Log.d(TAG, "Disconnecting from broker");
            client.disconnect();
            Log.d(TAG, "Disconnected.");
        } catch (MqttException me) {
            Log.e(TAG, me.getMessage());
        }
    }
}
