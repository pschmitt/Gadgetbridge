package nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.datasync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiConstants;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiTLV;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huawei.HuaweiSupportProvider;

public class HuaweiDataSyncSleepApnea implements HuaweiDataSyncCommon.DataCallback {
    private final Logger LOG = LoggerFactory.getLogger(HuaweiDataSyncSleepApnea.class);

    private final HuaweiSupportProvider support;

    public static final String SRC_PKG_NAME = "hw.health.apneajsmodule";
    public static final String PKG_NAME = "hw.watch.health.osa";
    private static final int CONFIG_ID_SLEEP_APNEA = 900300007;
    private static final int CONFIG_ID_SLEEP_BREATH = 900300008;
    private static final byte CONFIG_ACTION_QUERY = 2;

    static final byte[] QUERY_DATA = new HuaweiTLV().put(0x01, true).serialize();

    private Boolean sleepApneaAccountEnabled;
    private Boolean sleepBreathEnabled;
    private Boolean pendingSleepBreathEnabled;

    public HuaweiDataSyncSleepApnea(HuaweiSupportProvider support) {
        this.support = support;
        this.support.getHuaweiDataSyncManager().registerCallback(PKG_NAME, this);
    }

    private boolean sendCommonData(int configId, byte configAction, byte[] configData) {
        HuaweiDataSyncCommon.ConfigCommandData data = new HuaweiDataSyncCommon.ConfigCommandData();
        HuaweiDataSyncCommon.ConfigData config = new HuaweiDataSyncCommon.ConfigData();
        config.configId = configId;
        config.configAction = configAction;
        config.configData = configData;
        List<HuaweiDataSyncCommon.ConfigData> list = new ArrayList<>();
        list.add(config);
        data.setConfigDataList(list);
        return this.support.getHuaweiDataSyncManager().sendConfigCommand(SRC_PKG_NAME, PKG_NAME, data);
    }

    public boolean changeSleepBreatheState(boolean state) {
        HuaweiTLV tlv = new HuaweiTLV().put(0x01, state);
        final Boolean previousPendingState = pendingSleepBreathEnabled;
        pendingSleepBreathEnabled = state;
        if (!sendCommonData(CONFIG_ID_SLEEP_BREATH, (byte) 1, tlv.serialize())) {
            pendingSleepBreathEnabled = previousPendingState;
            return false;
        }
        return true;
    }

    public boolean changeSleepApneaState(boolean state) {
        HuaweiTLV tlv = new HuaweiTLV().put(0x01, state);
        return sendCommonData(CONFIG_ID_SLEEP_APNEA, (byte) 1, tlv.serialize());
    }

    public boolean querySleepApneaState() {
        return sendCommonData(CONFIG_ID_SLEEP_APNEA, CONFIG_ACTION_QUERY, QUERY_DATA);
    }

    @Override
    public void onConfigCommand(HuaweiDataSyncCommon.ConfigCommandData data) {
        if (data.getConfigDataList() == null) {
            return;
        }

        for (HuaweiDataSyncCommon.ConfigData config : data.getConfigDataList()) {
            if (config.configAction != CONFIG_ACTION_QUERY ||
                    (config.configId != CONFIG_ID_SLEEP_APNEA && config.configId != CONFIG_ID_SLEEP_BREATH)) {
                continue;
            }

            final Boolean enabled = getSleepBreathState(config.configData);
            if (enabled == null) {
                LOG.warn("Ignoring malformed sleep breathing state response for config {}", config.configId);
                continue;
            }

            if (config.configId == CONFIG_ID_SLEEP_APNEA) {
                sleepApneaAccountEnabled = enabled;
            } else {
                if (pendingSleepBreathEnabled != null && !pendingSleepBreathEnabled.equals(enabled)) {
                    // An older write response must not undo the latest requested setting.
                    continue;
                }
                pendingSleepBreathEnabled = null;
                sleepBreathEnabled = enabled;
            }
            updateSleepBreathPreference();
        }
    }

    private void updateSleepBreathPreference() {
        if (pendingSleepBreathEnabled != null) {
            return;
        }
        final Boolean effectiveState = getEffectiveSleepBreathState(sleepApneaAccountEnabled, sleepBreathEnabled);
        if (effectiveState != null) {
            setSleepBreathPreference(effectiveState);
        }
    }

    // 900300007 is only an account prerequisite. The switch represents the device
    // setting (900300008). Device state is unknown (never read), not a confirmed OFF.
    static Boolean getEffectiveSleepBreathState(final Boolean accountEnabled, final Boolean deviceEnabled) {
        if (Boolean.FALSE.equals(accountEnabled) || Boolean.FALSE.equals(deviceEnabled)) {
            return false;
        }
        if (Boolean.TRUE.equals(accountEnabled) && deviceEnabled != null) {
            return deviceEnabled;
        }
        return null;
    }

    void setSleepBreathPreference(final boolean enabled) {
        GBApplication.getDeviceSpecificSharedPrefs(support.getDevice().getAddress())
                .edit()
                .putBoolean(HuaweiConstants.PREF_HUAWEI_SLEEP_BREATH, enabled)
                .apply();
    }

    static Boolean getSleepBreathState(final byte[] configData) {
        if (configData == null) {
            return null;
        }

        try {
            final byte[] value = new HuaweiTLV().parse(configData).getBytes(0x01, null);
            if (value == null || value.length != 1 || (value[0] != 0 && value[0] != 1)) {
                return null;
            }
            return value[0] == 1;
        } catch (final ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }

    @Override
    public void onEventCommand(HuaweiDataSyncCommon.EventCommandData data) {

    }

    @Override
    public void onDataCommand(HuaweiDataSyncCommon.DataCommandData data) {

    }

    @Override
    public void onDictDataCommand(HuaweiDataSyncCommon.DictDataCommandData data) {
    }
}
