package com.aayushjagtap.pickupplay

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.aayushjagtap.pickupplay.data.SessionsViewModel
import com.aayushjagtap.pickupplay.data.cloud.CloudSyncManager
import com.aayushjagtap.pickupplay.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** Single activity that hosts the four fragment screens through the Navigation component. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private val sessionsViewModel: SessionsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Keep content clear of the status bar, navigation bar, display cutout and keyboard
        // in every orientation, so nothing is clipped or hidden behind system UI.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = maxOf(bars.bottom, ime.bottom),
            )
            WindowInsetsCompat.CONSUMED
        }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // A4: database errors (failed load or failed join/leave) are reported once, on any screen.
        sessionsViewModel.messages.observe(this) { event ->
            event.take()?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
        }

        // A4 Phase 2: cloud sync. Restores the Firebase sign-in from the last app session and,
        // if signed in, syncs in the background. Every cloud success/failure message is shown
        // here, on whatever screen is open.
        val cloud = CloudSyncManager.get(this)
        cloud.startOnce()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cloud.messages.collect { message ->
                    Snackbar.make(binding.root, message.resolve(this@MainActivity), Snackbar.LENGTH_LONG).show()
                }
            }
        }
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.main_menu, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                // Hidden on the account screen itself.
                menu.findItem(R.id.action_account)?.isVisible =
                    navController.currentDestination?.id != R.id.accountFragment
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
                R.id.action_account -> {
                    navController.navigate(R.id.action_global_account)
                    true
                }
                else -> false
            }
        }, this)
        navController.addOnDestinationChangedListener { _, _, _ -> invalidateOptionsMenu() }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
}
