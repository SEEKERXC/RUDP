package cn.xiaofei.rudp;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public class RDatagram {
    public final ByteBuffer data;
    public InetSocketAddress address;
    int sendTimes = 0; //发送次数

    /**
     * 包头字节数
     */
    static final int HEADER_LENGTH = 24;

    /**
     * 数据包
     */
    static final byte TYPE_DATA = 0x00;

    /**
     * 确认包，当接收方收到数据包、确认包时会发送此消息。内容为：
     * 一个整数（4字节），表示收到的包的字节数。
     */
    static final byte TYPE_ACK = 0x22;

    /**
     * 连接请求，指的是询问对方是否能接收接下来要传送的数据。内容为：
     * 一个整数（4字节），表示将要发送的小包总数（不包含连接请求）
     */
    static final byte TYPE_CONNECT_REQUEST = 0x44;

    /**
     * 连接回应，发送方收到则表示接收方做好了接收准备。如果发送方超时仍未收到，则连接失败。内容为空
     */
    static final byte TYPE_CONNECT_RESPONSE = 0x55;

    /**
     * 新建一个数据包，数据不能超过1024字节
     *
     * @param sendId    发送id
     * @param receiveId 接收id
     * @param seq       序列号或确认号，二者相等
     * @param type      包类型
     * @param d         数据
     */
    RDatagram(long sendId, long receiveId, int seq, byte type, byte[] d) {
        int len = HEADER_LENGTH;
        if (d != null) len += d.length;
        data = ByteBuffer.allocate(len);
        byte[] bytes = data.array();
        //byte[0-3]为序列号。初始序列号是非0的随机整数，分配给连接包。
        System.arraycopy(Util.intToBytes(seq), 0, bytes, 0, 4);
        //byte[4-5]为数据字节数，包含头
        System.arraycopy(Util.shortToBytes((short) len), 0, bytes, 4, 2);
        //byte[6]空置
        //byte[7]为数据类型
        bytes[7] = type;
        //byte[8-15]为发送id
        System.arraycopy(Util.longToBytes(sendId), 0, bytes, 8, 8);
        //byte[16-23]为接收id
        System.arraycopy(Util.longToBytes(receiveId), 0, bytes, 16, 8);
        //之后是内容
        if (d != null)
            System.arraycopy(d, 0, bytes, HEADER_LENGTH, d.length);
    }

    /**
     * 根据接收到的数据新建
     */
    RDatagram(ByteBuffer data) {
        this.data = data;
        this.data.flip();
    }

    int getSeq() {
        byte[] bytes = data.array();
        return Util.bytesToInt(Util.subArray(bytes, 0, 4));
    }

    byte getType() {
        return data.array()[7];
    }

    byte[] getData() {
        if (data.array().length <= 24) {
            return new byte[0];
        }
        return Util.subArray(data.array(), 24, getTotalLength());
    }

    long getSendId() {
        byte[] bytes = data.array();
        return Util.bytesToLong(Util.subArray(bytes, 8, 16));
    }

    long getReceiveId() {
        byte[] bytes = data.array();
        return Util.bytesToLong(Util.subArray(bytes, 16, 24));
    }

    void setReceiveId(long receiveId) {
        byte[] bytes = Util.longToBytes(receiveId);
        System.arraycopy(bytes, 0, data.array(), 16, 8);
    }

    int getTotalLength() {
        byte[] bytes = data.array();
        return Util.bytesToShort(Util.subArray(bytes, 4, 6));
    }
}
