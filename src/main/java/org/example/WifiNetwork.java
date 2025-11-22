package org.example;

public class WifiNetwork {
    private final String ssid;
    private int signalStrength;
    private String security;
    private String frequency;
    private String bssid;
    private final boolean is5G;

    public WifiNetwork(String ssid, int signalStrength, String security, String frequency, String bssid, boolean is5G) {
        this.ssid = ssid;
        this.signalStrength = signalStrength;
        this.security = security;
        this.frequency = frequency;
        this.bssid = bssid;
        this.is5G = is5G;
    }

    public String getSsid() { return ssid; }
    public int getSignalStrength() { return signalStrength; }
    public String getSecurity() { return security; }
    public String getFrequency() { return frequency; }
    public String getBssid() { return bssid; }
    public boolean is5G() { return is5G; }

    public void setSignalStrength(int signalStrength) { this.signalStrength = signalStrength; }
    public void setSecurity(String security) { this.security = security; }

    @Override
    public String toString() {
        return ssid + (is5G ? " [5G]" : "") + " - " + signalStrength + "% - " + security;
    }
}