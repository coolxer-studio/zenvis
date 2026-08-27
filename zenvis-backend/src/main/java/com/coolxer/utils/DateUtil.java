package com.coolxer.utils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 时间工具类
 */
public class DateUtil {


    private static final String FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(FORMAT);
    private static final String FORMAT_01 = "MM-dd";
    public static final SimpleDateFormat SIMPLE_DATE_FORMAT_01 = new SimpleDateFormat(FORMAT_01);
    public static final List<String> HOUR_LIST = List.of("00:00", "01:00", "02:00", "03:00", "04:00", "05:00", "06:00", "07:00", "08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00", "23:00");


    /**
     * Date转LocalDate
     *
     * @param date
     */
    public static LocalDate date2LocalDate(Date date) {
        if (null == date) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static Date localDate2Date(LocalDate localDate) {
        if (null == localDate) {
            return null;
        }
        ZonedDateTime zonedDateTime = localDate.atStartOfDay(ZoneId.systemDefault());
        return Date.from(zonedDateTime.toInstant());
    }

    public static List<Date> getDateRange(LocalDate startDate, LocalDate endDate) {
        List<Date> dateRange = new ArrayList<>();
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);

        for (int i = 0; i <= daysBetween; i++) {
            LocalDate currentDate = startDate.plusDays(i);
            dateRange.add(localDate2Date(currentDate));
        }

        return dateRange;
    }

    public static List<Date> getDateRange(Date startDate, Date endDate) {
        return getDateRange(date2LocalDate(startDate), date2LocalDate(endDate));
    }

    public static List<String> getDateRangeWithFormat(Date startDate, Date endDate, String format) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
        List<String> dateStringRange = new ArrayList<>();
        getDateRange(date2LocalDate(startDate), date2LocalDate(endDate)).forEach(date -> {
            dateStringRange.add(simpleDateFormat.format(date));
        });
        return dateStringRange;
    }


    public static List<String> getDateRangeWithFormat01(Date startTime, Date endTime) {
        return getDateRangeWithFormat(startTime, endTime, FORMAT_01);
    }

    /**
     * 解析日期字符串为Date对象
     *
     * @param dateStr 日期字符串，支持多种格式
     * @return Date对象，解析失败返回null
     */
    public static Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        // 支持的日期格式列表
        String[] formats = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm",
                "yyyy/MM/dd",
                "yyyyMMddHHmmss",
                "yyyyMMdd",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setLenient(false); // 严格模式
                return sdf.parse(dateStr.trim());
            } catch (Exception e) {
                // 继续尝试下一个格式
                continue;
            }
        }

        // 如果所有格式都解析失败，返回null
        return null;
    }

    /**
     * 格式化Date对象为字符串
     *
     * @param date Date对象
     * @return 格式化后的字符串，格式为 yyyy-MM-dd HH:mm:ss
     */
    public static String formatDate(Date date) {
        if (date == null) {
            return null;
        }
        return SIMPLE_DATE_FORMAT.format(date);
    }

    /**
     * 格式化Date对象为指定格式的字符串
     *
     * @param date   Date对象
     * @param format 日期格式
     * @return 格式化后的字符串
     */
    public static String formatDate(Date date, String format) {
        if (date == null || format == null || format.trim().isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.format(date);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前时间的字符串表示
     *
     * @return 当前时间字符串，格式为 yyyy-MM-dd HH:mm:ss
     */
    public static String getCurrentDateTime() {
        return SIMPLE_DATE_FORMAT.format(new Date());
    }

    /**
     * 获取当前日期的字符串表示
     *
     * @return 当前日期字符串，格式为 yyyy-MM-dd
     */
    public static String getCurrentDate() {
        return formatDate(new Date(), "yyyy-MM-dd");
    }

    /**
     * 获取当前时间的字符串表示
     *
     * @return 当前时间字符串，格式为 HH:mm:ss
     */
    public static String getCurrentTime() {
        return formatDate(new Date(), "HH:mm:ss");
    }

    /**
     * 判断字符串是否为有效的日期格式
     *
     * @param dateStr 日期字符串
     * @return true表示有效，false表示无效
     */
    public static boolean isValidDate(String dateStr) {
        return parseDate(dateStr) != null;
    }

    /**
     * 获取两个日期之间的天数差
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 天数差
     */
    public static long getDaysBetween(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        long diffInMillies = Math.abs(endDate.getTime() - startDate.getTime());
        return diffInMillies / (24 * 60 * 60 * 1000);
    }

    /**
     * 获取两个日期之间的小时数差
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 小时数差
     */
    public static long getHoursBetween(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        long diffInMillies = Math.abs(endDate.getTime() - startDate.getTime());
        return diffInMillies / (60 * 60 * 1000);
    }

    /**
     * 获取两个日期之间的分钟数差
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 分钟数差
     */
    public static long getMinutesBetween(Date startDate, Date endDate) {
        if (startDate == null || endDate == null) {
            return 0;
        }
        long diffInMillies = Math.abs(endDate.getTime() - startDate.getTime());
        return diffInMillies / (60 * 1000);
    }

    /**
     * 获取指定日期是星期几
     *
     * @param date 日期
     * @return 星期几（1-7，1表示星期一，7表示星期日）
     */
    public static int getDayOfWeek(Date date) {
        if (date == null) {
            return 0;
        }
        LocalDate localDate = date2LocalDate(date);
        return localDate.getDayOfWeek().getValue();
    }

    /**
     * 获取指定日期是星期几的中文表示
     *
     * @param date 日期
     * @return 星期几的中文表示
     */
    public static String getDayOfWeekChinese(Date date) {
        if (date == null) {
            return "";
        }
        int dayOfWeek = getDayOfWeek(date);
        String[] weekDays = {"", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"};
        return weekDays[dayOfWeek];
    }

    /**
     * 获取指定日期是当月的第几天
     *
     * @param date 日期
     * @return 当月的第几天
     */
    public static int getDayOfMonth(Date date) {
        if (date == null) {
            return 0;
        }
        LocalDate localDate = date2LocalDate(date);
        return localDate.getDayOfMonth();
    }

    /**
     * 获取指定日期是当年的第几天
     *
     * @param date 日期
     * @return 当年的第几天
     */
    public static int getDayOfYear(Date date) {
        if (date == null) {
            return 0;
        }
        LocalDate localDate = date2LocalDate(date);
        return localDate.getDayOfYear();
    }

    /**
     * 获取指定日期是当年的第几周
     *
     * @param date 日期
     * @return 当年的第几周
     */
    public static int getWeekOfYear(Date date) {
        if (date == null) {
            return 0;
        }
        LocalDate localDate = date2LocalDate(date);
        return localDate.get(java.time.temporal.WeekFields.ISO.weekOfYear());
    }

    /**
     * 获取指定日期是当月的第几周
     *
     * @param date 日期
     * @return 当月的第几周
     */
    public static int getWeekOfMonth(Date date) {
        if (date == null) {
            return 0;
        }
        LocalDate localDate = date2LocalDate(date);
        return localDate.get(java.time.temporal.WeekFields.ISO.weekOfMonth());
    }

    /**
     * 获取指定日期所在月份的天数
     *
     * @param date 日期
     * @return 月份的天数
     */
    public static int getDaysInMonth(Date date) {
        if (date == null) {
            return 0;
        }
        LocalDate localDate = date2LocalDate(date);
        return localDate.lengthOfMonth();
    }

    /**
     * 获取指定日期所在年份的天数
     *
     * @param date 日期
     * @return 年份的天数
     */
    public static int getDaysInYear(Date date) {
        if (date == null) {
            return 0;
        }
        LocalDate localDate = date2LocalDate(date);
        return localDate.lengthOfYear();
    }

    /**
     * 判断指定日期是否为闰年
     *
     * @param date 日期
     * @return true表示闰年，false表示平年
     */
    public static boolean isLeapYear(Date date) {
        if (date == null) {
            return false;
        }
        LocalDate localDate = date2LocalDate(date);
        return localDate.isLeapYear();
    }

    /**
     * 获取指定日期加上指定天数后的日期
     *
     * @param date 原日期
     * @param days 要加的天数
     * @return 新日期
     */
    public static Date addDays(Date date, int days) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date2LocalDate(date);
        LocalDate newLocalDate = localDate.plusDays(days);
        return localDate2Date(newLocalDate);
    }

    /**
     * 获取指定日期减去指定天数后的日期
     *
     * @param date 原日期
     * @param days 要减的天数
     * @return 新日期
     */
    public static Date subtractDays(Date date, int days) {
        return addDays(date, -days);
    }

    /**
     * 获取指定日期加上指定月数后的日期
     *
     * @param date   原日期
     * @param months 要加的月数
     * @return 新日期
     */
    public static Date addMonths(Date date, int months) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date2LocalDate(date);
        LocalDate newLocalDate = localDate.plusMonths(months);
        return localDate2Date(newLocalDate);
    }

    /**
     * 获取指定日期减去指定月数后的日期
     *
     * @param date   原日期
     * @param months 要减的月数
     * @return 新日期
     */
    public static Date subtractMonths(Date date, int months) {
        return addMonths(date, -months);
    }

    /**
     * 获取指定日期加上指定年数后的日期
     *
     * @param date  原日期
     * @param years 要加的年数
     * @return 新日期
     */
    public static Date addYears(Date date, int years) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date2LocalDate(date);
        LocalDate newLocalDate = localDate.plusYears(years);
        return localDate2Date(newLocalDate);
    }

    /**
     * 获取指定日期减去指定年数后的日期
     *
     * @param date  原日期
     * @param years 要减的年数
     * @return 新日期
     */
    public static Date subtractYears(Date date, int years) {
        return addYears(date, -years);
    }

    /**
     * 获取指定日期的开始时间（00:00:00）
     *
     * @param date 日期
     * @return 开始时间
     */
    public static Date getStartOfDay(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date2LocalDate(date);
        return localDate2Date(localDate);
    }

    /**
     * 获取指定日期的结束时间（23:59:59）
     *
     * @param date 日期
     * @return 结束时间
     */
    public static Date getEndOfDay(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date2LocalDate(date);
        ZonedDateTime zonedDateTime = localDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault());
        return Date.from(zonedDateTime.toInstant());
    }

    /**
     * 获取指定日期所在月份的开始日期
     *
     * @param date 日期
     * @return 月份开始日期
     */
    public static Date getStartOfMonth(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date2LocalDate(date);
        LocalDate startOfMonth = localDate.withDayOfMonth(1);
        return localDate2Date(startOfMonth);
    }

    /**
     * 获取指定日期所在月份的结束日期
     *
     * @param date 日期
     * @return 月份结束日期
     */
    public static Date getEndOfMonth(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date2LocalDate(date);
        LocalDate endOfMonth = localDate.withDayOfMonth(localDate.lengthOfMonth());
        return localDate2Date(endOfMonth);
    }

    /**
     * 获取指定日期所在年份的开始日期
     *
     * @param date 日期
     * @return 年份开始日期
     */
    public static Date getStartOfYear(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date2LocalDate(date);
        LocalDate startOfYear = localDate.withDayOfYear(1);
        return localDate2Date(startOfYear);
    }

    /**
     * 获取指定日期所在年份的结束日期
     *
     * @param date 日期
     * @return 年份结束日期
     */
    public static Date getEndOfYear(Date date) {
        if (date == null) {
            return null;
        }
        LocalDate localDate = date2LocalDate(date);
        LocalDate endOfYear = localDate.withDayOfYear(localDate.lengthOfYear());
        return localDate2Date(endOfYear);
    }

}
