/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.systemupdatersample.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.UpdateEngine;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.android.systemupdatersample.R;
import com.example.android.systemupdatersample.UpdateConfig;
import com.example.android.systemupdatersample.UpdateManager;
import com.example.android.systemupdatersample.UpdaterState;
import com.example.android.systemupdatersample.util.UpdateConfigs;
import com.example.android.systemupdatersample.util.UpdateEngineErrorCodes;
import com.example.android.systemupdatersample.util.UpdateEngineStatuses;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

/**
 * UI for SystemUpdaterSample app.
 */
public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";

    private TextView mTextViewBuild;
    private Spinner mSpinnerConfigs;
    private TextView mTextViewConfigsDirHint;
    private Button mButtonReload;
    private Button mButtonApplyConfig;
    private Button mButtonStop;
    private Button mButtonReset;
    private Button mButtonSuspend;
    private Button mButtonResume;
    private ProgressBar mProgressBar;
    private TextView mTextViewUpdaterState;
    private TextView mTextViewEngineStatus;
    private TextView mTextViewEngineErrorCode;
    private TextView mTextViewUpdateInfo;
    private Button mButtonSwitchSlot;

    private List<UpdateConfig> mConfigs;

    private final UpdateManager mUpdateManager =
            new UpdateManager(new UpdateEngine(), new Handler());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.mTextViewBuild = findViewById(R.id.textViewBuild);
        this.mSpinnerConfigs = findViewById(R.id.spinnerConfigs);
        this.mTextViewConfigsDirHint = findViewById(R.id.textViewConfigsDirHint);
        this.mButtonReload = findViewById(R.id.buttonReload);
        this.mButtonApplyConfig = findViewById(R.id.buttonApplyConfig);
        this.mButtonStop = findViewById(R.id.buttonStop);
        this.mButtonReset = findViewById(R.id.buttonReset);
        this.mButtonSuspend = findViewById(R.id.buttonSuspend);
        this.mButtonResume = findViewById(R.id.buttonResume);
        this.mProgressBar = findViewById(R.id.progressBar);
        this.mTextViewUpdaterState = findViewById(R.id.textViewUpdaterState);
        this.mTextViewEngineStatus = findViewById(R.id.textViewEngineStatus);
        this.mTextViewEngineErrorCode = findViewById(R.id.textViewEngineErrorCode);
        this.mTextViewUpdateInfo = findViewById(R.id.textViewUpdateInfo);
        this.mButtonSwitchSlot = findViewById(R.id.buttonSwitchSlot);

        this.mTextViewConfigsDirHint.setText(UpdateConfigs.getConfigsRoot(this));

        uiResetWidgets();
        loadUpdateConfigs();

        this.mUpdateManager.setOnStateChangeCallback(this::onUpdaterStateChange);
        this.mUpdateManager.setOnEngineStatusUpdateCallback(this::onEngineStatusUpdate);
        this.mUpdateManager.setOnEngineCompleteCallback(this::onEnginePayloadApplicationComplete);
        this.mUpdateManager.setOnProgressUpdateCallback(this::onProgressUpdate);
    }

    @Override
    protected void onDestroy() {
        this.mUpdateManager.setOnEngineStatusUpdateCallback(null);
        this.mUpdateManager.setOnProgressUpdateCallback(null);
        this.mUpdateManager.setOnEngineCompleteCallback(null);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "onResume - begin");
        super.onResume();
        // Binding to UpdateEngine invokes onStatusUpdate callback,
        // persisted updater state has to be loaded and prepared beforehand.
        this.mUpdateManager.bind();

        ZipFile zipFile;
        ZipEntry zipEntry;

        final String sdcard = Environment.getExternalStorageDirectory().getPath();
        final String updateZip = "ic421_update.zip";
        final String pathName = sdcard + "/" + updateZip;
        File file = new File(pathName);
//        File file = new File("/sdcard/ic421_update.zip");
//        File file = new File("/storage/emulated/0/ic421_update.zip");
        Log.i(TAG, "sdcard: " + sdcard);
        Log.i(TAG, "updateZip: " + updateZip);
        Log.i(TAG, "pathName: " + pathName);

//        内置SD卡路径：/storage/emulated/0
//        外置SD卡路径：/storage/extSdCard


        org.apache.commons.compress.archivers.zip.ZipFile apacheZipFile;
        ZipArchiveEntry zipArchiveEntry;
        InputStream inputStream;
        ZipArchiveInputStream zipArchiveInputStream;


        if (file.exists()) {
            Log.i(TAG, "File - exists");

            // headerKeyValuePairs
            try {
                zipFile = new ZipFile(file, Charset.defaultCharset());
                Log.i(TAG, "ZipFile: " + zipFile.getName());

                zipEntry = zipFile.getEntry("payload_properties.txt");
                if (null == zipEntry) {
                    Log.i(TAG, "payload_properties.txt is null");
                } else {
                    Log.i(TAG, "ZipEntry: " + zipEntry.getName());
                    inputStream = zipFile.getInputStream(zipEntry);
                    StringBuilder stringBuilder = new StringBuilder();
                    byte[] buffer = new byte[1024];
                    int read = 0;
                    while ((read = inputStream.read(buffer, 0, 1024)) >= 0) {
                        stringBuilder.append(new String(buffer, 0, read));
                    }
                    inputStream.close();


                    // TODO 核实需要传入的形式

                    /**
                     * 一个字串的形式
                     */
                    String header = stringBuilder.toString();
                    Log.i(TAG, "Header: " + stringBuilder.toString());
/*
2019-06-19 11:16:16.925 18725-18725/com.example.android.systemupdatersample I/MainActivity: Header: FILE_HASH=/D1pEWqr2At2ZkTSxYl5PsppC9Q+kDNwmiazHaNyYnk=
    FILE_SIZE=1294713948
    METADATA_HASH=RsYX8f1E/BfTLuYA0YPjNXZ6quPdp3zzuJVylcg1vtA=
    METADATA_SIZE=119474
*/

                    /**
                     * 每一行当做一个数组元素的形式
                     */
                    String[] headerKeyValuePairs = header.split("\\n");
                    Log.i(TAG, "array length: " + headerKeyValuePairs.length);
                    for (String kv : headerKeyValuePairs) {
                        Log.i(TAG, "kv: " + kv);
                    }
/*
2019-06-19 11:16:16.926 18725-18725/com.example.android.systemupdatersample I/MainActivity: array length: 4
2019-06-19 11:16:16.926 18725-18725/com.example.android.systemupdatersample I/MainActivity: kv: FILE_HASH=/D1pEWqr2At2ZkTSxYl5PsppC9Q+kDNwmiazHaNyYnk=
2019-06-19 11:16:16.926 18725-18725/com.example.android.systemupdatersample I/MainActivity: kv: FILE_SIZE=1294713948
2019-06-19 11:16:16.926 18725-18725/com.example.android.systemupdatersample I/MainActivity: kv: METADATA_HASH=RsYX8f1E/BfTLuYA0YPjNXZ6quPdp3zzuJVylcg1vtA=
2019-06-19 11:16:16.926 18725-18725/com.example.android.systemupdatersample I/MainActivity: kv: METADATA_SIZE=119474
*/
                }

            } catch (IOException e) {
                e.printStackTrace();
            }


            try {
                apacheZipFile = new org.apache.commons.compress.archivers.zip.ZipFile(file);
                Log.i(TAG, "ZipFile: " + apacheZipFile.toString());
                zipArchiveEntry = apacheZipFile.getEntry("payload.bin");

                if (null == zipArchiveEntry) {
                    Log.i(TAG, "payload.bin is null");
                } else {
                    Log.i(TAG, "ZipArchiveEntry: " + zipArchiveEntry.getName());
//                    inputStream = zipFile.getInputStream(zipEntry);
//                    zipArchiveInputStream = new ZipArchiveInputStream(inputStream,"UTF-8");
                    Log.i(TAG, "Offset: " + zipArchiveEntry.getDataOffset());
                    Log.i(TAG, "Size: " + zipArchiveEntry.getSize());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {
            Log.i(TAG, "File - null");
        }


/*
        ArrayList<String> list = new ArrayList<>();
        list.add("A");
        list.add("B");
        list.add("C");
        int listSize = list.size();
        Log.i(TAG, "listSize: " + listSize);

        // 使用泛型，无需显式类型转换
        String[] array = list.toArray(new String[0]);
        Log.i(TAG, "array[0]: " + array[0]);
*/

        Log.i(TAG, "onResume - end");
    }

    @Override
    protected void onPause() {
        this.mUpdateManager.unbind();
        super.onPause();
    }

    /**
     * reload button is clicked
     */
    public void onReloadClick(View view) {
        loadUpdateConfigs();
    }

    /**
     * view config button is clicked
     */
    public void onViewConfigClick(View view) {
        Log.i(TAG, "onViewConfigClick - begin");
        UpdateConfig config = mConfigs.get(mSpinnerConfigs.getSelectedItemPosition());
        new AlertDialog.Builder(this)
                .setTitle(config.getName())
                .setMessage(config.getRawJson())
                .setPositiveButton(R.string.close, (dialog, id) -> dialog.dismiss())
                .show();
        Log.i(TAG, "onViewConfigClick - end");
    }

    /**
     * apply config button is clicked
     */
    public void onApplyConfigClick(View view) {
        Log.i(TAG, "onApplyConfigClick - begin");
        new AlertDialog.Builder(this)
                .setTitle("Apply Update")
                .setMessage("Do you really want to apply this update?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                    uiResetWidgets();
                    uiResetEngineText();
                    applyUpdate(getSelectedConfig());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        Log.i(TAG, "onApplyConfigClick - end");
    }

    private void applyUpdate(UpdateConfig config) {
        Log.i(TAG, "applyUpdate - begin");
        try {
            // 我们做本地升级时没必要传想 config 这么复杂的参数
            // TODO 调整升级配置参数
            mUpdateManager.applyUpdate(this, config);
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to apply update " + config.getName(), e);
        }
        Log.i(TAG, "applyUpdate - end");
    }

    /**
     * stop button clicked
     */
    public void onStopClick(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Stop Update")
                .setMessage("Do you really want to cancel running update?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                    cancelRunningUpdate();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void cancelRunningUpdate() {
        try {
            mUpdateManager.cancelRunningUpdate();
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to cancel running update", e);
        }
    }

    /**
     * reset button clicked
     */
    public void onResetClick(View view) {
        new AlertDialog.Builder(this)
                .setTitle("Reset Update")
                .setMessage("Do you really want to cancel running update"
                        + " and restore old version?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.ok, (dialog, whichButton) -> {
                    resetUpdate();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void resetUpdate() {
        try {
            mUpdateManager.resetUpdate();
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to reset update", e);
        }
    }

    /**
     * suspend button clicked
     */
    public void onSuspendClick(View view) {
        try {
            mUpdateManager.suspend();
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to suspend running update", e);
        }
    }

    /**
     * resume button clicked
     */
    public void onResumeClick(View view) {
        try {
            uiResetWidgets();
            uiResetEngineText();
            mUpdateManager.resume();
        } catch (UpdaterState.InvalidTransitionException e) {
            Log.e(TAG, "Failed to resume running update", e);
        }
    }

    /**
     * switch slot button clicked
     */
    public void onSwitchSlotClick(View view) {
        uiResetWidgets();
        mUpdateManager.setSwitchSlotOnReboot();
    }

    /**
     * Invoked when SystemUpdaterSample app state changes.
     * Value of {@code state} will be one of the
     * values from {@link UpdaterState}.
     */
    private void onUpdaterStateChange(int state) {
        Log.i(TAG, "onUpdaterStateChange - begin");
        Log.i(TAG, "onUpdaterStateChange state: "
                + UpdaterState.getStateText(state)
                + "/" + state);
        runOnUiThread(() -> {
            setUiUpdaterState(state);

            if (state == UpdaterState.IDLE) {
                uiStateIdle();
            } else if (state == UpdaterState.RUNNING) {
                uiStateRunning();
            } else if (state == UpdaterState.PAUSED) {
                uiStatePaused();
            } else if (state == UpdaterState.ERROR) {
                uiStateError();
            } else if (state == UpdaterState.SLOT_SWITCH_REQUIRED) {
                uiStateSlotSwitchRequired();
            } else if (state == UpdaterState.REBOOT_REQUIRED) {
                uiStateRebootRequired();
            }
        });
        Log.i(TAG, "onUpdaterStateChange - end");
    }

    /**
     * Invoked when {@link UpdateEngine} status changes. Value of {@code status} will
     * be one of the values from {@link UpdateEngine.UpdateStatusConstants}.
     */
    private void onEngineStatusUpdate(int status) {
        Log.i(TAG, "onEngineStatusUpdate - begin");
        Log.i(TAG, "onEngineStatusUpdate - status: "
                + UpdateEngineStatuses.getStatusText(status)
                + "/" + status);
        runOnUiThread(() -> {
            setUiEngineStatus(status);
        });
        Log.i(TAG, "onEngineStatusUpdate - end");
    }

    /**
     * Invoked when the payload has been applied, whether successfully or
     * unsuccessfully. The value of {@code errorCode} will be one of the
     * values from {@link UpdateEngine.ErrorCodeConstants}.
     */
    private void onEnginePayloadApplicationComplete(int errorCode) {
        final String completionState = UpdateEngineErrorCodes.isUpdateSucceeded(errorCode)
                ? "SUCCESS"
                : "FAILURE";
        Log.i(TAG,
                "PayloadApplicationCompleted - errorCode="
                        + UpdateEngineErrorCodes.getCodeName(errorCode) + "/" + errorCode
                        + " " + completionState);
        runOnUiThread(() -> {
            setUiEngineErrorCode(errorCode);
        });
    }

    /**
     * Invoked when update progress changes.
     */
    private void onProgressUpdate(double progress) {
        mProgressBar.setProgress((int) (100 * progress));
    }

    /** resets ui */
    private void uiResetWidgets() {
        mTextViewBuild.setText(Build.DISPLAY);
        mSpinnerConfigs.setEnabled(false);
        mButtonReload.setEnabled(false);
        mButtonApplyConfig.setEnabled(false);
        mButtonStop.setEnabled(false);
        mButtonReset.setEnabled(false);
        mButtonSuspend.setEnabled(false);
        mButtonResume.setEnabled(false);
        mProgressBar.setEnabled(false);
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);
        mButtonSwitchSlot.setEnabled(false);
        mTextViewUpdateInfo.setTextColor(Color.parseColor("#aaaaaa"));
    }

    private void uiResetEngineText() {
        Log.i(TAG, "uiResetEngineText - begin");
        mTextViewEngineStatus.setText(R.string.unknown);
        mTextViewEngineErrorCode.setText(R.string.unknown);
        // Note: Do not reset mTextViewUpdaterState; UpdateManager notifies updater state properly.
        Log.i(TAG, "uiResetEngineText - end");
    }

    private void uiStateIdle() {
        uiResetWidgets();
        mButtonReset.setEnabled(true);
        mSpinnerConfigs.setEnabled(true);
        mButtonReload.setEnabled(true);
        mButtonApplyConfig.setEnabled(true);
        mProgressBar.setProgress(0);
    }

    private void uiStateRunning() {
        uiResetWidgets();
        mProgressBar.setEnabled(true);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
        mButtonStop.setEnabled(true);
        mButtonSuspend.setEnabled(true);
    }

    private void uiStatePaused() {
        uiResetWidgets();
        mButtonReset.setEnabled(true);
        mProgressBar.setEnabled(true);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
        mButtonResume.setEnabled(true);
    }

    private void uiStateSlotSwitchRequired() {
        uiResetWidgets();
        mButtonReset.setEnabled(true);
        mProgressBar.setEnabled(true);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
        mButtonSwitchSlot.setEnabled(true);
        mTextViewUpdateInfo.setTextColor(Color.parseColor("#777777"));
    }

    private void uiStateError() {
        uiResetWidgets();
        mButtonReset.setEnabled(true);
        mProgressBar.setEnabled(true);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
    }

    private void uiStateRebootRequired() {
        uiResetWidgets();
        mButtonReset.setEnabled(true);
    }

    /**
     * loads json configurations from configs dir that is defined in {@link UpdateConfigs}.
     */
    private void loadUpdateConfigs() {
        Log.i(TAG, "loadUpdateConfigs - begin");
        mConfigs = UpdateConfigs.getUpdateConfigs(this);
        loadConfigsToSpinner(mConfigs);
        Log.i(TAG, "loadUpdateConfigs - end");
    }

    /**
     * @param status update engine status code
     */
    private void setUiEngineStatus(int status) {
        Log.i(TAG, "setUiEngineStatus - begin");
        Log.i(TAG, "setUiEngineStatus - status: " + status);
        String statusText = UpdateEngineStatuses.getStatusText(status);
        mTextViewEngineStatus.setText(statusText + "/" + status);
        Log.i(TAG, "setUiEngineStatus - end");
    }

    /**
     * @param errorCode update engine error code
     */
    private void setUiEngineErrorCode(int errorCode) {
        String errorText = UpdateEngineErrorCodes.getCodeName(errorCode);
        mTextViewEngineErrorCode.setText(errorText + "/" + errorCode);
    }

    /**
     * @param state updater sample state
     */
    private void setUiUpdaterState(int state) {
        String stateText = UpdaterState.getStateText(state);
        mTextViewUpdaterState.setText(stateText + "/" + state);
    }

    private void loadConfigsToSpinner(List<UpdateConfig> configs) {
        Log.i(TAG, "loadConfigsToSpinner - begin");
        String[] spinnerArray = UpdateConfigs.configsToNames(configs);
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                spinnerArray);
        spinnerArrayAdapter.setDropDownViewResource(android.R.layout
                .simple_spinner_dropdown_item);
        mSpinnerConfigs.setAdapter(spinnerArrayAdapter);
        Log.i(TAG, "loadConfigsToSpinner - end");
    }

    private UpdateConfig getSelectedConfig() {
        Log.i(TAG, "getSelectedConfig - mSpinnerConfigs.getSelectedItemPosition(): " + mSpinnerConfigs.getSelectedItemPosition());
        return mConfigs.get(mSpinnerConfigs.getSelectedItemPosition());
    }

}
