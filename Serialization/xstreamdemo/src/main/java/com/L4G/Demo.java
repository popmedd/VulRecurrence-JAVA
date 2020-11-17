package com.L4G;

import com.sun.beans.finder.MethodFinder;
import sun.reflect.misc.MethodUtil;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

//把最终导致了反序列化漏洞的代码单独拿出来
public class Demo {
    public static void main(String[] args) {
        Comparable<String> realSubject = new RealSubject();
        InvocationHandler handler = new DynamicProxy(realSubject);
        Comparable<String> proxy = (Comparable<String>) Proxy.newProxyInstance(handler.getClass().getClassLoader(), realSubject.getClass().getInterfaces(), handler);
        System.out.println(proxy.getClass().getName());
        proxy.compareTo(null);
    }
}

class RealSubject implements Comparable {

    @Override
    public int compareTo(Object o) {
        return 0;
    }
}
class DynamicProxy implements InvocationHandler {
    Comparable realSubject;
    String action = "start";
    Object target;

    public DynamicProxy(Comparable<String> realSubject) {
        this.realSubject = realSubject;
    }

    public Object invoke(Object object, Method method, Object[] args) throws Throwable {
        this.realSubject.compareTo(args);
        invokeInternal(object, method, args);

        return null;
    }

    public Object invokeInternal(Object proxy, Method method, Object[] arguments) throws Exception {
        action = "start";
        target = new ProcessBuilder("open", "/System/Applications/Calculator.app");
        Method method1 = getMethod(target.getClass(), action, new Class[]{});
        return MethodUtil.invoke(method1, target, new Object[]{});
    }

    public Method getMethod(Class<?> type, String name, Class<?>... args){
        try {
            return MethodFinder.findMethod(type, name, args);
        }
        catch (NoSuchMethodException exception) {
            return null;
        }
    }
}