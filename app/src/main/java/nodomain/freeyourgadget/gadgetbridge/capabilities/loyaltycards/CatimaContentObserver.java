package nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class CatimaContentObserver extends ContentObserver {
    private static final Logger LOG = LoggerFactory.getLogger(CatimaContentObserver.class);

    private static final String AUTHORITY_SUFFIX = ".contentprovider.cards";
    private static final long SYNC_DELAY_MS = 1500;

    private final Context context;
    private final Handler handler;
    private final CatimaManager catimaManager;
    private final Set<String> registeredPackages = new HashSet<>();
    private final Set<String> changedPackages = new HashSet<>();
    private final Runnable syncRunnable = this::syncChangedPackages;

    public CatimaContentObserver(final Context context) {
        this(context, new Handler(Looper.getMainLooper()));
    }

    private CatimaContentObserver(final Context context, final Handler handler) {
        super(handler);
        this.context = context;
        this.handler = handler;
        this.catimaManager = new CatimaManager(context);
    }

    public void register() {
        for (final CharSequence packageName : catimaManager.findInstalledCatimaPackages()) {
            if (registeredPackages.contains(packageName.toString())) {
                continue;
            }
            final CatimaContentProvider catima = new CatimaContentProvider(context, packageName.toString());
            if (ContextCompat.checkSelfPermission(context, catima.getReadPermission()) != PackageManager.PERMISSION_GRANTED) {
                LOG.debug("No permission to read cards from {}", packageName);
                continue;
            }
            try {
                context.getContentResolver().registerContentObserver(catima.getAuthorityUri(), true, this);
                registeredPackages.add(packageName.toString());
                LOG.info("Registered loyalty cards observer for {}", packageName);
            } catch (final SecurityException e) {
                LOG.error("Failed to register loyalty cards observer for {}", packageName, e);
            }
        }
    }

    public void unregister() {
        handler.removeCallbacks(syncRunnable);
        changedPackages.clear();
        registeredPackages.clear();
        context.getContentResolver().unregisterContentObserver(this);
    }

    @Override
    public void onChange(final boolean selfChange, @Nullable final Uri uri) {
        LOG.debug("Got loyalty cards change: {}", uri);

        if (uri == null || uri.getAuthority() == null || !uri.getAuthority().endsWith(AUTHORITY_SUFFIX)) {
            return;
        }

        changedPackages.add(uri.getAuthority().substring(0, uri.getAuthority().length() - AUTHORITY_SUFFIX.length()));

        // A single change in Catima can notify more than one uri, debounce them
        handler.removeCallbacks(syncRunnable);
        handler.postDelayed(syncRunnable, SYNC_DELAY_MS);
    }

    private void syncChangedPackages() {
        for (final GBDevice device : GBApplication.app().getDeviceManager().getDevices()) {
            if (!device.isInitialized()) {
                continue;
            }
            final String catimaPackage = catimaManager.getCatimaPackage(device);
            if (catimaPackage == null || !changedPackages.contains(catimaPackage)) {
                continue;
            }
            catimaManager.autoSync(device);
        }
        changedPackages.clear();
    }
}
