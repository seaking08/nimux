package de.muenchen.appcenter.nimux

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.get
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.navigationrail.NavigationRailView
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import de.muenchen.appcenter.nimux.util.UserSessionManager
import de.muenchen.appcenter.nimux.util.faceRecognitionPrefKey
import de.muenchen.appcenter.nimux.util.hideKeyboard
import de.muenchen.appcenter.nimux.util.standbyBoolPrefKey
import de.muenchen.appcenter.nimux.util.systemColorPrefKey
import de.muenchen.appcenter.nimux.util.systemThemePrefKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.sqrt

//Todo: Bugfix ohne Internet, Bugfix Übersetzung nicht vorhanden
//Todo: Settingsrework -->1 Geld Feature nur für admin
//Todo: Warehouse System in Kombination mit Produkt Anzahl, Ausbuchen,...

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration

    @Inject
    lateinit var sessionManager: UserSessionManager
    private lateinit var auth: FirebaseAuth
    private lateinit var timer: CountDownTimer
    val countDownTime = 45000
    private lateinit var navController: NavController
    private var isLoggedInState: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareEnterTransition()

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val selectedColor = sharedPrefs.getString(systemColorPrefKey, "default")
        isRailExpanded = savedInstanceState?.getBoolean("rail_expanded") ?: false

        when (selectedColor) {
            "green" -> setTheme(R.style.AppTheme_Green)
            "purple" -> setTheme(R.style.AppTheme_Purple)
            "red" -> setTheme(R.style.AppTheme_Red)
            "orange" -> setTheme(R.style.AppTheme_Orange)
            "blue" -> setTheme(R.style.AppTheme_Blue)
            else -> setTheme(R.style.AppTheme)
        }

        super.onCreate(savedInstanceState)

        val selectedTheme =
            sharedPrefs.getInt(systemThemePrefKey, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(selectedTheme)

        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        auth = FirebaseAuth.getInstance()

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        timer = object : CountDownTimer(countDownTime.toLong(), 1000) {
            override fun onTick(p0: Long) {
                Timber.d(p0.toString())
                if (p0 in 9001..9999) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.standby_ten_seconds_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFinish() {
                startActivity(Intent(this@MainActivity, InActivity::class.java))
            }
        }

        val currentUser = auth.currentUser
        val tenantId = sessionManager.getTenantId()
        updateUIState(currentUser != null && tenantId != null)

        goFullScreen()

        findViewById<View>(R.id.main_act_content).setOnApplyWindowInsetsListener { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsets.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom
            }
            windowInsets
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val metrics = resources.displayMetrics
        val yInches = metrics.heightPixels / metrics.ydpi
        val xInches = metrics.widthPixels / metrics.xdpi
        val diagonalInches = sqrt((xInches * xInches + yInches * yInches).toDouble())
        if (diagonalInches >= 7)
            menuInflater.inflate(R.menu.app_bar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        //val isLoggedIn =
        //    sessionManager.getTenantId() != null // user is logged in if tenant id is set

        //menu.findItem(R.id.menu_action_help)?.isVisible = isLoggedIn
        //menu.findItem(R.id.menu_action_feedback)?.isVisible = isLoggedIn

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // if the lock symbol inside the app is pressed, show InActivity and hide all system controls
            R.id.menu_action_lock -> {
                val intent = Intent(this@MainActivity, InActivity::class.java)
                startActivity(intent)
            }

            //R.id.menu_action_help -> {
            //    navController.navigate(R.id.helpFragment)
            //}

            // R.id.menu_action_feedback -> navController.navigate(R.id.nav_feedback)
        }
        return super.onOptionsItemSelected(item)
    }

    private var isRailExpanded = false

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("rail_expanded", isRailExpanded)
    }

    @SuppressLint("CutPasteId")
    private fun buildUI() {
        val metrics = resources.displayMetrics
        val yInches = metrics.heightPixels / metrics.ydpi
        val xInches = metrics.widthPixels / metrics.xdpi
        val diagonalInches = sqrt((xInches * xInches + yInches * yInches).toDouble())
        if (diagonalInches >= 7) {
            // 6.5inch device or bigger
            //val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
            val navGraph = navController.navInflater.inflate(R.navigation.mobile_navigation)
            if (PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(faceRecognitionPrefKey, false)
            ) {
                navGraph.setStartDestination(R.id.nav_home_auto)
            } else navGraph.setStartDestination(R.id.nav_home_manual)

            navController.graph = navGraph

            val appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_home_auto,
                    R.id.nav_home_manual,
                    R.id.nav_overview,
                    R.id.nav_statistics,
                    R.id.nav_user_store,
                    R.id.nav_manage_stuff,
                    R.id.nav_suggest_users,
                    R.id.nav_user_store,
                    R.id.nav_help,
                    R.id.nav_feedback,
                    R.id.nav_hub,
                    R.id.nav_warehouse
                )
            )

            setupActionBarWithNavController(navController, appBarConfiguration)
            val navRail = findViewById<NavigationRailView>(R.id.nav_view)
            
            val railHeader = navRail.getHeaderView()!!
            val btnMenu = railHeader.findViewById<ImageButton>(R.id.rail_menu_button)
            val fabBuchen = railHeader.findViewById<ExtendedFloatingActionButton>(R.id.fab_buchen)
            val logoText = railHeader.findViewById<TextView>(R.id.rail_logo_text)

            val railDelayMs = 80L
            val textFadeMs = 0L
            var pendingAction: Runnable? = null

            var lastMenuClickTime = 0L
            val menuClickCooldown = 250L

            fun applyRailState(expanded: Boolean) {
                pendingAction?.let { navRail.removeCallbacks(it) }
                logoText.animate().cancel()

                val parentLayout = navRail.parent as? ViewGroup
                parentLayout?.let {
                    val fastTransition = AutoTransition().apply {
                        duration = 200L
                        interpolator = FastOutSlowInInterpolator()
                    }
                    TransitionManager.beginDelayedTransition(it, fastTransition)
                }

                if (expanded) {
                    logoText.visibility = View.VISIBLE
                    btnMenu.setImageResource(R.drawable.outline_menu_open_24)

                    navRail.expand()
                    fabBuchen.extend()

                    logoText.animate()
                        .setStartDelay(100L)
                        .alpha(1f)
                        .setDuration(textFadeMs)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()

                } else {
                    btnMenu.setImageResource(R.drawable.ic_baseline_menu_24)

                    logoText.animate()
                        .setStartDelay(0L)
                        .alpha(0f)
                        .setDuration(textFadeMs / 2)
                        .setInterpolator(FastOutLinearInInterpolator())
                        .withEndAction {
                            logoText.visibility = View.GONE
                        }
                        .start()

                    navRail.collapse()

                    pendingAction = Runnable { fabBuchen.shrink() }
                    navRail.postDelayed(pendingAction, railDelayMs)
                }

                isRailExpanded = expanded
            }

            fabBuchen.post { applyRailState(isRailExpanded) }

            applyRailState(isRailExpanded)

            btnMenu.setOnClickListener {
                val currentTime = android.os.SystemClock.elapsedRealtime()
                if (currentTime - lastMenuClickTime > menuClickCooldown) {
                    lastMenuClickTime = currentTime
                    applyRailState(!isRailExpanded)
                }
            }

            fabBuchen.setOnClickListener {
                val sharedPref =
                    PreferenceManager.getDefaultSharedPreferences(this)
                val faceRecognitionEnabled =
                    sharedPref.getBoolean(faceRecognitionPrefKey, false)
                val destination =
                    if (faceRecognitionEnabled) R.id.nav_home_auto else R.id.nav_home_manual
                val options = NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, true, false)
                    .build()

                if (navController.currentDestination?.id != destination) {
                    navController.navigate(destination, null, options)
                }
            }

            fun syncChecked(menu: Menu, targetId: Int) {
                for (i in 0 until menu.size()) {
                    val m = menu.getItem(i)
                    m.subMenu?.let { syncChecked(it, targetId) }
                    if (m.isCheckable) m.isChecked = (m.itemId == targetId)
                }
            }

            navRail.setOnItemSelectedListener { item ->
                val options = if (item.itemId == R.id.nav_settings) null
                else NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, inclusive = true, saveState = false)
                    .build()

                if (navController.currentDestination?.id != item.itemId) {
                    navController.navigate(item.itemId, null, options)
                }
                navRail.post { syncChecked(navRail.menu, item.itemId) }
                true
            }

            //toDo: Rework concept
            val noSelectionDestinations = setOf(R.id.nav_home_auto, R.id.nav_home_manual)
            val parentItemFor = mapOf(
                R.id.manageUsersFragment to R.id.nav_manage_stuff,
                R.id.manageProductsFragment to R.id.nav_manage_stuff,
                R.id.suggestedUsersFragment to R.id.nav_suggest_users,
                R.id.suggUserPasswordFragment to R.id.nav_suggest_users,
                R.id.storeUserFragment to R.id.nav_user_store,
                R.id.helpFragment to R.id.nav_help,
                R.id.nav_feedback to R.id.nav_feedback,
                R.id.nav_hub to R.id.nav_hub
            )

            fun updateRailSelection(destinationId: Int) {
                when {
                    destinationId in noSelectionDestinations ->
                        navRail.post { syncChecked(navRail.menu, -1) }

                    navRail.menu.findItem(destinationId) != null ->
                        navRail.post { syncChecked(navRail.menu, destinationId) }

                    parentItemFor.containsKey(destinationId) ->
                        navRail.post {
                            syncChecked(
                                navRail.menu,
                                parentItemFor.getValue(destinationId)
                            )
                        }

                }
            }

            navController.addOnDestinationChangedListener { _, destination, _ ->
                timerRestart()
                updateRailSelection(destination.id)
            }

            navController.currentDestination?.id?.let { updateRailSelection(it) }
        } else {
            // smaller device
            val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
            val navView: NavigationView = findViewById(R.id.nav_view)
            appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.suggestedUsersFragment,
                    R.id.suggUserPasswordFragment,
                    R.id.nav_overview,
                    R.id.nav_statistics,
                    R.id.nav_user,
                    R.id.nav_products,
                    R.id.manageUsersFragment,
                    R.id.manageProductsFragment,
                    R.id.nav_history,
                    R.id.historyListFragment,
                    R.id.nav_user_store
                ), drawerLayout
            )

            setupActionBarWithNavController(navController, appBarConfiguration)
            navView.setupWithNavController(navController)

            navController.addOnDestinationChangedListener { _, destination, _ ->
                markCorrectNavDrawerItems(destination.id, navView)
                timerRestart()
            }
        }
        //TODO: check if necessary
        val db = Firebase.firestore
        db.enableNetwork()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (::appBarConfiguration.isInitialized) {
            val navController = findNavController(R.id.nav_host_fragment)
            hideKeyboard(this)
            return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
        }
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        Timber.d("MainActivity is started")
    }

    /**
     * If the user is not logged in, set a new navgraph and navigate to the SignInFragment.
     */
    private fun buildLogIn() {
        val navGraph = navController.navInflater.inflate(R.navigation.signin_navigation)
        navController.graph = navGraph
        setupActionBarWithNavController(navController)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        timerRestart()
    }

    override fun onResume() {
        super.onResume()

        val currentUser = auth.currentUser
        val tenantId = sessionManager.getTenantId()

        if (currentUser != null && tenantId != null) {
            validateTenant(currentUser.uid)
        } else {
            updateUIState(false)
        }

        timerRestart()
    }

    private fun validateTenant(uid: String) {
        lifecycleScope.launch {
            try {
                //Firebase data
                val firebaseResult = withContext(Dispatchers.IO) {
                    sessionManager.syncSessionInfoWithFirebase(uid)
                }
                val currentlyAssignedTenant = if (firebaseResult == "null") null else firebaseResult

                //Local Data
                val localResult = sessionManager.getTenantId()
                val localTenant = if (localResult == "null") null else localResult

                Timber.d("Firebase tenant: $currentlyAssignedTenant, Local tenant: $localTenant")

                //Log out if different
                if (currentlyAssignedTenant != null && localTenant != null && currentlyAssignedTenant != localTenant) {
                    Timber.w("Tenant mismatch! Logging out. (Firebase: $currentlyAssignedTenant, Local: $localTenant)")
                    forceLogout()
                } else {
                    updateUIState(true)
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to validate tenant")
            }
        }
    }

    private fun updateUIState(isLoggedIn: Boolean) {
        if (isLoggedInState == isLoggedIn) return

        isLoggedInState = isLoggedIn

        if (isLoggedIn) {
            Timber.d("User logged in → build UI")
            buildUIOnce()
        } else {
            Timber.d("User not logged in → show login")
            buildLoginOnce()
        }
    }

    private var uiBuilt = false
    private var loginBuilt = false

    private fun buildUIOnce() {
        if (uiBuilt) return
        uiBuilt = true
        loginBuilt = false

        buildUI()

        val currentUser = auth.currentUser
        val tenantId = sessionManager.getTenantId()

        Timber.d("User ${currentUser?.uid} (${currentUser?.email}) tenant: $tenantId")

        if (!sessionManager.hasAdminRole() && !sessionManager.hasAccessRole()) {
            navController.navigate(R.id.noAccessFragment)
        }
    }

    private fun buildLoginOnce() {
        if (loginBuilt) return
        loginBuilt = true
        uiBuilt = false
        buildLogIn()
    }

    private fun forceLogout() {
        sessionManager.clearSession()
        FirebaseAuth.getInstance().signOut()
        isLoggedInState = null
        uiBuilt = false
        loginBuilt = false
        updateUIState(false)
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun timerRestart() {
        timer.cancel()
        if (PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(standbyBoolPrefKey, false)
        )
            timer.start()
    }

    override fun onPause() {
        super.onPause()
        timer.cancel()
    }


    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun markCorrectNavDrawerItems(destId: Int, navView: NavigationView) {
        val overviewNum = 0
        val statisticNum = 1
        val suggestionNum = 2
        val manageUserNum = 3
        val manageProdNum = 4
        val historyNum = 5
        val storeNum = 6
        val settingsNum = 7

        when (destId) {

            R.id.suggUserPasswordFragment -> navView.menu[suggestionNum].isChecked = true
            R.id.suggestedUsersFragment -> navView.menu[suggestionNum].isChecked = true
            R.id.suggestedUserManageFragment -> navView.menu[suggestionNum].isChecked = true

            R.id.nav_overview -> navView.menu[overviewNum].isChecked = true

            R.id.nav_statistics -> navView.menu[statisticNum].isChecked = true

            R.id.nav_user -> navView.menu[manageUserNum].isChecked = true
            R.id.manageUsersFragment -> navView.menu[manageUserNum].isChecked = true
            R.id.manageUserItem -> navView.menu[manageUserNum].isChecked = true
            R.id.addUserFragment -> navView.menu[manageUserNum].isChecked = true

            R.id.nav_products -> navView.menu[manageProdNum].isChecked = true
            R.id.manageProductsFragment -> navView.menu[manageProdNum].isChecked = true
            R.id.productItemFragment -> navView.menu[manageProdNum].isChecked = true
            R.id.editProductFragment -> navView.menu[manageProdNum].isChecked = true
            R.id.addProductFragment -> navView.menu[manageProdNum].isChecked = true

            R.id.nav_history -> navView.menu[historyNum].isChecked = true
            R.id.historyListFragment -> navView.menu[historyNum].isChecked = true

            R.id.nav_user_store -> navView.menu[storeNum].isChecked = true

            R.id.nav_settings -> navView.menu[settingsNum].isChecked = true
        }
    }

    private fun prepareEnterTransition() {
        findViewById<View>(android.R.id.content).transitionName =
            "inactivity_to_main_transition"
        window.sharedElementEnterTransition = MaterialFadeThrough().apply {
            addTarget(android.R.id.content)
            duration = resources.getInteger(R.integer.motion_medium).toLong()
            interpolator = FastOutSlowInInterpolator()
        }
    }
}