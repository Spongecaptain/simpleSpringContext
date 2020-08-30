package cool.spongecaptain.service;

import cool.spongecaptain.dao.UserDao;

/**
 * @program: simpleSpringContext
 * @description:
 * @author: Spongecaptain
 * @created: 2020/08/30 09:57
 */
public class UserServiceImpl implements UserService{
    UserDao dao;
    public void find() {
        System.out.println("service");
        dao.query();
    }
}
