package cn.xiaofei.rudp;

/**
 * 监听接收到的数据，并且获取发送地址
 */
public interface ReceiveListener {
    /**
     * 收到数据包会调用此方法
     *
     * @param rDatagram 包含数据与地址，其中地址是最后一个收到的数据包的地址，可能会与前面的地址不同
     */
    void onReceived(RDatagram rDatagram);
}
