package net.vhati.openuhs.androidreader;

import android.content.Intent;
import android.os.Bundle;
import androidx.core.content.IntentCompat;
import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Just switch to the downloader immediately.
        Intent intent = new Intent().setClass(this, DownloaderActivity.class);

        // Suppress a lint error (unrecognized IntentCompat flag) with a comment.
        //noinspection WrongConstant
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME);

        this.startActivity(intent);
        finish();
    }
}
