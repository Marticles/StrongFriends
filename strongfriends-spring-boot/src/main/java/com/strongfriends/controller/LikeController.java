package com.strongfriends.controller;

import com.alibaba.fastjson.JSON;
import com.strongfriends.async.EventModel;
import com.strongfriends.async.EventProducer;
import com.strongfriends.async.EventType;
import com.strongfriends.model.EntityType;
import com.strongfriends.model.HostHolder;
import com.strongfriends.model.Like;
import com.strongfriends.model.News;
import com.strongfriends.service.LikeService;
import com.strongfriends.service.NewsService;
import com.strongfriends.util.StrongFriendsUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class LikeController {
    @Autowired
    LikeService likeService;

    @Autowired
    HostHolder hostHolder;

    @Autowired
    NewsService newsService;

    @Autowired
    EventProducer eventProducer;

    @RequestMapping(path = {"/like"}, method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public String like(@RequestParam("newsId") int newsId) {
        Like like = new Like();
        News news = newsService.getById(newsId);
        if (hostHolder.getUser() == null) {
            return StrongFriendsUtil.getJSONString(1);
        }

        int userId = hostHolder.getUser().getId();
        like.setUserId(userId);
        like.setEntityType(EntityType.ENTITY_POST);
        like.setEntityId(newsId);
        String likeString = JSON.toJSONString(like, true);

        eventProducer.produceEvent(new EventModel(EventType.LIKE)
                .setEntityOwnerId(news.getUserId())
                .setActorId(hostHolder.getUser().getId()).setEntityId(newsId)
                .setExt("like", likeString));

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String likeMsg = String.valueOf(likeService.getLikeNum(newsId, EntityType.ENTITY_POST));
        String disLikeMsg = String.valueOf(likeService.getDisLikeNum(newsId, EntityType.ENTITY_POST));
        String returnJson = StrongFriendsUtil.getJSONString(0, likeMsg+"-"+disLikeMsg);

        return returnJson;
    }

    @RequestMapping(path = {"/dislike"}, method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseBody
    public String disLike(@RequestParam("newsId") int newsId) {
        if (hostHolder.getUser() == null) {
            return StrongFriendsUtil.getJSONString(1);
        }
        Like like = new Like();
        int userId = hostHolder.getUser().getId();

        like.setUserId(userId);
        like.setEntityType(EntityType.ENTITY_POST);
        like.setEntityId(newsId);
        String likeString = JSON.toJSONString(like, true);

        News news = newsService.getById(newsId);

        eventProducer.produceEvent(new EventModel(EventType.DISLIKE)
                .setEntityOwnerId(news.getUserId())
                .setActorId(hostHolder.getUser().getId()).setEntityId(newsId)
                .setExt("dislike", likeString));

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        String likeMsg = String.valueOf(likeService.getLikeNum(newsId, EntityType.ENTITY_POST));
        String disLikeMsg = String.valueOf(likeService.getDisLikeNum(newsId, EntityType.ENTITY_POST));
        String returnJson = StrongFriendsUtil.getJSONString(0, likeMsg+"-"+disLikeMsg);

        return returnJson;
    }
}