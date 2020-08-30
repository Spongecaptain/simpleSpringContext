package cool.spongecaptain.test;

import cool.spongecaptain.service.UserService;
import cool.spongecaptain.util.BeanFactory;

/**
 * @program: simpleSpringContext
 * @description:
 * @author: Spongecaptain
 * @created: 2020/08/30 09:59
 */
public class Test {
    public static void main(String[] args) {

        BeanFactory beanFactory = new BeanFactory("spring.xml");
        UserService service = (UserService)beanFactory.getBean("service");
        service.find();
    }


}
