package cn.inu1255.guard;

import android.view.accessibility.AccessibilityNodeInfo;

public interface AccessibilityNodeCallback<T> {
    T onMethod(AccessibilityNodeInfo node, int id, String path, T parent);
}
