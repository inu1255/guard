package cn.inu1255.guard;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        setContentView(R.layout.activity_main);
        refresh();
        ITool.init(this);
        final EditText editText = findViewById(R.id.editText);
        Switch back_window = findViewById(R.id.back_window);
        back_window.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ITool.applyMoveTop(MainActivity.this);
            }
        });
        Switch accessibility = findViewById(R.id.accessibility);
        accessibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ITool.openAccessibilitySetting(MainActivity.this);
            }
        });
        View button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ServiceTool.setGuardPackage(editText.getText().toString());
                if (ServiceTool.isConnected()) {
                    if (!ServiceTool.isAccessibilityOn())
                        Toast.makeText(MainActivity.this, "服务挂了，请重启", Toast.LENGTH_LONG).show();
                    else
                        ServiceTool.restart();
                } else
                    Toast.makeText(MainActivity.this, "请先开启辅助功能", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refresh() {
        final EditText editText = findViewById(R.id.editText);
        editText.setText(ServiceTool.getGuardPackage());
        Switch back_window = findViewById(R.id.back_window);
        back_window.setChecked(ITool.checkMoveTop(this));
        Switch accessibility = findViewById(R.id.accessibility);
        boolean ok = ITool.isAccessibilitySettingsOn(this);
        accessibility.setChecked(ok);
        if (ok) {
            if (!ServiceTool.isAccessibilityOn())
                Toast.makeText(this, "服务挂了，请重启", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }
}
