package nodomain.freeyourgadget.internethelper.aidl.udp;

import android.os.ParcelFileDescriptor;
import nodomain.freeyourgadget.internethelper.aidl.udp.IUdpCallback;

interface IUdpService {
    int version();

    ParcelFileDescriptor open(String host, int port, String networkRequestId, IUdpCallback cb);
}
