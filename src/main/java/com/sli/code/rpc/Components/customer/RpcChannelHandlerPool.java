package com.sli.code.rpc.Components.customer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sli.code.rpc.Components.comm.RpcDecoder;
import com.sli.code.rpc.Components.comm.RpcEncoder;
import com.sli.code.rpc.Components.comm.RpcRequest;
import com.sli.code.rpc.Components.comm.RpcResponse;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class RpcChannelHandlerPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcChannelHandlerPool.class);
    private volatile static RpcChannelHandlerPool channelHandlerPool;

    EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);
    //当前链接节点
    private CopyOnWriteArrayList<RpcClientChannelHandler> connectedHandlers = new CopyOnWriteArrayList<>();
    
    private Map<InetSocketAddress, RpcClientChannelHandler> connectedServerNodes = new ConcurrentHashMap<>();
    //private Map<InetSocketAddress, Channel> connectedServerNodes = new ConcurrentHashMap<>();

    private ReentrantLock lock = new ReentrantLock();
    private Condition connected = lock.newCondition();
    protected long connectTimeoutMillis = 6000;
    private AtomicInteger roundRobin = new AtomicInteger(0);
    private volatile boolean isRuning = true;

    private RpcChannelHandlerPool() {
    }

    public static RpcChannelHandlerPool getInstance() {
        if (channelHandlerPool == null) {
            synchronized (RpcChannelHandlerPool.class) {
                if (channelHandlerPool == null) {
                	channelHandlerPool = new RpcChannelHandlerPool();
                }
            }
        }
        return channelHandlerPool;
    }

    public void updateConnectedHandler(List<String> allServerAddress) {
        if (allServerAddress != null) {
            if (allServerAddress.size() > 0) {  // Get available server node
                //update local serverNodes cache
                HashSet<InetSocketAddress> newAllServerNodeSet = new HashSet<InetSocketAddress>();
                for (int i = 0; i < allServerAddress.size(); ++i) {
                    String[] array = allServerAddress.get(i).split(":");
                    if (array.length == 2) { // Should check IP and port
                        String host = array[0];
                        int port = Integer.parseInt(array[1]);
                        final InetSocketAddress remotePeer = new InetSocketAddress(host, port);
                        newAllServerNodeSet.add(remotePeer);
                    }
                }

                // Add new server node
                for (final InetSocketAddress serverNodeAddress : newAllServerNodeSet) {
                    if (!connectedServerNodes.keySet().contains(serverNodeAddress)) {
                        connectServerNode(serverNodeAddress);
                    }
                }

                // Close and remove invalid server nodes
                for (int i = 0; i < connectedHandlers.size(); ++i) {
                    RpcClientChannelHandler connectedServerHandler = connectedHandlers.get(i);
                    SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
                    if (!newAllServerNodeSet.contains(remotePeer)) {
                        LOGGER.info("Remove invalid server node " + remotePeer);
                        RpcClientChannelHandler handler = connectedServerNodes.get(remotePeer);
                        handler.close();
                        connectedServerNodes.remove(remotePeer);
                        connectedHandlers.remove(connectedServerHandler);
                    }
                }

            } else { // No available server node ( All server nodes are down )
                LOGGER.error("No available server node. All server nodes are down !!!");
                for (final RpcClientChannelHandler connectedServerHandler : connectedHandlers) {
                    SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
                    RpcClientChannelHandler handler = connectedServerNodes.get(remotePeer);
                    handler.close();
                    connectedServerNodes.remove(connectedServerHandler);
                }
                connectedHandlers.clear();
            }
        }
    }

    public void  reconnect(final RpcClientChannelHandler handler, final SocketAddress remotePeer){
        if(handler!=null){
            connectedHandlers.remove(handler);
            connectedServerNodes.remove(handler.getRemotePeer());
        }
        connectServerNode((InetSocketAddress)remotePeer);
    }

    private void connectServerNode(final InetSocketAddress remotePeer) {
    	ClientThreadPool.submit(new Runnable() {
            @Override
            public void run() {
                Bootstrap b = new Bootstrap();
                b.group(eventLoopGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new RpcInitializerHandler());

                ChannelFuture channelFuture = b.connect(remotePeer);
                channelFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(final ChannelFuture channelFuture) throws Exception {
                        if (channelFuture.isSuccess()) {
                            LOGGER.debug("Successfully connect to remote server. remote peer = " + remotePeer);
                            RpcClientChannelHandler handler = channelFuture.channel().pipeline().get(RpcClientChannelHandler.class);
                            addHandler(handler);
                        }
                    }
                });
            }
        });
    }

    private void addHandler(RpcClientChannelHandler handler) {
        connectedHandlers.add(handler);
        InetSocketAddress remoteAddress = (InetSocketAddress) handler.getChannel().remoteAddress();
        connectedServerNodes.put(remoteAddress, handler);
        signalAvailableHandler();
    }

    private void signalAvailableHandler() {
        lock.lock();
        try {
            connected.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private boolean waitingForHandler() throws InterruptedException {
        lock.lock();
        try {
            return connected.await(this.connectTimeoutMillis, TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }
//轮询选择handler处理请求
    public RpcClientChannelHandler chooseHandler() {
        CopyOnWriteArrayList<RpcClientChannelHandler> handlers = (CopyOnWriteArrayList<RpcClientChannelHandler>) this.connectedHandlers.clone();
        int size = handlers.size();
        while (isRuning && size <= 0) {
            try {
                boolean available = waitingForHandler();
                if (available) {
                    handlers = (CopyOnWriteArrayList<RpcClientChannelHandler>) this.connectedHandlers.clone();
                    size = handlers.size();
                }
            } catch (InterruptedException e) {
                LOGGER.error("Waiting for available node is interrupted! ", e);
                throw new RuntimeException("Can't connect any servers!", e);
            }
        }
        int index = (roundRobin.getAndAdd(1) + size) % size;
        LOGGER.debug("=====总请求次数："+roundRobin+";handler个数："+size);
        return handlers.get(index);
    }

    public void stop(){
        isRuning = false;
        for (int i = 0; i < connectedHandlers.size(); ++i) {
            RpcClientChannelHandler connectedServerHandler = connectedHandlers.get(i);
            connectedServerHandler.close();
        }
        signalAvailableHandler();
        ClientThreadPool.shutDown();
        eventLoopGroup.shutdownGracefully();
    }
    
    private static class RpcInitializerHandler extends ChannelInitializer<SocketChannel>
    {
    	@Override
        protected void initChannel(SocketChannel socketChannel) throws Exception {
            ChannelPipeline cp = socketChannel.pipeline();
            cp.addLast(new RpcEncoder(RpcRequest.class));
            cp.addLast(new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 0));
            cp.addLast(new RpcDecoder(RpcResponse.class));
            cp.addLast(new RpcClientChannelHandler());
        }
    }
    
}
