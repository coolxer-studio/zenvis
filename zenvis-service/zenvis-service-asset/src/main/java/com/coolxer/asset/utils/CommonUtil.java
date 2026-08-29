package com.coolxer.asset.utils;

import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author yaoqi.li
 */
public class CommonUtil {
  /**
   *
   * @param obj
   * @param attributeName
   * @param <T>
   * @return
   */
  public static <T> String getAttribute(Object obj, String attributeName) {
    try {
      Class<?> objClass = obj.getClass();
      attributeName = StringUtils.capitalize(attributeName);
      Method attributeMethod = objClass.getMethod("get" + attributeName);
      attributeMethod.setAccessible(true);
      return (String) attributeMethod.invoke(obj);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      e.printStackTrace();
      return null;
    }
  }
}
