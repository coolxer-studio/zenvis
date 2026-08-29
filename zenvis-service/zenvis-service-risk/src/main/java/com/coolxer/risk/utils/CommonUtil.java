package com.coolxer.risk.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yaoqi.li
 */
public class CommonUtil {
  /**
   * 拆分集合
   *
   * @param resList 要拆分的集合
   * @param count   每个集合的元素个数
   * @return 返回拆分后的各个集合
   */
  public static <T> List<List<T>> splitList(List<T> resList, int count) {
    List<List<T>> ret = new ArrayList<>();
    int size = resList.size();
    //数据量不足count指定的大小
    if (size <= count) {
      ret.add(resList);
    } else {
      int pre = size / count;
      int last = size % count;
      //前面pre个集合，每个大小都是count个元素
      for (int i = 0; i < pre; i++) {
        List<T> itemList = new ArrayList<>();
        for (int j = 0; j < count; j++) {
          itemList.add(resList.get(i * count + j));
        }
        ret.add(itemList);
      }
      //last的进行处理
      if (last > 0) {
        List<T> itemList = new ArrayList<>();
        for (int i = 0; i < last; i++) {
          itemList.add(resList.get(pre * count + i));
        }
        ret.add(itemList);
      }
    }
    return ret;
  }
}
