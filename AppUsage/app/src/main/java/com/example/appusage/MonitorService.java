package com.example.appusage;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MonitorService extends Service {
    private boolean initialized = false;
    private final IBinder mBinder = new LocalBinder();
    private ServiceCallback callback = null;
    private Timer timer = null;
    private final Handler mHandler = new Handler();
    private String foreground = null;
    private ArrayList<HashMap<String, Object>> processList;
    private ArrayList<String> packages;
    private Date split = null;

    public static int SERVICE_PERIOD = 5000;

    private final ProcessList pl = new ProcessList(this) {
        @Override
        protected boolean isFilteredByName(String pack) {
            // TODO: filter processes by names, return true to skip the process
            // always return false (by default) to monitor all processes
            return false;
        }

    };

    public interface ServiceCallback {
        void sendResults(int resultCode, Bundle b);
    }

    public class LocalBinder extends Binder {
        MonitorService getService() {
            // Return this instance of the service so clients can call public methods
            return MonitorService.this;
        }
    }

    public MonitorService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        if (initialized) {
            return mBinder;
        }
        return null;
    }

    public void setCallback(ServiceCallback callback) {
        this.callback = callback;
    }

    private boolean addToStatistics(String target) {
        boolean changed = false;
        Date now = new Date();
        if (!TextUtils.isEmpty(target)) {
            if (!target.equals(foreground)) {
                int i;
                if (foreground != null && split != null) {
                    // TODO: calculate time difference from current moment
                    // to the moment when previous foreground process was activated
                    i = packages.indexOf(foreground);
                    long delta = (now.getTime() - split.getTime()) / 1000;
                    Long time = (Long) processList.get(i).get(ProcessList.COLUMN_PROCESS_TIME);
                    if (time != null) {
                        // TODO: add the delta to statistics of 'foreground'
                        time += delta;
                    } else {
                        time = new Long(delta);
                    }
                    processList.get(i).put(ProcessList.COLUMN_PROCESS_TIME, time);
                }

                // update count of process activation for new 'target'
                i = packages.indexOf(target);
                Integer count = (Integer) processList.get(i).get(ProcessList.COLUMN_PROCESS_COUNT);
                if (count != null) count++;
                else {
                    count = new Integer(1);
                }
                processList.get(i).put(ProcessList.COLUMN_PROCESS_COUNT, count);

                foreground = target;
                split = now;
                changed = true;
            }
        }
        return changed;
    }

    public void start() {
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new MonitoringTimerTask(), 500, SERVICE_PERIOD);
        }

        // TODO: startForeground(srvcid, createNotification(null));
    }

    public void stop() {
        timer.cancel();
        timer.purge();
        timer = null;
    }

    private class MonitoringTimerTask extends TimerTask {
        @Override
        public void run() {
            fillProcessList();

            ActivityManager activityManager = (ActivityManager) MonitorService.this.getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningTaskInfo> taskInfo = activityManager.getRunningTasks(1);
            String current = taskInfo.get(0).topActivity.getPackageName();

            // check if current process changed
            if (addToStatistics(current) && callback != null) {
                final Bundle b = new Bundle();
                // TODO: pass necessary info to UI via bundle
                mHandler.post(new Runnable() {
                    public void run() {
                        callback.sendResults(1, b);
                    }
                });
            }
        }
    }

    private void fillProcessList() {
        pl.fillProcessList(processList, packages);
    }

    public abstract class ProcessList {
        // process package name
        public static final String COLUMN_PROCESS_NAME = "process";

        // TODO: arbitrary property (can be user-fiendly name)
        public static final String COLUMN_PROCESS_PROP = "property";

        // number of times a process has been activated
        public static final String COLUMN_PROCESS_COUNT = "count";

        // number of seconds a process was in foreground
        public static final String COLUMN_PROCESS_TIME = "time";

        private ContextWrapper context;

        ProcessList(ContextWrapper context) {
            this.context = context;
        }

        protected abstract boolean isFilteredByName(String pack);

        public void fillProcessList(ArrayList<HashMap<String, Object>> processList, ArrayList<String> packages) {
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> procInfo = activityManager.getRunningAppProcesses();

            HashMap<String, Object> hm;
            final PackageManager pm = context.getApplicationContext().getPackageManager();

            for (int i = 0; i < procInfo.size(); i++) {
                String process = procInfo.get(i).processName;
                String packageList = Arrays.toString(procInfo.get(i).pkgList);
                if (!packageList.contains(process)) {
                    process = procInfo.get(i).pkgList[0];
                }

                if (!packages.contains(process) && !isFilteredByName(process)) {
                    ApplicationInfo ai;
                    String applicationName = "";

                    for (int k = 0; k < procInfo.get(i).pkgList.length; k++) {
                        String thisPackage = procInfo.get(i).pkgList[k];
                        try {
                            ai = pm.getApplicationInfo(thisPackage, 0);
                        } catch (final PackageManager.NameNotFoundException e) {
                            ai = null;
                        }
                        if (k > 0) applicationName += " / ";
                        applicationName += (String) (ai != null ? pm.getApplicationLabel(ai) : "(unknown)");
                    }

                    packages.add(process);
                    hm = new HashMap<String, Object>();
                    hm.put(COLUMN_PROCESS_NAME, process);
                    hm.put(COLUMN_PROCESS_PROP, applicationName);
                    processList.add(hm);
                }
            }

            // optional sorting
            Comparator<HashMap<String, Object>> comparator = new Comparator<HashMap<String, Object>>() {
                public int compare(HashMap<String, Object> object1, HashMap<String, Object> object2) {
                    return ((String) object1.get(COLUMN_PROCESS_NAME)).compareToIgnoreCase((String) object2.get(COLUMN_PROCESS_NAME));
                }
            };
            Collections.sort(processList, comparator);

            packages.clear();
            for (HashMap<String, Object> e : processList) {
                packages.add((String) e.get(COLUMN_PROCESS_NAME));
            }
        }

    }
}
