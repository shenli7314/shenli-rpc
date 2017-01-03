package com.sli.code.rpc.util;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.internal.StringUtil;

public class LoadConfigUtil 
{
	private static final Logger LOGGER = LoggerFactory.getLogger(LoadConfigUtil.class);
	
	/**
	 * 
	 * @param clazz 配置对象
	 * @param fileName  配置文件路径
	 * @throws Exception 
	 */
	public static void loadConfig(Class<?> clazz,String fileName) 
	{
		Map<String,String> conf = loadProperties(fileName);
		setConfig(conf,clazz);
		
	}
	
	public static Map<String,String> loadProperties(String fileName)
	{
		Properties prop = new Properties();
		Map<String,String> conf = new HashMap<>();
		try 
		{
			prop.load(LoadConfigUtil.class.getClassLoader().getResourceAsStream(fileName));
			for(Entry<Object, Object> ent : prop.entrySet())
			{
				conf.put((String)ent.getKey(),(String) ent.getValue());
			}
		
		} 
		catch (Exception e) 
		{
			LOGGER.error("=======================ex:{}",e);
		}
		return conf;
	}
	
	public static void setConfig(Map<String,String> conf,Class<?> clazz)
	{
		try 
		{
			Field[] fields = clazz.getFields();
			for(Field f:fields)
			{
				String val = conf.get(f.getName());
				if(StringUtil.isNullOrEmpty(val))
				{
					continue ;
				}
				if(f.getType() == Integer.class || f.getType() == int.class)
				{
					f.set(clazz, Integer.valueOf(val));
				}
				else if(f.getType() == Byte.class || f.getType() == byte.class)
				{
					f.set(clazz, Byte.valueOf(val));
				}
				else if(f.getType() == Long.class || f.getType() == long.class)
				{
					f.set(clazz, Long.valueOf(val));
				}
				else if(f.getType() == Short.class || f.getType() == short.class)
				{
					f.set(clazz, Short.valueOf(val));
				}
				else if(f.getType() == String.class)
				{
					f.set(clazz, String.valueOf(val));
				}
				else if(f.getType() == Double.class || f.getType() == double.class)
				{
					f.set(clazz, Double.valueOf(val));
				}
				else if(f.getType() == Float.class || f.getType() == float.class)
				{
					f.set(clazz, Float.valueOf(val));
				}
				else if(f.getType() == Boolean.class || f.getType() == boolean.class)
				{
					f.set(clazz, Boolean.valueOf(val));
				}
				else if(f.getType() == List.class || f.getType() == ArrayList.class || f.getType() == LinkedList.class)
				{
					List<String> list = new ArrayList<String>();
					Collections.addAll(list, val.split(","));
					f.set(clazz, list);
				}
				else  
				{
					throw new Exception("un supported data type");
				}
			}
		}
		catch (Exception e) 
		{
			LOGGER.error("=======================ex:{}",e);
		}
	}
	
}
