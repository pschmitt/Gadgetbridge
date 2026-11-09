package nodomain.freeyourgadget.gadgetbridge.test;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.Objects;

import ch.qos.logback.classic.util.ContextInitializer;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.GBEnvironment;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import java.lang.reflect.Field;
import java.util.List;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager;

import static org.junit.Assert.assertNotNull;

/**
 * Base class for all testcases in Gadgetbridge that are supposed to run locally
 * with robolectric.
 * <p>
 * Important: To run them, create a run configuration and execute them in the Gadgetbridge/app/
 * directory.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 24, application = GBTestApplication.class)
public abstract class TestBase {
    protected GBApplication app = (GBApplication) RuntimeEnvironment.application;
    protected DaoSession daoSession;
    protected DBHandler dbHandler;

    // Make sure logging is set up for all testcases, so that we can debug problems
    @BeforeClass
    public static void setupSuite() {
        GBEnvironment.setupEnvironment(GBEnvironment.createLocalTestEnvironment());

        // print everything going to android.util.Log to System.out
        System.setProperty("robolectric.logging", "stdout");

        if (System.getProperty(ContextInitializer.CONFIG_FILE_PROPERTY) == null) {
            File workingDir = new File(Objects.requireNonNull(System.getProperty("user.dir")));
            File configFile = new File(workingDir, "src/main/assets/logback.xml");
            System.out.println(configFile.getAbsolutePath());
            System.setProperty(ContextInitializer.CONFIG_FILE_PROPERTY, configFile.getAbsolutePath());
        }
    }

    @Before
    public void setUp() throws Exception {
        app = (GBApplication) RuntimeEnvironment.application;
        assertNotNull(app);
        assertNotNull(getContext());
        app.getDatabaseManager().setupDatabase();
        dbHandler = GBApplication.acquireDB();
        daoSession = dbHandler.getDaoSession();
        assertNotNull(daoSession);
    }

    /**
     * Releases the write lock taken in {@link #setUp()}. This does not close the database, which
     * {@link GBApplication#onTerminate()} does when Robolectric tears down the application.
     */
    @After
    public void releaseDbHandler() throws Exception {
        if (dbHandler != null) {
            dbHandler.close();
            dbHandler = null;
        }
    }

    protected GBDevice createDummyGDevice(String macAddress) {
        GBDevice dummyGBDevice = new GBDevice(macAddress, "Testie", "Tesie Alias", "Test Folder", DeviceType.TEST);
        dummyGBDevice.setFirmwareVersion("1.2.3");
        dummyGBDevice.setModel("4.0");
        return dummyGBDevice;
    }

    protected Context getContext() {
        return app;
    }

    protected void registerDeviceWithManager(final GBDevice device) {
        try {
            final DeviceManager deviceManager = GBApplication.app().getDeviceManager();
            final Field field = DeviceManager.class.getDeclaredField("deviceList");
            field.setAccessible(true);
            final List<GBDevice> deviceList = (List<GBDevice>) field.get(deviceManager);
            deviceList.clear();
            deviceList.add(device);
        } catch (final Exception e) {
            throw new AssertionError("Failed to register test device", e);
        }
    }
}
