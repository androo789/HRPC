package com.yingjun.rpc.protocol;

/**
 * 请求包
 *
 * 这类是干啥的？？？
 *
 * @author yingjun
 */
public class RPCRequest {

    private String requestId;//这个id是干啥用的？？？看见过忘了？？？ TODO
    //注意这里虽然是一个id，但是类型是string，而不是int！！！
    private String className;
    private String methodName;
    private Object[] parameters;
    private Class<?>[] parameterTypes;

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }
}