package com.coolxer.plugin.controller;


/**
 * 资产主机数据传输对象
 */
public class UserDto {

    /**
     * ID
     */
    private String id;

    /**
     * 用户名称
     */
    private String name;

    /**
     * 身份编码
     */
    private String card;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCard() { return card; }
    public void setCard(String card) { this.card = card; }


}
