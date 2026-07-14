package com.coolxer.service.retrieval.impl;

import com.coolxer.commons.enums.ResultCodeEnum;
import com.coolxer.commons.exception.ApiException;
import com.coolxer.model.retrieval.dto.RequestCriteriaDto;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parser for the deliberately restricted advanced where expression language.
 *
 * <p>The parser is stateless. All cursor/depth/count data belongs to one parse
 * invocation, so a singleton service can safely use the same parser instance.</p>
 */
class WhereExpressionParser {

    static final int MAX_EXPRESSION_LENGTH = 8 * 1024;
    static final int MAX_CONDITION_COUNT = 50;
    static final int MAX_NESTING_DEPTH = 10;
    static final int MAX_IN_VALUES = 200;
    static final int MAX_VALUE_LENGTH = 2 * 1024;

    private static final Pattern FIELD_PATTERN = Pattern.compile("[A-Za-z_][\\w]*");
    private static final Map<String, String> OPERATOR_MAP = Map.of(
            "=", "equal",
            "==", "equal",
            "!=", "notequal",
            "<>", "notequal",
            ">", "greatthan",
            "<", "lessthan",
            ">=", "greatequalthan",
            "<=", "lessequalthan"
    );

    WhereExpression parse(String expression) {
        String normalizedExpression = normalizeExpression(expression);
        ParserState state = new ParserState(tokenize(normalizedExpression));
        WhereNode root = parseOrExpression(state, 0);
        state.expect(TokenType.END, "高级where表达式格式不正确");
        if (state.conditionCount == 0) {
            throw invalid("高级where表达式条件不能为空");
        }
        List<RequestCriteriaDto> criteriaList = new ArrayList<>();
        collectCriteria(root, criteriaList);
        return new WhereExpression(root, root.logicOrDefault(), criteriaList, normalizedExpression);
    }

    private String normalizeExpression(String expression) {
        if (StringUtils.isBlank(expression)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "高级where表达式不能为空");
        }
        String trimmed = expression.trim();
        if (trimmed.length() > MAX_EXPRESSION_LENGTH) {
            throw invalid("高级where表达式长度不能超过" + MAX_EXPRESSION_LENGTH + "个字符");
        }
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("where ")) {
            trimmed = trimmed.substring("where ".length()).trim();
        }
        if (StringUtils.isBlank(trimmed)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "高级where表达式不能为空");
        }
        return trimmed;
    }

    private WhereNode parseOrExpression(ParserState state, int depth) {
        WhereNode left = parseAndExpression(state, depth);
        while (state.matchKeyword("or")) {
            left = group("or", left, parseAndExpression(state, depth));
        }
        return left;
    }

    private WhereNode parseAndExpression(ParserState state, int depth) {
        WhereNode left = parsePrimary(state, depth);
        while (state.matchKeyword("and")) {
            left = group("and", left, parsePrimary(state, depth));
        }
        return left;
    }

    private WhereNode parsePrimary(ParserState state, int depth) {
        if (state.match(TokenType.LPAREN)) {
            int nestedDepth = depth + 1;
            if (nestedDepth > MAX_NESTING_DEPTH) {
                throw invalid("高级where表达式括号嵌套不能超过" + MAX_NESTING_DEPTH + "层");
            }
            WhereNode nested = parseOrExpression(state, nestedDepth);
            state.expect(TokenType.RPAREN, "高级where表达式括号不匹配");
            return nested;
        }
        state.conditionCount++;
        if (state.conditionCount > MAX_CONDITION_COUNT) {
            throw invalid("高级where表达式条件不能超过" + MAX_CONDITION_COUNT + "个");
        }
        return WhereNode.condition(parseCondition(state));
    }

    private RequestCriteriaDto parseCondition(ParserState state) {
        String field = normalizeField(state.expectIdentifier("高级where表达式字段不合法"));
        validateField(field);

        if (state.matchKeyword("between")) {
            return criteria(field, "between", List.of(parseValue(state), parseBetweenSecondValue(state)));
        }
        if (state.matchKeyword("in")) {
            state.expect(TokenType.LPAREN, "in操作符需要使用括号");
            List<String> values = new ArrayList<>();
            values.add(parseValue(state));
            while (state.match(TokenType.COMMA)) {
                if (values.size() >= MAX_IN_VALUES) {
                    throw invalid("in操作符的值不能超过" + MAX_IN_VALUES + "个");
                }
                values.add(parseValue(state));
            }
            state.expect(TokenType.RPAREN, "in操作符括号不匹配");
            return criteria(field, "in", values);
        }
        if (state.matchKeyword("like")) {
            return criteria(field, "match", List.of(normalizeLikeValue(parseValue(state))));
        }
        if (state.matchKeyword("is")) {
            if (state.matchKeyword("not")) {
                expectNullKeyword(state);
                return criteria(field, "isnotnull", List.of());
            }
            expectNullKeyword(state);
            return criteria(field, "isnull", List.of());
        }

        Token operatorToken = state.peek();
        if (operatorToken.type() == TokenType.IDENT) {
            String operator = operatorToken.text().toLowerCase(Locale.ROOT);
            if (List.of("isnull", "isnotnull").contains(operator)) {
                state.advance();
                return criteria(field, operator, List.of());
            }
            if (List.of("equal", "notequal", "match", "contains", "greatthan", "lessthan", "greatequalthan", "lessequalthan").contains(operator)) {
                state.advance();
                return criteria(field, "contains".equals(operator) ? "match" : operator, List.of(parseValue(state)));
            }
        }
        if (operatorToken.type() == TokenType.OPERATOR && OPERATOR_MAP.containsKey(operatorToken.text())) {
            state.advance();
            return criteria(field, OPERATOR_MAP.get(operatorToken.text()), List.of(parseValue(state)));
        }
        throw invalid("高级where表达式操作符不正确");
    }

    private void expectNullKeyword(ParserState state) {
        if (!state.matchKeyword("null")) {
            throw invalid("is操作符仅支持null判断");
        }
    }

    private String parseBetweenSecondValue(ParserState state) {
        if (!state.matchKeyword("and")) {
            throw invalid("between操作符需要两个值");
        }
        return parseValue(state);
    }

    private RequestCriteriaDto criteria(String attribute, String operator, List<String> values) {
        RequestCriteriaDto criteria = new RequestCriteriaDto();
        criteria.setAttribute(attribute);
        criteria.setOperator(operator);
        criteria.setValueList(values);
        return criteria;
    }

    private WhereNode group(String logic, WhereNode left, WhereNode right) {
        List<WhereNode> children = new ArrayList<>();
        appendGroupChild(children, logic, left);
        appendGroupChild(children, logic, right);
        return WhereNode.group(logic, children);
    }

    private void appendGroupChild(List<WhereNode> children, String logic, WhereNode node) {
        if ("group".equals(node.type()) && logic.equals(node.logic())) {
            children.addAll(node.children());
        } else {
            children.add(node);
        }
    }

    private void collectCriteria(WhereNode node, List<RequestCriteriaDto> criteriaList) {
        if ("condition".equals(node.type())) {
            criteriaList.add(node.criteria());
            return;
        }
        node.children().forEach(child -> collectCriteria(child, criteriaList));
    }

    private List<Token> tokenize(String source) {
        List<Token> result = new ArrayList<>();
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '`') {
                int end = source.indexOf('`', index + 1);
                if (end < 0) {
                    throw invalid("高级where表达式反引号不匹配");
                }
                String identifier = source.substring(index + 1, end);
                result.add(new Token(TokenType.IDENT, "`" + identifier + "`"));
                index = end + 1;
                continue;
            }
            char quoteEnd = quoteEnd(current);
            if (quoteEnd != 0) {
                StringBuilder value = new StringBuilder();
                index++;
                boolean closed = false;
                while (index < source.length()) {
                    char quoted = source.charAt(index);
                    if (quoted == quoteEnd) {
                        if ((current == '\'' || current == '"') && index + 1 < source.length() && source.charAt(index + 1) == quoteEnd) {
                            value.append(quoted);
                            index += 2;
                            continue;
                        }
                        index++;
                        closed = true;
                        break;
                    }
                    value.append(quoted);
                    index++;
                }
                if (!closed) {
                    throw invalid("高级where表达式引号不匹配");
                }
                result.add(new Token(TokenType.STRING, value.toString()));
                continue;
            }
            if (current == '(') {
                result.add(new Token(TokenType.LPAREN, "("));
                index++;
                continue;
            }
            if (current == ')') {
                result.add(new Token(TokenType.RPAREN, ")"));
                index++;
                continue;
            }
            if (current == ',') {
                result.add(new Token(TokenType.COMMA, ","));
                index++;
                continue;
            }
            String twoChars = index + 1 < source.length() ? source.substring(index, index + 2) : "";
            if (List.of(">=", "<=", "!=", "<>", "==").contains(twoChars)) {
                result.add(new Token(TokenType.OPERATOR, twoChars));
                index += 2;
                continue;
            }
            if (current == '=' || current == '>' || current == '<') {
                result.add(new Token(TokenType.OPERATOR, Character.toString(current)));
                index++;
                continue;
            }
            int start = index;
            while (index < source.length() && !Character.isWhitespace(source.charAt(index)) && !isDelimiter(source.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw invalid("高级where表达式格式不正确");
            }
            result.add(new Token(TokenType.IDENT, source.substring(start, index)));
        }
        result.add(new Token(TokenType.END, ""));
        return List.copyOf(result);
    }

    private boolean isDelimiter(char value) {
        return value == '(' || value == ')' || value == ',' || value == '=' || value == '>' || value == '<' || value == '!';
    }

    private String parseValue(ParserState state) {
        Token token = state.peek();
        if (token.type() != TokenType.STRING && token.type() != TokenType.IDENT) {
            throw invalid("检索条件值不能为空");
        }
        state.advance();
        String value = token.text().trim();
        if (StringUtils.isBlank(value)) {
            throw new ApiException(ResultCodeEnum.FIELD_IS_EMPTY.getCode(), "检索条件值不能为空");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw invalid("检索条件单值不能超过" + MAX_VALUE_LENGTH + "个字符");
        }
        if (token.type() == TokenType.IDENT && isUnsafeRawValue(value)) {
            throw invalid("高级where表达式值必须为数字、无空格文本或引号包裹文本");
        }
        return value;
    }

    private boolean isUnsafeRawValue(String value) {
        return StringUtils.containsWhitespace(value)
                || value.contains(";")
                || value.contains("--")
                || value.contains("/*")
                || value.contains("*/");
    }

    private String normalizeLikeValue(String value) {
        return StringUtils.removeEnd(StringUtils.removeStart(value, "%"), "%");
    }

    private String normalizeField(String field) {
        if (field != null && field.length() >= 2 && field.startsWith("`") && field.endsWith("`")) {
            return field.substring(1, field.length() - 1);
        }
        return field;
    }

    private void validateField(String field) {
        if (!FIELD_PATTERN.matcher(StringUtils.defaultString(field)).matches()) {
            throw invalid("高级where表达式字段不合法: " + field);
        }
    }

    private char quoteEnd(char current) {
        return switch (current) {
            case '\'' -> '\'';
            case '"' -> '"';
            case '‘' -> '’';
            case '“' -> '”';
            default -> 0;
        };
    }

    private ApiException invalid(String message) {
        return new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), message);
    }

    record WhereExpression(WhereNode root, String logic, List<RequestCriteriaDto> criteriaList, String normalizedExpression) {
    }

    record WhereNode(String type, String logic, RequestCriteriaDto criteria, List<WhereNode> children) {

        static WhereNode condition(RequestCriteriaDto criteria) {
            return new WhereNode("condition", null, criteria, List.of());
        }

        static WhereNode group(String logic, List<WhereNode> children) {
            return new WhereNode("group", logic, null, List.copyOf(children));
        }

        String logicOrDefault() {
            return StringUtils.defaultIfBlank(logic, "and");
        }
    }

    private static final class ParserState {
        private final List<Token> tokens;
        private int position;
        private int conditionCount;

        private ParserState(List<Token> tokens) {
            this.tokens = tokens;
        }

        private Token peek() {
            return tokens.get(position);
        }

        private void advance() {
            position++;
        }

        private Token expect(TokenType type, String message) {
            Token token = peek();
            if (token.type() != type) {
                throw new ApiException(ResultCodeEnum.NO_SUPPORTED.getCode(), message);
            }
            advance();
            return token;
        }

        private String expectIdentifier(String message) {
            return expect(TokenType.IDENT, message).text();
        }

        private boolean match(TokenType type) {
            if (peek().type() == type) {
                advance();
                return true;
            }
            return false;
        }

        private boolean matchKeyword(String keyword) {
            Token token = peek();
            if (token.type() == TokenType.IDENT && keyword.equalsIgnoreCase(token.text())) {
                advance();
                return true;
            }
            return false;
        }
    }

    private enum TokenType {
        IDENT,
        STRING,
        OPERATOR,
        LPAREN,
        RPAREN,
        COMMA,
        END
    }

    private record Token(TokenType type, String text) {
    }
}
