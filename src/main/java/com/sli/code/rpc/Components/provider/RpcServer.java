package com.sli.code.rpc.Components.provider;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.sli.code.rpc.Components.comm.RpcConfig;
import com.sli.code.rpc.Components.comm.RpcDecoder;
import com.sli.code.rpc.Components.comm.RpcEncoder;
import com.sli.code.rpc.Components.comm.RpcRequest;
import com.sli.code.rpc.Components.comm.RpcResponse;
import com.sli.code.rpc.util.LoadConfigUtil;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.util.internal.StringUtil;

public class RpcServer implements ApplicationContextAware, InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServer.class);

    private String serverAddress;
    private ServiceRegistry serviceRegistry;

    private Map<String, Object> handlerMap = new HashMap<>();

    public RpcServer() {
    	init();
    	LOGGER.info("====服务端初始化初始化完成=====");
    }

    public void init()
    {
    	if(StringUtil.isNullOrEmpty(RpcConfig.registryAddress)
    			|| StringUtil.isNullOrEmpty(RpcConfig.serverAddress))
    	{
    		LoadConfigUtil.loadConfig(RpcConfig.class,"rpc.properties");
    	}
    	this.serviceRegistry = new ServiceRegistry(RpcConfig.registryAddress);
        this.serverAddress = RpcConfig.serverAddress;
    }

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        Map<String, Object> serviceBeanMap = ctx.getBeansWithAnnotation(RpcService.class);
        if (MapUtils.isNotEmpty(serviceBeanMap)) {
            for (Object serviceBean : serviceBeanMap.values()) {
                String interfaceName = serviceBean.getClass().getAnnotation(RpcService.class).value().getName();
                handlerMap.put(interfaceName, serviceBean);
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        	@Override
                            public void initChannel(SocketChannel channel) throws Exception {
                                channel.pipeline()
                                        .addLast(new LengthFieldBasedFrameDecoder(65536,0,4,0,0))
                                        	// 将 RPC 请求进行解码（为了处理请求）
                                        .addLast(new RpcDecoder(RpcRequest.class))
                                     // 将 RPC 响应进行编码（为了返回响应）
                                        .addLast(new RpcEncoder(RpcResponse.class))
                                     // 处理 RPC 请求
                                        .addLast(new RpcServerHandler(handlerMap));
                            }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            String[] array = serverAddress.split(":");
            String host = array[0];
            int port = Integer.parseInt(array[1]);

            ChannelFuture future = bootstrap.bind(host, port).sync();
            LOGGER.debug("Server started on port {}", port);
            System.out.println("======Server started on port========"+port);
            if (serviceRegistry != null) {
                serviceRegistry.register(serverAddress);
            }

            future.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
