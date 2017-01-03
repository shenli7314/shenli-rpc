package com.sli.code.rpc.Components.comm;

public class RpcConfig 
{
	
	public static int ZK_SESSION_TIMEOUT = 5000;

	public static String ZK_REGISTRY_PATH = "/registry";
	public static String ZK_DATA_PATH = ZK_REGISTRY_PATH + "/data";
	
	public static String registryAddress = "";
	public static String serverAddress = "";
}
