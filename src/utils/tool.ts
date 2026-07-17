const toTypeString = <T>(val: T): string => {
  return Object.prototype.toString.call(val);
};
const typeName = <T>(val: T): string => {
  return Object.prototype.toString
    .call(val)
    .replace(/^\[object (\S+)]$/, '$1')
    .toLowerCase();
};
const hasOwnProperty = Object.prototype.hasOwnProperty;
const hasOwn = <T>(val: T, key: string): boolean => hasOwnProperty.call(val, key);
const isFunction = <T>(val: T): boolean => toTypeString(val) === '[object Function]';
const isAsync = <T>(val: T): boolean => toTypeString(val) === '[object AsyncFunction]';
const isObject = <T>(val: T): boolean => val !== null && typeof val === 'object';
const isArray = Array.isArray;
const isString = <T>(val: T): boolean => typeof val === 'string';
const isNumber = <T>(val: T): boolean => typeof val === 'number';
const isBigInt = <T>(val: T): boolean => typeof val === 'bigint';
const isBoolean = <T>(val: T): boolean => typeof val === 'boolean';
const isRegExp = <T>(val: T): boolean => toTypeString(val) === '[object RegExp]';
const isDate = <T>(val: T): boolean => val instanceof Date;
const isMap = <T>(val: T): boolean => toTypeString(val) === '[object Map]';
const isSet = <T>(val: T): boolean => toTypeString(val) === '[object Set]';
const isPromise = <T>(val: T): boolean => toTypeString(val) === '[object Promise]';
const isSymbol = <T>(val: T): boolean => typeof val === 'symbol';
const isNullOrUndefined = <T>(val: T): boolean => {
  if (val === null) return true;
  return typeof val === 'undefined';
};
const rules = {
  // 邮箱的校验
  mainRule: (str: string): boolean => {
    return new RegExp(
      '^(([^<>()\\[\\]\\\\.,;:\\s@"]+(\\.[^<>()\\[\\]\\\\.,;:\\s@"]+)*)|(".+"))@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$',
    ).test(str);
  },

  // 密码校验
  passwordRule: (str: string): boolean => {
    const check= /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[@#!$])[A-Za-z\d@#!$]{8,16}$/;
    if (!check.test(str)) {
      return false
    }else{
      return true
    }
  },
  // 账号
  userNameRule: (str: string): boolean => {
    return new RegExp('^[a-zA-Z0-9_@]{6,16}$').test(str);
  }
};
const logTime = (msg: string, auto = true): object => {
  const start = () => {
    console.time(msg);
  };
  const end = () => {
    console.timeEnd(msg);
  };
  if (auto) start();
  return { start, end };
};

const newMessageError = (msg: string): Error => {
  const e = new Error();
  e.message = msg;
  return e;
};

const isInternetURL = (str: string): boolean => {
  return new RegExp(
    '^(http|https)://([\\w-]+\\.)+[\\w-]+(:([0-9]{1,4}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5]))?(/[\\w-./?%&=#]*)?$',
  ).test(str);
};

const isIpURL = (str: string): boolean => {
  return new RegExp(
    '^((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})(\\.((2(5[0-5]|[0-4]\\d))|[0-1]?\\d{1,2})){3}$',
  ).test(str);
};

const onCharacterEntities = (str: string): string => {
  return str.replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
};

const unCharacterEntities = (str: string): string => {
  return str.replaceAll('&lt;', '<').replaceAll('&gt;', '>').replaceAll('&quot;', '"');
};

export {
  rules,
  // 计时
  logTime,
  // Object.prototype.toString.call(val)
  toTypeString,
  // 获取可以识别的名称
  typeName,
  // hasOwnProperty
  hasOwn,
  // 验证普通函数
  isFunction,
  // 验证 async 的函数
  isAsync,
  // 验证 Promise
  isPromise,
  // 验证 Object
  isObject,
  // 验证数组
  isArray,
  // 验证字符串
  isString,
  // 验证 number
  isNumber,
  // 验证 BigInt
  isBigInt,
  // 验证 布尔
  isBoolean,
  // 验证正则类型
  isRegExp,
  // 验证日期
  isDate,
  // 验证 map
  isMap,
  // 验证 set
  isSet,
  // 验证 Symbol
  isSymbol,
  // null 或者 undefined 返回 true
  isNullOrUndefined,
  // 返回一个标准错误信息
  newMessageError,
  isInternetURL,
  isIpURL,
  onCharacterEntities,
  unCharacterEntities,
};
