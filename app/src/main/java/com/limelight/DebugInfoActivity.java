package com.limelight;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.limelight.utils.DeviceUtils;
import com.limelight.v2.V2Theme;
import com.limelight.v2.ui.ThemedDialogs;
import com.limelight.v2.ui.V2Alert;
import com.limelight.v2.ui.V2Page;

import java.util.ArrayList;
import java.util.List;

public class DebugInfoActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView tx_gamepad_info;
    private Vibrator vibrator;
    private View bt_vibrator;
    private List<InputDevice> ids = new ArrayList<>();
    private Vibrator vibratorOnline;
    private View bt_vibrator_value;
    private int simulatedAmplitude = 220;
    // LavArtemis Dev: the screen in the v2 theme (null in the stable, which keeps its layout)
    private V2Page page;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tx_content;
        if (ThemedDialogs.enabled()) {
            tx_content = buildV2Page();
        }
        else {
            setContentView(R.layout.activity_axitest);

            tx_gamepad_info = findViewById(R.id.tx_game_pad_info);
            tx_content = findViewById(R.id.tx_content);
            bt_vibrator = findViewById(R.id.bt_vibrator);
            bt_vibrator_value = findViewById(R.id.bt_vibrator_value);
        }

        vibrator = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String kernelVersion = System.getProperty("os.version");
        StringBuffer sb = new StringBuffer();
        sb.append(getString(R.string.debug_info_android_version) + DeviceUtils.getSDKVersionName());
        sb.append("\t" + getString(R.string.debug_info_api_version) + Build.VERSION.SDK_INT);
        sb.append("\n" + getString(R.string.debug_info_kernel_version) + kernelVersion);
        sb.append("\n" + getString(R.string.debug_info_brand_model) + DeviceUtils.getManufacturer() + "\t-\t" + DeviceUtils.getModel());
        tx_content.setText(sb.toString());

        boolean hasVibrator = ((Vibrator) getSystemService(Context.VIBRATOR_SERVICE)).hasVibrator();
        String content = hasVibrator ? getString(R.string.debug_info_has_vibration_motor) : getString(R.string.debug_info_no_vibration_motor);
        setLabel(bt_vibrator, getString(R.string.debug_info_test_device_vibration, content));

        showSimlateAmp();
    }

    private void showSimlateAmp() {
        setLabel(bt_vibrator_value, getString(R.string.debug_info_vibration_amplitude, simulatedAmplitude));
    }

    private static void setLabel(View button, String text) {
        if (button instanceof com.limelight.v2.ui.PillButton) {
            ((com.limelight.v2.ui.PillButton) button).setText(text);
        }
        else {
            ((Button) button).setText(text);
        }
    }

    /**
     * The same buttons and texts as activity_axitest.xml, in the v2 theme: a header with back and
     * the title, the buttons keep their ids so onClick() does not change.
     */
    private TextView buildV2Page() {
        page = new V2Page(this, V2Theme.load(this), getString(R.string.title_debug_info), this::finish);
        LinearLayout c = page.content();
        LinearLayout row = page.buttonRow(c);
        bt_vibrator = button(row, R.id.bt_vibrator, getString(R.string.debug_info_test_device_vibration, ""));
        button(row, R.id.bt_update_gamepad, getString(R.string.debug_info_refresh_gamepad_list));
        button(row, R.id.bt_vibrator_gamepad, getString(R.string.debug_info_test_gamepad_rumble));
        LinearLayout row2 = page.buttonRow(c);
        bt_vibrator_value = button(row2, R.id.bt_vibrator_value, getString(R.string.debug_info_vibration_amplitude, simulatedAmplitude));
        button(row2, R.id.bt_vibrator_cancle, getString(R.string.debug_info_stop_vibration));
        TextView content = page.text(c, getString(R.string.debug_info_refresh_gamepad_info), true);
        tx_gamepad_info = page.text(c, "", false);
        setContentView(page.getView());
        return content;
    }

    private View button(LinearLayout row, int id, String label) {
        View b = page.button(row, label, false, this);
        b.setId(id);
        return b;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (page != null) {
            V2Page.onTouch(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (page != null) {
            V2Page.onMotion(ev);
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (page != null) {
            V2Page.onKey(event);
        }
        return super.dispatchKeyEvent(event);
    }

    /** setItems() of an AlertDialog, or the v2 list in LavArtemis Dev */
    private void choose(String[] items, DialogInterface.OnClickListener onPick) {
        if (page != null) {
            ThemedDialogs.list(this, getString(R.string.debug_info_please_choose), items, which -> onPick.onClick(null, which));
            return;
        }
        new AlertDialog.Builder(this).setItems(items, onPick).setTitle(getString(R.string.debug_info_please_choose)).create().show();
    }

    private void cancleRumble() {
        if (vibratorOnline != null) {
            vibratorOnline.cancel();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.bt_vibrator_cancle) {
            cancleRumble();
            return;
        }
        // Device Vibration
        if (v.getId() == R.id.bt_vibrator) {
            String[] titles = new String[]{getString(R.string.debug_info_simple_vibration), getString(R.string.debug_info_continuous_hd_vibration)};
            choose(titles, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                    switch (which) {
                        case 0:
                            vibrator.vibrate(1000);
                            break;
                        case 1:
                            rumble(vibrator);
                            break;
                    }
                }
            });
            return;
        }

        // Gamepad Vibration
        if (v.getId() == R.id.bt_vibrator_gamepad) {
            if (ids.isEmpty()) {
                Toast.makeText(DebugInfoActivity.this, getString(R.string.debug_info_no_gamepad_detected), Toast.LENGTH_LONG).show();
                return;
            }
            String[] strings = new String[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                strings[i] = ids.get(i).getName();
            }
            choose(strings, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (dialog != null) {
                        dialog.dismiss();
                    }
                    if (ids.get(which).getVibrator().hasVibrator()) {
                        String[] titles = new String[]{getString(R.string.debug_info_simple_vibration), getString(R.string.debug_info_continuous_hd_vibration)};
                        choose(titles, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which2) {
                                if (dialog != null) {
                                    dialog.dismiss();
                                }
                                switch (which2) {
                                    case 0:
                                        ids.get(which).getVibrator().vibrate(1000);
                                        break;
                                    case 1:
                                        cancleRumble();
                                        vibratorOnline = ids.get(which).getVibrator();
                                        rumble(vibratorOnline);
                                        break;
                                }
                            }
                        });
                    } else {
                        Toast.makeText(DebugInfoActivity.this, getString(R.string.debug_info_no_vibrator), Toast.LENGTH_SHORT).show();
                    }
                }
            });
            return;
        }

        // Refresh Gamepad Info
        if (v.getId() == R.id.bt_update_gamepad) {
            updateGamePad();
            return;
        }

        if (v.getId() == R.id.bt_vibrator_value) {
            SeekBar mSeekBar = getSeekBar();
            if (page != null) {
                mSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(page.theme.accent));
                mSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(page.theme.accent));
                new V2Alert(this, page.theme).title(getString(R.string.debug_info_set_amplitude))
                        .content(mSeekBar).button(getString(R.string.v2_ok), null).show();
                return;
            }
            AlertDialog.Builder editDialog = new AlertDialog.Builder(this);
            editDialog.setTitle(getString(R.string.debug_info_set_amplitude));
            editDialog.setView(mSeekBar);
            editDialog.create().show();
        }
    }

    private SeekBar getSeekBar() {
        SeekBar mSeekBar = new SeekBar(this);
        mSeekBar.setMax(255);
        mSeekBar.setProgress(simulatedAmplitude);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                simulatedAmplitude = progress;
                showSimlateAmp();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        return mSeekBar;
    }

    private void rumble(Vibrator vibrator) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{1000}, new int[]{simulatedAmplitude}, 0));
        } else {
            long pwmPeriod = 20;
            long onTime = (long) ((simulatedAmplitude / 255.0) * pwmPeriod);
            long offTime = pwmPeriod - onTime;
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build();
            vibrator.vibrate(new long[]{0, onTime, offTime}, 0, audioAttributes);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (vibratorOnline != null) {
            vibratorOnline.cancel();
        }
    }

    private void updateGamePad() {
        ids.clear();
        StringBuffer sb = new StringBuffer();
        sb.append("\n");
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice dev = InputDevice.getDevice(deviceId);
            int sources = dev.getSources();
            if (((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
                    || ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK)) {
                if (getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_X) != null &&
                        getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Y) != null) {
                    // This is a gamepad
                    ids.add(dev);
                    sb.append(getString(R.string.debug_info_name) + dev.getName());
                    sb.append("\n");
                    sb.append(getString(R.string.debug_info_sensors));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        String sensor = "";
                        if (dev.getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                            sensor += getString(R.string.debug_info_accelerometer);
                        }
                        if (dev.getSensorManager().getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
                            sensor += getString(R.string.debug_info_gyroscope);
                        }
                        if (sensor.length() == 0) {
                            sb.append(getString(R.string.debug_info_no_relevant_driver));
                        } else {
                            sb.append(sensor);
                        }
                        sb.append("\n");
                    } else {
                        sb.append(getString(R.string.debug_info_no_api_below_android12));
                        sb.append("\n");
                    }
                    sb.append(getString(R.string.debug_info_vid_pid) + dev.getVendorId() + "_" + dev.getProductId()
                            + "\t    [" + String.format("%04x", dev.getVendorId()) + "_" + String.format("%04x", dev.getProductId()) + "]");
                    sb.append("\n");
                    sb.append(getString(R.string.debug_info_vibration) + (dev.getVibrator().hasVibrator() ? getString(R.string.debug_info_supported) : getString(R.string.debug_info_not_supported)));
                    sb.append("\n");
                    sb.append(getString(R.string.debug_info_details) + "\n");
                    sb.append(dev.toString());
                    sb.append("\n");
                }
            }
        }
        tx_gamepad_info.setText(getString(R.string.debug_info_number_of_gamepads) + ids.size() + "\n" + sb.toString());
    }

    private static InputDevice.MotionRange getMotionRangeForJoystickAxis(InputDevice dev, int axis) {
        InputDevice.MotionRange range;

        // First get the axis for SOURCE_JOYSTICK
        range = dev.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
        if (range == null) {
            // Now try the axis for SOURCE_GAMEPAD
            range = dev.getMotionRange(axis, InputDevice.SOURCE_GAMEPAD);
        }

        return range;
    }
}
