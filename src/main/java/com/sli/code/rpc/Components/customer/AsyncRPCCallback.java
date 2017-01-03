package com.sli.code.rpc.Components.customer;

public interface AsyncRPCCallback {

    void success(Object result);

    void fail(Exception e);

}
