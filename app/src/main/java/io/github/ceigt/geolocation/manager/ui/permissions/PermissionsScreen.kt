package io.github.ceigt.geolocation.manager.ui.permissions

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import io.github.ceigt.geolocation.R
import io.github.ceigt.geolocation.manager.ui.navigation.Screen
import io.github.ceigt.geolocation.manager.ui.permissions.components.PermanentlyDeniedScreen
import io.github.ceigt.geolocation.manager.ui.permissions.components.PermissionRequestScreen

@Composable
fun PermissionsScreen(navController: NavController, permissionsViewModel: PermissionsViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity

    if (activity == null) {
        Text(stringResource(R.string.permissions_activity_error))
        return
    }

    val hasPermissions by permissionsViewModel.hasPermissions
    val permanentlyDenied by permissionsViewModel.permanentlyDenied
    val permissionsChecked by permissionsViewModel.permissionsChecked
    var automaticRequestStarted by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { _ ->
            permissionsViewModel.checkPermissions(context)
            if (!permissionsViewModel.hasPermissions.value) {
                permissionsViewModel.checkIfPermanentlyDenied(activity)
            }
        }
    )

    LaunchedEffect(Unit) {
        permissionsViewModel.checkPermissions(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionsViewModel.checkPermissions(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(permissionsChecked, hasPermissions, permanentlyDenied) {
        if (!permissionsChecked) return@LaunchedEffect

        if (hasPermissions) {
            navController.navigate(Screen.Map.route) {
                popUpTo(Screen.Permissions.route) { inclusive = true }
            }
        } else if (!permanentlyDenied && !automaticRequestStarted) {
            automaticRequestStarted = true
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    if (!permissionsChecked) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (!hasPermissions) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (permanentlyDenied) {
                    PermanentlyDeniedScreen(context)
                } else {
                    PermissionRequestScreen {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
            }
        }
    }
}
