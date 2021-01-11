package cn.xiaofei.rudp;

/**
 * 发送任务，提供发送的实时信息
 *
 * @author Xiao Fei, jdfohewk@gmail.com
 * @version 2021.1
 */
public class SendTask {
    public SendTask(long sendId, int total, int initSeq) {
        this.sendId = sendId;
        this.total = total;
        this.totalPackages = total / 1024 + 1;
        if (total % 1024 == 0) this.totalPackages--;
        this.initSeq = initSeq;
    }

    long sendId;
    boolean connected = false;

    Runnable onConnected;
    Runnable onSending;
    Runnable onCompleted;
    Runnable onFailed;

    /**
     * 需要发送的总字节数
     */
    final int total;

    /**
     * 需要发送的数据小包总数
     */
    int totalPackages;

    /**
     * 已发送的字节数
     */
    int sent = 0;

    /**
     * 初始序列号
     */
    final int initSeq;

    /**
     * 获取已经发送的数据的百分比
     *
     * @return The percentage of data that has been sent.
     */
    public float getPercentage() {
        return (float) sent / (float) total;
    }

    /**
     * 连接成功回调
     */
    public SendTask onConnected(Runnable onConnected) {
        this.onConnected = onConnected;
        return this;
    }

    /**
     * 发送中回调
     */
    public SendTask onSending(Runnable onSending) {
        this.onSending = onSending;
        return this;
    }

    /**
     * 发送完成回调
     */
    public SendTask onCompleted(Runnable onCompleted) {
        this.onCompleted = onCompleted;
        return this;
    }

    /**
     * 发送结束回调
     */
    public SendTask onFailed(Runnable onFailed) {
        this.onFailed = onFailed;
        return this;
    }

}
