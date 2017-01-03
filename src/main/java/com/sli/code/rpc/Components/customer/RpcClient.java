package com.sli.code.rpc.Components.customer;

import java.lang.reflect.Proxy;

import com.sli.code.rpc.Components.comm.RpcConfig;
import com.sli.code.rpc.Components.customer.proxy.IAsyncObjectProxy;
import com.sli.code.rpc.Components.customer.proxy.ObjectProxy;
import com.sli.code.rpc.util.LoadConfigUtil;

import io.netty.util.internal.StringUtil;
/**
 * 远程调用客户端（消费端）
 * @author shenli
 *
 */
public class RpcClient {

    private ServiceDiscovery serviceDiscovery;
    private static RpcClient rpcClient = null;

    private RpcClient()
    {
    	init();
    }
    
    public static RpcClient getInstance()
    {
    	if(null == rpcClient)
    	{
    		synchronized (RpcClient.class) 
    		{
				if(null == rpcClient)
				{
					rpcClient = new RpcClient();
				}
			}
    	}
    	return rpcClient;
    }
    
    private void init()
    {
    	if(StringUtil.isNullOrEmpty(RpcConfig.registryAddress))
    	{
    		LoadConfigUtil.loadConfig(RpcConfig.class,"rpc.properties");
    	}
    	serviceDiscovery = new ServiceDiscovery(RpcConfig.registryAddress);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new ObjectProxy<T>(interfaceClass)
        );
    }

    public static <T> IAsyncObjectProxy createAsync(Class<T> interfaceClass) {
        return new ObjectProxy<T>(interfaceClass);
    }

    public static void submit(Runnable task){
    	ClientThreadPool.submit(task);
    }

    public void stop() {
    	ClientThreadPool.shutDown();
        serviceDiscovery.stop();
        RpcChannelHandlerPool.getInstance().stop();
    }
}

