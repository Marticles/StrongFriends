package com.strongfriends.controller;

import com.strongfriends.async.EventModel;
import com.strongfriends.async.EventProducer;
import com.strongfriends.async.EventType;
import com.strongfriends.model.News;
import com.strongfriends.model.ViewObject;
import com.strongfriends.service.NewsService;
import com.strongfriends.service.UserService;
import com.strongfriends.util.StrongFriendsUtil;
import com.strongfriends.util.StrongFriendsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class LoginController {
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    UserService userService;

    @Autowired
    EventProducer eventProducer;

    @RequestMapping(path = {"/reg/"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public String reg(Model model, @RequestParam("username") String username,
                      @RequestParam("password") String password,
                      @RequestParam(value="rember", defaultValue = "0") int rememberme,
                      HttpServletResponse response) {
        try {
            Map<String, Object> map = userService.register(username, password);
            if (map.containsKey("ticket")) {
                Cookie cookie = new Cookie("ticket", map.get("ticket").toString());
                cookie.setPath("/");
                if (rememberme > 0) {
                    cookie.setMaxAge(3600*24*5);
                }
                response.addCookie(cookie);
                // 将cookie设置成HttpOnly以防止XSS攻击
                response.addHeader("Set-Cookie", "HttpOnly");
                return StrongFriendsUtil.getJSONString(0, "注册成功");
            } else {
                return StrongFriendsUtil.getJSONString(1, map);
            }

        } catch (Exception e) {
            logger.error("注册异常" + e.getMessage());
            return StrongFriendsUtil.getJSONString(1, "注册异常");
        }
    }

    @RequestMapping(path = {"/login/"}, method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public String login(Model model, @RequestParam("username") String username,
                        @RequestParam("password") String password,
                        @RequestParam(value="rember", defaultValue = "0") int rememberme,
                        HttpServletResponse response) {
        try {
            Map<String, Object> map = userService.login(username, password);
            if (map.containsKey("ticket")) {
                Cookie cookie = new Cookie("ticket", map.get("ticket").toString());
                cookie.setPath("/");
                if (rememberme > 0) {
                    cookie.setMaxAge(3600*24*5);
                }
                response.addCookie(cookie);
                                return StrongFriendsUtil.getJSONString(0, "登录成功");
            } else {
                return StrongFriendsUtil.getJSONString(1, map);
            }

        } catch (Exception e) {
            logger.error("登录异常" + e.getMessage());
            return StrongFriendsUtil.getJSONString(1, "登录异常");
        }
    }

    @RequestMapping(path = {"/logout/"}, method = {RequestMethod.GET, RequestMethod.POST})
    public String logout(@CookieValue("ticket") String ticket) {
        userService.logout(ticket);
        return "redirect:/";
    }





}
