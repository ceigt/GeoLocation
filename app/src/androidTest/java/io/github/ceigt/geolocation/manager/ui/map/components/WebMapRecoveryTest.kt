package io.github.ceigt.geolocation.manager.ui.map.components

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.ceigt.geolocation.R
import io.github.ceigt.geolocation.data.MapProvider
import io.github.ceigt.geolocation.manager.ui.map.MapViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebMapRecoveryTest {
    @get:Rule val rule = ActivityScenarioRule(ComponentActivity::class.java)

    @Test fun changingProviderReplacesTheDisplayedWebView() {
        val provider = mutableStateOf(MapProvider.GOOGLE)
        val credential = mutableStateOf("")
        onActivity { activity ->
            val model = MapViewModel(activity.application)
            activity.setContent {
                MapViewContainer(model, provider.value, "", "", "", credential.value)
            }
        }
        awaitWebView()
        lateinit var original: WebView
        onActivity { original = findWebView(it.window.decorView)!!; credential.value = "test-credential" }
        awaitCondition { findWebView(it.window.decorView)?.let { view -> view !== original } == true }
        lateinit var changedCredential: WebView
        onActivity {
            changedCredential = findWebView(it.window.decorView)!!
            assertNull(original.parent)
            provider.value = MapProvider.AMAP
        }
        awaitCondition { findWebView(it.window.decorView)?.let { view -> view !== changedCredential } == true }
        onActivity { assertNull(changedCredential.parent) }
    }

    @Test fun rendererCrashRecreatesTheMapAndRepeatedCrashOffersRetry() {
        onActivity { activity ->
            val model = MapViewModel(activity.application)
            activity.setContent { MapViewContainer(model, MapProvider.GOOGLE, "", "", "", "") }
        }
        awaitWebView()
        lateinit var original: WebView
        onActivity {
            original = findWebView(it.window.decorView)!!
            original.loadUrl("chrome://crash")
        }
        awaitCondition { findWebView(it.window.decorView)?.let { view -> view !== original } == true }
        var retryText = ""
        onActivity {
            assertNull(original.parent)
            retryText = it.getString(R.string.map_retry)
            findWebView(it.window.decorView)!!.loadUrl("chrome://crash")
        }
        awaitCondition { findWebView(it.window.decorView) == null && !it.isFinishing }
        awaitCondition { clickText(it.window.decorView, retryText) }
        awaitWebView()
    }

    @Test fun deadRendererIsDetachedAndANewRendererCanLoad() {
        val gone = CountDownLatch(1)
        val loaded = CountDownLatch(1)
        val callbacks = WebMapCallbacks()
        lateinit var view: WebView
        lateinit var parent: FrameLayout
        lateinit var controller: WebMapController
        var replacement: WebView? = null
        onActivity { activity ->
            parent = FrameLayout(activity)
            view = WebView(activity)
            controller = WebMapController(view, callbacks)
            parent.addView(view)
            callbacks.onRendererGone = {
                assertNull(view.parent)
                assertFalse(callbacks.active)
                gone.countDown()
            }
            view.webViewClient = MapWebViewClient(controller, callbacks)
            view.loadUrl("chrome://crash")
        }
        try {
            assertTrue("Renderer exit was not handled", gone.await(20, TimeUnit.SECONDS))
            onActivity { activity ->
                controller.destroy()
                controller.markReady()
                controller.search("ignored after disposal")
                val fresh = WebView(activity)
                replacement = fresh
                parent.addView(fresh)
                fresh.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) { loaded.countDown() }
                }
                fresh.loadDataWithBaseURL(null, "<html>recovered</html>", "text/html", "UTF-8", null)
            }
            assertTrue("Replacement renderer could not load", loaded.await(20, TimeUnit.SECONDS))
        } finally {
            onActivity {
                controller.destroy()
                parent.removeAllViews()
                replacement?.destroy()
            }
        }
    }

    private fun onActivity(action: (ComponentActivity) -> Unit) { rule.scenario.onActivity(action) }

    private fun awaitWebView() = awaitCondition { findWebView(it.window.decorView) != null }

    private fun awaitCondition(condition: (ComponentActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var passed = false
            onActivity { passed = condition(it) }
            if (passed) return
            SystemClock.sleep(50)
        }
        fail("Map did not reach the expected state")
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun clickText(view: View, text: String): Boolean {
        if (view is ViewRootForTest) return clickText(view.semanticsOwner.unmergedRootSemanticsNode, text)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (clickText(view.getChildAt(index), text)) return true
            }
        }
        return false
    }

    private fun clickText(node: SemanticsNode, text: String): Boolean {
        if (node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true) {
            var target: SemanticsNode? = node
            while (target != null) {
                if (target.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true) return true
                target = target.parent
            }
        }
        return node.children.any { clickText(it, text) }
    }
}
