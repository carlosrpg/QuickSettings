package qssop;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.HandlerThread;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Installs one APK through a PackageInstaller session opened under the
 * com.android.shell package context. Run with:
 *   CLASSPATH=qs_s.dex app_process /system/bin qssop.SessionInstaller /data/local/tmp/qs.apk
 *
 * The J7 (Desay SV, SOP6) refuses `pm install` from the adb-shell uid, so the setup page
 * cannot install over ADB the ordinary way. A session created directly here is not treated
 * as an adb install and passes that gate -- the same route jconfig's installer uses.
 * Prints QS_INSTALL:ok on success, QS_INSTALL:failed reason=... otherwise.
 */
public final class SessionInstaller {
    private static final String ACTION = "qssop.SESSION_RESULT";
    // Hidden PackageManager install flags: REPLACE_EXISTING | ALL_USERS | ALLOW_DOWNGRADE |
    // ALL_WHITELIST_RESTRICTED_PERMISSIONS. ALL_USERS so the app reaches whichever user owns the
    // car's UI (not necessarily user 0). Stable values since API 21/23.
    private static final int INSTALL_FLAGS = 0x00000002 | 0x00000040 | 0x00000080 | 0x00400000;

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable t) {
            while (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) {
                t = t.getCause();
            }
            fail(t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length < 1) { fail("usage"); return; }
        File apk = new File(args[0]);
        if (!apk.isFile()) { fail("apk_missing"); return; }

        // systemMain()/attach() build a Handler on this thread, so the main looper must exist first.
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepareMainLooper();

        // Context system = ActivityThread.systemMain().getSystemContext();  (both @hide)
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("systemMain").invoke(null);
        Context system = (Context) at.getMethod("getSystemContext").invoke(thread);
        Context shell = system.createPackageContext("com.android.shell", 0);
        PackageInstaller pi = shell.getPackageManager().getPackageInstaller();

        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        Field flags = PackageInstaller.SessionParams.class.getDeclaredField("installFlags");
        flags.setAccessible(true);
        flags.setInt(params, flags.getInt(params) | INSTALL_FLAGS);

        int sessionId = pi.createSession(params);
        PackageInstaller.Session session = pi.openSession(sessionId);
        try (InputStream in = new FileInputStream(apk);
             OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            session.fsync(out);
        }

        // app_process is not a registered app, so registerReceiver fails with SecurityException.
        // Observe the outcome through a SessionCallback instead (the route jconfig uses). commit()
        // still needs an IntentSender; the broadcast it targets goes nowhere and is ignored.
        HandlerThread ht = new HandlerThread("qs-commit");
        ht.start();
        Handler handler = new Handler(ht.getLooper());
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] ok = { false };
        final int watched = sessionId;
        PackageInstaller.SessionCallback cb = new PackageInstaller.SessionCallback() {
            @Override public void onCreated(int id) {}
            @Override public void onBadgingChanged(int id) {}
            @Override public void onActiveChanged(int id, boolean active) {}
            @Override public void onProgressChanged(int id, float progress) {}
            @Override public void onFinished(int id, boolean success) {
                if (id == watched) { ok[0] = success; latch.countDown(); }
            }
        };
        pi.registerSessionCallback(cb, handler);

        PendingIntent pending = PendingIntent.getBroadcast(
                shell, sessionId,
                new Intent(ACTION).setPackage("com.android.shell"),
                PendingIntent.FLAG_UPDATE_CURRENT);
        session.commit(pending.getIntentSender());

        if (!latch.await(120, TimeUnit.SECONDS)) { fail("timeout"); return; }
        if (ok[0]) {
            System.out.println("QS_INSTALL:ok");
            System.exit(0);
        } else {
            fail("session_failed");
        }
    }

    private static void fail(String reason) {
        System.out.println("QS_INSTALL:failed reason=" + reason);
        System.exit(1);
    }
}
