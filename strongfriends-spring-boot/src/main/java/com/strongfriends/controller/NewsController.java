package com.strongfriends.controller;

import com.alibaba.fastjson.JSON;
import com.strongfriends.async.EventModel;
import com.strongfriends.async.EventProducer;
import com.strongfriends.async.EventType;
import com.strongfriends.model.*;
import com.strongfriends.service.CommentService;
import com.strongfriends.service.LikeService;
import com.strongfriends.service.NewsService;
import com.strongfriends.service.UserService;
import com.strongfriends.util.StrongFriendsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

@Controller
public class NewsController {
    private static final Logger logger = LoggerFactory.getLogger(NewsController.class);

    @Autowired
    NewsService newsService;

    @Autowired
    UserService userService;

    @Autowired
    CommentService commentService;

    @Autowired
    HostHolder hostHolder;

    @Autowired
    LikeService likeService;

    @Autowired
    EventProducer eventProducer;



    @RequestMapping(path = {"/news/{newsId}"}, method = {RequestMethod.GET})
    public String newsDetail(@PathVariable("newsId") int newsId, Model model) {
        News news = newsService.getById(newsId);
        if (news != null) {
            int localUserId = hostHolder.getUser() != null ? hostHolder.getUser().getId() : 0;
            if (localUserId != 0) {
                model.addAttribute("like", likeService.getLikeStatus(localUserId, EntityType.ENTITY_POST, news.getId()));
            } else {
                model.addAttribute("like", 0);
            }

            List<ViewObject> commentVOs = new ArrayList<ViewObject>();

            // 从Redis中拿评论
            HashSet commentsRedis = new HashSet(commentService.getCommentsByEntityFromRedis(newsId));
            int sizeFromRedis = commentsRedis.size();

            // 从MySQL中拿评论
            List<Comment> commentsMySQL = commentService.getCommentsByEntity(news.getId(), EntityType.ENTITY_COMMENT);
            int sizeFromMySQL = commentsMySQL.size();
            Iterator<Comment> it = commentsRedis.iterator();

            // 如果Redis与MySQL中的评论数量一致
            if(sizeFromMySQL==sizeFromRedis){
                while(it.hasNext()){
                    Comment comment = JSON.parseObject(String.valueOf(it.next()),Comment.class);
                    ViewObject vo = new ViewObject();
                    vo.set("comment", comment);
                    vo.set("floor",sizeFromRedis);
                    vo.set("user", userService.getUser(comment.getUserId()));
                    vo.set("userName",userService.getUser(comment.getUserId()).getName());
                    commentVOs.add(vo);
                    sizeFromRedis -=1;
                }
            // 如果不一致(读MySQL返回，再把Mysql数据写入Redis缓存)

            }else{
                synchronized (this) {

                    // 读MySQL数据
                    for (Comment comment : commentsMySQL) {
                        ViewObject vo = new ViewObject();
                        vo.set("comment", comment);
                        vo.set("floor",sizeFromMySQL);
                        vo.set("user", userService.getUser(comment.getUserId()));
                        vo.set("userName",userService.getUser(comment.getUserId()).getName());
                        commentVOs.add(vo);
                        sizeFromMySQL-=1;
                    }

                    // 将MySQL数据写入Redis缓存
                    for (Comment comment : commentsMySQL) {
                        commentService.addCommentToRedis(comment);
                    }


                }

            }

            model.addAttribute("comments", commentVOs);
        }
        news.setLikeCount((int) likeService.getLikeNum(news.getId(),EntityType.ENTITY_POST));
        news.setDisLikeCount((int) likeService.getDisLikeNum(news.getId(),EntityType.ENTITY_POST));
        model.addAttribute("news", news);
        model.addAttribute("owner", userService.getUser(news.getUserId()));

        return "detail";
    }

    @RequestMapping(path = {"/image"}, method = {RequestMethod.GET})
    @ResponseBody
    public void getImage(@RequestParam("name") String imageName,
                         HttpServletResponse response) {
        try {
            response.setContentType("image");
            StreamUtils.copy(new FileInputStream(new
                    File(StrongFriendsUtil.IMAGE_DIR +"/"+ imageName)), response.getOutputStream());
        } catch (Exception e) {
            logger.error("读取图片错误" + imageName + e.getMessage());
        }
    }

    @RequestMapping(path = {"/uploadImage/"}, method = {RequestMethod.POST})
    @ResponseBody
    public String uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String fileUrl = newsService.saveImage(file);
            if (fileUrl == null) {
                return StrongFriendsUtil.getJSONString(1, "上传图片失败");
            }
            return StrongFriendsUtil.getJSONString(0, fileUrl);
        } catch (Exception e) {
            logger.error("上传图片失败" + e.getMessage());
            return StrongFriendsUtil.getJSONString(1, "上传失败");
        }
    }

    @RequestMapping(path = {"/user/addNews/"}, method = {RequestMethod.POST})
    @ResponseBody
    public String addNews(@RequestParam("image") String image,
                          @RequestParam("title") String title,
                          @RequestParam("link") String link) {
        try {
            News news = new News();
            news.setCreatedDate(new Date());
            news.setTitle(title);
            news.setImage(image);
            news.setLink(link);
            if (hostHolder.getUser() != null) {
                news.setUserId(hostHolder.getUser().getId());
            } else {
                // 匿名用户id
                news.setUserId(888888888);
            }


            // newsService.addNews(news);

            String newsString = JSON.toJSONString(news, true);
            eventProducer.produceEvent(new EventModel(EventType.POST)
                    .setExt("news",newsString));

            return StrongFriendsUtil.getJSONString(0);
        } catch (Exception e) {
            logger.error("添加新帖失败" + e.getMessage());
            return StrongFriendsUtil.getJSONString(1, "发布失败");
        }
    }

    @RequestMapping(path = {"/addComment"}, method = {RequestMethod.POST})
    public String addComment(@RequestParam("newsId") int newsId,
                             @RequestParam("content") String content) {
        try {
            Comment comment = new Comment();
            comment.setUserId(hostHolder.getUser().getId());
            comment.setContent(content);
            comment.setEntityType(EntityType.ENTITY_COMMENT);
            comment.setEntityId(newsId);
            comment.setCreatedDate(new Date());
            comment.setStatus(0);
            // commentService.addComment(comment);

            String commentJson = JSON.toJSONString(comment, true);

            eventProducer.produceEvent(new EventModel(EventType.COMMENT)
                    .setActorId(hostHolder.getUser().getId())
                    .setEntityId(comment.getEntityId())
                    .setEntityOwnerId(newsService.getUserId(newsId))
                    .setExt("msg",commentJson));

        } catch (Exception e) {
            logger.error("提交评论错误" + e.getMessage());
        }
        return "redirect:/news/" + String.valueOf(newsId);
    }
}
