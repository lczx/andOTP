/*
 * Copyright (C) 2015 Bruno Bierbaumer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.shadowice.flocke.andotp;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.zxing.client.android.Intents;
import com.google.zxing.integration.android.IntentIntegrator;

import org.shadowice.flocke.andotp.ItemTouchHelper.SimpleItemTouchHelperCallback;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private ArrayList<Entry> entries;
    private EntriesCardAdapter adapter;
    private FloatingActionButton fab;

    private Handler handler;
    private Runnable handlerTask;

    private static final int PERMISSIONS_REQUEST_CAMERA = 42;
    private static final int PERMISSIONS_REQUEST_WRITE_EXPORT = 24;

    private void doScanQRCode(){
        new IntentIntegrator(MainActivity.this)
                .setCaptureActivity(CaptureActivityAnyOrientation.class)
                .setOrientationLocked(false)
                .initiateScan();
    }

    private void scanQRCode(){
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            doScanQRCode();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
        }
    }

    private void showAbout() {
        View messageView = getLayoutInflater().inflate(R.layout.dialog_about, null, false);

        String versionName = "";
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        TextView versionText = (TextView) messageView.findViewById(R.id.about_version);
        versionText.setText(versionName);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.app_name);
        builder.setIcon(R.mipmap.ic_launcher);
        builder.setView(messageView);
        builder.create();
        builder.show();
    }

    private void doExportJSON() {
        boolean success = SettingsHelper.exportAsJSON(this);

        if (success)
            showSimpleSnackbar(R.string.msg_export_success);
    }

    private void exportJSON() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            doExportJSON();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_WRITE_EXPORT);
        }
    }

    private void exportJSONWithWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(getString(R.string.msg_security_warning))
                .setMessage(getString(R.string.msg_export_warning))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        exportJSON();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {}
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
       if(requestCode == PERMISSIONS_REQUEST_CAMERA) {
           if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
               doScanQRCode();
           } else {
               showSimpleSnackbar(R.string.msg_camera_permission);
           }
       } else if (requestCode == PERMISSIONS_REQUEST_WRITE_EXPORT) {
           if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
               doExportJSON();
           } else {
               showSimpleSnackbar(R.string.msg_storage_permissions);
           }
       } else {
           super.onRequestPermissionsResult(requestCode, permissions, grantResults);
       }
    }

    private void showSimpleSnackbar(int string_res) {
        Snackbar.make(fab, string_res, Snackbar.LENGTH_LONG).setCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                super.onDismissed(snackbar, event);

                if (entries.isEmpty()) {
                    showNoAccount();
                }
            }
        }).show();
    }

    private void showNoAccount(){
        Snackbar noAccountSnackbar = Snackbar.make(fab, R.string.no_accounts, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.button_add, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        scanQRCode();
                    }
                });
        noAccountSnackbar.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_launcher);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fab = (FloatingActionButton) findViewById(R.id.action_scan);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scanQRCode();
            }
        });

        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.progressBar);

        RecyclerView recList = (RecyclerView) findViewById(R.id.cardList);
        recList.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        recList.setLayoutManager(llm);

        entries = SettingsHelper.load(this);

        adapter = new EntriesCardAdapter(entries);
        recList.setAdapter(adapter);

        ItemTouchHelper.Callback callback = new SimpleItemTouchHelperCallback(adapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recList);

        adapter.setMoveEventCallback(new EntriesCardAdapter.MoveEventCallback() {
            @Override
            public void onMoveEventStart() {
                stopUpdater();
            }

            @Override
            public void onMoveEventStop() {
                startUpdater();

                SettingsHelper.store(getBaseContext(), entries);
            }
        });

        if(entries.isEmpty()){
            showNoAccount();
        }

        handler = new Handler();
        handlerTask = new Runnable()
        {
            @Override
            public void run() {
                int progress =  (int) (TOTPHelper.TOTP_DEFAULT_PERIOD - (System.currentTimeMillis() / 1000) % TOTPHelper.TOTP_DEFAULT_PERIOD) ;
                progressBar.setProgress(progress*100);

                ObjectAnimator animation = ObjectAnimator.ofInt(progressBar, "progress", (progress-1)*100);
                animation.setDuration(1000);
                animation.setInterpolator(new LinearInterpolator());
                animation.start();

                boolean change = false;
                for(int i =0;i < adapter.getItemCount(); i++){
                    change = adapter.getItem(i).updateOTP();
                }

                if (change)
                    adapter.notifyDataSetChanged();

                handler.postDelayed(this, 1000);
            }
        };
    }

    public void stopUpdater() {
        handler.removeCallbacks(handlerTask);
    }

    public void startUpdater() {
        handler.post(handlerTask);
    }

    @Override
    public void onResume() {
        super.onResume();
        startUpdater();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopUpdater();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == IntentIntegrator.REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            try {
                Entry e = new Entry(intent.getStringExtra(Intents.Scan.RESULT));
                e.setCurrentOTP(TOTPHelper.generate(e.getSecret(), e.getPeriod()));
                entries.add(e);
                SettingsHelper.store(this, entries);

                adapter.notifyDataSetChanged();

                Snackbar.make(fab, R.string.msg_account_added, Snackbar.LENGTH_LONG).show();
            } catch (Exception e) {
                Snackbar.make(fab, R.string.msg_invalid_qr_code, Snackbar.LENGTH_LONG).setCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar snackbar, int event) {
                        super.onDismissed(snackbar, event);

                        if(entries.isEmpty()){
                            showNoAccount();
                        }
                    }
                }).show();

                return;
            }
        }

        if(entries.isEmpty()){
            showNoAccount();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_export) {
            exportJSONWithWarning();

            return true;
        } else if (id == R.id.action_import) {
            SettingsHelper.importFromJSON(this);

            return true;
        } else if (id == R.id.action_about){
            showAbout();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}