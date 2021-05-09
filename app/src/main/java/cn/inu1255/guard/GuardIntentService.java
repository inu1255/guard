package cn.inu1255.guard;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;

public class GuardIntentService extends IntentService {
    public GuardIntentService() {
        super("GuardIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            ServiceTool.setGuardPackage(intent.getStringExtra("pkg"));
            ServiceTool.guard_accessibility_name = intent.getStringExtra("name");
            ServiceTool.guard_check_duration = intent.getIntExtra("duration", ServiceTool.guard_check_duration);
            if (intent.getBooleanExtra("restart", false)) {
                ServiceTool.restart();
            }
        }
    }

    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private void handleActionFoo(String param1, String param2) {
        // TODO: Handle action Foo
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Handle action Baz in the provided background thread with the provided
     * parameters.
     */
    private void handleActionBaz(String param1, String param2) {
        // TODO: Handle action Baz
        throw new UnsupportedOperationException("Not yet implemented");
    }
}