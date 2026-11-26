package cn.edu.qau.timetable

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cn.edu.qau.timetable.data.qz.QzEndpoints.Target
import cn.edu.qau.timetable.notify.ReminderReceiver
import cn.edu.qau.timetable.ui.GradesScreen
import cn.edu.qau.timetable.ui.KaoyanFeature
import cn.edu.qau.timetable.ui.KaoyanHost
import cn.edu.qau.timetable.ui.MainViewModel
import cn.edu.qau.timetable.ui.SettingsScreen
import cn.edu.qau.timetable.ui.SyncScreen
import cn.edu.qau.timetable.ui.TimetableScreen
import cn.edu.qau.timetable.ui.TodayScreen
import cn.edu.qau.timetable.ui.motion.LocalMotionEnabled
import cn.edu.qau.timetable.ui.motion.QauMotion
import cn.edu.qau.timetable.ui.motion.motionOf
import cn.edu.qau.timetable.ui.motion.rememberMotionEnabled
import cn.edu.qau.timetable.ui.theme.QauTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var todayRequest by mutableIntStateOf(0)

    private val vm: MainViewModel by viewModels {
        val container = (application as QauApp).container
        viewModelFactory {
            initializer { MainViewModel(container.repo, container.reminders, container.silence, container.kaoyan) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ReminderReceiver.ensureChannel(this)
        if (savedInstanceState == null && intent.action == ACTION_SHOW_TODAY) todayRequest++

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            QauTheme(dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(LocalMotionEnabled provides rememberMotionEnabled()) {
                    AppRoot(vm, todayRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_SHOW_TODAY) todayRequest++
    }

    companion object {
        const val ACTION_SHOW_TODAY = "cn.edu.qau.timetable.SHOW_TODAY"
    }
}

internal enum class Tab(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val syncTarget: Target,
) {
    TIMETABLE("课表", Icons.Filled.DateRange, Icons.Outlined.DateRange, Target.TIMETABLE),
    TODAY("今日", Icons.Filled.Today, Icons.Outlined.Today, Target.TIMETABLE),
    GRADES("成绩", Icons.Filled.School, Icons.Outlined.School, Target.GRADES),
    KAOYAN("考研", Icons.Filled.AutoStories, Icons.Outlined.AutoStories, Target.TIMETABLE),

    // 保留枚举值，以便 Android 能从旧版保存的界面状态恢复。
    EXAMS("", Icons.Filled.DateRange, Icons.Outlined.DateRange, Target.TIMETABLE),
    ;

    companion object {
        fun available(hasKaoyan: Boolean = KaoyanFeature.hasKaoyan): List<Tab> =
            entries.filter { it != EXAMS && (it != KAOYAN || hasKaoyan) }
    }
}

private enum class Page { HOME, SETTINGS, SYNC }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: MainViewModel, todayRequest: Int) {
    val tabs = remember { Tab.available() }
    var selectedTab by rememberSaveable { mutableStateOf(Tab.TIMETABLE) }
    val tab = selectedTab.takeIf { it in tabs } ?: Tab.TIMETABLE
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var target by rememberSaveable { mutableStateOf(Target.TIMETABLE) }
    var settingsAdvanced by rememberSaveable { mutableStateOf(false) }
    val tabStates = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsStateWithLifecycle()

    fun openSync(syncTarget: Target) {
        target = syncTarget
        page = Page.SYNC
    }

    fun openSettings(advanced: Boolean = false) {
        settingsAdvanced = advanced
        page = Page.SETTINGS
    }

    LaunchedEffect(todayRequest) {
        if (todayRequest > 0) {
            selectedTab = Tab.TODAY
            page = Page.HOME
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            vm.clearToast()
        }
    }
    val motionEnabled = LocalMotionEnabled.current
    val scope = rememberCoroutineScope()
    val pageState = remember { SeekableTransitionState(page) }
    val pageTransition = rememberTransition(pageState, label = "App page")
    val spatial = motionOf(QauMotion.SlideSpatial)
    val fastSpatial = motionOf(QauMotion.SlideSpatialFast)
    val fade = motionOf(QauMotion.Effects)
    val fastFade = motionOf(QauMotion.EffectsFast)
    val scale = motionOf(QauMotion.SpatialFast)
    val iconScale = motionOf(QauMotion.ScaleSpringy)
    val layoutSign = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1

    LaunchedEffect(page) { pageState.animateTo(page) }
    PredictiveBackHandler(enabled = page != Page.HOME) { events ->
        // Home is the only parent of these two screens. Seek it underneath the outgoing page.
        try {
            events.collect { event ->
                if (motionEnabled) pageState.seekTo(event.progress, Page.HOME)
            }
            page = Page.HOME
        } catch (cancelled: CancellationException) {
            scope.launch { pageState.animateTo(page) }
            throw cancelled
        }
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        pageTransition.AnimatedContent(
            transitionSpec = {
                if (targetState == Page.HOME) {
                    ((fadeIn(fade) + scaleIn(scale, initialScale = 0.99f)) togetherWith
                        (slideOutHorizontally(spatial) { layoutSign * it / 3 } + fadeOut(fastFade)))
                        .apply { targetContentZIndex = -1f }
                } else {
                    (slideInHorizontally(spatial) { layoutSign * it / 3 } + fadeIn(fade)) togetherWith
                        (scaleOut(scale, targetScale = 0.99f) + fadeOut(fastFade))
                }
            },
        ) { visiblePage ->
            Scaffold(
                snackbarHost = { if (visiblePage == page) SnackbarHost(snackbar) },
                bottomBar = {
                    if (visiblePage == Page.HOME) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            tabs.forEach { item ->
                                val selected = tab == item
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTab = item },
                                    icon = {
                                        val zoom by animateFloatAsState(
                                            if (selected) QauMotion.SELECTED_ICON_SCALE else 1f,
                                            iconScale,
                                            label = "Navigation selection",
                                        )
                                        Icon(
                                            if (selected) item.selectedIcon else item.icon,
                                            null,
                                            Modifier.graphicsLayer {
                                                scaleX = zoom
                                                scaleY = zoom
                                            },
                                        )
                                    },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    when (visiblePage) {
                        Page.SETTINGS, Page.SYNC -> Column(Modifier.fillMaxSize()) {
                            TopAppBar(
                                title = { Text(if (visiblePage == Page.SETTINGS) "设置" else "教务同步") },
                                navigationIcon = {
                                    IconButton(onClick = { page = Page.HOME }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                                    }
                                },
                                windowInsets = WindowInsets(0, 0, 0, 0),
                            )
                            if (visiblePage == Page.SETTINGS) SettingsScreen(vm, openAdvanced = settingsAdvanced)
                            else SyncScreen(vm, initialTarget = target)
                        }
                        Page.HOME -> AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val direction = if (targetState.ordinal > initialState.ordinal) layoutSign else -layoutSign
                                (slideInHorizontally(fastSpatial) { direction * it / 16 } + fadeIn(fade)) togetherWith
                                    (slideOutHorizontally(fastSpatial) { -direction * it / 16 } + fadeOut(fastFade))
                            },
                            label = "Main tab",
                        ) { visibleTab ->
                            tabStates.SaveableStateProvider(visibleTab.name) {
                                val sync = { openSync(visibleTab.syncTarget) }
                                Column(Modifier.fillMaxSize()) {
                                    if (visibleTab != Tab.TIMETABLE && visibleTab != Tab.EXAMS) {
                                        TopAppBar(
                                            title = { Text(visibleTab.label, style = MaterialTheme.typography.titleLarge) },
                                            actions = {
                                                if (visibleTab != Tab.KAOYAN) {
                                                    FilledTonalIconButton(onClick = sync) {
                                                        Icon(Icons.Filled.Sync, "同步" + visibleTab.syncTarget.label)
                                                    }
                                                }
                                                IconButton(onClick = { openSettings() }) {
                                                    Icon(Icons.Filled.Settings, "设置")
                                                }
                                            },
                                            windowInsets = WindowInsets(0, 0, 0, 0),
                                        )
                                    }
                                    when (visibleTab) {
                                        Tab.TIMETABLE, Tab.EXAMS -> TimetableScreen(
                                            vm,
                                            onSync = sync,
                                            onSettings = { openSettings() },
                                        )
                                        Tab.TODAY -> TodayScreen(vm, onSync = sync)
                                        Tab.GRADES -> GradesScreen(vm, onSync = sync)
                                        Tab.KAOYAN -> KaoyanHost(
                                            vm,
                                            Modifier.fillMaxSize(),
                                            onOpenSettings = { openSettings(advanced = true) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
