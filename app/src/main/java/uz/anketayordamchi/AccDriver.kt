package uz.anketayordamchi

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** AccessibilityNodeInfo'ning bir lahzalik nusxasi (UiNode). Daraxt har safar yangidan quriladi. */
class AccNode(val info: AccessibilityNodeInfo, override val parent: AccNode?) : UiNode {
    override val cls: String = info.className?.toString() ?: ""
    override val text: String = info.text?.toString() ?: ""
    override val desc: String = info.contentDescription?.toString() ?: ""
    override val hint: String = if (Build.VERSION.SDK_INT >= 26) info.hintText?.toString() ?: "" else ""
    override val stateDesc: String = if (Build.VERSION.SDK_INT >= 30) info.stateDescription?.toString() ?: "" else ""
    override val id: String = info.viewIdResourceName ?: ""
    override val checkable: Boolean = info.isCheckable
    override val checked: Boolean = info.isChecked
    override val selected: Boolean = info.isSelected
    override val clickable: Boolean = info.isClickable
    override val editable: Boolean = info.isEditable
    override val enabled: Boolean = info.isEnabled
    override val visible: Boolean = info.isVisibleToUser
    override val showingHint: Boolean = Build.VERSION.SDK_INT >= 26 && info.isShowingHintText
    override val scrollable: Boolean = info.isScrollable
    override val left: Int
    override val top: Int
    override val right: Int
    override val bottom: Int
    val kids = ArrayList<UiNode>()
    override val children: List<UiNode> get() = kids

    init {
        val r = Rect()
        info.getBoundsInScreen(r)
        left = r.left; top = r.top; right = r.right; bottom = r.bottom
    }
}

/** UiDriver'ning haqiqiy (Accessibility) amalga oshirilishi. Faqat tanlangan Census paketi bilan ishlaydi. */
class AccDriver(private val svc: DiagService) : UiDriver {

    private val main = Handler(Looper.getMainLooper())

    private fun build(info: AccessibilityNodeInfo, parent: AccNode?, out: MutableList<UiNode>, depth: Int): AccNode {
        val n = AccNode(info, parent)
        out.add(n)
        if (depth < 40 && out.size < 4000) {
            for (i in 0 until info.childCount) {
                val c = info.getChild(i) ?: continue
                n.kids.add(build(c, n, out, depth + 1))
            }
        }
        return n
    }

    override fun snapshot(): Snapshot? {
        val target = svc.targetPackage()
        if (target.isEmpty()) return null
        for (attempt in 0 until 3) {
            val roots = ArrayList<AccessibilityNodeInfo>()
            try {
                // windows: yuqoridagi oyna birinchi; asosiy oyna birinchi bo'lishi uchun teskari tartib.
                for (w in svc.windows.reversed()) {
                    val r = w.root ?: continue
                    if (r.packageName?.toString() == target) roots.add(r)
                }
            } catch (_: Exception) {
            }
            if (roots.isEmpty()) {
                val r = svc.rootInActiveWindow
                if (r != null && r.packageName?.toString() == target) roots.add(r)
            }
            if (roots.isNotEmpty()) {
                val out = ArrayList<UiNode>()
                for (r in roots) build(r, null, out, 0)
                return Snapshot(out)
            }
            try { Thread.sleep(300) } catch (_: InterruptedException) {}
        }
        return null
    }

    private fun info(n: UiNode): AccessibilityNodeInfo = (n as AccNode).info

    override fun click(n: UiNode): Boolean {
        var p: UiNode? = n
        var hops = 0
        val maxH = svc.resources.displayMetrics.heightPixels * 0.4
        while (p != null && hops < 4) {
            if (p.clickable && (hops == 0 || p.h < maxH)) {
                if (info(p).performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
            p = p.parent
            hops++
        }
        return false
    }

    override fun tap(n: UiNode): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val dm = svc.resources.displayMetrics
        val x = n.cx.coerceIn(1, dm.widthPixels - 2).toFloat()
        val y = n.cy.coerceIn(1, dm.heightPixels - 2).toFloat()
        // O'z panelimiz (STOP, KIRITISH...) va DIAG tugmasi ustiga bosmaslik uchun.
        if (svc.overlayContains(x.toInt(), y.toInt())) {
            svc.logLine("tap bekor qilindi: nuqta o'z panelimiz ustiga tushadi (${x.toInt()},${y.toInt()})")
            return false
        }
        val path = Path()
        path.moveTo(x, y)
        path.lineTo(x + 1f, y)
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 60)).build()
        val latch = CountDownLatch(1)
        val ok = BooleanArray(1)
        main.post {
            val started = svc.dispatchGesture(g, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { ok[0] = true; latch.countDown() }
                override fun onCancelled(d: GestureDescription?) { latch.countDown() }
            }, null)
            if (!started) latch.countDown()
        }
        latch.await(3, TimeUnit.SECONDS)
        sleep(300)
        return ok[0]
    }

    override fun setText(n: UiNode, v: String): Boolean {
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, v)
        return info(n).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    override fun focus(n: UiNode): Boolean {
        val i = info(n)
        return i.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
            i.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    }

    /** Zaxira usul: matnni vaqtincha buferga qo'yib, joylashtiradi. Faqat izoh/son kabi shaxsiy bo'lmagan matn uchun. */
    override fun paste(n: UiNode, v: String): Boolean {
        val i = info(n)
        try {
            val cm = svc.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ay", v))
        } catch (e: Exception) {
            svc.logLine("paste: bufer yozilmadi: $e")
            return false
        }
        i.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle()
        b.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
        b.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, i.text?.length ?: 0)
        i.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, b)
        return i.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    override fun reveal(n: UiNode): Boolean =
        info(n).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)

    override fun scroll(forward: Boolean, prefer: ((UiNode) -> Boolean)?): Boolean {
        val s = snapshot() ?: return false
        val cands = s.nodes.filter {
            (it.scrollable || it.cls.endsWith("ScrollView") || it.cls.endsWith("RecyclerView") || it.cls.endsWith("ListView")) && it.visible
        }
        val pool = if (prefer != null) cands.filter(prefer).ifEmpty { cands } else cands
        val best = pool.maxByOrNull { (it.right - it.left).toLong() * (it.bottom - it.top) } ?: return false
        val act = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return info(best).performAction(act)
    }

    override fun sleep(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }

    override fun stopped(): Boolean = svc.isStopped()

    override fun log(s: String) = svc.logLine(s)
}
