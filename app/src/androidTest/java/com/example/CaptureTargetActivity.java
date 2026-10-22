package com.example;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A separate app process; uses only Android classes so it runs without Peek's runtime. */
public class CaptureTargetActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(245, 247, 250));
        String[] labels = {"Other app", "A page to select", "Screen sharing stays active here"};
        for (int index = 0; index < labels.length; index++) {
            TextView text = new TextView(this);
            text.setText(labels[index]);
            text.setTextSize(26f);
            text.setTextColor(Color.rgb(20, 35, 55));
            text.setPadding(32, 60, 32, 60);
            if (index == 1) text.setBackgroundColor(Color.rgb(135, 220, 210));
            page.addView(text);
        }
        setContentView(page);
    }
}
