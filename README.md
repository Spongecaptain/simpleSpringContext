# 自己实现一个简单的 SpringContext

> GitHub 项目地址：https://github.com/Spongecaptain/simpleSpringContext

## 1. SpringContext 的需求

在阅读 Spring 源码之前，我们很容易把 Spring 想象地国过于神秘，Spring 一定是依靠于某种魔法将各个类装配到一起，我们只需要配置，而是将类于类的依赖关系写死在代码中。

事实上，Spring 并不全能，也不神秘，我们需要告诉 Spring Context 各种东西来告诉它如何创建实例、注入依赖：

- 哪些类需要 IoC 容器来帮助我们构造；
- 类与类之间的依赖关系是如何；
- 实例需要通过哪种方式实现依赖注入，setter 方法还是构造器方法；

这一切内容既可以由 XML 配置来完成，也可以由 JDK1.5 推出的注解来完成。我在这以 XML 配置为例。

## 2. 待注入类

首先，我们需要满足面向接口编程，也就是说，A 类只会依赖于 B 接口，B 接口的真正实现类在配置文件中做出规定。

其次，这里我们假设 service 包依赖于 dao 包，但是 service 包下的类不主动调用 new 来构造一个 dao 类，而是需要通过 Spring Context 来帮助我们注入相关依赖。

**1.dao 包下的接口与类**

```java
public interface UserDao {
    void query();
}
```

```java
public class UserDaoImpl implements UserDao{
    public void query() {
        System.out.println("dao");
    }
}
```

**2.service 包下的接口与类**

```java
public interface UserService {
    void find();
}
```

```java
public class UserServiceImpl implements UserService{
    UserDao dao;//ServiceImpl 类依赖于 UserDao 接口的某一个实现类，但是这里不显式确定
    public void find() {
        System.out.println("service");
        dao.query();
    }
}
```

## 3. XML 配置文件

第一节说了 Spring Context 不是万能的，我们需要以某种方式告诉 Spring Context 如何来实现自动注入，我们这里以 XML 配置文件方式来说明：

```xml
<?xml version="1.0" encoding="UTF-8"?>

<bean>
    <bean id="dao" class="cool.spongecaptain.dao.UserDaoImpl"/>
	
    <bean id="service" class="cool.spongecaptain.service.UserServiceImpl">
        <property name = "dao" ref="dao"/>
    </bean>

</bean>
```

> 我们可以发现：与代码中面向接口编程不同，配置文件有着截然不同的特点：使用接口的实现类，而不是接口本身。

这里我们申明了两个类需要 Context 替我们完成构造与初始化：UserDaoImpl 与 UserServiceImpl，其中后者依赖于前者。

## 4. BeanFactory

BeanFactory 是实现 Spring 自动注入的关键，其通过读取配置文件来得知程序员的需求。但是注意，Spring 的 BeanFactory 要复杂很多，我这里知识一个非常简单的模型。

首先我们要了解 XML 的解析，对于一个 XML 文件，我们总是要将其配置成一个 DOM 树，然后依次读取树上的各个节点，因此我们需要能够解析 XML 的第三方依赖，这里我们可以使用 dom4j：

```xml
<dependency>
    <groupId>dom4j</groupId>
    <artifactId>dom4j</artifactId>
    <version>1.6.1</version>
</dependency>
```

 在利用 dom4j 来解析 XML 配置文件时，我们可以这么处理各个节点元素：

- bean 元素为每一个实例：
  - id 属性对应于每一个节点的名字，即 beanName；
  - class 属性对应于每一个 bean 的类型；
- property 子元素对应于需要注入的字段：
  - name 属性代表需要注入的字段名（定义于类中）；
  - ref 属性为某一个 bean 元素的 id，指向真正注入的 bean 实例。

其次，上述都在说我们如何来看待以及处理 XML 配置文件，我们接下来要知道如何利用上述配置信息来构造实例。

因为我们在 XML 配置文件中利用 bean 的 class 属性制定了实例的具体类型，但这是字符串，因此我们必须要利用 JDK 提供的反射来构造 bean 实例，主要用到的 API 为：

```java
//clazzName 为定义在 bean 元素内的 class 属性
Class clazz = Class.forName(clazzName);
```

另一方面，我们需要考虑如何构造一个未初始化完毕的 Bean 实例。类似于 Spring 中 BeanFactory 的构造逻辑，我们构造类首先要构造参数类型为默认类型的实例，这里我们的手段可以简单一点，直接利用 Constructor 类：

```java
object= clazz.newInstance();
```

> 但是如果通过构造器来注入实例，那么我们不能使用上述逻辑，这是因为构造需要入口参数。我们可以通过扫描构造器入口类型，然后根据类型注入类型相对应的默认值，然后注入构造器中进行实例的构造，不过这相对复杂，这里就不提供了。

最后，依赖的注入有不同的选择：

- 如果类有 set 方法，那么我们可以通过 set 方法来将依赖注入，这需要使用 JDK 中反射类 Method；
- 如果类没有 set 方法，但我们还是可以通过反射来完成依赖的实例的注入，这需要使用 JDK 中反射类 Field；

接下来的麻烦便是如何来遍历解析 DOM 树，如何解决依赖问题，这实际并不是重点，这里就不提了。

BeanFactory 类如下所示：

```java
public class BeanFactory {
    /**
     * key:beanName value:beanInstance
     * 这里需要使用 Object 作为类型，因为 Bean 的类型无法统一
     */
    Map<String,Object> map = new HashMap();

    /**
     *
     * @param xml 配置文件
     */
    public BeanFactory(String xml){
        parseXml(xml);
    }

    public void parseXml(String xml) throws BeanException{

        /**
         * 关于目录层架结构的说明：
         * 对于一个 Java 项目，调用任何一个 Class.getResource("/") 都会得到 target/classes/ 目录
         * 具体的 class 文件位于此目录下的具体 package 包中。而 resources/ 目录下的配置文件则会一并打包到 target/class 目录下
         */

        File file = new File(this.getClass().getResource("/").getPath()+xml);
        SAXReader reader = new SAXReader();
        try {
            Document document = reader.read(file);
            Element elementRoot = document.getRootElement();
            Attribute attribute = elementRoot.attribute("default");
            boolean flag=false;
            if (attribute!=null){
                flag=true;
            }
            for (Iterator<Element> itFirlst = elementRoot.elementIterator(); itFirlst.hasNext();) {
                /**
                 * setup1、实例化对象
                 */
                Element elementFirstChil = itFirlst.next();
                Attribute attributeId = elementFirstChil.attribute("id");
                String beanName = attributeId.getValue();
                Attribute attributeClass = elementFirstChil.attribute("class");
                String clazzName  = attributeClass.getValue();
                Class clazz = Class.forName(clazzName);

                /**
                 * 维护依赖关系
                 * 看这个对象有没有依赖（判断是否有property。或者判断类是否有属性）
                 * 如果有则注入
                 */
                Object object = null;
                for (Iterator<Element> itSecond = elementFirstChil.elementIterator(); itSecond.hasNext();){
                    // 得到ref的value，通过value得到对象（map）
                    // 得到name的值，然后根据值获取一个Filed的对象
                    //通过 field 的 set 方法 set 那个对象

                    //<property name="dao" ref="dao"></property>
                    Element elementSecondChil = itSecond.next();
                    if(elementSecondChil.getName().equals("property")){
                        //由于是 setter，沒有特殊的构造方法
                        object= clazz.newInstance();
                        String refVlaue = elementSecondChil.attribute("ref").getValue();
                        Object injetObject= map.get(refVlaue) ;
                        String nameVlaue = elementSecondChil.attribute("name").getValue();
                        Field field = clazz.getDeclaredField(nameVlaue);
                        field.setAccessible(true);
                        field.set(object,injetObject);

                    }else{
                        //证明有特殊的构造
                        String refVlaue = elementSecondChil.attribute("ref").getValue();
                        Object injetObject= map.get(refVlaue) ;
                        Class injectObjectClazz = injetObject.getClass();
                        Constructor constructor = clazz.getConstructor(injectObjectClazz.getInterfaces()[0]);
                        object = constructor.newInstance(injetObject);
                    }

                }
                if(object==null) {
                    if (flag) {
                        if (attribute.getValue().equals("byType")) {
                            //判断是否有依赖
                            Field fields[] = clazz.getDeclaredFields();
                            for (Field field : fields) {
                                //得到属性的类型，比如 String，那么这里就需要 field.getType() = String.class
                                Class injectObjectClazz = field.getType();
                                /**
                                 * 由于是 bytype, 所有需要遍历 map 当中的所有对象
                                 * 判断对象的类型是不是和这个injectObjectClazz相同
                                 */
                                int count = 0;
                                Object injectObject = null;
                                for (String key : map.keySet()) {
                                    Class temp = map.get(key).getClass().getInterfaces()[0];
                                    if (temp.getName().equals(injectObjectClazz.getName())) {
                                        injectObject = map.get(key);
                                        //记录找到一个，因为可能找到多个count
                                        count++;
                                    }
                                }

                                if (count > 1) {
                                    throw new BeanException("依赖注入仅仅需要注入一个实例，但找到了多个 candidate 实例");
                                } else {
                                    object = clazz.newInstance();
                                    field.setAccessible(true);
                                    field.set(object, injectObject);
                                }
                            }
                        }
                    }
                }

                if(object==null){//沒有子标签
                    object = clazz.newInstance();
                }
                map.put(beanName,object);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println(map);
    }
    public Object getBean(String beanName){
        return map.get(beanName);
    }

}
```

