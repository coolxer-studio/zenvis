package com.coolxer.risk.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yaoqi.li
 */
@Slf4j
public class DateUtil {


  /**
   * 天级别format格式
   */
  public static final String YYYYMMDD = "yyyyMMdd";
  private static final String FORMAT = "yyyy-MM-dd HH:mm:ss";
  public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(FORMAT);
  private static final String YYYY_MM_DD_01 = "yyyyMMdd";
  public static final SimpleDateFormat SIMPLE_DATE_FORMAT_YYYY_MM_DD_01 = new SimpleDateFormat(YYYY_MM_DD_01);

  private DateUtil() {
  }

  /**
   * 天级的时间格式化
   *
   * @param time
   * @return
   */
  public static String formatDayTime(long time) {
    // 用dateformatUtil
    return DateFormatUtils.format(time, YYYYMMDD);
  }

  public static long formatDayTime(String timeStr) {
    long time = 0;
    try {
      time = new SimpleDateFormat(YYYYMMDD).parse(timeStr).getTime();
    } catch (Exception e) {
      log.error("format date error", e);
    }
    return time;
  }

  /**
   * 秒级的时间格式化
   *
   * @param time
   * @return
   */
  public static String formatSecondsTime(long time) {
    return new SimpleDateFormat("yyyyMMddHHmmss").format(time);
  }

  /**
   * 获取计算期开始时间,单位：毫秒
   *
   * @param calculationPeriodDays 计算周期
   * @param endTime               计算周期结束时间
   * @return 计算期开始时间, 单位：毫秒 传入7天，结果为今天加上前六天
   */
  public static long getCalculationPeriodStartTime(int calculationPeriodDays, long endTime) {
    return org.apache.commons.lang3.time.DateUtils
        .addDays(new Date(endTime), -(calculationPeriodDays - 1)).getTime();
  }

  /**
   * <pre>
   *   getDaysByStartAndEnd(l1,l2)->[20221102,20221103]
   * </pre>
   *
   * @param startTime 开始时间
   * @param endTime   结束时间
   * @return 天数的列表
   */
  public static List<String> getDaysByStartAndEnd(long startTime, long endTime) {
    List<String> list = new ArrayList<>();
    Calendar calendar = Calendar.getInstance();
    while (startTime <= endTime) {
      list.add(new SimpleDateFormat(YYYYMMDD).format(startTime));
      calendar.setTime(new Date(startTime));
      calendar.add(Calendar.DATE, 1);
      startTime = calendar.getTimeInMillis();
    }
    return list;
  }

  /**
   * 根据传入时间获取起始和结束时间时间戳.
   *
   * @param strStart 开始时间
   * @param strEnd   结束时间
   * @return long类型的起始时间和结束时间数组
   */
  public static long[] getLongRangeTime(String strStart, String strEnd) {
    String[] timeRange = {strStart, strEnd};
    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    long[] times = new long[]{0, 0};
    try {
      Date dateStart;
      if (isDateFormat(timeRange[0])) {
        dateStart = simpleDateFormat2.parse(timeRange[0]);
      } else {
        dateStart = simpleDateFormat.parse(timeRange[0]);
      }
      times[0] = dateStart.getTime();

      Date dateEnd;
      if (isDateFormat(timeRange[1])) {
        dateEnd = simpleDateFormat2.parse(timeRange[1]);
      } else {
        // 获取结束时间
        strEnd = timeRange[1] + " 23:59:59";
        dateEnd = simpleDateFormat2.parse(strEnd);
      }
      times[1] = dateEnd.getTime();
    } catch (ParseException e) {
      log.error("", e);
    }
    return times;
  }

  /**
   * 判读时间是否为  yyyy-MM-dd HH:mm:ss  格式
   *
   * @param timeStr
   * @return
   */
  public static boolean isDateFormat(String timeStr) {
    String regex = "\\d{4}-\\d{2}-\\d{2}\\s{1}\\d{2}:\\d{2}:\\d{2}";
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(timeStr);
    return matcher.matches();
  }

}
