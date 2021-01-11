# RUDP
Reliable User Datagram Protocol.

#### Usage
```java
//Open a new RUDP channel
RDatagramChannel client1 = RDatagramChannel.open(5854);

//Send data to a specified address
ByteBuffer data = ByteBuffer.wrap("Nice to meet you.".getBytes());
client1.send(data, new InetSocketAddress("localhost", 5855))
        .onConnected(() -> System.out.println("connected")) //Set the connection success callback
        .onCompleted(() -> System.out.println("completed")) //Set the send completion callback
        .onFailed(() -> System.out.println("failed")); //Set the send failure callback

//Set receive listener
client1.setReceiveListener(new ReceiveListener() {
    @Override
    public void onReceived(RDatagram rDatagram) {
        String s = new String(rDatagram.data.array(), StandardCharsets.UTF_8);
        InetSocketAddress address = rDatagram.address;
        System.out.println("data: " + s + ", address: " + address);
    }
});

//Another RUDP channel
RDatagramChannel client2 = RDatagramChannel.open(5855);

//Set the receiving listener
client2.setReceiveListener(rDatagram -> {
    String s = new String(rDatagram.data.array(), StandardCharsets.UTF_8);
    InetSocketAddress address = rDatagram.address;
    System.out.println("data: " + s + ", address: " + address);

    //Send a reply
    if (s.equals("Nice to meet you."))
        client2.send(ByteBuffer.wrap("Nice to meet you too.".getBytes()), address); //send reply
});
```
