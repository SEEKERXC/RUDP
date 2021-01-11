package cn.xiaofei.rudp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>R即Reliable。RDatagramChannel即可靠的UDP信道。
 * <p>与TCP不同的是：
 * 1、RUDP以按小包分配序列号，
 * 2、RUDP不建立全双工信道，因此只需要两次握手。
 * 3、RUDP没有慢启动，没有拥塞控制。
 * <p>这里只提供可靠性，不提供安全性。
 * <p>想要更安全（主要是防止DDOS攻击），但是更低效的，请在Github搜索“RSUDP”：reliable safer user datagram protocol.
 *
 * @author Xiao Fei, jdfohewk@gmail.com
 * @version 2021.1
 */
public class RDatagramChannel {
    private DatagramChannel channel;

    /**
     * 发送端的连接请求
     */
    private final Map<Long, RDatagram> connectionRequests;

    /**
     * 接收端的连接池，key为连接信息，value为接收id
     */
    private final Map<Connection, Long> connections;

    /**
     * 发送端的发送信息，key为sendId
     */
    private final Map<Long, Send> sends;

    /**
     * 接收端的接收信息，key为receiveId
     */
    private final Map<Long, Receive> receives;

    /**
     * 发送任务列表
     */
    private final Map<Long, SendTask> sendTasks;

    /**
     * 外层map的key为本地数据id，value为数据包map。里层数据包map的key为序列号，value为数据包
     */
    private final Map<Long, Map<Integer, RDatagram>> sendWindow;

    /**
     * 允许同时接收来自多个地址的数据。
     * 最外层key是接收方数据id。里层key是序列号
     */
    private final Map<Long, Map<Integer, RDatagram>> receiveWindow;

    /**
     * 接收监听器
     */
    private ReceiveListener receiveListener;

    private final ExecutorService responseTask;
    private final ExecutorService ackTask;
    private final ExecutorService receiveTask;
    private final ExecutorService finalTask;
    private final ExecutorService callbackTask;

    private final ScheduledExecutorService connectionTimer;
    private final Map<Long, ScheduledExecutorService> sendTimers;


    private int retryTime = 15; //重试次数
    private long retryIntervalMillis = 200; //重试时间间隔，毫秒

    private RDatagramChannel(int port) {
        try {
            channel = DatagramChannel.open();
            channel.bind(new InetSocketAddress(port));
            channel.configureBlocking(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
        connectionRequests = new ConcurrentHashMap<>();
        connections = new HashMap<>();
        sends = new HashMap<>();
        receives = new HashMap<>();
        sendTasks = new HashMap<>();
        sendWindow = new HashMap<>();
        receiveWindow = new ConcurrentHashMap<>();
        responseTask = Executors.newCachedThreadPool();
        ackTask = Executors.newCachedThreadPool();
        receiveTask = Executors.newCachedThreadPool();
        finalTask = Executors.newCachedThreadPool();
        callbackTask = Executors.newCachedThreadPool();
        connectionTimer = new ScheduledThreadPoolExecutor(1);
        sendTimers = new HashMap<>();
        retryConnect();
        listen();
    }

    /**
     * 在指定端口开启RUDP传输通道
     *
     * @param port 本地端口号
     */
    public static RDatagramChannel open(int port) {
        return new RDatagramChannel(port);
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        responseTask.shutdown();
        ackTask.shutdown();
        finalTask.shutdown();
        callbackTask.shutdown();
        connectionTimer.shutdown();
        for (ScheduledExecutorService executorService : sendTimers.values()) executorService.shutdown();
    }

    public void setReceiveListener(ReceiveListener receiveListener) {
        this.receiveListener = receiveListener;
    }

    /**
     * 发送数据，数据被分割成若干个小包，每个包约1KB，每个包默认重试15次，间隔200ms。
     * 如果超过重试次数后，超时仍未收到ACK，则发送失败。
     * 发送前首先需要发一个请求包，以征得接收方的同意，并且获取一些关键信息。
     * 发送完成后需要发一个结束包，以告诉接收方进行整合。
     * <p>
     * 允许同时发送多个数据。
     * 一次发送的数据不超过当前可用内存并且小于 2^31 Byte 即可。
     *
     * @return 发送任务对象，用于实时监控发送状态，并且提供回调方法。
     */
    public SendTask send(ByteBuffer data, InetSocketAddress address) {
        byte[] bytes = data.array();
        if (bytes.length <= 0) return null;
        SendTask sendTask = connect(bytes, address);
        sendTasks.put(sendTask.sendId, sendTask);
        int seq = sendTask.initSeq;
        //分包并加入发送窗口
        Map<Integer, RDatagram> dataMap = new HashMap<>();
        sendWindow.put(sendTask.sendId, dataMap);
        for (int i = 0; i <= sendTask.totalPackages; i++) {
            ++seq;
            byte[] split = Util.subArray(bytes, i * 1024, (i + 1) * 1024);
            if (split.length <= 0) break;
            RDatagram d = new RDatagram(sendTask.sendId, 0, seq, RDatagram.TYPE_DATA, split); //receiveId在发送之前补充
            d.address = address;
            dataMap.put(seq, d);
        }
        return sendTask;
    }

    /**
     * 发起连接请求，并且重试retryTime次，每次间隔retryIntervalMillis毫秒。超过重试时间而未收到连接回应，则连接失败，即发送失败。
     * Initialize and send a connection request, and retry for {@link this.retryTime} times with interval of {@link this.retryIntervalMillis} ms.
     * If the retry time exceeds and no connection response is received, the connection fails and the send fails.
     *
     * @param address 接收方的地址 the receiver's address
     * @return 发送任务 the send task
     */
    private SendTask connect(byte[] data, InetSocketAddress address) {
        Random random = new Random();
        long sendId = random.nextLong();
        while (sendWindow.containsKey(sendId) || sendId == 0) sendId = random.nextLong();
        int seq = random.nextInt();
        while (seq == 0) seq = random.nextInt();
        SendTask sendTask = new SendTask(sendId, data.length, seq);
        byte[] d = Util.intToBytes(sendTask.totalPackages);
        RDatagram connectRequest = new RDatagram(sendId, 0, seq, RDatagram.TYPE_CONNECT_REQUEST, d);
        connectRequest.address = address;
        connectionRequests.put(sendId, connectRequest);
        try {
            channel.send(connectRequest.data, address);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Send send = new Send(sendTask.sendId, sendTask.totalPackages);
        sends.put(sendTask.sendId, send);
        return sendTask;
    }

    /**
     * 对接收到的数据包发送ack（DATA包以及FINAL包）
     *
     * @param rDatagram 接收到的DATA包或FINAL包
     */
    private synchronized void sendAck(RDatagram rDatagram) {
        if (rDatagram.getType() != RDatagram.TYPE_DATA) return;
        byte[] data = new byte[4];
        System.arraycopy(Util.intToBytes(rDatagram.getTotalLength() - RDatagram.HEADER_LENGTH), 0, data, 0, 4);
        RDatagram ack = new RDatagram(rDatagram.getSendId(), rDatagram.getReceiveId(), rDatagram.getSeq(), RDatagram.TYPE_ACK, data);
        try {
            channel.send(ack.data, rDatagram.address);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listen() {
        new Thread(() -> {
            try {
                Selector selector = Selector.open();
                channel.register(selector, SelectionKey.OP_READ);
                while (selector.select() > 0) {
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while (iterator.hasNext()) {
                        SelectionKey next = iterator.next();
                        if (next.isReadable()) {
                            DatagramChannel datagramChannel = (DatagramChannel) next.channel();
                            ByteBuffer allocate = ByteBuffer.allocate(1048); //每个包最大为1048字节
                            allocate.clear();
                            InetSocketAddress socketAddress = (InetSocketAddress) datagramChannel.receive(allocate);
                            RDatagram rDatagram = new RDatagram(allocate);
                            if (rDatagram.getSendId() == 0) return;
                            rDatagram.address = socketAddress;
                            byte type = rDatagram.getType();
                            switch (type) {
                                case RDatagram.TYPE_ACK:
                                    doAck(rDatagram);
                                    break;
                                case RDatagram.TYPE_DATA:
                                    doReceive(rDatagram);
                                    break;
                                case RDatagram.TYPE_CONNECT_REQUEST:
                                    doResponse(rDatagram);
                                    break;
                                case RDatagram.TYPE_CONNECT_RESPONSE:
                                    doSending(rDatagram);
                            }
                            allocate.clear();
                        }
                    }
                    iterator.remove();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * 处理连接请求，发送连接回应。这个回应不需要重试，不需要管对方是否收到。
     *
     * @param connectRequest 连接请求
     */
    private void doResponse(RDatagram connectRequest) {
        responseTask.execute(() -> {
            long sendId = connectRequest.getSendId();
            int seq = connectRequest.getSeq();
            int totalPackages = Util.bytesToInt(Util.subArray(connectRequest.getData(), 0, 4));
            InetSocketAddress address = connectRequest.address;
            Connection connection = new Connection(address, sendId);
            long receiveId;
            if (!connections.containsKey(connection)) {
                Random random = new Random();
                receiveId = random.nextLong();
                while (receiveWindow.containsKey(receiveId) || receiveId == 0) receiveId = random.nextLong();
                connections.put(connection, receiveId);
                Map<Integer, RDatagram> receiveMap = new HashMap<>();
                receiveWindow.put(receiveId, receiveMap);
                Receive receive = new Receive(receiveId, seq + 1, totalPackages);
                receives.put(receiveId, receive);
            } else receiveId = connections.get(connection);
            RDatagram connectResponse = new RDatagram(sendId, receiveId, connectRequest.getSeq(), RDatagram.TYPE_CONNECT_RESPONSE, null);
            try {
                channel.send(connectResponse.data, address);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 发送端开始发送数据（在连接成功后）
     *
     * @param connectResponse 从接收方收到的连接回应，给出了数据id
     */
    private void doSending(RDatagram connectResponse) {
        long sendId = connectResponse.getSendId();
        if (sendTimers.containsKey(sendId)) return;
        long receiveId = connectResponse.getReceiveId();
        connectionRequests.remove(sendId);
        SendTask sendTask = sendTasks.get(sendId);
        if (sendTask == null) return;
        if (sendTask.onConnected != null && !sendTask.connected) {
            sendTask.connected = true;
            callbackTask.execute(sendTask.onConnected);
        }
        Map<Integer, RDatagram> dataMap = sendWindow.get(sendId);
        if (dataMap == null) return;
        for (RDatagram rDatagram : dataMap.values())
            rDatagram.setReceiveId(receiveId);
        startTiming(sendId);
    }

    /**
     * 接收端接收数据
     */
    private void doReceive(RDatagram rDatagram) {
        receiveTask.execute(() -> {
            long receiveId = rDatagram.getReceiveId();
            Receive receive = receives.get(receiveId);
            int seq = rDatagram.getSeq();
            if (!receive.seqValid(seq)) return;
            Map<Integer, RDatagram> receiveMap;
            synchronized (receiveWindow) {
                receiveMap = receiveWindow.computeIfAbsent(receiveId, k -> new ConcurrentHashMap<>());
            }
            if (receiveMap.containsKey(seq)) return;
            receiveMap.put(rDatagram.getSeq(), rDatagram);
            int r = receive.receivedPackages.incrementAndGet();
            //todo:解决并发问题
            if (r >= receive.totalPackages)
                doFinal(receive, rDatagram.address);
            sendAck(rDatagram);
        });

    }

    /**
     * 发送端确认某个数据包已成功发送，从发送窗口删除
     * Confirm that a packet has been successfully sent and delete it from the sending window.
     *
     * @param ack 确认包。其序列号与对应数据包相等
     */
    private void doAck(RDatagram ack) {
        ackTask.execute(() -> {
            long sendId = ack.getSendId();
            if (sendId == 0) return;
            SendTask task = sendTasks.get(sendId);
            Send send = sends.get(sendId);
            Map<Integer, RDatagram> dataMap = sendWindow.get(sendId);
            if (dataMap == null) return;
            dataMap.remove(ack.getSeq());
            byte[] data = ack.getData();
            int len = Util.bytesToInt(Util.subArray(data, 0, 4));

            task.sent += len;
            if (task.onSending != null)
                callbackTask.execute(task.onSending);
            send.sentTotal++;
            if (send.sentTotal >= send.totalPackages) {
                if (task.onCompleted != null)
                    callbackTask.execute(task.onCompleted);
            }
        });

    }

    /**
     * 接收端处理final数据包，对接收到的数据进行收尾工作
     */
    private void doFinal(Receive receive, InetSocketAddress finalAddr) {
        finalTask.execute(() -> {
            int total = receive.totalPackages;
            int seq = receive.initSeq; //第一个数据包的序列号
            long receiveId = receive.receiveId;
            Map<Integer, RDatagram> receiveMap = receiveWindow.get(receiveId);
            int len = 0;
            for (RDatagram rDatagram : receiveMap.values())
                len += (rDatagram.getTotalLength() - RDatagram.HEADER_LENGTH);
            byte[] bytes = new byte[len];
            for (int i = 0; i < total; i++) {
                RDatagram rDatagram = receiveMap.get(seq++);
                if (rDatagram != null) {
                    byte[] split = rDatagram.getData();
                    System.arraycopy(split, 0, bytes, i * 1024, split.length);
                }
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
            RDatagram result = new RDatagram(byteBuffer);
            result.address = finalAddr;
            if (receiveListener != null) {
                receiveListener.onReceived(result);
            }

            receiveMap.clear();
            receiveWindow.remove(receiveId);
        });

    }

    /**
     * 重复发送连接请求
     */
    private void retryConnect() {
        connectionTimer.scheduleAtFixedRate(() -> {
            if (connectionRequests.isEmpty()) return;
            for (Map.Entry<Long, RDatagram> entry : connectionRequests.entrySet()) {
                RDatagram connRequest = entry.getValue();
                long sendId = entry.getKey();
                if (connRequest.sendTimes >= retryTime) {
                    connectionRequests.remove(sendId);
                    SendTask sendTask = sendTasks.get(sendId);
                    if (sendTask != null && sendTask.onFailed != null)
                        callbackTask.execute(sendTask.onFailed);
                    sendTasks.remove(sendId);
                    break;
                }
                try {
                    while (connRequest.data.hasRemaining())
                        channel.send(connRequest.data, connRequest.address);
                    connRequest.data.flip();
                    connRequest.sendTimes++;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 0, retryIntervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 开始发送指定本地id的数据包
     *
     * @param sendId 本地数据id
     */
    private void startTiming(long sendId) {
        Map<Integer, RDatagram> datagramMap = sendWindow.get(sendId);
        if (datagramMap == null) return;
        ScheduledExecutorService timer = new ScheduledThreadPoolExecutor(1);
        sendTimers.put(sendId, timer);
        timer.scheduleAtFixedRate(() -> {
            if (datagramMap.isEmpty()) return;
            for (RDatagram rDatagram : datagramMap.values()) {
                if (rDatagram.sendTimes >= retryTime) {
                    //超时，发送失败
                    SendTask sendTask = sendTasks.get(sendId);
                    if (sendTask != null && sendTask.onFailed != null)
                        callbackTask.execute(sendTask.onFailed);
                    sendTasks.remove(sendId);
                    timer.shutdownNow();
                }
                rDatagram.sendTimes++;
                try {
                    while (rDatagram.data.hasRemaining()) {
                        channel.send(rDatagram.data, rDatagram.address);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                rDatagram.data.flip();
            }

        }, 0, retryIntervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 设置重试次数。默认15次
     */
    public RDatagramChannel setRetryTime(int retryTime) {
        if (retryTime < 0) this.retryTime = 0;
        this.retryTime = retryTime;
        return this;
    }

    /**
     * 设置每次重试时间(ms)，默认200ms。一旦超过 重试次数*重试时间 仍未收到ACK，则发送失败。
     */
    public RDatagramChannel setRetryIntervalMillis(long retryIntervalMillis) {
        if (retryIntervalMillis < 0) return this;
        this.retryIntervalMillis = retryIntervalMillis;
        return this;
    }

    /**
     * 连接单元，由接收方维持
     * 一个连接由地址+发送id唯一确定。
     * 一次发送可能有多个不同的连接，而一个连接只属于一次发送。
     */
    private static final class Connection {
        public Connection(InetSocketAddress address, long sendId) {
            this.address = address;
            this.sendId = sendId;
        }

        /**
         * 发起连接时候的对方地址，在发送过程中很可能已经改变。
         */
        private final InetSocketAddress address;
        /**
         * 发送id，即连接请求的id
         */
        private final long sendId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Connection that = (Connection) o;
            return sendId == that.sendId &&
                    Objects.equals(address, that.address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, sendId);
        }
    }

    /**
     * 发送信息
     */
    private static final class Send {
        public Send(long sendId, int totalPackages) {
            this.sendId = sendId;
            this.totalPackages = totalPackages;
        }

        long sendId;
        int sentTotal = 0; //已发送成功的数量
        int totalPackages; //数据包总数
    }

    /**
     * 接收信息
     */
    private static final class Receive {
        public Receive(long receiveId, int initSeq, int totalPackages) {
            this.receiveId = receiveId;
            this.initSeq = initSeq;
            this.totalPackages = totalPackages;
        }

        long receiveId;
        int initSeq; //数据包的初始序列号
        int totalPackages; //数据包总数
        volatile AtomicInteger receivedPackages = new AtomicInteger(0); //已接收数据包总数

        /**
         * 判断序列号是否合法
         */
        public boolean seqValid(int seq) {
            int endSeq = initSeq + totalPackages - 1;
            if (endSeq <= initSeq) {
                return seq >= initSeq || seq <= endSeq;
            } else {
                return seq >= initSeq && seq <= endSeq;
            }
        }
    }


}
