//prettier.config.js
module.exports = {
  endOfLine: 'auto',

  // 每行代码长度（默认80）
  printWidth: 100,
  // 每个tab相当于多少个空格（默认2）ab进行缩进（默认false）
  tabWidth: 2,
  // 是否使用tab
  useTabs: false,
  // 使用单引号（默认false）
  singleQuote: true,
  // 声明结尾使用分号(默认true)
  semi: true,
  // 多行使用拖尾逗号（默认none）
  trailingComma: 'all',
  // 对象字面量的大括号间使用空格（默认true）
  bracketSpacing: true,
  // 多行JSX中的>放置在最后一行的结尾，而不是另起一行（默认false）
  // jsxBracketSameLine: false,
  // 箭头函数参数括号 默认avoid 可选 avoid| always
  // avoid 能省略括号的时候就省略 例如x => x
  // always 总是有括号
  arrowParens: 'avoid',
};
