package com.platinum.ott

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.platinum.ott.core.ServiceLocator
import com.platinum.ott.core.platform.isTV
import com.platinum.ott.navigation.ZenithNavHost
import com.platinum.ott.ui.theme.ZenithBackground
import com.platinum.ott.ui.theme.ZenithTheme

class MainActivity : ComponentActivity() {
    // На Android 13+ (TIRAMISU) POST_NOTIFICATIONS — runtime-разрешение,
    // без явного запроса уведомления о новых сериях не показывались бы
    // никогда, даже с настроенным NotificationChannel и рабочим воркером.
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = this
        val isTv = isTV(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Раньше ориентация была зашита в AndroidManifest.xml как
        // android:screenOrientation="landscape" — статически, для ВСЕХ
        // устройств разом. Манифест не может различить ТВ и телефон на
        // этапе сборки — поэтому приложение всегда открывалось в landscape
        // на смартфоне тоже, хотя ZenithNavHost уже правильно выбирает
        // Phone-экраны (PhoneHomeScreen и т.д., портретный Material3) через
        // тот же isTV(). Единственное место, которое реально знает тип
        // устройства — рантайм, поэтому ориентация теперь выставляется здесь.
        requestedOrientation = if (isTv) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val intentUri = intent?.data
        val deepLinkMovieId = if (intentUri?.scheme == "zenith" && intentUri.host == "player")
            intentUri.getQueryParameter("id") else null
        val deepLinkDetailId = if (intentUri?.scheme == "zenith" && intentUri.host == "detail")
            intentUri.getQueryParameter("id") else null
        // zenith://series?id=... — раньше уведомление о новой серии просто
        // открывало главный экран без деталей (диплинка на сериал не
        // существовало). Теперь есть SeriesEpisodesScreen — ведём сразу туда.
        val deepLinkSeriesId = if (intentUri?.scheme == "zenith" && intentUri.host == "series")
            intentUri.getQueryParameter("id") else null
        // Раньше ACTION_SEARCH долетал сюда, но дальше никуда не вёл — в
        // приложении не было ни одного экрана поиска, чтобы передать
        // введённый текст. Теперь есть SearchScreen/PhoneSearchScreen.
        val searchQuery = if (intent?.action == Intent.ACTION_SEARCH)
            intent.getStringExtra(SearchManager.QUERY) else null
        val startDestination = when {
            deepLinkMovieId != null -> "player/$deepLinkMovieId"
            deepLinkDetailId != null -> "detail/$deepLinkDetailId"
            deepLinkSeriesId != null -> "series/$deepLinkSeriesId"
            !searchQuery.isNullOrBlank() -> "search?q=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}"
            ServiceLocator.checkAuthUseCase.execute() -> "home"
            else -> "setup"
        }
        setContent {
            // Раньше ZenithTheme брал только системную тему, пользовательский
            // переключатель в настройках ни на что не влиял. darkThemeFlow
            // готов уже в ServiceLocator.init() — до логина тоже.
            val darkTheme by ServiceLocator.darkThemeFlow.collectAsState()
            ZenithTheme(darkTheme = darkTheme) {
                ZenithNavHost(
                    startDestination = startDestination,
                    isTV = isTv,
                    modifier = Modifier.fillMaxSize().background(ZenithBackground)
                )
            }
        }
    }
}
