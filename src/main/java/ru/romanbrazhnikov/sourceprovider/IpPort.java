package ru.romanbrazhnikov.sourceprovider;

public class IpPort {
    private String mIp;
    private int mPort;

    public IpPort(String ip, int port){
        mIp = ip;
        mPort = port;
    }

    public IpPort(String ipPort){
        String[] ipPortSplit = ipPort.split(":");
        mIp = ipPortSplit[0];
        mPort = Integer.parseInt(ipPortSplit[1]);
    }

    public String getIp() {
        return mIp;
    }

    public int getPort() {
        return mPort;
    }
}
