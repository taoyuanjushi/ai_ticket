package com.example.hello_demo.vo;

import com.example.hello_demo.entity.Ticket;

import java.util.List;

/**
 * 工单详情返回对象。
 * 用于返回工单本身、提交人信息和回复列表。
 */
public class TicketDetailVO {

    private Ticket ticket;
    private UserInfoVO user;
    private List<TicketReplyVO> replies;

    public TicketDetailVO() {
    }

    public TicketDetailVO(Ticket ticket, UserInfoVO user, List<TicketReplyVO> replies) {
        this.ticket = ticket;
        this.user = user;
        this.replies = replies;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public UserInfoVO getUser() {
        return user;
    }

    public void setUser(UserInfoVO user) {
        this.user = user;
    }

    public List<TicketReplyVO> getReplies() {
        return replies;
    }

    public void setReplies(List<TicketReplyVO> replies) {
        this.replies = replies;
    }
}
