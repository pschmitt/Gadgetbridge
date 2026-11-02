package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors

import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.GarminHttpRequest
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.GarminHttpResponse

interface HttpInterceptor {
    fun supports(request: GarminHttpRequest): Boolean
    fun handle(request: GarminHttpRequest): GarminHttpResponse?
}
