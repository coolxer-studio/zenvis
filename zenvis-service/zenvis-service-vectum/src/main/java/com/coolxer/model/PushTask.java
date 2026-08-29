package com.coolxer.model;

import lombok.Data;
import java.util.ArrayList;

/**
 * @author yaoqi.li
 */
@Data
public class PushTask {

    private String path;
    private String config;
    private ArrayList<LuaFile> luaFiles;

}
