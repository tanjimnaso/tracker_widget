package com.example.weighttrackerwidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

// This class receives Glance updates and is the entry point for the App Widget.
class WeightTrackerGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = WeightTrackerGlanceWidget
}

// Note: This requires adding the following to AndroidManifest.xml inside <application>:
// <receiver
//     android:name=".widget.WeightTrackerGlanceReceiver"
//     android:exported="true"
//     android:label="Weight Tracker Widget">
//     <intent-filter>
//         <action android:name="androidx.appwidget.action.APPWIDGET_UPDATE" />
//     </intent-filter>
//     <intent-filter>
//         <action android:name="com.example.weighttrackerwidget.ACTION_UPDATE_WIDGET" />
//     </intent-filter>
//     <meta-data
//         android:name="android.appwidget.provider"
//         android:resource="@xml/weight_tracker_app_widget_info" />
// </receiver>