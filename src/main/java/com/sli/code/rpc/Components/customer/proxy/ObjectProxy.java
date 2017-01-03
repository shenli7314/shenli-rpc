package com.sli.code.rpc.Components.customer.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sli.code.rpc.Components.comm.RpcRequest;
import com.sli.code.rpc.Components.customer.RPCFuture;
import com.sli.code.rpc.Components.customer.RpcChannelHandlerPool;
import com.sli.code.rpc.Components.customer.RpcClientChannelHandler;


public class ObjectProxy<T> implements InvocationHandler, IAsyncObjectProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectProxy.class);
    private Class<T> clazz;

    public ObjectProxy(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Object.class == method.getDeclaringClass()) {
            String name = method.getName();
            if ("equals".equals(name)) {
                return proxy == args[0];
            } else if ("hashCode".equals(name)) {
                return System.identityHashCode(proxy);
            } else if ("toString".equals(name)) {
                return proxy.getClass().getName() + "@" +
                        Integer.toHexString(System.identityHashCode(proxy)) +
                        ", with InvocationHandler " + this;
            } else {
                throw new IllegalStateException(String.valueOf(method));
            }
        }

        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);
        // Debug
        StringBuilder rpcRequestInfo = new StringBuilder();
        rpcRequestInfo.append("======requestClass:["+method.getDeclaringClass().getName()+"];");
        rpcRequestInfo.append("method:["+method.getName()+"];argsType:[");
        for (int i = 0; i < method.getParameterTypes().length; ++i) {
        	rpcRequestInfo.append(method.getParameterTypes()[i].getName()+",");
        }
        rpcRequestInfo.append("];argsValues:[");
        for (int i = 0; i < args.length; ++i) {
        	rpcRequestInfo.append(args[i].toString()+",");
        }
        rpcRequestInfo.append("]");
        LOGGER.debug(rpcRequestInfo.toString());
        RpcClientChannelHandler handler = RpcChannelHandlerPool.getInstance().chooseHandler();
        RPCFuture rpcFuture = handler.sendRequest(request);
        return rpcFuture.get();
    }

    @Override
    public RPCFuture call(String funcName, Object... args) {
    	RpcClientChannelHandler handler = RpcChannelHandlerPool.getInstance().chooseHandler();
        RpcRequest request = createRequest(this.clazz.getName(), funcName, args);
        RPCFuture rpcFuture = handler.sendRequest(request);
        return rpcFuture;
    }

    private RpcRequest createRequest(String className, String methodName, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(className);
        request.setMethodName(methodName);
        request.setParameters(args);

        Class[] parameterTypes = new Class[args.length];
        // Get the right class type
        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = getClassType(args[i]);
        }
        request.setParameterTypes(parameterTypes);
//        Method[] methods = clazz.getDeclaredMethods();
//        for (int i = 0; i < methods.length; ++i) {
//            // Bug: if there are 2 methods have the same name
//            if (methods[i].getName().equals(methodName)) {
//                parameterTypes = methods[i].getParameterTypes();
//                request.setParameterTypes(parameterTypes); // get parameter types
//                break;
//            }
//        }        
        // Debug
        StringBuilder rpcRequestInfo = new StringBuilder();
        rpcRequestInfo.append("======requestClass:["+className+"];");
        rpcRequestInfo.append("method:["+methodName+"];argsType:[");
        for (int i = 0; i < parameterTypes.length; ++i) {
        	rpcRequestInfo.append(parameterTypes[i].getName()+",");
        }
        rpcRequestInfo.append("];argsValues:[");
        for (int i = 0; i < args.length; ++i) {
        	rpcRequestInfo.append(args[i].toString()+",");
        }
        rpcRequestInfo.append("]");
        LOGGER.debug(rpcRequestInfo.toString());

        return request;
    }

    private Class<?> getClassType(Object obj){
        Class<?> classType = obj.getClass();
        String typeName = classType.getName();
        switch (typeName){
            case "java.lang.Integer":
                return Integer.TYPE;
            case "java.lang.Long":
                return Long.TYPE;
            case "java.lang.Float":
                return Float.TYPE;
            case "java.lang.Double":
                return Double.TYPE;
            case "java.lang.Character":
                return Character.TYPE;
            case "java.lang.Boolean":
                return Boolean.TYPE;
            case "java.lang.Short":
                return Short.TYPE;
            case "java.lang.Byte":
                return Byte.TYPE;
        }

        return classType;
    }

}
