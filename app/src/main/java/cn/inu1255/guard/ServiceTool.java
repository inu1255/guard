package cn.inu1255.guard;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ServiceTool {
    private static String TAG = "guard-service";
    public static String[] ignorePackages = {"com.android.systemui", "android"};
    public static String currentPackage;
    public static String currentClass;
    private static int accessibility_id = 1;
    private static int nodeId = 1;
    private static HashMap<Integer, AccessibilityNodeInfo> nodeMap = new HashMap<>();

    public static AccessibilitySampleService connected;
    private static Context c;
    private static String guard_package = "cn.inu1255.autoclick";
    private static long guard_package_running_at = 0;
    static String guard_accessibility_name = "米维";
    public static int guard_check_duration = 30000;
    private static boolean guard_restarting;
    private static long guard_restart_at;

    public static boolean isConnected() {
        return connected != null;
    }

    public static Context getContext() {
        return connected == null ? c : connected;
    }

    public static void setConnected(AccessibilitySampleService service) {
        connected = service;
        c = service;
    }

    public static boolean isHuawei() {
        return "HUAWEI".equals(Build.BRAND);
    }

    // region accessibility methods
    private static void bfs(AccessibilityNodeInfo root, AccessibilityNodeCallback cb) {
        if (connected == null) return;
        LinkedList<Object[]> queue = new LinkedList<>();
        boolean collect = root == null;
        if (root == null) {
            root = connected.getRootInActiveWindow();
            if (nodeId > 2000) {
                for (AccessibilityNodeInfo value : nodeMap.values()) {
                    try {
                        value.recycle();
                    } catch (Exception e) {
                    }
                }
                nodeId = 1;
            }
        }
        if (root == null) return;
        int accessibility_id = ServiceTool.accessibility_id;
        Object[] t = {root, "r", null};
        queue.push(t);
        HashSet<String> vset = new HashSet<>();
        HashSet<String> tset = new HashSet<>();
        CharSequence packageName = root.getPackageName();
        if (packageName == null) return;
        String pkg = packageName.toString();
        while (!queue.isEmpty()) {
            Object[] o = queue.pop();
            AccessibilityNodeInfo node = (AccessibilityNodeInfo) o[0];
            if (collect) nodeMap.put(nodeId, node);
            String path = (String) o[1];
            Object p = cb.onMethod(node, nodeId++, path, o[2]);
            if (p == WeFlags.BFS_SKIP) continue;
            else if (p == WeFlags.BFS_STOP) return;
            List<Object[]> list = new LinkedList<>();
            int n = node.getChildCount();
            int nc = 0;
            int ns = 0;
            for (int i = n - 1; i >= 0; i--) {
                if (ServiceTool.accessibility_id != accessibility_id) // 窗口已经切换，没必要获取了
                    return;
                AccessibilityNodeInfo c = node.getChild(i);
                if (c == null) continue;
                Rect rect = new Rect();
                node.getBoundsInScreen(rect);
                if (rect.right < 0 || rect.bottom < 0)
                    continue;
                CharSequence text = c.getText();
                CharSequence view = c.getViewIdResourceName();
                String path0 = null;
                String t0;
                if (text != null && !tset.contains(t0 = text.toString())) {
                    path0 = "T" + t0;
                    tset.add(t0);
                } else if (view != null && !vset.contains(t0 = view.toString())) {
                    t0 = t0.replaceFirst(pkg, "");
                    path0 = "V" + t0;
                    vset.add(t0);
                }
                if (c.isClickable()) nc++;
                if (c.isScrollable()) ns++;
                Object[] tmp = {c, i, p, path0};
                list.add(tmp);
            }
            for (Object[] tmp : list) {
                String path0 = (String) tmp[3];
                if (path0 == null) {
                    AccessibilityNodeInfo c = (AccessibilityNodeInfo) tmp[0];
                    if (c.isClickable() && nc == 1) path0 = path + ">c";
                    else if (c.isScrollable() && ns == 1) path0 = path + ">s";
                    else path0 = path + ">" + tmp[1];
                }
                tmp[1] = path0;
                queue.push(tmp);
            }
        }
    }

    private static void bfs(AccessibilityNodeCallback cb) {
        bfs(null, cb);
    }

    private static String getPathByNode(AccessibilityNodeInfo node) {
        if (connected == null) return "";
        AccessibilityNodeInfo root = node;
        AccessibilityNodeInfo p;
        while ((p = root.getParent()) != null) root = p;
        if (!root.equals(connected.getRootInActiveWindow())) return "";
        AccessibilityNodeInfo parent = node.getParent();
        if (parent == null)
            return "r";
        String pkg = node.getPackageName().toString();
        String view = node.getViewIdResourceName();
        String v = (view != null && pkg.length() < view.length()) ? view = view.replace(pkg, "") : view;
        CharSequence text = node.getText();
        String t0 = (text == null || text.length() < 1) ? null : text.toString();
        if (t0 != null && (t0.contains(">") || t0.contains("+"))) t0 = null;
        String t = t0;
        // root 直接找
        if (view != null) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(view);
            if (nodes.size() == 1) {
                return "v" + v;
            }
        }
        if (t != null) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(t);
            if (nodes.size() == 1) {
                return "t" + t;
            }
            AtomicInteger cnt = new AtomicInteger();
            if (nodes.size() < 1) {
                bfs((n, id, idx, pid) -> {
                    CharSequence s = n.getText();
                    if (s != null && t.equals(s.toString())) cnt.getAndIncrement();
                    return pid;
                });
            }
            if (cnt.get() == 1)
                return "T" + t;
        }
        // 通过父节点找
        if (view != null) {
            p = parent;
            String s = null;
            while (p != null) {
                List<AccessibilityNodeInfo> nodes = parent.findAccessibilityNodeInfosByViewId(view);
                if (nodes.size() == 1) {
                    String tmp = getPathByNode(parent) + ">v" + v;
                    if (s == null || s.length() > tmp.length())
                        s = tmp;
                } else {
                    break;
                }
                p = p.getParent();
            }
            if (s != null) return s;
        }
        int n = parent.getChildCount();
        int cn = 0;
        int sn = 0;
        int idx = -1;
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo c = parent.getChild(i);
            if (c == null) continue;
            if (c.isClickable()) cn++;
            if (c.isScrollable()) sn++;
            if (c.equals(node)) idx = i;
        }
        if (node.isClickable() && cn == 1) {
            return getPathByNode(parent) + ">c";
        }
        if (node.isScrollable() && sn == 1) {
            return getPathByNode(parent) + ">s";
        }
        return getPathByNode(parent) + ">" + idx;
    }

    private static AccessibilityNodeInfo getAccessibilityNodeByPath(AccessibilityNodeInfo node, String[] ss, int i, String pkg) {
        if (i >= ss.length || node == null) return node;
        AccessibilityNodeInfo tmpNode;
        int n;
        String s = ss[i];
        switch (s.charAt(0)) {
            case 'r':
                node = connected.getRootInActiveWindow();
                node = getAccessibilityNodeByPath(node, ss, i + 1, pkg);
                break;
            case 'v': // view包含
                String t = s.substring(1);
                AtomicReference<AccessibilityNodeInfo> tn = new AtomicReference<>();
                bfs(node, (info, id, idx, pid) -> {
                    CharSequence tmp = info.getViewIdResourceName();
                    if (tmp != null && tmp.toString().contains(t) && (pkg == null || pkg.equals(info.getPackageName().toString()))) {
                        info = getAccessibilityNodeByPath(info, ss, i + 1, pkg);
                        if (info != null) {
                            tn.set(info);
                            return WeFlags.BFS_STOP;
                        }
                    }
                    return id;
                });
                node = tn.get();
                break;
            case 't': // 文字正则
                t = s.substring(1);
                tn = new AtomicReference<>();
                bfs(node, (info, id, idx, pid) -> {
                    CharSequence nText = info.getText();
                    if (nText != null && nText.toString().matches(t) && (pkg == null || pkg.equals(info.getPackageName().toString()))) {
                        info = getAccessibilityNodeByPath(info, ss, i + 1, pkg);
                        if (info != null) {
                            tn.set(info);
                            return WeFlags.BFS_STOP;
                        }
                    }
                    return id;
                });
                node = tn.get();
                break;
            case 'x': // 文字view正则
                t = s.substring(1);
                tn = new AtomicReference<>();
                bfs(node, (info, id, idx, pid) -> {
                    CharSequence nText = info.getText();
                    CharSequence tmp = info.getViewIdResourceName();
                    if ((nText != null && nText.toString().matches(t) || tmp != null && tmp.toString().matches(t)) && (pkg == null || pkg.equals(info.getPackageName().toString()))) {
                        info = getAccessibilityNodeByPath(info, ss, i + 1, pkg);
                        if (info != null) {
                            tn.set(info);
                            return WeFlags.BFS_STOP;
                        }
                    }
                    return id;
                });
                node = tn.get();
                break;
            case 'V': // view完全匹配
                t = s.length() > 1 && (s.charAt(1) == ':') ? node.getPackageName().toString() + s.substring(1) : s.substring(1);
                tn = new AtomicReference<>();
                bfs(node, (info, id, idx, pid) -> {
                    CharSequence tmp = info.getViewIdResourceName();
                    if (tmp != null && tmp.toString().equals(t) && (pkg == null || pkg.equals(info.getPackageName().toString()))) {
                        info = getAccessibilityNodeByPath(info, ss, i + 1, pkg);
                        if (info != null) {
                            tn.set(info);
                            return WeFlags.BFS_STOP;
                        }
                    }
                    return id;
                });
                node = tn.get();
                break;
            case 'T': // 文字完全匹配
                t = s.substring(1);
                tn = new AtomicReference<>();
                bfs(node, (info, id, idx, pid) -> {
                    CharSequence nText = info.getText();
                    if (nText != null && t.equals(nText.toString()) && (pkg == null || pkg.equals(info.getPackageName().toString()))) {
                        info = getAccessibilityNodeByPath(info, ss, i + 1, pkg);
                        if (info != null) {
                            tn.set(info);
                            return WeFlags.BFS_STOP;
                        }
                    }
                    return id;
                });
                node = tn.get();
                break;
            case 'c': // 可点
                n = node.getChildCount();
                tmpNode = null;
                for (int j = 0; j < n; j++) {
                    AccessibilityNodeInfo c = node.getChild(j);
                    if (c == null) continue;
                    if (c.isClickable() && (pkg == null || pkg.equals(c.getPackageName().toString()))) {
                        tmpNode = c;
                        break;
                    }
                }
                node = getAccessibilityNodeByPath(tmpNode, ss, i + 1, pkg);
                break;
            case 's': // 可选择
                n = node.getChildCount();
                tmpNode = null;
                for (int j = 0; j < n; j++) {
                    AccessibilityNodeInfo c = node.getChild(j);
                    if (c == null) continue;
                    if (c.isScrollable() && (pkg == null || pkg.equals(c.getPackageName().toString()))) {
                        tmpNode = c;
                        break;
                    }
                }
                node = getAccessibilityNodeByPath(tmpNode, ss, i + 1, pkg);
                break;
            default:
                int j = Integer.parseInt(s);
                n = node.getChildCount();
                tmpNode = null;
                if (j < n) {
                    tmpNode = node.getChild(j);
                    if ((pkg != null && !pkg.equals(tmpNode.getPackageName().toString())))
                        tmpNode = null;
                }
                tmpNode = getAccessibilityNodeByPath(tmpNode, ss, i + 1, pkg);
                if (tmpNode == null) {
                    if (i + 1 < ss.length) {
                        for (int k = 0; k < n; k++) {
                            AccessibilityNodeInfo c = node.getChild(k);
                            if (c == null || k == j) continue;
                            tmpNode = getAccessibilityNodeByPath(c, ss, i + 1, pkg);
                            if (tmpNode != null) {
                                break;
                            }
                        }
                    }
                }
                node = tmpNode;
        }
        return node;
    }

    private static AccessibilityNodeInfo getAccessibilityNodeByPath(String path, String pkg) {
        if (connected == null) return null;
        AccessibilityNodeInfo root = connected.getRootInActiveWindow();
        if (root == null) return null;
        String[] ss = path.split(">");
        return getAccessibilityNodeByPath(root, ss, 0, pkg);
    }

    private static Map node2map(AccessibilityNodeInfo node, boolean withRect) {
        Map<String, Object> map = new HashMap<>();
        CharSequence text = node.getText();
        CharSequence pkg = node.getPackageName();
        String view = node.getViewIdResourceName();
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (text != null) map.put("text", text);
        if (view != null)
            map.put("view", view.length() > pkg.length() ? view.replace(pkg, "") : view);
        if (node.isCheckable()) map.put("clk", 1);
        if (withRect) {
            map.put("left", rect.left);
            map.put("right", rect.right);
            map.put("top", rect.top);
            map.put("bottom", rect.bottom);
        }
        map.put("pkg", pkg);
        CharSequence cls = node.getClassName();
        if (cls != null) map.put("cls", cls.toString());
        return map;
    }

    public static Map getNodeByPath(String path, String pkg) {
        AccessibilityNodeInfo node = getAccessibilityNodeByPath(path, pkg);
        if (node == null) return null;
        return (node2map(node, true));
    }

    private static boolean isSameRect(Rect a, Rect b) {
        return Math.abs(a.left - b.left) < 60 && Math.abs(a.top - b.top) < 60 && Math.abs(a.width() - b.width()) <= 60 && Math.abs(a.height() - b.height()) < 60;
    }

    private static boolean isSameNode(AccessibilityNodeInfo node, Rect rect) {
        Rect pr = new Rect();
        node.getBoundsInScreen(pr);
        return isSameRect(pr, rect);
    }

    private static AccessibilityNodeInfo findClickableNode(AccessibilityNodeInfo p, Rect rect, int depth) {
        if (p == null) return null;
        if (depth < 1) return null;
        if (p.isClickable()) return p;
        int n = p.getChildCount();
        for (int i = 0; i < n; i++) {
            AccessibilityNodeInfo child = p.getChild(i);
            if (child.isClickable() && isSameNode(child, rect)) {
                return child;
            }
        }
        return findClickableNode(p.getParent(), rect, depth - 1);
    }

    private static boolean click(AccessibilityNodeInfo node, int flag) {
        if (connected == null) return false;
        if (node == null) return false;
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (flag == 0) {
            AccessibilityNodeInfo p = findClickableNode(node, rect, 6);
            if (p != null) {
                p.getBoundsInScreen(rect);
                if (p.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
        }
        if ((flag & 2) == 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            node.getBoundsInScreen(rect);
            // AndroidN can click
            return clickXY(rect.centerX(), rect.centerY(), 100);
        }
        if ((flag & 1) == 0) {
            while (node != null && !node.isClickable()) node = node.getParent();
            if (node != null) {
                node.getBoundsInScreen(rect);
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
        return false;
    }

    public static boolean clickXY(int x, int y, int duration) {
        if (connected == null) return false;
        Path path = new Path();
        path.moveTo(x, y);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, duration);
            GestureDescription gestureDescription = builder.addStroke(stroke).build();
            return connected.dispatchGesture(gestureDescription, null, null);
        }
        return false;
    }

    public static int exit(int code) {
        System.exit(code);
        return 1;
    }

    public static boolean openAccessibilitySetting() {
        Context context = getContext();
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        try {
            context.startActivity(intent);
            return true;
        } catch (AndroidRuntimeException e) {
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isAccessibilityOn() {
        if (connected == null) return false;
        return performGlobalAction(100);
    }

    public static int disableAccessibility() {
        if (connected == null) return 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connected.disableSelf();
            return 0;
        }
        return 1;
    }

    public static String getCurrentPackage() {
        if (connected == null) return "";
        AccessibilityNodeInfo root = connected.getRootInActiveWindow();
        if (root == null) return currentPackage == null ? "" : currentPackage;
        return root.getPackageName().toString();
    }

    public static String getCurrentClass() {
        if (connected == null) return "";
        return currentClass == null ? "" : currentClass;
    }

    public static String getPathById(int id) {
        AccessibilityNodeInfo node = nodeMap.get(id);
        if (node == null) return "";
        return getPathByNode(node);
    }

    public static boolean clickById(int id, int flag) {
        AccessibilityNodeInfo node = nodeMap.get(id);
        if (node == null) return false;
        return click(node, flag);
    }

    public static boolean clickByPath(String path, String pkg, int flag) {
        AccessibilityNodeInfo node = getAccessibilityNodeByPath(path, pkg);
        if (node == null) return false;
        return click(node, flag);
    }

    public static boolean performGlobalAction(int action) {
        if (connected == null) return false;
        connected.performGlobalAction(action);
        return true;
    }

    public static boolean dispatchGesture(int[] ss, long startTime, long duration) {
        if (connected == null) return false;
        if (ss.length < 2 || (ss.length & 1) != 0) return false;
        Path path = new Path();
        path.moveTo((ss[0]), (ss[1]));
        for (int i = 2; i < ss.length; i += 2) {
            path.lineTo((ss[i]), (ss[i + 1]));
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, startTime, duration);
            GestureDescription gestureDescription = builder.addStroke(stroke).build();
            return connected.dispatchGesture(gestureDescription, null, null);
        }
        return false;
    }

    //endregion

    public static void setCurrentPackage(String pkg) {
        accessibility_id++;
        currentPackage = pkg;
    }

    public static void setGuardPackage(String pkg) {
        if (pkg != null) guard_package = pkg;
        guard_package_running_at = System.currentTimeMillis();
    }

    public static void stop() {
        Log.w(TAG, "开始清理");
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
        ITool.sleep(1000);
        // 清理任务
        String appName = getContext().getString(R.string.app_name);
        AtomicBoolean exist = new AtomicBoolean(true);
        AtomicBoolean isClearPage = new AtomicBoolean(false);
        int middle = ITool.width / 2;
        int n = 10;
        while (exist.get() && n-- > 0) {
            Log.w(TAG, "开始清理 n:" + n);
            exist.set(false);
            isClearPage.set(false);
            Rect rect = new Rect();
            if (isHuawei()) swapeRight();
            bfs((node, id, path, parent) -> {
                String view = node.getViewIdResourceName();
                if (view == null) return null;
                if (view.endsWith(":id/clear_all_recents_image_button")) isClearPage.set(true);
                if (view.endsWith(":id/clearAnimView")) isClearPage.set(true);
                if (exist.get()) return null;
                node.getBoundsInScreen(rect);
                if (rect.bottom > 0 && rect.right > 0) {
                    if (view.endsWith(":id/title") && !appName.equals(node.getText())) {
                        exist.set(true);
                    }
                }
                return null;
            });
            if (isClearPage.get() && exist.get()) {
                if (isHuawei()) {
                    int[] paths;
                    if (rect.centerX() < middle) {
                        paths = new int[]{rect.right - 50, ITool.height * 3 / 4, rect.right - 50, ITool.height / 4};
                    } else {
                        paths = new int[]{rect.left + 50, ITool.height * 3 / 4, rect.left + 50, ITool.height / 4};
                    }
                    dispatchGesture(paths, 0, 500);
                    ITool.sleep(1000);
                } else {
                    int[] paths;
                    if (rect.centerX() < middle) {
                        paths = new int[]{rect.centerX(), rect.centerY(), ITool.width - 50, rect.centerY()};
                    } else {
                        paths = new int[]{rect.centerX(), rect.centerY(), 50, rect.centerY()};
                    }
                    dispatchGesture(paths, 0, 500);
                    ITool.sleep(1000);
                }
            }
        }
    }

    public static void swapeRight() {
        int[] paths = {50, 960, 1000, 960};
        dispatchGesture(paths, 0, 500);
    }

    public static void swapeUp() {
        int[] paths = {540, 1344, 540, 576};
        dispatchGesture(paths, 0, 500);
    }

    public static void start() {
        if (TextUtils.isEmpty(guard_package)) return;
        if (!TextUtils.isEmpty(guard_accessibility_name)) {
            Log.w(TAG, "通过桌面打开程序:" + guard_package);
            performGlobalAction(2);
            ITool.sleep(1000);
            if (clickByPath("T" + guard_accessibility_name, null, 0)) {
                Log.w(TAG, "通过桌面打开程序成功");
            } else {
                Log.w(TAG, "通过桌面打开程序失败，尝试直接打开");
                ITool.open(getContext(), guard_package);
                ITool.sleep(2000);
            }
        } else {
            Log.w(TAG, "直接打开程序:" + guard_package);
            ITool.open(getContext(), guard_package);
            ITool.sleep(2000);
        }
        int n;
        n = 10;
        while (n-- > 0) {
            if (clickByPath("T允许", null, 0)) ;
            else if (clickByPath("T立即开始", null, 0)) ;
            else if (clickByPath("Vstart", null, 0)) break;
            ITool.sleep(1000);
        }
        Log.w(TAG, "点击立即开始:" + n);
        if (n <= 0) return;
        if (TextUtils.isEmpty(guard_accessibility_name)) return;
        ITool.sleep(1000);
        n = 10;
        while (n-- > 0) {
            AccessibilityNodeInfo node;
            if (isHuawei())
                node = getAccessibilityNodeByPath("Vandroid:id/action_bar_title", "com.android.settings");
            else
                node = getAccessibilityNodeByPath("Vmiui:id/action_bar_title", "com.android.settings");
            if (node != null && guard_accessibility_name.equals(node.getText()))
                break;
            if (clickByPath("T立即开始", null, 0)) ;
            else if (!"com.android.settings".equals(getCurrentPackage())) ;
            else if (clickByPath("T" + guard_accessibility_name, null, 0)) ;
            else if (clickByPath("T更多已下载的服务", null, 0)) ;
            else if (clickByPath("T已下载的服务", null, 0)) ;
            if (isHuawei()) {
                swapeUp();
            }
            ITool.sleep(500);
        }
        ITool.sleep(500);
        if (n > 0) {
            n = 10;
            while (n-- > 0) {
                if (!"com.android.settings".equals(getCurrentPackage())) ;
                else if (clickByPath("T确定", null, 0))
                    break;
                else if (clickByPath("T允许", null, 0))
                    break;
                else if (clickByPath("T开启服务", null, 0)) ;
                else if (clickByPath("T禁止", null, 0)) ;
                else if (clickByPath("T停止", null, 0)) ;
                else if (clickByPath("Vandroid:id/switch_widget", null, 0)) ;
                ITool.sleep(500);
            }
            ITool.sleep(500);
        }
        n = 10;
        while (n-- > 0 && "com.android.settings".equals(getCurrentPackage())) {
            performGlobalAction(1);
            ITool.sleep(500);
        }
    }

    static void restart() {
        Log.w(TAG, "开始重启");
        if (guard_restarting) {
            Log.e(TAG, "正在重启中，请忽重复请求");
            return;
        }
        if (guard_restart_at + 30000 > System.currentTimeMillis()) {
            Log.e(TAG, "重启过于频繁");
            return;
        }
        if (!isAccessibilityOn()) {
            return;
        }
        guard_restart_at = System.currentTimeMillis();
        guard_restarting = true;
        try {
            if (!ITool.isScreenOn(getContext()))
                ITool.wakeUpAndUnlock(getContext(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            stop();
            start();
            ITool.sleep(5000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        guard_restarting = false;
    }

    static void checkRunning() {
        if (guard_package_running_at + guard_check_duration < System.currentTimeMillis()) {
            Toast.makeText(getContext(), "米维未启动，开始启动", Toast.LENGTH_LONG).show();
            restart();
        }
    }

    public static String getGuardPackage() {
        return guard_package;
    }

    public static String getGuardTip() {
        if (guard_restart_at < 1) return "尚未守护";
        return "上次守护: " + (System.currentTimeMillis() - guard_restart_at) / 1000 + "秒前";
    }

    public static String getGuardTip1() {
        if (guard_package_running_at < 1) return "尚未运行";
        return "最近在线: " + (System.currentTimeMillis() - guard_package_running_at) / 1000 + "秒前";
    }
}
