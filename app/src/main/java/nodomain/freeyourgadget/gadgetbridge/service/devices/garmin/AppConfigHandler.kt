package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.protobuf.ByteString
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.config.DynamicAppConfig
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.config.DynamicAppConfig.AppConfigBoolean
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.config.DynamicAppConfig.AppConfigFloat
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.config.DynamicAppConfig.AppConfigInteger
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.config.DynamicAppConfig.AppConfigString
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventAppConfig
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiAppSettings
import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiSmartProto.Smart
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.GarminJson
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.GarminJsonException
import nodomain.freeyourgadget.gadgetbridge.util.UuidUtil
import nodomain.freeyourgadget.gadgetbridge.util.protobuf.buildWith
import org.slf4j.LoggerFactory
import java.util.UUID

class AppConfigHandler(private val deviceSupport: GarminSupport) {
    fun process(appConfigService: GdiAppSettings.ConnectIQAppSettingsService): Boolean {
        if (appConfigService.hasSaveAppSettingsResponse()) {
            val uuid = UuidUtil.fromBytes(appConfigService.saveAppSettingsResponse.appIdentifier.toByteArray())
            val status = appConfigService.saveAppSettingsResponse.response
            LOG.debug("Got app config set status for {}: {}", uuid, status)
            if (status != GdiAppSettings.SaveAppSettingsResponse.Response.SUCCESS) {
                LOG.warn("Failed to set configs for {}, status={}", uuid, status)
                evaluateEvent(uuid, GBDeviceEventAppConfig.Event.APP_CONFIG_SET_FAILED)
            } else {
                evaluateEvent(uuid, GBDeviceEventAppConfig.Event.APP_CONFIG_SET_SUCCESS)
            }
            return true
        }

        if (appConfigService.hasGetAppSettingsResponse()) {
            val uuid = UuidUtil.fromBytes(appConfigService.getAppSettingsResponse.appIdentifier.toByteArray())
            val status = appConfigService.getAppSettingsResponse.response
            LOG.debug("Got app config get status for {}: {}", uuid, status)
            if (status != GdiAppSettings.GetAppSettingsResponse.Response.SUCCESS) {
                LOG.warn("Failed to get configs for {}, status={}", uuid, status)
                evaluateEvent(uuid, GBDeviceEventAppConfig.Event.APP_CONFIG_GET_FAILED)
            }
            // On success we just wait, we'll get the configs in a future command
            return true
        }

        if (!appConfigService.hasSendAppSettingsRequest()) {
            LOG.warn("Got unknown app settings response")
            return false
        }

        val settings: GdiAppSettings.SendAppSettingsRequest = appConfigService.sendAppSettingsRequest
        val uuid = UuidUtil.fromBytes(settings.appIdentifier.toByteArray())

        LOG.debug("Got app config ret for {}", uuid)

        val jsonElement: JsonElement = try {
            GarminJson.decode(settings.settingsValues.toByteArray())
        } catch (e: Exception) {
            LOG.error("Failed to decode app settings for {}", uuid, e)
            evaluateEvent(uuid, GBDeviceEventAppConfig.Event.APP_CONFIG_GET_FAILED)
            return true
        }

        LOG.debug("Got app settings for {}: {}", uuid, jsonElement)

        if (jsonElement !is JsonObject) {
            LOG.error(
                "App settings for {} are not a JSON object: {}",
                uuid,
                jsonElement.javaClass
            )
            evaluateEvent(uuid, GBDeviceEventAppConfig.Event.APP_CONFIG_GET_FAILED)
            return true
        }
        val configs = jsonElement.keySet().map { key ->
            LOG.debug("App setting {}: {}", key, jsonElement.get(key))

            val value = jsonElement.get(key)
            if (value !is JsonPrimitive) {
                LOG.error(
                    "App setting value for {} is not a JSON primitive: {}",
                    key,
                    value.javaClass
                )
                evaluateEvent(uuid, GBDeviceEventAppConfig.Event.APP_CONFIG_GET_FAILED)
                return true
            }

            if (value.isBoolean) {
                return@map AppConfigBoolean(key, value.getAsBoolean())
            } else if (value.isNumber) {
                val num = value.getAsNumber()
                val doubleValue = num.toDouble()
                val longValue = num.toLong()
                if (doubleValue != longValue.toDouble() || num is Float || num is Double) {
                    return@map AppConfigFloat(key, num.toFloat())
                } else {
                    return@map AppConfigInteger(key, num.toInt())
                }
            } else if (value.isString) {
                return@map AppConfigString(key, value.getAsString())
            } else {
                LOG.error("Unknown app setting type: {}", value)
                evaluateEvent(uuid, GBDeviceEventAppConfig.Event.APP_CONFIG_GET_FAILED)
                return true
            }
        }.toList()

        evaluateEvent(uuid, GBDeviceEventAppConfig.Event.APP_CONFIG_GET_SUCCESS, ArrayList(configs))

        return true
    }

    fun evaluateEvent(uuid: UUID, event: GBDeviceEventAppConfig.Event, configs: ArrayList<DynamicAppConfig> = ArrayList()) {
        deviceSupport.evaluateGBDeviceEvent(GBDeviceEventAppConfig(uuid, event, configs))
    }

    fun onAppConfigRequest(uuid: UUID): Smart {
        LOG.debug("Encoding app config request for {}", uuid)
        return Smart.newBuilder().buildWith {
            connectIqAppSettingsService = GdiAppSettings.ConnectIQAppSettingsService.newBuilder().buildWith {
                getAppSettingsRequest = GdiAppSettings.GetAppSettingsRequest.newBuilder().buildWith {
                    appIdentifier = ByteString.copyFrom(UuidUtil.toBytes(uuid))
                }
            }
        }
    }

    fun onAppConfigSet(uuid: UUID, configs: List<DynamicAppConfig>): Smart? {
        LOG.debug("Encoding app config set for {} with {} configs", uuid, configs.size)

        val jsonObject = JsonObject()
        for (config in configs) {
            when (config) {
                is AppConfigBoolean -> jsonObject.addProperty(config.key, config.value)
                is AppConfigString -> jsonObject.addProperty(config.key, config.value)
                is AppConfigFloat -> jsonObject.addProperty(config.key, config.value)
                is AppConfigInteger -> jsonObject.addProperty(config.key, config.value)
            }
        }

        val configsBytes: ByteArray
        try {
            configsBytes = GarminJson.encode(jsonObject)
        } catch (e: GarminJsonException) {
            LOG.error("Failed to encode configs to Garmin JSON", e)
            deviceSupport.evaluateGBDeviceEvent(GBDeviceEventAppConfig(uuid, GBDeviceEventAppConfig.Event.APP_CONFIG_SET_FAILED))
            return null
        }

        return Smart.newBuilder().buildWith {
            connectIqAppSettingsService = GdiAppSettings.ConnectIQAppSettingsService.newBuilder().buildWith {
                saveAppSettingsRequest = GdiAppSettings.SaveAppSettingsRequest.newBuilder().buildWith {
                    appIdentifier = ByteString.copyFrom(UuidUtil.toBytes(uuid))
                    settingsValues = ByteString.copyFrom(configsBytes)
                }
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(AppConfigHandler::class.java)
    }
}
