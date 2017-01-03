package com.sli.code.rpc.Components.provider;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServerThreadPool {
	
	private static ThreadPoolExecutor threadPoolExecutor ;
	
	public static void submit(Runnable task)
	{
		if(null == threadPoolExecutor)
		{
			synchronized (ServerThreadPool.class) 
			{
				if(null == threadPoolExecutor)
				{
					threadPoolExecutor = new ThreadPoolExecutor(16, 16, 600L, TimeUnit.SECONDS
							, new ArrayBlockingQueue<Runnable>(65536));
				}
			}
		}
		threadPoolExecutor.submit(task);
	}
	
	public static void shutDown()
	{
		if(null != threadPoolExecutor)
		{
			threadPoolExecutor.shutdown();
		}
		
	}
}
