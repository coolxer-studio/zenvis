package com.coolxer.plugin.controller;

import java.io.Serializable;

/**
 * 系统信息
 *
 * @author hunter
 */
public class UserVo implements Serializable {

    private String id;
    private String user;
    private String card;

    public UserVo() {}

    public UserVo(String id, String user, String card) {
        this.id = id;
        this.user = user;
        this.card = card;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getCard() { return card; }
    public void setCard(String card) { this.card = card; }

}
