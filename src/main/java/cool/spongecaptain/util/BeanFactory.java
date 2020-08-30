package cool.spongecaptain.util;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @program: simpleSpringContext
 * @description:
 * @author: Spongecaptain
 * @created: 2020/08/30 10:00
 */
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