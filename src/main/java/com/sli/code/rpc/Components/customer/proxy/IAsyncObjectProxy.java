package com.sli.code.rpc.Components.customer.proxy;

import com.sli.code.rpc.Components.customer.RPCFuture;

public interface IAsyncObjectProxy {
    public RPCFuture call(String funcName, Object... args);
}