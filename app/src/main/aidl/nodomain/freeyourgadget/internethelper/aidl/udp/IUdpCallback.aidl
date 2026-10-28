package nodomain.freeyourgadget.internethelper.aidl.udp;

oneway interface IUdpCallback {
    void onClosed(String reason);
}
