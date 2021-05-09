package cn.inu1255.guard;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.lang.reflect.Method;

public class ITool {
    public static int height;
    public static int width = 0;
    public static int nav_height;
    public static int btn_height;

    public static void init(Context context) {
        if (width < 1) {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display defaultDisplay = windowManager.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            defaultDisplay.getRealMetrics(metrics);
            Resources resources = context.getResources();
            int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
            nav_height = resources.getDimensionPixelSize(resourceId);
            btn_height = getNavigationBarHeight(context);
            width = metrics.widthPixels;
            height = metrics.heightPixels;
        }
    }

    /**
     * 非全面屏下 虚拟键高度(无论是否隐藏)
     */
    public static int getNavigationBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    public static String getLauncherName(Context context) {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = context.getPackageManager().resolveActivity(intent, 0);
        if (res.activityInfo == null) {
            return "";
        }
        return res.activityInfo.packageName;
    }

    public static boolean open(Context context, String pkg) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) return false;
        context.startActivity(intent);
        return true;
    }

    public static boolean isMIUI() {
        return "Xiaomi".equals(Build.MANUFACTURER);
    }

    public static boolean checkMoveTop(Context context) {
        if (!ITool.isMIUI()) return true;
        if (context == null) return false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int op = 10021;
            try {
                Method method = ops.getClass().getMethod("checkOpNoThrow", new Class[]{int.class, int.class, String.class});
                Integer result = (Integer) method.invoke(ops, op, Process.myUid(), context.getPackageName());
                return result == AppOpsManager.MODE_ALLOWED;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
        return false;
    }

    public static boolean applyMoveTop(Context context) {
        if (context == null) return false;
        Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR");
        //ComponentName componentName = new ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
        //i.setComponent(componentName);
        i.putExtra("extra_pkgname", context.getPackageName());
        try {
            context.startActivity(i);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean openAccessibilitySetting(Context context) {
        try {
            context.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return true;
        } catch (Exception e) {
            context.startActivity(new Intent(Settings.ACTION_SETTINGS));
            return false;
        }
    }

    public static boolean isAccessibilitySettingsOn(Context context) {
        int accessibilityEnabled = 0;
        String pkg_name = context.getPackageName();
        final String service = pkg_name + "/" + AccessibilitySampleService.class.getCanonicalName().replace(pkg_name, "");
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getApplicationContext().getContentResolver(),
                    android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    context.getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessibilityService = mStringColonSplitter.next();

                    if (accessibilityService.replace("/" + pkg_name, "/").equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void openSelf(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
