package cn.inu1255.guard;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.view.accessibility.AccessibilityEvent;

public class AccessibilitySampleService extends AccessibilityService {

    private String launcherName;

    @Override
    public void onCreate() {
        super.onCreate();
        launcherName = ITool.getLauncherName(this);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        ServiceTool.setConnected(this);
        ITool.openSelf(this);
        ServiceTool.setGuardPackage(null);
        new Handler().postDelayed(() -> checkRunning(), 10000);
    }

    private void checkRunning() {
        ServiceTool.checkRunning();
        new Handler().postDelayed(() -> {
            checkRunning();
        }, 1000);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        CharSequence pkgName = event.getPackageName();
        CharSequence clsName = event.getClassName();
        if (pkgName != null && clsName != null) {
            for (String ignorePackage : ServiceTool.ignorePackages) {
                if (ignorePackage.equals(pkgName))
                    return; // 忽略系统UI事件
            }
        }
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {  // 窗口切换
            if (pkgName == null || clsName == null) return;
            if (event.getSource() == null && launcherName.equals(clsName.toString())) return;
            ServiceTool.setCurrentPackage(pkgName.toString());
            if (ServiceTool.currentPackage.equals(ServiceTool.getCurrentPackage()))
                ServiceTool.currentClass = clsName.toString();
        } else if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) { // 通知消息
            if (pkgName == null) return;
        } else { // 滚动、内容、点击

        }
    }

    @Override
    public void onInterrupt() {
    }
}
