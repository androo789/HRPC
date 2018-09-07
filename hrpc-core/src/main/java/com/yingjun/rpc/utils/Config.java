package com.yingjun.rpc.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * 读取配置文件
 *
 * @author yingjun
 */
public class Config {

    public static final int ZK_SESSION_TIMEOUT = 5000;
    public static final String ZK_ROOT_PATH = "/HRPC";

    private static Properties properties;//Properties这是java工具类

    static {
       try {
           InputStream inputStream = Config.class.getClassLoader().getResourceAsStream("system.properties");
           // ClassLoader.getResourceAsStream() 表示去classpath下寻找这个文件，，，
           //这一句是什么意思？？？system.properties文件在核心包里面没有啊？？？,我这个类的类加载器？？？？？
           //明白了，就是在client的机器上，使用hrpc-core里面的这个类，，，此时的classpath还是就是client的classpath，
           // 印象中spring的classpath就是
           BufferedReader bf = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
           properties = new Properties();
           properties.load(bf);
       } catch (IOException e1) {
           e1.printStackTrace();
       }
   }

    public static int getIdntProperty(String key) {
        return Integer.parseInt(properties.getProperty(key));
    }

    public static String getSdtringProperty(String key) {
        return properties.getProperty(key);
    }
}
