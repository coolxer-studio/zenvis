package com.coolxer.service.system.impl;

import cn.hutool.core.lang.tree.Tree;
import cn.hutool.core.lang.tree.TreeNode;
import cn.hutool.core.lang.tree.TreeNodeConfig;
import cn.hutool.core.lang.tree.TreeUtil;
import com.coolxer.commons.enums.MenuType;
import com.coolxer.model.system.vo.MenuVo;
import com.coolxer.service.system.PermissionTreeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限树接口实现
 */
@Slf4j
@Service
public class PermissionTreeServiceImpl implements PermissionTreeService {

    /**
     * 返回前端字段-节点标签
     */
    private static final String LABEL = "label";

    /**
     * 返回前端字段-节点值
     */
    private static final String VALUE = "value";


    /**
     * 返回前端字段-层级
     */
    private static final String LEVEL = "level";

    /**
     * 返回前端字段-编码
     */
    private static final String CODE = "code";

    /**
     * 返回前端字段-角标
     */
    private static final String SUPERSCRIPT = "superscript";

    /**
     * 返回前端字段-路由
     */
    private static final String ROUTE = "route";

    /**
     * 返回前端字段-参数
     */
    private static final String PARAMS = "params";
    /**
     * 树起始节点
     */
    private static final String ROOT_ID = "0";

    /**
     * 返回前端字段顺序
     */
    private static final String ORDER_NUM = "weight";

    /**
     * 返回前端字段id
     */
    private static final String ID = "id";

    /**
     * 返回前端字段父级id
     */
    private static final String PARENT_ID = "pid";

    /**
     * 菜单树深度
     */
    private static final int MAX_DEEP = 3;

    /**
     * map初始大小
     */
    private static final int INITIAL_CAPACITY = 4;

    @Override
    public List<Tree<String>> getPermissionTreeFromMenu(List<MenuVo> menuVoList) {
        Base64.Encoder urlEncoder = Base64.getUrlEncoder();
        List<TreeNode<Integer>> nodeList = menuVoList.stream().map(menu -> {
            Map<String, Object> map = new HashMap<>(INITIAL_CAPACITY);
            map.put(LEVEL, menu.getLevel());
            map.put(CODE, menu.getType().name() + "_" + menu.getId());
            map.put(SUPERSCRIPT, menu.getSuperscript());
            map.put(ROUTE, menu.getRoute());
            // EXTERNAL_APP、HTML_PAGE需要对参数base64编码
            map.put(PARAMS, (menu.getType() == MenuType.EXTERNAL_APP || menu.getType() == MenuType.HTML_PAGE) ? urlEncoder.encodeToString(menu.getParams().getBytes()) : menu.getParams());
            return new TreeNode<Integer>()
                    .setId(menu.getId())
                    .setName(menu.getName())
                    .setParentId(menu.getParentId())
                    .setWeight(menu.getOrderNumber())
                    .setExtra(map);
        }).toList();

        //配置
        TreeNodeConfig treeNodeConfig = new TreeNodeConfig();
        // 自定义属性名 都要默认值的
        treeNodeConfig.setWeightKey(ORDER_NUM);
        treeNodeConfig.setIdKey(ID);
        treeNodeConfig.setParentIdKey(PARENT_ID);
        // 最大递归深度
        treeNodeConfig.setDeep(MAX_DEEP);

        //转换器
        return TreeUtil.build(nodeList, ROOT_ID, treeNodeConfig,
                (treeNode, tree) -> {
                    tree.setId(String.valueOf(treeNode.getId()));
                    tree.setParentId(String.valueOf(treeNode.getParentId()));
                    tree.setWeight(treeNode.getWeight());
                    tree.setName(treeNode.getName());
                    // 扩展属性
                    tree.putExtra(LABEL, treeNode.getName());
                    tree.putExtra(VALUE, String.valueOf(treeNode.getId()));
                    tree.putExtra(LEVEL, treeNode.getExtra().get(LEVEL));
                    tree.putExtra(CODE, treeNode.getExtra().get(CODE));
                    tree.putExtra(SUPERSCRIPT, treeNode.getExtra().get(SUPERSCRIPT));
                    tree.putExtra(ROUTE, treeNode.getExtra().get(ROUTE));
                    tree.putExtra(PARAMS, treeNode.getExtra().get(PARAMS));
                });
    }
}
