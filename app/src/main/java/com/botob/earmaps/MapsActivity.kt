package com.botob.earmaps

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.widget.Toast
import com.botob.earmaps.speech.EarAutomaticSpeechRecognizer
import com.here.android.mpa.common.*
import com.here.android.mpa.mapping.Map
import com.here.android.mpa.mapping.MapFragment
import com.here.android.mpa.mapping.MapGesture
import com.here.android.mpa.mapping.MapMarker
import com.here.android.mpa.nlp.Error
import com.here.android.mpa.nlp.Nlp
import com.here.android.mpa.search.CategoryFilter
import com.here.android.mpa.search.PlaceLink
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Logger


class MapsActivity : AppCompatActivity(), OnEngineInitListener, PositioningManager.OnPositionChangedListener {
    companion object {
        /**
         * The logger object.
         */
        private val LOG: Logger = Logger.getLogger(MapsActivity::class.java.name)

        /**
         * The permissions request code.
         */
        private val REQUEST_CODE_ASK_PERMISSIONS = 1

        /**
         * The permissions that need to be explicitly requested from end user.
         */
        private val REQUIRED_SDK_PERMISSIONS = arrayOf<String>(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_CONTACTS)
    }

    /**
     * The map fragment.
     */
    private lateinit var mMapFragment: MapFragment

    /**
     * The natural language processing object.
     */
    private lateinit var mNlp: Nlp

    /**
     * the listener for search queries.
     */
    private var mSearchListener = object : Nlp.OnSearchListener {
        override fun onComplete(error: Error?, searchString: String?, whereString: String?,
                                nearString: String?, places: MutableList<PlaceLink>?) {
            LOG.info("onComplete: Search results are available")
            if (error != Error.NONE) {
                LOG.warning("Search error: $error")
            } else if (places != null) {
                var placesString = places.joinToString { place -> place.title }
                LOG.info("Found places: $placesString")
                for (place in places) {
                    mMapFragment.map.addMapObject(MapMarker(place.position, null))
                }
            }
        }

        override fun onStart(p0: CategoryFilter?, p1: GeoBoundingBox?) {
            LOG.info("onStart: Search CATEGORY start event")
        }

        override fun onStart(p0: GeoCoordinate?) {
            LOG.info("onStart: Search REVERSE start event")
        }

        override fun onStart(p0: String?, p1: GeoBoundingBox?) {
            LOG.info("onStart: Search STRING start event")
        }

    }

    /**
     * Initializes stuff at activity creation.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
    }

    /**
     * Handles permission request result.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            REQUEST_CODE_ASK_PERMISSIONS -> {
                for (index in permissions.indices.reversed()) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        // Exit the app if one permission is not granted.
                        Toast.makeText(this, "Required permission '" + permissions[index]
                                + "' not granted, exiting", Toast.LENGTH_LONG).show()
                        finish()
                        return
                    }
                }
                initializeApp()
            }
        }
    }

    /**
     * Executes actions upon successful HERE map engine initialization.
     */
    override fun onEngineInitializationCompleted(error: OnEngineInitListener.Error?) {
        if (error != OnEngineInitListener.Error.NONE) {
            LOG.warning("Map initialization issue:" + error.toString())
        } else {
            initializeMapping()
        }
    }

    /**
     * Executes actions upon position fix changed.
     */
    override fun onPositionFixChanged(locationMethod: PositioningManager.LocationMethod?, locationStatus: PositioningManager.LocationStatus?) {
        // NA.
    }

    /**
     * Executes actions upon position updated.
     */
    override fun onPositionUpdated(locationMethod: PositioningManager.LocationMethod?, geoPosition: GeoPosition?, mapMatched: Boolean) {
        mMapFragment.map.setCenter(geoPosition?.coordinate, Map.Animation.LINEAR)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyActivatesSpeech(keyCode)) {
            // Just consume the event and listen to voice
            mNlp.startListening()
            return true
        } else super.onKeyUp(keyCode, event)
    }

    /**
     * Checks HW keys to activate the Speech Recognition Mode.
     *
     * @param keyCode specified Key code
     * @return TRUE if key activates speech
     */
    private fun keyActivatesSpeech(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP ->
                // Volume UP key triggers Nlp to listen to
                // voice input
                return true
            else -> return false
        }
    }

    /**
     * Checks the dynamically-controlled permissions and requests missing permissions from end user.
     */
    private fun checkPermissions() {
        val missingPermissions = ArrayList<String>()
        // check all required dynamic permissions
        for (permission in REQUIRED_SDK_PERMISSIONS) {
            val result = ContextCompat.checkSelfPermission(this, permission)
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
            }
        }
        if (!missingPermissions.isEmpty()) {
            val permissions = missingPermissions
                    .toTypedArray()
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS)
        } else {
            val grantResults = IntArray(REQUIRED_SDK_PERMISSIONS.size)
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED)
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                    grantResults)
        }
    }

    /**
     * Initializes the app.
     */
    private fun initializeApp() {
        setContentView(R.layout.activity_maps)
        mMapFragment = fragmentManager
                .findFragmentById(R.id.map) as MapFragment
        mMapFragment.init(this)
    }

    /**
     * Initializes the mapping related components.
     */
    private fun initializeMapping() {
        initializePositioning()
        initializeNlp()
        mMapFragment.mapGesture.addOnGestureListener(object : MapGesture.OnGestureListener.OnGestureListenerAdapter() {
            override fun onPanStart() {
                PositioningManager.getInstance().stop()
                PositioningManager.getInstance().removeListener(this@MapsActivity)
            }
        }, 1, true)
    }

    /**
     * Initializes the positioning via HERE.
     */
    private fun initializePositioning() {
        mMapFragment.positionIndicator.isVisible = true
        mMapFragment.positionIndicator.isAccuracyIndicatorVisible = true
        PositioningManager.getInstance().addListener(WeakReference(this))
        PositioningManager.getInstance().start(PositioningManager.LocationMethod.GPS_NETWORK_INDOOR)
    }

    /**
     * Initializes the NLP objects.
     */
    private fun initializeNlp() {
        mNlp = Nlp.getInstance()
        val speechRecognizer = EarAutomaticSpeechRecognizer(applicationContext, mNlp)
        val listener = Nlp.OnInitializationListener { error ->
            if (error == Error.NONE) {
                mNlp.speak("Nlp initialized successfully")
                mNlp.isTalkBackEnabled = true
                mNlp.speechVolume = 33
                mNlp.isRepeatAfterMeEnabled = true

                // Add listeners.
                mNlp.addListener(mSearchListener)
            }
        }
        mNlp.init(this@MapsActivity, mMapFragment, null, speechRecognizer, listener)
    }

}
